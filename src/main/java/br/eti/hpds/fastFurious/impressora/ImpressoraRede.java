package br.eti.hpds.fastFurious.impressora;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Imprime abrindo um socket TCP (porta 9100) direto na impressora.
 * Só funciona se o Spring estiver na mesma rede que a impressora.
 * Se a impressora estiver ligada ao totem, não use este modo: o totem imprime com
 * GET /pedido/{id}/cupom/escpos.
 */
@Service
@ConditionalOnProperty(name = "impressora.modo", havingValue = "rede")
public class ImpressoraRede implements ImpressoraCupom {

    @Value("${impressora.host}") private String host;
    @Value("${impressora.porta:9100}") private int porta;

    @Override
    public void imprimir(Cupom cupom) {
        byte[] dados = CupomEscPos.gerar(cupom);
        try (Socket socket = new Socket(host, porta)) {
            OutputStream saida = socket.getOutputStream();
            saida.write(dados);
            saida.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao imprimir", e);
        }
    }
}
