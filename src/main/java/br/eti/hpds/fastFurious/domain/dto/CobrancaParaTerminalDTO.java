package br.eti.hpds.fastFurious.domain.dto;

/** O que o app da maquininha recebe para abrir o pagamento. */
public record CobrancaParaTerminalDTO(
        String cobrancaId,
        Long pedidoId,
        String referencia,
        long valorCentavos,
        String paymentCode,
        String clientId,
        String accessToken) {
}
