package br.eti.hpds.fastFurious.domain.dto;

import java.time.LocalDateTime;

/**
 * Resposta leve para o totem consultar a cada 3 s.
 * pago = o pagamento foi confirmado. status mostra o resto: AGUARDANDO_PAGAMENTO, ABERTO...
 * CANCELADO (inclusive por QR Code expirado) significa que o totem pode parar de esperar.
 */
public record StatusPagamentoPedidoDTO(
        Long pedidoId,
        boolean pago,
        String status,
        String formaPagamento,
        String pixCopiaECola,      // só enquanto aguarda pagamento
        LocalDateTime pixExpiraEm,
        LocalDateTime pagoEm) {
}
