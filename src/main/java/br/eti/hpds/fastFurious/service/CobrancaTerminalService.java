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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CobrancaTerminalService {

    private static final Logger log = LoggerFactory.getLogger(CobrancaTerminalService.class);

    private final CobrancaTerminalRepository repo;
    private final TransactionTemplate tx;

    public CobrancaTerminalService(CobrancaTerminalRepository repo, PlatformTransactionManager txManager) {
        this.repo = repo;
        this.tx = new TransactionTemplate(txManager);
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
                return null; // outro terminal pegou primeiro
            }
            c.setStatus(StatusCobranca.EM_ANDAMENTO);
            c.setAtualizadaEm(LocalDateTime.now());
            return repo.save(c);
        }));
    }

    /**
     * Registra o resultado enviado pelo app. Idempotente para cobranças já PAGAS (o app pode
     * reenviar). O valor pago precisa ser igual ao esperado; qualquer diferença vira PARCIAL
     * e não libera o pedido.
     */
    public CobrancaTerminal registrarResultado(String id, ResultadoTerminalDTO dto) {
        return tx.execute(status -> {
            CobrancaTerminal c = repo.buscarParaAtualizar(id).orElseThrow(() ->
                    new RegraNegocioException(HttpStatus.NOT_FOUND, "Cobrança não encontrada"));

            if (c.getStatus() == StatusCobranca.PAGA) {
                return c; // reenvio do mesmo resultado
            }
            if (c.getStatus() != StatusCobranca.EM_ANDAMENTO) {
                throw new RegraNegocioException(HttpStatus.CONFLICT,
                        "Cobrança em estado " + c.getStatus() + ": resultado não aceito");
            }

            c.setMensagem(dto.mensagem());
            c.setAuthCode(dto.authCode());
            c.setNsu(dto.nsu());
            c.setBandeira(dto.bandeira());
            c.setValorPagoCentavos(dto.valorPagoCentavos());
            c.setAtualizadaEm(LocalDateTime.now());

            if (!Boolean.TRUE.equals(dto.sucesso())) {
                c.setStatus(StatusCobranca.RECUSADA);
            } else if (dto.valorPagoCentavos() != null && dto.valorPagoCentavos().equals(c.getValorCentavos())) {
                c.setStatus(StatusCobranca.PAGA);
            } else {
                c.setStatus(StatusCobranca.PARCIAL);
                log.error("Cobrança {} (pedido {}): pago {} centavos, esperado {}. Requer tratamento manual.",
                        c.getId(), c.getPedidoId(), dto.valorPagoCentavos(), c.getValorCentavos());
            }
            return repo.save(c);
        });
    }
}