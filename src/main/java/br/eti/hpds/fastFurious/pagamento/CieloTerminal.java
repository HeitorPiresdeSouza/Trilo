package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.domain.model.OpcaoPagamento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Integração Remota da Cielo (Order Manager).
 * Fluxo: criar pedido (POST /orders) -> liberar para pagamento (PUT ?operation=PLACE)
 * -> cliente paga na maquininha -> consultar (GET) até o status virar PAID.
 */
@Service
public class CieloTerminal implements PagamentoTerminal {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA =
            new ParameterizedTypeReference<>() { };

    private final RestClient http;

    public CieloTerminal(
            // Sandbox: https://api.cielo.com.br/sandbox-lio/order-management/v1
            @Value("${cielo.base-url:https://api.cielo.com.br/order-management/v1}") String baseUrl,
            @Value("${cielo.client-id}") String clientId,
            @Value("${cielo.access-token}") String token,
            @Value("${cielo.merchant-id}") String merchantId) {

        // Timeouts: sem eles uma Cielo lenta trava a requisição (e o agendador de consultas)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));

        // RestClient.builder() direto: não depende do bean RestClient.Builder do Spring Boot
        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("client-id", clientId)
                .defaultHeader("access-token", token)
                .defaultHeader("merchant-id", merchantId)
                .build();
    }

    @Override
    public ResultadoPagamento cobrar(BigDecimal valor, OpcaoPagamento tipo, String referencia) {
        long centavos = valor.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

        Map<String, Object> pedido = new HashMap<>();
        pedido.put("number", referencia);          // id do pedido no SEU sistema
        pedido.put("reference", "PED-" + referencia);
        pedido.put("status", "DRAFT");             // padrão da API; PLACE é feito em seguida
        pedido.put("price", centavos);             // número, em centavos
        pedido.put("items", List.of(Map.of(
                "sku", referencia,
                "name", "Pedido " + referencia,
                "unit_price", centavos,
                "quantity", 1,
                "unit_of_measure", "EACH")));
        pedido.put("transactions", List.of());     // exigido no corpo, vazio na criação

        // payment_code: só funciona nos terminais da Nova Smart (L300 V4, L400, DX800)
        pedido.put("payment_code", paymentCode(tipo));
        if (tipo == OpcaoPagamento.CARTAO_DEBITO || tipo == OpcaoPagamento.CARTAO_CREDITO) {
            pedido.put("installments", "0");       // "0" = à vista
        }

        // 1) Cria o pedido (201). Assume que o JSON devolvido traz o "id" (UUID).
        Map<String, Object> criado = http.post().uri("/orders").body(pedido)
                .retrieve().body(MAPA);
        if (criado == null || criado.get("id") == null) {
            throw new IllegalStateException("Resposta da Cielo sem o id do pedido");
        }
        String id = String.valueOf(criado.get("id"));

        // 2) PLACE: libera o pedido para pagamento, exibindo-o na Cielo Smart.
        http.put().uri("/orders/{id}?operation=PLACE", id).retrieve().toBodilessEntity();

        return new ResultadoPagamento(id, StatusPagamento.PENDENTE);
    }

    @Override
    public StatusPagamento consultar(String transacaoId) {
        // Assume GET /orders/{id} ("Consultar um pedido"). Confirme o caminho e o formato
        // da resposta no sandbox (use PUT ?operation=PAY para simular um pagamento).
        Map<String, Object> pedido = http.get().uri("/orders/{id}", transacaoId)
                .retrieve().body(MAPA);
        if (pedido == null) {
            return StatusPagamento.PENDENTE;
        }
        if ("PAID".equals(pedido.get("status")) || temPagamentoConfirmado(pedido)) {
            return StatusPagamento.PAGO;
        }
        if ("CANCELED".equals(pedido.get("status"))) {
            return StatusPagamento.CANCELADO;
        }
        return StatusPagamento.PENDENTE;
    }

    /** Alguma transação de pagamento com status CONFIRMED (campos da entidade Transaction). */
    private boolean temPagamentoConfirmado(Map<String, Object> pedido) {
        if (pedido.get("transactions") instanceof List<?> lista) {
            for (Object o : lista) {
                if (o instanceof Map<?, ?> t
                        && "CONFIRMED".equals(t.get("status"))
                        && (t.get("transaction_type") == null || "PAYMENT".equals(t.get("transaction_type")))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Traduz a opção de pagamento para o payment_code da Cielo.
     * CARTAO_DEBITO / CARTAO_CREDITO: testados no sandbox só o débito; os demais valores
     * ainda são palpite (a documentação não lista os aceitos).
     */
    private String paymentCode(OpcaoPagamento tipo) {
        return switch (tipo) {
            case CARTAO_DEBITO  -> "DEBITO_AVISTA";
            case CARTAO_CREDITO -> "CREDITO_AVISTA";
            case PIX            -> "PIX";
            case DINHEIRO       -> throw new IllegalArgumentException("Dinheiro não passa pela maquininha");
        };
    }

    @Override
    public void cancelar(String transacaoId) {
        // "Alterar status" só aceita PLACE, PAY e CLOSE: não cancela/estorna.
        throw new UnsupportedOperationException("Cancelamento ainda não implementado");
    }
}