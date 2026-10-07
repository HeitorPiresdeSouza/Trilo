package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.domain.model.OpcaoPagamento;
import java.math.BigDecimal;

/**
 * Abstração da maquininha. O resto do sistema só conhece esta interface,
 * então trocar de adquirente significa criar outra implementação.
 */
public interface PagamentoTerminal {

    /** Envia a cobrança para a maquininha. Devolve o id da transação na adquirente. */
    ResultadoPagamento cobrar(BigDecimal valor, OpcaoPagamento tipo, String referencia);

    /** Consulta na adquirente a situação atual da cobrança. */
    StatusPagamento consultar(String transacaoId);

    void cancelar(String transacaoId);

}