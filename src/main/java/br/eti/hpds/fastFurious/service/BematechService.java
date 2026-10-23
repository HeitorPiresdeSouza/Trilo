package br.eti.hpds.fastfurious.service;

import br.eti.hpds.fastFurious.domain.model.ItemPedido;
import br.eti.hpds.fastFurious.domain.model.Pedido;
import org.springframework.stereotype.Service;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Service
public class BematechService {

    /**
     * Monta o texto formatado do cupom com margem à esquerda para não cortar.
     */
    public String montarTextoCupom(Pedido pedido) {
        StringBuilder sb = new StringBuilder();
        
        // Margem esquerda (espaços adicionados no início de cada linha)
        String margem = " "; 

        sb.append(margem).append("========================================\n");
        sb.append(margem).append("                 TRILO                  \n");
        sb.append(margem).append("========================================\n");
        sb.append(margem).append("    Pedido #: ").append(pedido.getId()).append("\n");

        if (pedido.getDataAbertura() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            sb.append(margem).append("    Data: ").append(pedido.getDataAbertura().format(formatter)).append("\n");
        }

        sb.append(margem).append("    Consumo: ").append(pedido.getConsumo()).append("\n");
        sb.append(margem).append("    Pagamento: ").append(pedido.getPagamento()).append("\n");
        sb.append(margem).append("----------------------------------------\n");
        sb.append(margem).append("    QTD  ITEM                  VALOR    \n");
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
        sb.append(margem).append(String.format("    TOTAL:                  R$%8.2f\n\n\n\n", valorTotalDouble));
        sb.append(margem).append("========================================\n");
        sb.append(margem).append("       Obrigado pela preferencia!       \n\n\n\n");
        
        sb.append("\n\n\n\n\n\n\n");

        return sb.toString();
    }

    /**
     * Envia o texto utilizando a impressora do Windows (Spooler).
     */
    public void imprimirTexto(String textoParaImprimir) {
        try {
            byte[] rawData = textoParaImprimir.getBytes(StandardCharsets.ISO_8859_1);
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            Doc doc = new SimpleDoc(rawData, flavor, null);

            PrintService printService = null;
            PrintService[] services = PrintServiceLookup.lookupPrintServices(flavor, null);
            
            // Procura especificamente pela impressora Bematech e ignora impressoras PDF/XPS
            for (PrintService service : services) {
                String nomeImpressora = service.getName().toUpperCase();
                if (nomeImpressora.contains("BEMATECH") || nomeImpressora.contains("MP-2500")) {
                    printService = service;
                    break;
                }
            }

            if (printService != null) {
                DocPrintJob printJob = printService.createPrintJob();
                PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
                printJob.print(doc, pras);
                System.out.println("Sucesso: Cupom enviado para a impressora: " + printService.getName());
            } else {
                System.err.println("ERRO: A impressora Bematech não foi encontrada nas impressoras instaladas do Windows.");
            }

        } catch (Exception e) {
            System.err.println("Erro ao enviar dados para a impressora: " + e.getMessage());
        }
    }
}