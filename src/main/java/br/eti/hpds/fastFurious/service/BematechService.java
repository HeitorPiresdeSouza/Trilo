package br.eti.hpds.fastfurious.service;

import br.eti.hpds.fastFurious.domain.model.ItemPedido;
import br.eti.hpds.fastFurious.domain.model.Pedido;
import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Service
public class BematechService {

    // Atualizado para a porta correta detetada pelo sistema
    private static final String PORT_NAME = "COM1";

    /**
     * Monta o texto formatado do cupom com margem à esquerda e avanço extra no final.
     */
    public String montarTextoCupom(Pedido pedido) {
        StringBuilder sb = new StringBuilder();
        
        // Margem esquerda (espaços adicionados no início de cada linha)
        String margem = "   "; 

        sb.append(margem).append("========================================\n");
        sb.append(margem).append("             FAST & FURIOUS             \n");
        sb.append(margem).append("========================================\n");
        sb.append(margem).append("Pedido #: ").append(pedido.getId()).append("\n");

        if (pedido.getDataAbertura() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            sb.append(margem).append("Data: ").append(pedido.getDataAbertura().format(formatter)).append("\n");
        }

        sb.append(margem).append("Consumo: ").append(pedido.getConsumo()).append("\n");
        sb.append(margem).append("Pagamento: ").append(pedido.getPagamento()).append("\n");
        sb.append(margem).append("----------------------------------------\n");
        sb.append(margem).append("QTD  ITEM                       VALOR   \n");
        sb.append(margem).append("----------------------------------------\n");

        if (pedido.getListaItens() != null) {
            for (ItemPedido item : pedido.getListaItens()) {
                String nomeProduto = (item.getProduto() != null && item.getProduto().getName() != null) 
                    ? item.getProduto().getName() 
                    : "Produto";
                
                if (nomeProduto.length() > 20) {
                    nomeProduto = nomeProduto.substring(0, 20);
                }
                
                BigDecimal valorUnitario = item.getvUnit() != null ? item.getvUnit() : BigDecimal.ZERO;
                BigDecimal valorTotalItem = valorUnitario.multiply(BigDecimal.valueOf(item.getQuantidade()));

                sb.append(margem).append(String.format("%-4d %-22s R$%6.2f\n", 
                        item.getQuantidade(), 
                        nomeProduto, 
                        valorTotalItem.doubleValue()));
            }
        }

        sb.append(margem).append("----------------------------------------\n");
        double valorTotalDouble = pedido.getValorTotal() != null ? pedido.getValorTotal().doubleValue() : 0.0;
        sb.append(margem).append(String.format("TOTAL:                     R$%8.2f\n", valorTotalDouble));
        sb.append(margem).append("========================================\n");
        sb.append(margem).append("       Obrigado pela preferencia!       \n");
        
        // Linhas em branco no final para forçar o avanço do papel
        sb.append("\n\n\n\n\n\n\n");

        return sb.toString();
    }

    /**
     * Envia os bytes do cupom diretamente para a porta serial COM1.
     */
    public void imprimirTexto(String textoParaImprimir) {
        SerialPort comPort = SerialPort.getCommPort(PORT_NAME);

        comPort.setBaudRate(9600);
        comPort.setNumDataBits(8);
        comPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        comPort.setParity(SerialPort.NO_PARITY);
        comPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

        if (!comPort.openPort()) {
            System.err.println("ERRO CRITICO: A porta " + PORT_NAME + " esta ocupada por outro programa ou nao existe!");
            return;
        }

        System.out.println("Porta " + PORT_NAME + " aberta com sucesso pelo Java!");

        try {
            comPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 1000, 0);

            // Comando ESC/POS para inicializar a impressora
            byte[] initPrinter = new byte[]{0x1B, 0x40};
            comPort.writeBytes(initPrinter, initPrinter.length);

            // Converte o texto para ISO-8859-1 (suporta acentos) e envia
            byte[] textBytes = textoParaImprimir.getBytes(StandardCharsets.ISO_8859_1);
            comPort.writeBytes(textBytes, textBytes.length);

            System.out.println("Sucesso: Cupom enviado diretamente para a porta " + PORT_NAME);
        } catch (Exception e) {
            System.err.println("Erro ao enviar dados para a impressora serial: " + e.getMessage());
        } finally {
            comPort.closePort();
        }
    }
}