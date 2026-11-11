package br.eti.hpds.fastFurious.service;

import br.eti.hpds.fastFurious.domain.model.CobrancaTerminal;
import br.eti.hpds.fastFurious.domain.model.Estorno;
import br.eti.hpds.fastFurious.domain.model.Pedido;
import br.eti.hpds.fastFurious.domain.model.RotaEstorno;
import br.eti.hpds.fastFurious.domain.model.StatusCobranca;
import br.eti.hpds.fastFurious.domain.model.StatusEstorno;
import br.eti.hpds.fastFurious.domain.model.StatusPedido;
import br.eti.hpds.fastFurious.domain.repository.EstornoRepository;
import br.eti.hpds.fastFurious.domain.repository.PedidoRepository;
import br.eti.hpds.fastFurious.exceptionhandler.RegraNegocioException;
import br.eti.hpds.fastFurious.pagamento.CieloPixPagamento;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

/**
 * Estorno (devolução) de pedido já pago. Sempre devolve o valor TOTAL.
 *
 * Quando o estorno é pedido, o pedido sai da cozinha (ESTORNO_PENDENTE). Se o estorno conclui,
 * vira ESTORNADO; se falha ou expira, volta ao status anterior e pode ser tentado de novo.
 *
 * Rotas: cartão -> app da maquininha (cancelamento por deep link); PIX gerado pela API da
 * Cielo -> devolução por API; dinheiro, PIX da maquininha ou dados incompletos -> manual.
 */
@Service
public class EstornoService {

    private static final Logger log = LoggerFactory.getLogger(EstornoService.class);

    private final EstornoRepository estornos;
    private final PedidoRepository pedidos;
    private final CobrancaTerminalService cobrancas;
    private final ObjectProvider<CieloPixPagamento> pix;
    private final TransactionTemplate tx;
    private final long esperaMaquininhaSegundos;

    public EstornoService(EstornoRepository estornos,
                          PedidoRepository pedidos,
                          CobrancaTerminalService cobrancas,
                          ObjectProvider<CieloPixPagamento> pix,
                          PlatformTransactionManager txManager,
                          @Value("${estorno.espera-maquininha-segundos:600}") long esperaMaquininhaSegundos) {
        this.estornos = estornos;
        this.pedidos = pedidos;
        this.cobrancas = cobrancas;
        this.pix = pix;
        this.tx = new TransactionTemplate(txManager);
        this.esperaMaquininhaSegundos = esperaMaquininhaSegundos;
    }

    // ------------------------------------------------------------------ solicitar

    /**
     * Pede o estorno de um pedido pago (ABERTO ou PRONTO). Não é @Transactional de propósito:
     * a chamada à Cielo (PIX) acontece fora da transação.
     */
    public Estorno solicitar(Long pedidoId, String motivo, String operador) {
        Estorno criado = tx.execute(status -> {
            Pedido p = pedidos.buscarParaAtualizar(pedidoId).orElseThrow(() ->
                    new RegraNegocioException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));

            if (p.getDataPagamento() == null) {
                throw new RegraNegocioException(HttpStatus.CONFLICT,
                        "O pedido não foi pago: não há o que estornar (para cancelar, use PUT /pedido/atualizaStatus/{id})");
            }
            if (p.getStatus() != StatusPedido.ABERTO && p.getStatus() != StatusPedido.PRONTO) {
                throw new RegraNegocioException(HttpStatus.CONFLICT,
                        "Só é possível estornar pedido ABERTO ou PRONTO (status atual: " + p.getStatus() + ")");
            }

            Estorno e = montar(p, motivo, operador);
            p.setStatus(StatusPedido.ESTORNO_PENDENTE); // sai da fila da cozinha e trava um 2º estorno
            pedidos.save(p);
            return estornos.save(e);
        });

        if (criado.getRota() == RotaEstorno.PIX_API) {
            executarDevolucaoPix(criado.getId());
        }
        return estornos.findById(criado.getId()).orElseThrow();
    }

    private Estorno montar(Pedido p, String motivo, String operador) {
        long centavos = p.getValorTotal().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        Estorno e = new Estorno(p.getId(), p.getPagamento(), centavos, p.getTransacaoId(), motivo, operador);
        e.setStatusAnteriorPedido(p.getStatus().name());

        switch (p.getPagamento()) {
            case DINHEIRO -> manual(e, "Devolva o dinheiro ao cliente no caixa e confirme em PUT /estornos/{id}/concluir");
            case PIX -> {
                if (p.getPixCopiaECola() != null && pix.getIfAvailable() != null) {
                    e.setRota(RotaEstorno.PIX_API);
                    e.setStatus(StatusEstorno.SOLICITADO);
                } else {
                    manual(e, "PIX da maquininha: faça a devolução pelo aplicativo ou portal da Cielo e confirme em PUT /estornos/{id}/concluir");
                }
            }
            case CARTAO_DEBITO, CARTAO_CREDITO -> {
                CobrancaTerminal c = p.getTransacaoId() == null
                        ? null : cobrancas.buscar(p.getTransacaoId()).orElse(null);
                boolean completo = c != null && c.getStatus() == StatusCobranca.PAGA
                        && c.getOrdemId() != null && c.getNsu() != null && c.getAuthCode() != null;
                if (completo) {
                    e.setRota(RotaEstorno.APP);
                    e.setStatus(StatusEstorno.SOLICITADO);
                    e.setOrdemId(c.getOrdemId());
                    e.setNsu(c.getNsu());
                    e.setAuthCode(c.getAuthCode());
                } else {
                    manual(e, "Dados da transação incompletos para cancelar pela maquininha: faça o cancelamento no terminal e confirme em PUT /estornos/{id}/concluir");
                }
            }
        }
        return e;
    }

    private void manual(Estorno e, String instrucao) {
        e.setRota(RotaEstorno.MANUAL);
        e.setStatus(StatusEstorno.PENDENTE_MANUAL);
        e.setMensagem(instrucao);
    }

    private void executarDevolucaoPix(String estornoId) {
        Estorno e = estornos.findById(estornoId).orElseThrow();
        CieloPixPagamento gateway = pix.getIfAvailable();
        try {
            if (gateway == null) {
                throw new IllegalStateException("PIX pela Cielo não está configurado");
            }
            gateway.devolver(e.getTransacaoId(), e.getValorCentavos());
            tx.executeWithoutResult(s -> estornos.buscarParaAtualizar(estornoId).ifPresent(x -> {
                if (x.getStatus() == StatusEstorno.SOLICITADO) {
                    x.setStatus(StatusEstorno.EM_ANDAMENTO);
                    x.setMensagem("Devolução solicitada à Cielo; aguardando confirmação");
                    x.setAtualizadoEm(LocalDateTime.now());
                    estornos.save(x);
                }
            }));
        } catch (RuntimeException ex) {
            log.error("Falha ao solicitar a devolução do PIX do pedido {}", e.getPedidoId(), ex);
            falharSe(estornoId, StatusEstorno.SOLICITADO, "A Cielo não aceitou a devolução: " + ex.getMessage());
            throw new RegraNegocioException(HttpStatus.BAD_GATEWAY,
                    "A Cielo não aceitou a devolução do PIX. O pedido foi mantido; tente de novo ou devolva manualmente.");
        }
    }

    // ------------------------------------------------------------------ conclusão manual

    /** Quem devolveu o dinheiro (ou fez o cancelamento no terminal) confirma aqui. Idempotente. */
    public Estorno concluirManual(String estornoId) {
        return tx.execute(status -> {
            Estorno e = estornos.buscarParaAtualizar(estornoId).orElseThrow(() ->
                    new RegraNegocioException(HttpStatus.NOT_FOUND, "Estorno não encontrado"));
            if (e.getStatus() == StatusEstorno.CONCLUIDO) {
                return e;
            }
            if (e.getStatus() != StatusEstorno.PENDENTE_MANUAL && e.getStatus() != StatusEstorno.EM_ANDAMENTO) {
                throw new RegraNegocioException(HttpStatus.CONFLICT,
                        "Estorno em estado " + e.getStatus() + ": não pode ser concluído manualmente");
            }
            concluirNaTransacao(e, "Concluído manualmente");
            return e;
        });
    }

    // ------------------------------------------------------------------ rota APP (maquininha)

    /** Entrega ao app o estorno mais antigo à espera e o marca como EM_ANDAMENTO (uma única vez). */
    public Optional<Estorno> reservarProximoParaApp() {
        return Optional.ofNullable(tx.execute(status -> {
            Estorno primeiro = estornos.findFirstByStatusAndRotaOrderByCriadoEmAsc(
                    StatusEstorno.SOLICITADO, RotaEstorno.APP).orElse(null);
            if (primeiro == null) {
                return null;
            }
            Estorno e = estornos.buscarParaAtualizar(primeiro.getId()).orElse(null);
            if (e == null || e.getStatus() != StatusEstorno.SOLICITADO) {
                return null; // outro terminal pegou, ou expirou agora
            }
            e.setStatus(StatusEstorno.EM_ANDAMENTO);
            e.setAtualizadoEm(LocalDateTime.now());
            return estornos.save(e);
        }));
    }

    /** Resultado do cancelamento informado pelo app. Idempotente para estornos já concluídos. */
    public Estorno registrarResultadoApp(String estornoId, boolean sucesso, String mensagem) {
        return tx.execute(status -> {
            Estorno e = estornos.buscarParaAtualizar(estornoId).orElseThrow(() ->
                    new RegraNegocioException(HttpStatus.NOT_FOUND, "Estorno não encontrado"));
            if (e.getStatus() == StatusEstorno.CONCLUIDO) {
                return e;
            }
            if (e.getStatus() != StatusEstorno.EM_ANDAMENTO) {
                throw new RegraNegocioException(HttpStatus.CONFLICT,
                        "Estorno em estado " + e.getStatus() + ": resultado não aceito");
            }
            if (sucesso) {
                concluirNaTransacao(e, "Cancelado na maquininha");
            } else {
                falharNaTransacao(e, mensagem == null ? "Cancelamento recusado na maquininha" : mensagem);
            }
            return e;
        });
    }

    // ------------------------------------------------------------------ rotinas do poller

    /** Confirma devoluções PIX na Cielo e expira estornos que a maquininha não buscou. */
    public void processarPendentes() {
        CieloPixPagamento gateway = pix.getIfAvailable();
        if (gateway != null) {
            for (Estorno e : estornos.findByStatus(StatusEstorno.EM_ANDAMENTO)) {
                if (e.getRota() != RotaEstorno.PIX_API) {
                    continue;
                }
                try {
                    if (gateway.devolucaoConcluida(e.getTransacaoId())) {
                        tx.executeWithoutResult(s -> estornos.buscarParaAtualizar(e.getId()).ifPresent(x -> {
                            if (x.getStatus() == StatusEstorno.EM_ANDAMENTO) {
                                concluirNaTransacao(x, "Devolução confirmada pela Cielo");
                            }
                        }));
                    }
                } catch (Exception ex) {
                    log.warn("Falha ao consultar a devolução do estorno {}: {}", e.getId(), ex.toString());
                }
            }
        }

        LocalDateTime limite = LocalDateTime.now().minusSeconds(esperaMaquininhaSegundos);
        for (Estorno e : estornos.findByStatus(StatusEstorno.SOLICITADO)) {
            if (e.getRota() == RotaEstorno.APP && e.getCriadoEm() != null && e.getCriadoEm().isBefore(limite)) {
                falharSe(e.getId(), StatusEstorno.SOLICITADO, "A maquininha não buscou o estorno a tempo");
            }
        }
    }

    // ------------------------------------------------------------------ consultas

    public Optional<Estorno> buscar(String id) {
        return estornos.findById(id);
    }

    public List<Estorno> listarPorPedido(Long pedidoId) {
        return estornos.findByPedidoIdOrderByCriadoEmDesc(pedidoId);
    }

    // ------------------------------------------------------------------ internos (dentro de transação)

    private void concluirNaTransacao(Estorno e, String mensagem) {
        e.setStatus(StatusEstorno.CONCLUIDO);
        e.setMensagem(mensagem);
        e.setAtualizadoEm(LocalDateTime.now());
        e.setConcluidoEm(LocalDateTime.now());
        estornos.save(e);

        pedidos.buscarParaAtualizar(e.getPedidoId()).ifPresent(p -> {
            if (p.getStatus() == StatusPedido.ESTORNO_PENDENTE) {
                p.setStatus(StatusPedido.ESTORNADO);
                p.setDataEstorno(LocalDateTime.now());
                pedidos.save(p);
            }
        });
    }

    private void falharNaTransacao(Estorno e, String mensagem) {
        e.setStatus(StatusEstorno.FALHOU);
        e.setMensagem(mensagem);
        e.setAtualizadoEm(LocalDateTime.now());
        estornos.save(e);

        // volta ao status anterior para o pedido poder ser estornado de novo
        pedidos.buscarParaAtualizar(e.getPedidoId()).ifPresent(p -> {
            if (p.getStatus() == StatusPedido.ESTORNO_PENDENTE && e.getStatusAnteriorPedido() != null) {
                p.setStatus(StatusPedido.valueOf(e.getStatusAnteriorPedido()));
                pedidos.save(p);
            }
        });
    }

    /** Falha o estorno só se ele ainda estiver no status esperado (reconfere sob lock). */
    private void falharSe(String estornoId, StatusEstorno esperado, String mensagem) {
        tx.executeWithoutResult(s -> estornos.buscarParaAtualizar(estornoId).ifPresent(e -> {
            if (e.getStatus() == esperado) {
                falharNaTransacao(e, mensagem);
                log.warn("Estorno {} (pedido {}) falhou: {}", e.getId(), e.getPedidoId(), mensagem);
            }
        }));
    }
}
