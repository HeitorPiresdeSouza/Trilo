package br.eti.hpds.fastFurious.service;

import br.eti.hpds.fastFurious.domain.dto.ResultadoTerminalDTO;
import br.eti.hpds.fastFurious.domain.model.CobrancaTerminal;
import br.eti.hpds.fastFurious.domain.model.StatusCobranca;
import br.eti.hpds.fastFurious.domain.repository.CobrancaTerminalRepository;
import br.eti.hpds.fastFurious.exceptionhandler.RegraNegocioException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CobrancaTerminalService {

    private static final Logger log = LoggerFactory.getLogger(CobrancaTerminalService.class);

    private final CobrancaTerminalRepository repo;
    private final TransactionTemplate tx;
    private final long esperaMaquininhaSegundos;
    private final long expiracaoSegundos;

    public CobrancaTerminalService(CobrancaTerminalRepository repo,
                                   PlatformTransactionManager txManager,
                                   @Value("${pagamento.cartao.espera-maquininha-segundos:60}") long esperaMaquininhaSegundos,
                                   @Value("${pagamento.cartao.expiracao-segundos:300}") long expiracaoSegundos) {
        this.repo = repo;
        this.tx = new TransactionTemplate(txManager);
        this.esperaMaquininhaSegundos = esperaMaquininhaSegundos;
        this.expiracaoSegundos = expiracaoSegundos;
    }

    public CobrancaTerminal criar(Long pedidoId, long valorCentavos, String paymentCode) {
        return repo.save(new CobrancaTerminal(pedidoId, valorCentavos, paymentCode));
    }

    public Optional<CobrancaTerminal> buscar(String id) {
        return repo.findById(id);
    }

    /** Entrega a cobrança pendente mais antiga ao app e a marca como EM_ANDAMENTO (uma única vez). */
    public Optional<CobrancaTerminal> reservarProxima() {
        return Optional.ofNullable(tx.execute(status -> {
            CobrancaTerminal primeira = repo.findFirstByStatusOrderByCriadaEmAsc(StatusCobranca.PENDENTE).orElse(null);
            if (primeira == null) {
                return null;
            }
            CobrancaTerminal c = repo.buscarParaAtualizar(primeira.getId()).orElse(null);
            if (c == null || c.getStatus() != StatusCobranca.PENDENTE) {
                return null; // outro terminal pegou primeiro, ou expirou agora
            }
            c.setStatus(StatusCobranca.EM_ANDAMENTO);
            c.setAtualizadaEm(LocalDateTime.now());
            return repo.save(c);
        }));
    }

    /**
     * Registra o resultado enviado pelo app. Idempotente para cobranças já PAGAS (o app pode
     * reenviar). O valor pago precisa ser igual ao esperado; qualquer diferença vira PARCIAL
     * e não libera o pedido. Também aceita resultado de cobrança EXPIRADA: se o cliente pagou
     * depois do prazo, o pagamento fica registrado (e o pedido é reaberto pelo chamador).
     */
    public CobrancaTerminal registrarResultado(String id, ResultadoTerminalDTO dto) {
        return tx.execute(status -> {
            CobrancaTerminal c = repo.buscarParaAtualizar(id).orElseThrow(() ->
                    new RegraNegocioException(HttpStatus.NOT_FOUND, "Cobrança não encontrada"));

            if (c.getStatus() == StatusCobranca.PAGA) {
                return c; // reenvio do mesmo resultado
            }
            boolean tardio = c.getStatus() == StatusCobranca.EXPIRADA;
            if (c.getStatus() != StatusCobranca.EM_ANDAMENTO && !tardio) {
                throw new RegraNegocioException(HttpStatus.CONFLICT,
                        "Cobrança em estado " + c.getStatus() + ": resultado não aceito");
            }

            c.setMensagem(dto.mensagem());
            c.setAuthCode(dto.authCode());
            c.setNsu(dto.nsu());
            c.setOrdemId(dto.ordemId());
            c.setBandeira(dto.bandeira());
            c.setValorPagoCentavos(dto.valorPagoCentavos());
            c.setAtualizadaEm(LocalDateTime.now());

            if (!Boolean.TRUE.equals(dto.sucesso())) {
                c.setStatus(StatusCobranca.RECUSADA);
            } else if (dto.valorPagoCentavos() != null && dto.valorPagoCentavos().equals(c.getValorCentavos())) {
                c.setStatus(StatusCobranca.PAGA);
                if (tardio) {
                    log.warn("Cobrança {} (pedido {}) foi paga depois de expirar (NSU {}).",
                            c.getId(), c.getPedidoId(), dto.nsu());
                }
            } else {
                c.setStatus(StatusCobranca.PARCIAL);
                log.error("Cobrança {} (pedido {}): pago {} centavos, esperado {}. Requer tratamento manual.",
                        c.getId(), c.getPedidoId(), dto.valorPagoCentavos(), c.getValorCentavos());
            }
            return repo.save(c);
        });
    }

    /**
     * Expira cobranças paradas, para o totem não esperar para sempre:
     *  - PENDENTE há mais de pagamento.cartao.espera-maquininha-segundos: a maquininha não buscou
     *    (desligada, sem rede ou app fechado);
     *  - EM_ANDAMENTO há mais de pagamento.cartao.expiracao-segundos: sem resposta da maquininha.
     * Quem cancela o pedido é o PagamentoPoller, ao consultar a cobrança expirada.
     */
    public void expirarVencidas() {
        expirar(StatusCobranca.PENDENTE, esperaMaquininhaSegundos, true,
                "A maquininha não buscou a cobrança a tempo");
        expirar(StatusCobranca.EM_ANDAMENTO, expiracaoSegundos, false,
                "Sem resposta da maquininha a tempo");
    }

    private void expirar(StatusCobranca alvo, long segundos, boolean contarDaCriacao, String motivo) {
        LocalDateTime limite = LocalDateTime.now().minusSeconds(segundos);
        for (CobrancaTerminal candidata : repo.findByStatus(alvo)) {
            LocalDateTime referencia = contarDaCriacao ? candidata.getCriadaEm() : candidata.getAtualizadaEm();
            if (referencia == null || !referencia.isBefore(limite)) {
                continue;
            }
            tx.executeWithoutResult(status -> repo.buscarParaAtualizar(candidata.getId()).ifPresent(c -> {
                LocalDateTime ref = contarDaCriacao ? c.getCriadaEm() : c.getAtualizadaEm();
                // reconfere sob lock: um resultado pode ter chegado agora
                if (c.getStatus() == alvo && ref != null && ref.isBefore(limite)) {
                    c.setStatus(StatusCobranca.EXPIRADA);
                    c.setMensagem(motivo);
                    c.setAtualizadaEm(LocalDateTime.now());
                    repo.save(c);
                    log.warn("Cobrança {} (pedido {}) expirada: {}", c.getId(), c.getPedidoId(), motivo);
                }
            }));
        }
    }

    /** Pedido cancelado pelo cliente/atendente: se a maquininha ainda não buscou, não busca mais. */
    public void cancelarSePendente(String id) {
        tx.executeWithoutResult(status -> repo.buscarParaAtualizar(id).ifPresent(c -> {
            if (c.getStatus() == StatusCobranca.PENDENTE) {
                c.setStatus(StatusCobranca.EXPIRADA);
                c.setMensagem("Pedido cancelado antes de a maquininha buscar a cobrança");
                c.setAtualizadaEm(LocalDateTime.now());
                repo.save(c);
            }
        }));
    }
}
