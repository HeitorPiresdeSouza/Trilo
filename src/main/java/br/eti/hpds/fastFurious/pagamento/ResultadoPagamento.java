package br.eti.hpds.fastFurious.pagamento;

import java.time.LocalDateTime;

/**
 * Retorno de uma cobrança. O resultado final só é conhecido depois, via consultar().
 * Os campos pix* só vêm preenchidos quando a cobrança é um PIX com QR Code.
 */
public record ResultadoPagamento(
        String transacaoId,
        StatusPagamento status,
        String pixCopiaECola,
        String pixQrCodeImagem,
        LocalDateTime pixExpiraEm) {

    public ResultadoPagamento(String transacaoId, StatusPagamento status) {
        this(transacaoId, status, null, null, null);
    }
}
