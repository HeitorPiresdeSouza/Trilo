package br.eti.hpds.fastFurious.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarEstornoDTO(
        @NotBlank(message = "O motivo é obrigatório")
        @Size(max = 200, message = "Motivo com no máximo 200 caracteres")
        String motivo) {
}
