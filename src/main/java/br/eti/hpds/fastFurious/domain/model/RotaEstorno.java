package br.eti.hpds.fastFurious.domain.model;

/** Por onde o dinheiro é devolvido. */
public enum RotaEstorno {
    APP,      // cartão: o app da maquininha executa o cancelamento (deep link payment-reversal)
    PIX_API,  // PIX gerado pela API E-commerce da Cielo: devolução por API
    MANUAL    // dinheiro, PIX da maquininha ou dados incompletos: devolução feita por uma pessoa
}
