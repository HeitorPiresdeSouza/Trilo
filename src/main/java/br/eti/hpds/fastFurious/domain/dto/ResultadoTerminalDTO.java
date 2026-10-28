package br.eti.hpds.fastFurious.domain.dto;

import jakarta.validation.constraints.NotNull;

/** O que o app da maquininha devolve depois do pagamento. */
public record ResultadoTerminalDTO(
        @NotNull(message = "sucesso é obrigatório") Boolean sucesso,
        Long valorPagoCentavos,
        String authCode,
        String nsu,
        String bandeira,
        String mensagem) {
}
