package br.eti.hpds.fastFurious.domain.model;

public enum StatusPedido {
    AGUARDANDO_PAGAMENTO, // criado, ainda não pago: não aparece para a cozinha
    ABERTO,               // pago: entra na fila da cozinha
    PRONTO,
    ENTREGUE,
    CANCELADO
}