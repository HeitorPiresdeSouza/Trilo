package br.eti.hpds.fastFurious.impressora;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Conteúdo do cupom. "aviso" é a faixa de destaque: "PAGO" ou "PAGUE NO CAIXA" (dinheiro).
 */
public record Cupom(
        String lojaId,
        String numero,
        List<Item> itens,
        BigDecimal total,
        String formaPagamento,
        String consumo,
        LocalDateTime dataHora,
        String aviso) {

    public record Item(String descricao, BigDecimal valor) { }
}
