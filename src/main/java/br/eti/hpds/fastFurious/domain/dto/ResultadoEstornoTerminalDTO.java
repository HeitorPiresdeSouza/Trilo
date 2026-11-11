package br.eti.hpds.fastFurious.domain.dto;

import jakarta.validation.constraints.NotNull;

/** O que o app da maquininha devolve depois de tentar o cancelamento. */
public record ResultadoEstornoTerminalDTO(
        @NotNull(message = "sucesso é obrigatório") Boolean sucesso,
        String mensagem) {
}
