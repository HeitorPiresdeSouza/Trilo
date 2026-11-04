package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.domain.repository.PedidoRepository;
import br.eti.hpds.fastFurious.service.PedidoService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe a notificação da Cielo ("Notification Post") e só usa o PaymentId para CONSULTAR a Cielo.
 * O corpo da notificação nunca decide se o pedido foi pago: quem decide é a consulta à API,
 * então uma notificação falsa não consegue liberar um pedido.
 *
 * A Cielo só envia se o suporte cadastrar a URL (estática, até 255 caracteres). Como a notificação
 * é um acelerador opcional, o PagamentoPoller continua sendo a garantia.
 */
@RestController
@RequestMapping("/webhooks/cielo")
@ConditionalOnProperty(name = "cielo.pix.merchant-id")
public class CieloPixWebhookController {

    private static final Logger log = LoggerFactory.getLogger(CieloPixWebhookController.class);
    private static final String HEADER = "X-Webhook-Token";

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final byte[] token;

    public CieloPixWebhookController(PedidoRepository pedidoRepository,
                                     PedidoService pedidoService,
                                     @Value("${cielo.pix.webhook-token:}") String token) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/pix")
    public ResponseEntity<Void> receber(
            @RequestBody Map<String, Object> corpo,
            @RequestHeader(value = HEADER, required = false) String tokenRecebido) {

        // Se um token foi configurado, ele também deve ser cadastrado no suporte da Cielo como
        // header de retorno (a Cielo permite até 3 headers estáticos).
        if (token.length > 0) {
            boolean ok = tokenRecebido != null
                    && MessageDigest.isEqual(token, tokenRecebido.getBytes(StandardCharsets.UTF_8));
            if (!ok) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        Object paymentId = corpo.get("PaymentId");
        if (paymentId != null) {
            pedidoRepository.findByTransacaoId(String.valueOf(paymentId)).ifPresent(p -> {
                try {
                    pedidoService.verificarPagamento(p.getId());
                } catch (Exception e) {
                    log.warn("Webhook: falha ao verificar o pedido {}: {}", p.getId(), e.toString());
                }
            });
        }
        // A Cielo espera 200 OK; qualquer outra resposta gera novas tentativas.
        return ResponseEntity.ok().build();
    }
}
