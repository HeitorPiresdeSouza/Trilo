package br.eti.hpds.fastFurious.service;

import br.eti.hpds.fastFurious.domain.model.ItemPedido;
import br.eti.hpds.fastFurious.domain.model.OpcaoPagamento;
import br.eti.hpds.fastFurious.domain.model.Pedido;
import br.eti.hpds.fastFurious.domain.model.Produto;
import br.eti.hpds.fastFurious.domain.model.StatusPedido;
import br.eti.hpds.fastFurious.domain.repository.PedidoRepository;
import br.eti.hpds.fastFurious.domain.repository.ProdutoRepository;
import br.eti.hpds.fastFurious.exceptionhandler.RegraNegocioException;
import br.eti.hpds.fastFurious.impressora.Cupom;
import br.eti.hpds.fastFurious.impressora.ImpressoraCupom;
import br.eti.hpds.fastFurious.pagamento.PagamentoTerminal;
import br.eti.hpds.fastFurious.pagamento.ResultadoPagamento;
import br.eti.hpds.fastFurious.pagamento.StatusPagamento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final PagamentoTerminal terminal;
    private final ObjectProvider<ImpressoraCupom> impressora;
    private final TransactionTemplate tx;
    private final String lojaId;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProdutoRepository produtoRepository,
                         PagamentoTerminal terminal,
                         ObjectProvider<ImpressoraCupom> impressora,
                         PlatformTransactionManager txManager,
                         @Value("${impressora.loja-id:loja-1}") String lojaId) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.terminal = terminal;
        this.impressora = impressora;
        this.tx = new TransactionTemplate(txManager);
        this.lojaId = lojaId;
    }

    // ------------------------------------------------------------------ criação

    /**
     * Cria o pedido como AGUARDANDO_PAGAMENTO, calcula o total no servidor e, se o pagamento
     * for por cartão/PIX, envia a cobrança à maquininha. Não é @Transactional de propósito:
     * a chamada HTTP à Cielo não deve segurar uma transação de banco aberta.
     */
    public Pedido criar(Pedido pedido) {
        if (pedido.getListaItens() == null || pedido.getListaItens().isEmpty()) {
            throw new RegraNegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "O pedido precisa ter ao menos um item");
        }

        pedido.setId(null);
        pedido.setDataAbertura(LocalDateTime.now());
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido.setDataPagamento(null);
        pedido.setDataCancelado(null);
        pedido.setTransacaoId(null);

        BigDecimal total = BigDecimal.ZERO;
        for (ItemPedido item : pedido.getListaItens()) {
            if (item.getProduto() == null || item.getProduto().getId() == null) {
                throw new RegraNegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "Todo item precisa informar o produto (id)");
            }
            Long produtoId = item.getProduto().getId();
            Produto produto = produtoRepository.findById(produtoId).orElseThrow(() ->
                    new RegraNegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "Produto não encontrado: " + produtoId));
            if (produto.getValor() == null) {
                throw new RegraNegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "Produto sem valor cadastrado: " + produtoId);
            }

            int quantidade = item.getQuantidade() == null ? 1 : item.getQuantidade();
            if (quantidade < 1) {
                throw new RegraNegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "Quantidade inválida para o produto " + produtoId);
            }

            item.setId(null);
            item.setQuantidade(quantidade);
            item.setProduto(produto);
            item.setvUnit(produto.getValor());   // o preço vem do banco, nunca do cliente
            item.setPedido(pedido);              // vínculo bidirecional

            total = total.add(produto.getValor().multiply(BigDecimal.valueOf(quantidade)));
        }
        pedido.setValorTotal(total);

        Pedido salvo = pedidoRepository.save(pedido);

        if (salvo.getPagamento() != OpcaoPagamento.DINHEIRO) {
            iniciarCobranca(salvo);
        }
        return salvo;
    }

    private void iniciarCobranca(Pedido pedido) {
        try {
            ResultadoPagamento r = terminal.cobrar(
                    pedido.getValorTotal(), pedido.getPagamento(), String.valueOf(pedido.getId()));
            pedido.setTransacaoId(r.transacaoId());
            pedidoRepository.save(pedido);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar a cobrança do pedido {} à maquininha", pedido.getId(), e);
            pedido.setStatus(StatusPedido.CANCELADO);
            pedido.setDataCancelado(LocalDateTime.now());
            pedidoRepository.save(pedido);
            throw new RegraNegocioException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível enviar a cobrança para a maquininha. Tente novamente.");
        }
    }

    // ------------------------------------------------------------------ pagamento

    /** Consulta a adquirente e, se o pedido foi pago (ou cancelado lá), atualiza o nosso lado. */
    public void verificarPagamento(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId).orElse(null);
        if (pedido == null
                || pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO
                || pedido.getTransacaoId() == null) {
            return;
        }

        StatusPagamento situacao = terminal.consultar(pedido.getTransacaoId());
        if (situacao == StatusPagamento.PAGO) {
            confirmarPagamento(pedidoId);
        } else if (situacao == StatusPagamento.CANCELADO) {
            cancelarSeAguardando(pedidoId);
        }
    }

    /**
     * Marca o pedido como pago (ABERTO) e imprime o cupom. Idempotente: se o pedido já não está
     * AGUARDANDO_PAGAMENTO, não faz nada. Retorna true se este chamado foi quem confirmou.
     * A impressão acontece DEPOIS do commit: falha na impressora não desfaz o pagamento.
     */
    public boolean confirmarPagamento(Long pedidoId) {
        Cupom cupom = tx.execute(status -> {
            Pedido p = pedidoRepository.buscarParaAtualizar(pedidoId).orElse(null);
            if (p == null || p.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
                return null;
            }
            p.setStatus(StatusPedido.ABERTO);
            p.setDataPagamento(LocalDateTime.now());
            pedidoRepository.save(p);
            return montarCupom(p); // monta dentro da transação (itens são lazy)
        });

        if (cupom == null) {
            log.debug("Pedido {} já confirmado (ou inexistente); nada a fazer", pedidoId);
            return false;
        }
        imprimir(cupom);
        return true;
    }

    /** Atendente confirma o recebimento em dinheiro. */
    public Optional<Pedido> confirmarPagamentoDinheiro(Long pedidoId) {
        Optional<Pedido> opt = pedidoRepository.findById(pedidoId);
        if (opt.isEmpty()) {
            return opt;
        }
        if (opt.get().getPagamento() != OpcaoPagamento.DINHEIRO) {
            throw new RegraNegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Este pedido não é em dinheiro; o pagamento é confirmado pela maquininha");
        }
        confirmarPagamento(pedidoId);
        return pedidoRepository.findById(pedidoId);
    }

    private void cancelarSeAguardando(Long pedidoId) {
        tx.executeWithoutResult(status -> pedidoRepository.buscarParaAtualizar(pedidoId).ifPresent(p -> {
            if (p.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO) {
                p.setStatus(StatusPedido.CANCELADO);
                p.setDataCancelado(LocalDateTime.now());
                pedidoRepository.save(p);
            }
        }));
    }

    private Cupom montarCupom(Pedido p) {
        List<Cupom.Item> itens = new ArrayList<>();
        for (ItemPedido i : p.getListaItens()) {
            Produto produto = i.getProduto();
            String nome = produto.getName() != null ? produto.getName() : produto.getDescricao();
            itens.add(new Cupom.Item(i.getQuantidade() + "x " + nome,
                    i.getvUnit().multiply(BigDecimal.valueOf(i.getQuantidade()))));
        }
        return new Cupom(lojaId, String.valueOf(p.getId()), itens);
    }

    private void imprimir(Cupom cupom) {
        ImpressoraCupom imp = impressora.getIfAvailable();
        if (imp == null) {
            log.warn("Nenhuma impressora configurada (impressora.modo); cupom do pedido {} não foi impresso", cupom.numero());
            return;
        }
        try {
            imp.imprimir(cupom);
        } catch (RuntimeException e) {
            // O pedido já está pago. Só registra; falta uma forma de reimprimir.
            log.error("Falha ao imprimir o cupom do pedido {}", cupom.numero(), e);
        }
    }

    // ------------------------------------------------------------------ demais operações

    public Optional<Pedido> atualizarStatus(Long id, StatusPedido novoStatus) {

        Optional<Pedido> optPedido = pedidoRepository.findById(id);

        if (optPedido.isEmpty()) {
            return optPedido;
        }

        Pedido pedidoAntigo = optPedido.get();

        // AGUARDANDO_PAGAMENTO -> ABERTO só pelo fluxo de pagamento; depois: ABERTO -> PRONTO -> ENTREGUE
        if (novoStatus == StatusPedido.PRONTO && pedidoAntigo.getStatus() == StatusPedido.ABERTO) {
            pedidoAntigo.setStatus(StatusPedido.PRONTO);
            pedidoAntigo.setDataPronto(LocalDateTime.now());

        } else if (novoStatus == StatusPedido.ENTREGUE && pedidoAntigo.getStatus() == StatusPedido.PRONTO) {
            pedidoAntigo.setStatus(StatusPedido.ENTREGUE);
            pedidoAntigo.setDataEntregue(LocalDateTime.now());

        } else if (novoStatus == StatusPedido.CANCELADO && pedidoAntigo.getStatus() != StatusPedido.ENTREGUE) {
            if (pedidoAntigo.getDataPagamento() != null) {
                // pedido já pago: cancelar sem estornar deixaria o cliente sem o dinheiro
                throw new RegraNegocioException(HttpStatus.CONFLICT,
                        "Pedido já pago: o estorno ainda não está implementado");
            }
            pedidoAntigo.setStatus(StatusPedido.CANCELADO);
            pedidoAntigo.setDataCancelado(LocalDateTime.now());

        } else {
            throw new RegraNegocioException(HttpStatus.CONFLICT,
                    "Status " + novoStatus + " não pode ser aplicado em " + pedidoAntigo.getStatus().name());
        }

        return Optional.of(pedidoRepository.save(pedidoAntigo));
    }

    public void excluir(Long pedidoID) {
        pedidoRepository.deleteById(pedidoID);
    }

    // TODO: continua como antes (salva o pedido existente sem aplicar os dados novos).
    // Defina quais campos o PUT pode alterar antes de corrigir.
    public Optional<Pedido> salvar(Pedido pedidoNovo) {

        Optional<Pedido> optPedidoExistente = pedidoRepository.findById(pedidoNovo.getId());

        if (optPedidoExistente.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(pedidoRepository.save(optPedidoExistente.get()));
    }
}