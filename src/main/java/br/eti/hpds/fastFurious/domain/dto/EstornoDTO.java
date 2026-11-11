package br.eti.hpds.fastFurious.domain.dto;

import br.eti.hpds.fastFurious.domain.model.Estorno;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EstornoDTO(
        String id,
        Long pedidoId,
        String forma,
        String rota,
        String status,
        BigDecimal valor,
        String motivo,
        String operador,
        String mensagem,
        LocalDateTime criadoEm,
        LocalDateTime concluidoEm) {

    public static EstornoDTO de(Estorno e) {
        return new EstornoDTO(
                e.getId(),
                e.getPedidoId(),
                e.getForma() == null ? null : e.getForma().name(),
                e.getRota() == null ? null : e.getRota().name(),
                e.getStatus() == null ? null : e.getStatus().name(),
                BigDecimal.valueOf(e.getValorCentavos(), 2),
                e.getMotivo(),
                e.getOperador(),
                e.getMensagem(),
                e.getCriadoEm(),
                e.getConcluidoEm());
    }
}
