package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/webhooks/pagamento")
public class PagamentoWebhookController {

    private final AssinaturaWebhookValidator validator;
    private final PedidoService pedidos;
    private final ObjectMapper mapper;

    public PagamentoWebhookController(AssinaturaWebhookValidator validator,
                                      PedidoService pedidos,
                                      ObjectMapper mapper) {
        this.validator = validator;
        this.pedidos = pedidos;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestBody String corpo, // String = corpo cru, necessário para o HMAC
            @RequestHeader(value = "X-Signature", required = false) String assinatura)
            throws JsonProcessingException {

        if (!validator.valida(corpo, assinatura)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        NotificacaoPagamento n = mapper.readValue(corpo, NotificacaoPagamento.class);

        // confirmarPagamento DEVE ser idempotente: o webhook pode chegar repetido.
        Long pedidoId = Long.valueOf(n.referencia());
        pedidos.confirmarPagamento(pedidoId);
        
        return ResponseEntity.ok().build();
    }
}