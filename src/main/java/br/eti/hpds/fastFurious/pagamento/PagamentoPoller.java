package br.eti.hpds.fastFurious.pagamento;

import br.eti.hpds.fastFurious.domain.model.Pedido;
import br.eti.hpds.fastFurious.domain.model.StatusPedido;
import br.eti.hpds.fastFurious.domain.repository.PedidoRepository;
import br.eti.hpds.fastFurious.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consulta periodicamente a adquirente para descobrir quais pedidos foram pagos.
 * Serve como fonte principal enquanto não há webhook e continua útil depois como rede de segurança.
 */
@Component
public class PagamentoPoller {

    private static final Logger log = LoggerFactory.getLogger(PagamentoPoller.class);

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;

    public PagamentoPoller(PedidoRepository pedidoRepository, PedidoService pedidoService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
    }

    @Scheduled(fixedDelayString = "${pagamento.polling.intervalo-ms:5000}")
    public void verificarPendentes() {
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