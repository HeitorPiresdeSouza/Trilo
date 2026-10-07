package br.eti.hpds.fastFurious.pagamento;

/** Retorno de uma cobrança. O resultado final só é conhecido depois, via consultar(). */
public record ResultadoPagamento(String transacaoId, StatusPagamento status) { }