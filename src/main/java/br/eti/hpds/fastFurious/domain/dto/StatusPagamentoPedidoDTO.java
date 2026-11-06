package br.eti.hpds.fastFurious.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resposta leve para o totem consultar a cada 3 s.
 *
 * pago = o pagamento foi confirmado. status = AGUARDANDO_PAGAMENTO, ABERTO... Se vier
 * CANCELADO (QR Code vencido, maquininha sem resposta, pedido cancelado), o totem pode parar de esperar.
 *
 * etapa diz o que mostrar ao cliente:
 *   AGUARDANDO_PIX         mostrar o QR Code (pixCopiaECola / imagem da criação)
 *   AGUARDANDO_MAQUININHA  "dirija-se à maquininha" (ela ainda vai abrir a tela de pagamento)
 *   PAGANDO_NA_MAQUININHA  "aproxime ou insira o cartão"
 *   PAGAR_NO_CAIXA         pedido em dinheiro: levar o cupom ao caixa
 *   PAGO                   imprimir o cupom (GET /pedido/{id}/cupom/escpos)
 *   CANCELADO              avisar que o pedido foi cancelado
 */
public record StatusPagamentoPedidoDTO(
        Long pedidoId,
        boolean pago,
        String status,
        String formaPagamento,
        String etapa,
        BigDecimal valorTotal,
        String pixCopiaECola,      // só enquanto aguarda pagamento
        LocalDateTime pixExpiraEm,
        LocalDateTime pagoEm) {
}
