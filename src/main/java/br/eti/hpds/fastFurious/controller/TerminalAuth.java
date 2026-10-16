package br.eti.hpds.fastFurious.controller;

import br.eti.hpds.fastFurious.exceptionhandler.RegraNegocioException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Autentica o app da maquininha por um token compartilhado (header X-Terminal-Token). */
@Component
@ConditionalOnProperty(name = "pagamento.terminal", havingValue = "app-smart", matchIfMissing = true)
public class TerminalAuth {

    private final byte[] token;

    public TerminalAuth(@Value("${terminal.token}") String token) {
        if (token == null || token.length() < 24) {
            throw new IllegalStateException("terminal.token precisa ter ao menos 24 caracteres");
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    public void exigir(String recebido) {
        boolean ok = recebido != null
                && MessageDigest.isEqual(token, recebido.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new RegraNegocioException(HttpStatus.UNAUTHORIZED, "Token do terminal inválido");
        }
    }
}