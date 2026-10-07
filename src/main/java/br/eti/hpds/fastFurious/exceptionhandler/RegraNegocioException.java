package br.eti.hpds.fastFurious.exceptionhandler;

import org.springframework.http.HttpStatus;

/** Erro de regra de negócio, com o status HTTP que deve ser devolvido ao cliente. */
public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;

    public RegraNegocioException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}