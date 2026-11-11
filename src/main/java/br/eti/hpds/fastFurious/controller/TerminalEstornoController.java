package br.eti.hpds.fastFurious.controller;

import br.eti.hpds.fastFurious.domain.dto.EstornoParaTerminalDTO;
import br.eti.hpds.fastFurious.domain.dto.ResultadoEstornoTerminalDTO;
import br.eti.hpds.fastFurious.service.EstornoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints usados SOMENTE pelo app instalado na maquininha, para executar estornos de cartão. */
@RestController
@RequestMapping("/terminal/estornos")
@ConditionalOnProperty(name = "pagamento.terminal", havingValue = "app-smart", matchIfMissing = true)
public class TerminalEstornoController {

    private static final String HEADER = "X-Terminal-Token";

    private final TerminalAuth auth;
    private final EstornoService estornos;
    private final String clientId;
    private final String accessToken;

    public TerminalEstornoController(TerminalAuth auth,
                                     EstornoService estornos,
                                     @Value("${cielo.client-id}") String clientId,
                                     @Value("${cielo.access-token}") String accessToken) {
        this.auth = auth;
        this.estornos = estornos;
        this.clientId = clientId;
        this.accessToken = accessToken;
    }

    /** O app consulta junto com as cobranças. 204 = nenhum estorno a executar. */
    @GetMapping("/proximo")
    public ResponseEntity<EstornoParaTerminalDTO> proximo(
            @RequestHeader(value = HEADER, required = false) String token) {
        auth.exigir(token);

        return estornos.reservarProximoParaApp()
                .map(e -> ResponseEntity.ok(new EstornoParaTerminalDTO(
                        e.getId(),
                        e.getPedidoId(),
                        e.getOrdemId(),
                        e.getNsu(),
                        e.getAuthCode(),
                        e.getValorCentavos(),
                        clientId,
                        accessToken)))
                .orElse(ResponseEntity.noContent().build());
    }

    /** O app informa o resultado do cancelamento. Pode ser reenviado com segurança. */
    @PostMapping("/{id}/resultado")
    public ResponseEntity<Void> resultado(
            @PathVariable String id,
            @RequestHeader(value = HEADER, required = false) String token,
            @Valid @RequestBody ResultadoEstornoTerminalDTO dto) {
        auth.exigir(token);
        estornos.registrarResultadoApp(id, Boolean.TRUE.equals(dto.sucesso()), dto.mensagem());
        return ResponseEntity.ok().build();
    }
}
