package br.eti.hpds.fastFurious.controller;

import br.eti.hpds.fastFurious.domain.dto.CobrancaParaTerminalDTO;
import br.eti.hpds.fastFurious.domain.dto.ResultadoTerminalDTO;
import br.eti.hpds.fastFurious.domain.model.CobrancaTerminal;
import br.eti.hpds.fastFurious.domain.model.StatusCobranca;
import br.eti.hpds.fastFurious.service.CobrancaTerminalService;
import br.eti.hpds.fastFurious.service.PedidoService;
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

/** Endpoints usados SOMENTE pelo app instalado na maquininha. */
@RestController
@RequestMapping("/terminal/cobrancas")
@ConditionalOnProperty(name = "pagamento.terminal", havingValue = "app-smart", matchIfMissing = true)
public class TerminalController {

    private static final String HEADER = "X-Terminal-Token";

    private final TerminalAuth auth;
    private final CobrancaTerminalService cobrancas;
    private final PedidoService pedidos;
    private final String clientId;
    private final String accessToken;

    public TerminalController(TerminalAuth auth,
                              CobrancaTerminalService cobrancas,
                              PedidoService pedidos,
                              @Value("${cielo.client-id}") String clientId,
                              @Value("${cielo.access-token}") String accessToken) {
        this.auth = auth;
        this.cobrancas = cobrancas;
        this.pedidos = pedidos;
        this.clientId = clientId;
        this.accessToken = accessToken;
    }

    /** O app consulta a cada poucos segundos. 204 = nada a cobrar. */
    @GetMapping("/proxima")
    public ResponseEntity<CobrancaParaTerminalDTO> proxima(
            @RequestHeader(value = HEADER, required = false) String token) {
        auth.exigir(token);

        return cobrancas.reservarProxima()
                .map(c -> ResponseEntity.ok(new CobrancaParaTerminalDTO(
                        c.getId(),
                        c.getPedidoId(),
                        "Pedido " + c.getPedidoId(),
                        c.getValorCentavos(),
                        c.getPaymentCode(),
                        clientId,
                        accessToken)))
                .orElse(ResponseEntity.noContent().build());
    }

    /** O app informa o resultado. Pode ser reenviado com segurança (idempotente). */
    @PostMapping("/{id}/resultado")
    public ResponseEntity<Void> resultado(
            @PathVariable String id,
            @RequestHeader(value = HEADER, required = false) String token,
            @Valid @RequestBody ResultadoTerminalDTO dto) {
        auth.exigir(token);

        CobrancaTerminal c = cobrancas.registrarResultado(id, dto);
        if (c.getStatus() == StatusCobranca.PAGA) {
            pedidos.confirmarPagamento(c.getPedidoId()); // idempotente
        }
        return ResponseEntity.ok().build();
    }
}