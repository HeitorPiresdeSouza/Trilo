package br.eti.hpds.fastFurious.service;

import br.eti.hpds.fastFurious.domain.model.ItemPedido;
import br.eti.hpds.fastFurious.domain.model.Pedido;
import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Service
public class BematechService {

    private static final String PORT_NAME = "COM1"; 
    private static final int BAUD_RATE = 9600;

    /**
     * Monta o texto completo do cupom com base na entidade Pedido
     */
    public String montarTextoCupom(Pedido pedido) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("================================\n");
        sb.append("              TRILO             \n");
        sb.append("================================\n");
        sb.append("Pedido ID: ").append(pedido.getId()).append("\n");
        sb.append("Data: ").append(pedido.getDataAbertura() != null ? pedido.getDataAbertura().toString() : "").append("\n");
        sb.append("Consumo: ").append(pedido.getConsumo()).append("\n");
        sb.append("Pagamento: ").append(pedido.getPagamento()).append("\n");
        sb.append("--------------------------------\n");
        sb.append("ITENS DO PEDIDO:\n");
        
        if (pedido.getListaItens() != null) {
            for (ItemPedido item : pedido.getListaItens()) {
                String nomeProduto = (item.getProduto() != null && item.getProduto().getName() != null) 
                                     ? item.getProduto().getName() : "Produto";
                int qtd = item.getQuantidade();
                BigDecimal vUnit = item.getvUnit();
                BigDecimal subtotal = vUnit != null ? vUnit.multiply(BigDecimal.valueOf(qtd)) : BigDecimal.ZERO;
                
                sb.append(String.format("%dx %s\n   R$ %.2f un | Subtotal: R$ %.2f\n", 
                          qtd, nomeProduto, vUnit, subtotal));
            }
        }
        
        sb.append("--------------------------------\n");
        sb.append(String.format("VALOR TOTAL: R$ %.2f\n", pedido.getValorTotal()));
        sb.append("================================\n");
        sb.append("   Obrigado pela preferencia!   \n");
        
        return sb.toString();
    }

    /**
     * Envia o texto formatado para a impressora térmica via porta serial
     */
    public void imprimirTexto(String textoParaImprimir) {
        SerialPort comPort = SerialPort.getCommPort(PORT_NAME);
        
        comPort.setBaudRate(BAUD_RATE);
        comPort.setNumDataBits(8);
        comPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        comPort.setParity(SerialPort.NO_PARITY);

        if (!comPort.openPort()) {
            throw new RuntimeException("Não foi possível abrir a porta serial " + PORT_NAME);
        }

        try {
            byte[] initCommand = new byte[] { 0x1B, 0x40 }; // Inicializa
            byte[] cutCommand = new byte[] { 0x1B, 0x69 };  // Corta papel
            String espacosFinais = "\n\n\n\n";

            StringBuilder payload = new StringBuilder();
            payload.append(textoParaImprimir);
            payload.append(espacosFinais);

            byte[] textBytes = payload.toString().getBytes(StandardCharsets.ISO_8859_1);

            comPort.writeBytes(initCommand, initCommand.length);
            comPort.writeBytes(textBytes, textBytes.length);
            comPort.writeBytes(cutCommand, cutCommand.length);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao enviar dados para a impressora: " + e.getMessage());
        } finally {
            comPort.closePort();
        }
    }
}