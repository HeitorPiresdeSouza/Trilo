package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.domain.model.Pedido;
import br.eti.hpds.fastFurious.domain.model.StatusPedido;
import br.eti.hpds.fastFurious.domain.repository.PedidoRepository;
import br.eti.hpds.fastFurious.service.CobrancaTerminalService;
import br.eti.hpds.fastFurious.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consulta periodicamente a origem do pagamento (Cielo ou o app da maquininha) para descobrir
 * quais pedidos foram pagos, cancelar os PIX cujo QR Code expirou e encerrar cobranças de
 * cartão paradas. É a fonte principal; o webhook, quando existir, só acelera.
 */
@Component
public class PagamentoPoller {

    private static final Logger log = LoggerFactory.getLogger(PagamentoPoller.class);

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final CobrancaTerminalService cobrancas;

    public PagamentoPoller(PedidoRepository pedidoRepository,
                           PedidoService pedidoService,
                           CobrancaTerminalService cobrancas) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.cobrancas = cobrancas;
    }

    @Scheduled(fixedDelayString = "${pagamento.polling.intervalo-ms:3000}")
    public void verificarPendentes() {
        try {
            cobrancas.expirarVencidas();
        } catch (Exception e) {
            log.warn("Falha ao expirar cobranças de cartão: {}", e.toString());
        }

        for (Pedido pedido : pedidoRepository.findByStatus(StatusPedido.AGUARDANDO_PAGAMENTO)) {
            if (pedido.getTransacaoId() == null) {
                continue; // dinheiro: confirmado manualmente pelo atendente
            }
            try {
                pedidoService.verificarPagamento(pedido.getId());
            } catch (Exception e) {
                // uma falha em um pedido não pode impedir a verificação dos outros
                log.warn("Falha ao verificar pagamento do pedido {}: {}", pedido.getId(), e.toString());
            }
        }
    }
}
