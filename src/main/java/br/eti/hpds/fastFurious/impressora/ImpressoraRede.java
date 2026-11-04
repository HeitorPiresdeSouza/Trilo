package br.eti.hpds.fastFurious.impressora;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Socket;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;

/**
 * Imprime abrindo um socket TCP (porta 9100) direto na impressora.
 * Só funciona se o Spring estiver na mesma rede que a impressora.
 * O texto sai sem acentos para não depender da tabela de caracteres da impressora.
 */
@Service
@ConditionalOnProperty(name = "impressora.modo", havingValue = "rede")
public class ImpressoraRede implements ImpressoraCupom {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String LINHA = "--------------------------------";

    @Value("${impressora.host}") private String host;
    @Value("${impressora.porta:9100}") private int porta;

    @Override
    public void imprimir(Cupom cupom) {
        try (Socket socket = new Socket(host, porta);
             EscPos escpos = new EscPos(socket.getOutputStream())) {

            Style centro = new Style().setBold(true)
                    .setJustification(EscPosConst.Justification.Center);

            escpos.writeLF(centro, "FAST FURIOUS");
            escpos.writeLF(texto("Pedido: " + cupom.numero()));
            if (cupom.dataHora() != null) {
                escpos.writeLF(cupom.dataHora().format(DATA));
            }
            if (cupom.consumo() != null) {
                escpos.writeLF(texto(cupom.consumo()));
            }
            escpos.writeLF(LINHA);

            // for em vez de forEach: os métodos do escpos lançam IOException,
            // que não pode ser propagada de dentro de uma lambda
            for (Cupom.Item i : cupom.itens()) {
                escpos.writeLF(texto(i.descricao()) + "  R$ " + dinheiro(i.valor()));
            }

            escpos.writeLF(LINHA);
            escpos.writeLF(centro, "TOTAL  R$ " + dinheiro(cupom.total()));
            if (cupom.formaPagamento() != null) {
                escpos.writeLF(texto("Pagamento: " + cupom.formaPagamento()));
            }
            if (cupom.aviso() != null) {
                escpos.feed(1);
                escpos.writeLF(centro, texto(cupom.aviso()));
            }

            escpos.feed(3).cut(EscPos.CutMode.FULL);

        } catch (IOException e) {
            throw new IllegalStateException("Falha ao imprimir", e);
        }
    }

    private static String dinheiro(BigDecimal v) {
        return v == null ? "0,00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Remove acentos (á -> a, ç -> c). */
    private static String texto(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
