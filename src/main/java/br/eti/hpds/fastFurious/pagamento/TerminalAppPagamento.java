package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.domain.model.CobrancaTerminal;
import br.eti.hpds.fastFurious.domain.model.OpcaoPagamento;
import br.eti.hpds.fastFurious.service.CobrancaTerminalService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Cobra pela maquininha através do app Android instalado nela (integração via Deep Link).
 * O backend só registra a cobrança; o app a busca, abre a tela de pagamento da Cielo já com
 * a forma escolhida no totem e devolve o resultado.
 * É a implementação padrão (pagamento.terminal=app-smart).
 */
@Service
@ConditionalOnProperty(name = "pagamento.terminal", havingValue = "app-smart", matchIfMissing = true)
public class TerminalAppPagamento implements PagamentoTerminal {

    private final CobrancaTerminalService cobrancas;

    public TerminalAppPagamento(CobrancaTerminalService cobrancas) {
        this.cobrancas = cobrancas;
    }

    @Override
    public ResultadoPagamento cobrar(BigDecimal valor, OpcaoPagamento tipo, String referencia) {
        long centavos = valor.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        CobrancaTerminal c = cobrancas.criar(Long.valueOf(referencia), centavos, paymentCode(tipo));
        return new ResultadoPagamento(c.getId(), StatusPagamento.PENDENTE);
    }

    @Override
    public StatusPagamento consultar(String transacaoId) {
        return cobrancas.buscar(transacaoId)
                .map(c -> switch (c.getStatus()) {
                    case PAGA -> StatusPagamento.PAGO;
                    case RECUSADA -> StatusPagamento.CANCELADO;
                    default -> StatusPagamento.PENDENTE; // PENDENTE, EM_ANDAMENTO e PARCIAL (manual)
                })
                .orElse(StatusPagamento.PENDENTE);
    }

    /** Códigos do campo paymentCode do deep link (lista oficial da Cielo). */
    private String paymentCode(OpcaoPagamento tipo) {
        return switch (tipo) {
            case CARTAO_DEBITO  -> "DEBITO_AVISTA";
            case CARTAO_CREDITO -> "CREDITO_AVISTA";
            case PIX            -> "PIX";
            case DINHEIRO       -> throw new IllegalArgumentException("Dinheiro não passa pela maquininha");
        };
    }

    @Override
    public void cancelar(String transacaoId) {
        // Ainda não implementado (no deep link o estorno usa lio://payment-reversal, feito pelo app).
        throw new UnsupportedOperationException("Cancelamento ainda não implementado");
    }
}