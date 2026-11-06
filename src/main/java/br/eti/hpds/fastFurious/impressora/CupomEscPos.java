package br.eti.hpds.fastFurious.impressora;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;

/**
 * Monta o cupom em comandos ESC/POS (os bytes que uma impressora térmica entende).
 * É usado pela impressora de rede do servidor e pelo endpoint que entrega os bytes ao totem.
 * O texto sai sem acentos para não depender da tabela de caracteres da impressora.
 */
public final class CupomEscPos {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String LINHA = "--------------------------------";

    private CupomEscPos() { }

    public static byte[] gerar(Cupom cupom) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (EscPos escpos = new EscPos(saida)) {

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
            throw new IllegalStateException("Falha ao montar o cupom", e);
        }
        return saida.toByteArray();
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
