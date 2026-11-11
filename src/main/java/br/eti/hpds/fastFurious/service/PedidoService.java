package br.eti.hpds.fastFurious.service;

import br.eti.hpds.fastFurious.domain.dto.StatusPagamentoPedidoDTO;
import br.eti.hpds.fastFurious.domain.model.ItemPedido;
import br.eti.hpds.fastFurious.domain.model.OpcaoPagamento;
import br.eti.hpds.fastFurious.domain.model.Pedido;
import br.eti.hpds.fastFurious.domain.model.Produto;
import br.eti.hpds.fastFurious.domain.model.StatusCobranca;
import br.eti.hpds.fastFurious.domain.model.StatusPedido;
import br.eti.hpds.fastFurious.domain.repository.PedidoRepository;
import br.eti.hpds.fastFurious.domain.repository.ProdutoRepository;
import br.eti.hpds.fastFurious.exceptionhandler.RegraNegocioException;
import br.eti.hpds.fastFurious.impressora.Cupom;
import br.eti.hpds.fastFurious.impressora.CupomEscPos;
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
    private final List<PagamentoTerminal> terminais;
    private final ObjectProvider<ImpressoraCupom> impressora;
    private final TransactionTemplate tx;
    private final String lojaId;
    private final boolean imprimirAoConfirmarDinheiro;
    private final long toleranciaPixSegundos;
    private final CobrancaTerminalService cobrancas;
    private final String modoImpressora;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProdutoRepository produtoRepository,
                         List<PagamentoTerminal> terminais,
                         ObjectProvider<ImpressoraCupom> impressora,
                         PlatformTransactionManager txManager,
                         @Value("${impressora.loja-id:loja-1}") String lojaId,
                         @Value("${impressora.imprimir-ao-confirmar-dinheiro:false}") boolean imprimirAoConfirmarDinheiro,
                         @Value("${pagamento.pix.tolerancia-segundos:15}") long toleranciaPixSegundos,
                         CobrancaTerminalService cobrancas,
                         @Value("${impressora.modo:}") String modoImpressora) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.terminais = terminais;
        this.impressora = impressora;
        this.tx = new TransactionTemplate(txManager);
        this.lojaId = lojaId;
        this.imprimirAoConfirmarDinheiro = imprimirAoConfirmarDinheiro;
        this.toleranciaPixSegundos = toleranciaPixSegundos;
        this.cobrancas = cobrancas;
        this.modoImpressora = modoImpressora;
    }

    // ------------------------------------------------------------------ criação

    /**
     * Cria o pedido como AGUARDANDO_PAGAMENTO, calcula o total no servidor e:
     *  - cartão/PIX: inicia a cobrança (no PIX, devolve o QR Code junto com o pedido);
     *  - dinheiro: imprime na hora o cupom "PAGUE NO CAIXA".
     * Não é @Transactional de propósito: a chamada HTTP à Cielo não deve segurar uma transação.
     */
    public Pedido criar(Pedido pedido) {
        if (pedido.getListaItens() == null || pedido.getListaItens().isEmpty()) {
            throw new RegraNegocioException(HttpStatus.UNPROCESSABLE_ENTITY, "O pedido precisa ter ao menos um item");
        }

        // Falha cedo (antes de gravar) se a forma de pagamento não tem quem a atenda
        PagamentoTerminal terminal = pedido.getPagamento() == OpcaoPagamento.DINHEIRO
                ? null : terminalPara(pedido.getPagamento());

        pedido.setId(null);
        pedido.setDataAbertura(LocalDateTime.now());
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido.setDataPagamento(null);
        pedido.setDataCancelado(null);
        pedido.setTransacaoId(null);
        pedido.setPixCopiaECola(null);
        pedido.setPixExpiraEm(null);
        pedido.setPixQrCodeImagem(null);

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

        if (terminal == null) {
            // dinheiro: o cliente leva o cupom ao caixa
            imprimir(montarCupom(salvo, "PAGUE NO CAIXA"));
        } else {
            iniciarCobranca(salvo, terminal);
        }
        return salvo;
    }

    private void iniciarCobranca(Pedido pedido, PagamentoTerminal terminal) {
        try {
            ResultadoPagamento r = terminal.cobrar(
                    pedido.getValorTotal(), pedido.getPagamento(), String.valueOf(pedido.getId()));
            pedido.setTransacaoId(r.transacaoId());
            pedido.setPixCopiaECola(r.pixCopiaECola());
            pedido.setPixExpiraEm(r.pixExpiraEm());
            pedido.setPixQrCodeImagem(r.pixQrCodeImagem()); // não é gravado; só vai na resposta
            pedidoRepository.save(pedido);
        } catch (RuntimeException e) {
            log.error("Falha ao iniciar a cobrança do pedido {}", pedido.getId(), e);
            pedido.setStatus(StatusPedido.CANCELADO);
            pedido.setDataCancelado(LocalDateTime.now());
            pedidoRepository.save(pedido);
            throw new RegraNegocioException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível iniciar a cobrança. Tente novamente.");
        }
    }

    private PagamentoTerminal terminalPara(OpcaoPagamento tipo) {
        // A lista vem ordenada por @Order: o CieloPixPagamento tem prioridade no PIX
        return terminais.stream()
                .filter(t -> t.suporta(tipo))
                .findFirst()
                .orElseThrow(() -> new RegraNegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Forma de pagamento indisponível: " + tipo));
    }

    // ------------------------------------------------------------------ pagamento

    /**
     * Consulta a origem do pagamento. Pago: confirma. Cancelado lá: cancela aqui.
     * PIX com QR Code vencido (mais a tolerância): cancela o pedido.
     */
    public void verificarPagamento(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId).orElse(null);
        if (pedido == null
                || pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO
                || pedido.getTransacaoId() == null) {
            return;
        }

        // Sempre consulta antes de cancelar por expiração: cobre o pagamento no último segundo
        StatusPagamento situacao = terminalPara(pedido.getPagamento()).consultar(pedido.getTransacaoId());
        if (situacao == StatusPagamento.PAGO) {
            confirmarPagamento(pedidoId);
        } else if (situacao == StatusPagamento.CANCELADO) {
            cancelarSeAguardando(pedidoId);
        } else if (pixExpirou(pedido)) {
            log.info("Pedido {}: QR Code Pix expirado sem pagamento; cancelando", pedidoId);
            cancelarSeAguardando(pedidoId);
        }
    }

    private boolean pixExpirou(Pedido pedido) {
        return pedido.getPixExpiraEm() != null
                && LocalDateTime.now().isAfter(pedido.getPixExpiraEm().plusSeconds(toleranciaPixSegundos));
    }

    private record Confirmacao(boolean confirmada, Cupom cupom) { }

    /**
     * Pagamento confirmado por CONSULTA (PIX, ou cartão pelo poller): só aceita pedido
     * AGUARDANDO_PAGAMENTO. Idempotente: se já não está, não faz nada. Retorna true se este
     * chamado foi quem confirmou.
     */
    public boolean confirmarPagamento(Long pedidoId) {
        return confirmar(pedidoId, false);
    }

    /**
     * Pagamento confirmado pelo RESULTADO real da maquininha. Aceita também um pedido CANCELADO
     * que nunca foi pago (cancelado por tempo ou pelo cliente): o dinheiro já foi recebido, então
     * o pedido é reaberto em vez de ser perdido.
     */
    public boolean confirmarPagamentoRecebido(Long pedidoId) {
        return confirmar(pedidoId, true);
    }

    /**
     * Marca o pedido como pago (ABERTO) e prepara o cupom. A impressão pelo servidor acontece
     * DEPOIS do commit: falha na impressora não desfaz o pagamento. Em dinheiro o cupom já saiu
     * na criação; só sai outro se impressora.imprimir-ao-confirmar-dinheiro=true.
     */
    private boolean confirmar(Long pedidoId, boolean aceitaCancelado) {
        Confirmacao c = tx.execute(status -> {
            Pedido p = pedidoRepository.buscarParaAtualizar(pedidoId).orElse(null);
            boolean aguardando = p != null && p.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO;
            boolean reabrir = p != null && aceitaCancelado
                    && p.getStatus() == StatusPedido.CANCELADO && p.getDataPagamento() == null;
            if (p == null || (!aguardando && !reabrir)) {
                return new Confirmacao(false, null);
            }
            if (reabrir) {
                log.warn("Pedido {}: pagamento recebido depois do cancelamento; pedido reaberto", pedidoId);
                p.setDataCancelado(null);
            }
            p.setStatus(StatusPedido.ABERTO);
            p.setDataPagamento(LocalDateTime.now());
            pedidoRepository.save(p);

            boolean imprimir = p.getPagamento() != OpcaoPagamento.DINHEIRO || imprimirAoConfirmarDinheiro;
            return new Confirmacao(true, imprimir ? montarCupom(p, "PAGO") : null); // itens são lazy
        });

        if (c == null || !c.confirmada()) {
            log.debug("Pedido {} não está aguardando pagamento; nada a fazer", pedidoId);
            return false;
        }
        if (c.cupom() != null) {
            imprimir(c.cupom());
        }
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

    /** O que o totem consulta a cada 3 s. Só lê o banco; quem consulta a Cielo é o poller. */
    public Optional<StatusPagamentoPedidoDTO> statusDoPagamento(Long pedidoId) {
        return pedidoRepository.findById(pedidoId).map(p -> {
            boolean aguardando = p.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO;
            return new StatusPagamentoPedidoDTO(
                    p.getId(),
                    p.getDataPagamento() != null,
                    p.getStatus().name(),
                    p.getPagamento().name(),
                    etapaDoPagamento(p),
                    p.getValorTotal(),
                    aguardando ? p.getPixCopiaECola() : null,
                    p.getPixExpiraEm(),
                    p.getDataPagamento());
        });
    }

    /** O que o totem deve mostrar ao cliente agora (ver StatusPagamentoPedidoDTO). */
    private String etapaDoPagamento(Pedido p) {
        if (p.getStatus() == StatusPedido.ESTORNADO) {
            return "ESTORNADO";
        }
        if (p.getStatus() == StatusPedido.ESTORNO_PENDENTE) {
            return "ESTORNO_EM_ANDAMENTO";
        }
        if (p.getDataPagamento() != null) {
            return "PAGO";
        }
        if (p.getStatus() == StatusPedido.CANCELADO) {
            return "CANCELADO";
        }
        if (p.getPagamento() == OpcaoPagamento.DINHEIRO) {
            return "PAGAR_NO_CAIXA";
        }
        if (p.getPixCopiaECola() != null) {
            return "AGUARDANDO_PIX";
        }
        // cartão (ou PIX na maquininha): a cobrança diz se a tela de pagamento já abriu
        boolean pagandoNaMaquininha = p.getTransacaoId() != null
                && cobrancas.buscar(p.getTransacaoId())
                        .map(c -> c.getStatus() == StatusCobranca.EM_ANDAMENTO)
                        .orElse(false);
        return pagandoNaMaquininha ? "PAGANDO_NA_MAQUININHA" : "AGUARDANDO_MAQUININHA";
    }

    // ------------------------------------------------------------------ cupom

    /** Cupom do pedido: existe se já foi pago ou se é em dinheiro e ainda está valendo. */
    public Optional<Cupom> obterCupom(Long pedidoId) {
        return Optional.ofNullable(tx.execute(status -> {
            Pedido p = pedidoRepository.findById(pedidoId).orElse(null);
            if (p == null) {
                return null;
            }
            if (p.getStatus() == StatusPedido.ESTORNADO || p.getStatus() == StatusPedido.ESTORNO_PENDENTE) {
                throw new RegraNegocioException(HttpStatus.CONFLICT, "Pedido estornado: não há cupom");
            }
            boolean pago = p.getDataPagamento() != null;
            boolean dinheiroAberto = p.getPagamento() == OpcaoPagamento.DINHEIRO
                    && p.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO;
            if (!pago && !dinheiroAberto) {
                throw new RegraNegocioException(HttpStatus.CONFLICT, "O cupom só existe depois do pagamento");
            }
            return montarCupom(p, pago ? "PAGO" : "PAGUE NO CAIXA");
        }));
    }

    /** Reimprime na impressora do servidor. Devolve false se o pedido não existe. */
    public boolean reimprimirCupom(Long pedidoId) {
        Optional<Cupom> cupom = obterCupom(pedidoId);
        if (cupom.isEmpty()) {
            return false;
        }
        ImpressoraCupom imp = impressora.getIfAvailable();
        if (imp == null) {
            throw new RegraNegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Nenhuma impressora configurada no servidor (impressora.modo)");
        }
        try {
            imp.imprimir(cupom.get());
        } catch (RuntimeException e) {
            log.error("Falha ao reimprimir o cupom do pedido {}", pedidoId, e);
            throw new RegraNegocioException(HttpStatus.BAD_GATEWAY, "Falha ao imprimir o cupom");
        }
        return true;
    }

    /** Cupom em comandos ESC/POS, pronto para o totem enviar à impressora ligada a ele. */
    public Optional<byte[]> obterCupomEscPos(Long pedidoId) {
        return obterCupom(pedidoId).map(CupomEscPos::gerar);
    }

    private Cupom montarCupom(Pedido p, String aviso) {
        List<Cupom.Item> itens = new ArrayList<>();
        for (ItemPedido i : p.getListaItens()) {
            Produto produto = i.getProduto();
            String nome = produto.getName() != null ? produto.getName() : produto.getDescricao();
            itens.add(new Cupom.Item(i.getQuantidade() + "x " + nome,
                    i.getvUnit().multiply(BigDecimal.valueOf(i.getQuantidade()))));
        }
        return new Cupom(lojaId, String.valueOf(p.getId()), itens, p.getValorTotal(),
                nomeDaForma(p.getPagamento()), nomeDoConsumo(p), p.getDataAbertura(), aviso);
    }

    private String nomeDaForma(OpcaoPagamento forma) {
        return switch (forma) {
            case PIX -> "PIX";
            case CARTAO_CREDITO -> "CARTAO DE CREDITO";
            case CARTAO_DEBITO -> "CARTAO DE DEBITO";
            case DINHEIRO -> "DINHEIRO";
        };
    }

    private String nomeDoConsumo(Pedido p) {
        if (p.getConsumo() == null) {
            return null;
        }
        return switch (p.getConsumo()) {
            case LOCAL -> "CONSUMO NO LOCAL";
            case LEVAR -> "PARA LEVAR";
            case ENTREGA -> "ENTREGA";
        };
    }

    private void imprimir(Cupom cupom) {
        ImpressoraCupom imp = impressora.getIfAvailable();
        if (imp == null) {
            if ("totem".equalsIgnoreCase(modoImpressora)) {
                log.debug("Impressora no totem: cupom do pedido {} disponível em GET /pedido/{}/cupom/escpos",
                        cupom.numero(), cupom.numero());
            } else {
                log.warn("Nenhuma impressora configurada (impressora.modo); cupom do pedido {} não foi impresso", cupom.numero());
            }
            return;
        }
        try {
            imp.imprimir(cupom);
        } catch (RuntimeException e) {
            // O pedido segue válido. Use POST /pedido/{id}/cupom/imprimir para reimprimir.
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
                        "Pedido já pago: use POST /pedido/{id}/estorno para devolver o valor");
            }
            pedidoAntigo.setStatus(StatusPedido.CANCELADO);
            pedidoAntigo.setDataCancelado(LocalDateTime.now());
            if (pedidoAntigo.getTransacaoId() != null) {
                cobrancas.cancelarSePendente(pedidoAntigo.getTransacaoId());
            }

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
