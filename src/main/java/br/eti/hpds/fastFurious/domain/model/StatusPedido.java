package br.eti.hpds.fastFurious.domain.model;

public enum StatusPedido {
    AGUARDANDO_PAGAMENTO, // criado, ainda não pago: não aparece para a cozinha
    ABERTO,               // pago: entra na fila da cozinha
    PRONTO,
    ENTREGUE,
    CANCELADO,            // cancelado sem ter sido pago
    ESTORNO_PENDENTE,     // pago, estorno solicitado e ainda não concluído (sai da cozinha)
    ESTORNADO             // pago e devolvido ao cliente
}
