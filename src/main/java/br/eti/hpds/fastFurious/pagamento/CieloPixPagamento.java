package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.domain.model.OpcaoPagamento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * PIX com QR Code gerado pela API E-commerce da Cielo (integração nova, provedor "Cielo2").
 * O QR Code é mostrado na tela do totem. Só é ativado se cielo.pix.merchant-id estiver definido;
 * sem ele, o PIX cai no TerminalAppPagamento (QR Code na maquininha).
 *
 * ATENÇÃO: segundo a documentação da Cielo, a integração nova de PIX NÃO tem sandbox para a
 * criação do QR Code: os testes são em produção, com dinheiro de verdade.
 */
@Service
@Order(1)
@ConditionalOnProperty(name = "cielo.pix.merchant-id")
public class CieloPixPagamento implements PagamentoTerminal {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA =
            new ParameterizedTypeReference<>() { };

    private final RestClient transacao;
    private final RestClient consulta;
    private final int expiracaoSegundos;
    private final String nomeCliente;

    public CieloPixPagamento(
            @Value("${cielo.pix.url-transacao:https://api.cieloecommerce.cielo.com.br}") String urlTransacao,
            @Value("${cielo.pix.url-consulta:https://apiquery.cieloecommerce.cielo.com.br}") String urlConsulta,
            @Value("${cielo.pix.merchant-id}") String merchantId,
            @Value("${cielo.pix.merchant-key}") String merchantKey,
            @Value("${cielo.pix.expiracao-segundos:60}") int expiracaoSegundos,
            @Value("${cielo.pix.cliente-nome:Cliente}") String nomeCliente) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));

        this.transacao = RestClient.builder().requestFactory(factory).baseUrl(urlTransacao)
                .defaultHeader("MerchantId", merchantId)
                .defaultHeader("MerchantKey", merchantKey)
                .build();
        this.consulta = RestClient.builder().requestFactory(factory).baseUrl(urlConsulta)
                .defaultHeader("MerchantId", merchantId)
                .defaultHeader("MerchantKey", merchantKey)
                .build();
        this.expiracaoSegundos = expiracaoSegundos;
        this.nomeCliente = nomeCliente;
    }

    @Override
    public boolean suporta(OpcaoPagamento tipo) {
        return tipo == OpcaoPagamento.PIX;
    }

    @Override
    public ResultadoPagamento cobrar(BigDecimal valor, OpcaoPagamento tipo, String referencia) {
        long centavos = valor.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

        Map<String, Object> corpo = Map.of(
                "MerchantOrderId", "PED" + referencia,   // sem caracteres especiais, até 25
                "Customer", Map.of("Name", nomeCliente),
                "Payment", Map.of(
                        "Type", "Pix",
                        "Provider", "Cielo2",
                        "Amount", centavos,
                        "QrCode", Map.of("Expiration", expiracaoSegundos)));

        Map<String, Object> resposta = transacao.post().uri("/1/sales").body(corpo)
                .retrieve().body(MAPA);

        Map<?, ?> pagamento = (resposta != null && resposta.get("Payment") instanceof Map<?, ?> m) ? m : null;
        if (pagamento == null || pagamento.get("PaymentId") == null) {
            throw new IllegalStateException("Resposta da Cielo sem Payment.PaymentId");
        }

        String status = String.valueOf(pagamento.get("Status"));
        if ("0".equals(status) || "13".equals(status)) {
            throw new IllegalStateException("Cielo não gerou o QR Code Pix: " + pagamento.get("ReturnMessage"));
        }

        Object qrString = pagamento.get("QrCodeString");
        if (qrString == null) {
            throw new IllegalStateException("Resposta da Cielo sem Payment.QrCodeString");
        }
        Object qrImagem = pagamento.get("QrCodeBase64Image");

        return new ResultadoPagamento(
                String.valueOf(pagamento.get("PaymentId")),
                StatusPagamento.PENDENTE,
                String.valueOf(qrString),
                qrImagem == null ? null : String.valueOf(qrImagem),
                LocalDateTime.now().plusSeconds(expiracaoSegundos));
    }

    /**
     * Status do PIX na Cielo: 2 = pago (PaymentConfirmed); 12 = pendente (QR Code gerado);
     * 13 = abortado; 11 = estornado; 0 = falha ao gerar. Só o 2 libera o pedido.
     */
    @Override
    public StatusPagamento consultar(String transacaoId) {
        Map<String, Object> resposta = consulta.get().uri("/1/sales/{id}", transacaoId)
                .retrieve().body(MAPA);

        if (resposta == null || !(resposta.get("Payment") instanceof Map<?, ?> pagamento)) {
            return StatusPagamento.PENDENTE;
        }
        return switch (String.valueOf(pagamento.get("Status"))) {
            case "2" -> StatusPagamento.PAGO;
            case "0", "11", "13" -> StatusPagamento.CANCELADO;
            default -> StatusPagamento.PENDENTE;
        };
    }

    /**
     * Solicita a devolução do PIX: PUT /1/sales/{PaymentId}/void?amount={centavos}.
     * A Cielo responde que a devolução foi SOLICITADA; ela só termina quando o status do
     * pagamento passa a 11 (veja devolucaoConcluida). Segundo a documentação, a conta precisa
     * estar habilitada para devolução de PIX e o prazo é de até 90 dias.
     */
    public void devolver(String paymentId, long centavos) {
        Map<String, Object> resposta = transacao.put()
                .uri("/1/sales/{id}/void?amount={valor}", paymentId, centavos)
                .retrieve().body(MAPA);

        if (resposta != null) {
            Object codigo = resposta.get("ProviderReturnCode");
            if (codigo != null && !"0".equals(String.valueOf(codigo))) {
                throw new IllegalStateException("Cielo recusou a devolução: "
                        + resposta.get("ProviderReturnMessage"));
            }
        }
    }

    /** A devolução terminou? O status do pagamento na Cielo vira 11 (estornado). */
    public boolean devolucaoConcluida(String paymentId) {
        Map<String, Object> resposta = consulta.get().uri("/1/sales/{id}", paymentId)
                .retrieve().body(MAPA);
        return resposta != null
                && resposta.get("Payment") instanceof Map<?, ?> pagamento
                && "11".equals(String.valueOf(pagamento.get("Status")));
    }

    @Override
    public void cancelar(String transacaoId) {
        // O QR Code expira sozinho (cielo.pix.expiracao-segundos). A devolução de um PIX já
        // pago é feita pelo EstornoService (devolver).
        throw new UnsupportedOperationException("Use o EstornoService para devolver um PIX pago");
    }
}
