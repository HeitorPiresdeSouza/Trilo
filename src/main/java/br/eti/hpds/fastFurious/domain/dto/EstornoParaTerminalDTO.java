package br.eti.hpds.fastFurious.domain.dto;

/** O que o app da maquininha recebe para executar o cancelamento (lio://payment-reversal). */
public record EstornoParaTerminalDTO(
        String estornoId,
        Long pedidoId,
        String ordemId,
        String cieloCode,   // NSU do pagamento original
        String authCode,
        long valorCentavos,
        String clientId,
        String accessToken) {
}
