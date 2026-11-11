package br.eti.hpds.fastFurious.domain.model;

public enum StatusEstorno {
    SOLICITADO,      // aguardando a maquininha (ou a chamada à Cielo, no PIX)
    EM_ANDAMENTO,    // a maquininha buscou / a Cielo aceitou: aguardando confirmação
    PENDENTE_MANUAL, // sem automação: alguém devolve o dinheiro e confirma
    CONCLUIDO,
    FALHOU           // recusado ou expirou; o pedido volta ao status anterior
}
