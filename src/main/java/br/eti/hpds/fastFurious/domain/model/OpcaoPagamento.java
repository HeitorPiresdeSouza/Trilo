package br.eti.hpds.fastFurious.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OpcaoPagamento {
    PIX,
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    DINHEIRO;

    @JsonCreator
    public static OpcaoPagamento fromString(String valor) {
        for (OpcaoPagamento tipo : OpcaoPagamento.values()) {
            if (tipo.name().equalsIgnoreCase(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Opção de pagamento inválida: " + valor);
    }
}