package br.eti.hpds.fastFurious.controller;

import br.eti.hpds.fastFurious.exceptionhandler.RegraNegocioException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Protege o estorno com um token de gerente (header X-Gerente-Token).
 * É uma proteção mínima: o totem NUNCA deve conhecer este token. Para produção, o ideal é
 * login com Spring Security. Sem estorno.token configurado, o estorno fica desabilitado.
 */
@Component
public class GerenteAuth {

    private final byte[] token;

    public GerenteAuth(@Value("${estorno.token:}") String token) {
        if (!token.isEmpty() && token.length() < 24) {
            throw new IllegalStateException("estorno.token precisa ter ao menos 24 caracteres");
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    public void exigir(String recebido) {
        if (token.length == 0) {
            throw new RegraNegocioException(HttpStatus.FORBIDDEN,
                    "Estorno desabilitado: defina estorno.token no application.properties");
        }
        boolean ok = recebido != null
                && MessageDigest.isEqual(token, recebido.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new RegraNegocioException(HttpStatus.UNAUTHORIZED, "Token de gerente inválido");
        }
    }
}
