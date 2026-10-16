package br.eti.hpds.fastFurious.domain.model;

/** Ciclo de vida de uma cobrança enviada ao app da maquininha. */
public enum StatusCobranca {
    PENDENTE,      // criada, ainda não buscada pelo app da maquininha
    EM_ANDAMENTO,  // o app buscou e abriu a tela de pagamento da Cielo
    PAGA,          // pagamento aprovado, valor igual ao esperado
    RECUSADA,      // recusado, cancelado pelo cliente ou erro
    PARCIAL        // valor pago diferente do esperado: exige tratamento manual
}