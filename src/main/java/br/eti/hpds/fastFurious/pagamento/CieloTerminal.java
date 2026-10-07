package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.domain.model.OpcaoPagamento;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Integração Remota da Cielo (Order Manager).
 * Fluxo: criar pedido (POST /orders) -> liberar para pagamento (PUT ?operation=PLACE)
 * -> cliente paga na maquininha -> consultar/receber notificação até status PAID.
 */
@Service
public class CieloTerminal implements PagamentoTerminal {

    private final RestClient http;

    public CieloTerminal(RestClient.Builder builder,
                         @Value("${cielo.base-url:https://api.cielo.com.br/order-management/v1}") String baseUrl,
                         @Value("${cielo.client-id}") String clientId,
                         @Value("${cielo.access-token}") String token,
                         @Value("${cielo.merchant-id}") String merchantId) {
        this.http = builder
                .baseUrl(baseUrl)
                .defaultHeader("client-id", clientId)
                .defaultHeader("access-token", token)
                .defaultHeader("merchant-id", merchantId)
                .build();
    }

    @Override
    public ResultadoPagamento cobrar(BigDecimal valor, OpcaoPagamento tipo, String referencia) {
        long centavos = valor.movePointRight(2).longValueExact(); // R$ 10,00 -> 1000

        Map<String, Object> pedido = new HashMap<>();
        pedido.put("number", referencia);         // id do pedido no SEU sistema
        pedido.put("reference", "PED-" + referencia);
        pedido.put("status", "DRAFT");             // padrão da API; PLACE é feito em seguida
        pedido.put("price", centavos);             // número, em centavos
        pedido.put("items", List.of(Map.of(
                "sku", referencia,
                "name", "Pedido " + referencia,
                "unit_price", centavos,
                "quantity", 1,
                "unit_of_measure", "EACH")));
        pedido.put("transactions", List.of());     // obrigatório no corpo, mas vazio na criação

        String paymentCode = paymentCode(tipo);
        if (paymentCode != null) {
            pedido.put("payment_code", paymentCode);
        }
        
        if (tipo == OpcaoPagamento.CARTAO_DEBITO || tipo == OpcaoPagamento.CARTAO_CREDITO) {
            pedido.put("installments", "0");
        }

        Map<String, Object> criado = http.post().uri("/orders").body(pedido)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
        String id = String.valueOf(criado.get("id"));

        http.put().uri("/orders/{id}?operation=PLACE", id).retrieve().toBodilessEntity();

        // Passando o status correspondente (ajuste para o construtor correto do seu ResultadoPagamento)
        return new ResultadoPagamento(id, StatusPagamento.PENDENTE);
    }

    private String paymentCode(OpcaoPagamento tipo) {
        if (tipo == null) return null;
        return switch (tipo) {
            case CARTAO_DEBITO  -> "DEBITO_AVISTA";
            case CARTAO_CREDITO -> "CREDITO_AVISTA";
            case PIX            -> "PIX";
            default             -> null; // Garante que cobre qualquer outro valor do enum
        };
    }

    @Override
    public StatusPagamento consultar(String transacaoId) {
        String resposta = http.get().uri("/orders/{id}", transacaoId).retrieve().body(String.class);
        return StatusPagamento.valueOf(resposta);
    }

    @Override
    public void cancelar(String transacaoId) {
        throw new UnsupportedOperationException("Cancelamento ainda não implementado");
    }
}