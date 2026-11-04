package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.domain.model.OpcaoPagamento;
import java.math.BigDecimal;

/**
 * Abstração de uma forma de cobrar. O PedidoService escolhe a implementação pela forma de
 * pagamento (suporta), então cada forma pode usar um caminho diferente.
 */
public interface PagamentoTerminal {

    /** Esta implementação sabe cobrar a forma de pagamento informada? */
    boolean suporta(OpcaoPagamento tipo);

    /** Inicia a cobrança. Devolve o id da transação (e, no PIX, o QR Code). */
    ResultadoPagamento cobrar(BigDecimal valor, OpcaoPagamento tipo, String referencia);

    /** Consulta na origem a situação atual da cobrança. */
    StatusPagamento consultar(String transacaoId);

    void cancelar(String transacaoId);
}
