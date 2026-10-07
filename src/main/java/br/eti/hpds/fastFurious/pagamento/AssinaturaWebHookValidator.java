package br.eti.hpds.fastFurious.pagamento;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Valida HMAC-SHA256 do corpo cru. Padrão comum, mas o formato exato
 * (header, algoritmo, hex/base64) depende da adquirente. Confirme na documentação.
 */
@Component
public class AssinaturaWebhookValidator {

    private final byte[] segredo;

    public AssinaturaWebhookValidator(@Value("${pagamento.webhook.segredo}") String segredo) {
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
    }

    public boolean valida(String corpo, String assinaturaRecebida) {
        if (assinaturaRecebida == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            byte[] esperado = mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8));
            String esperadoHex = HexFormat.of().formatHex(esperado);

            // Comparação em tempo constante (evita timing attack)
            return MessageDigest.isEqual(
                    esperadoHex.getBytes(StandardCharsets.UTF_8),
                    assinaturaRecebida.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erro ao validar assinatura", e);
        }
    }
}