package br.eti.hpds.fastFurious.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.UUID;

/** Pedido de devolução de um pedido já pago. Também serve de fila para o app da maquininha. */
@Entity
public class Estorno {

    @Id
    private String id;

    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    private OpcaoPagamento forma;

    @Enumerated(EnumType.STRING)
    private RotaEstorno rota;

    @Enumerated(EnumType.STRING)
    private StatusEstorno status;

    /** Valor a devolver, em centavos (sempre o total do pedido). */
    private Long valorCentavos;

    /** Status do pedido antes do estorno, para restaurar se o estorno falhar. */
    private String statusAnteriorPedido;

    /** Id do pagamento original: cobrança (cartão) ou PaymentId da Cielo (PIX). */
    private String transacaoId;

    // Dados do pagamento original no cartão, exigidos pelo cancelamento da Cielo
    private String ordemId;
    private String nsu;
    private String authCode;

    private String motivo;
    private String operador;

    @Column(length = 500)
    private String mensagem;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime concluidoEm;

    public Estorno() {
    }

    public Estorno(Long pedidoId, OpcaoPagamento forma, long valorCentavos, String transacaoId,
                   String motivo, String operador) {
        this.id = UUID.randomUUID().toString();
        this.pedidoId = pedidoId;
        this.forma = forma;
        this.valorCentavos = valorCentavos;
        this.transacaoId = transacaoId;
        this.motivo = motivo;
        this.operador = operador;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    public String getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public OpcaoPagamento getForma() { return forma; }
    public Long getValorCentavos() { return valorCentavos; }
    public String getTransacaoId() { return transacaoId; }
    public String getMotivo() { return motivo; }
    public String getOperador() { return operador; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    public RotaEstorno getRota() { return rota; }
    public void setRota(RotaEstorno rota) { this.rota = rota; }

    public StatusEstorno getStatus() { return status; }
    public void setStatus(StatusEstorno status) { this.status = status; }

    public String getStatusAnteriorPedido() { return statusAnteriorPedido; }
    public void setStatusAnteriorPedido(String statusAnteriorPedido) { this.statusAnteriorPedido = statusAnteriorPedido; }

    public String getOrdemId() { return ordemId; }
    public void setOrdemId(String ordemId) { this.ordemId = ordemId; }

    public String getNsu() { return nsu; }
    public void setNsu(String nsu) { this.nsu = nsu; }

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) {
        this.mensagem = (mensagem != null && mensagem.length() > 500) ? mensagem.substring(0, 500) : mensagem;
    }

    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }

    public LocalDateTime getConcluidoEm() { return concluidoEm; }
    public void setConcluidoEm(LocalDateTime concluidoEm) { this.concluidoEm = concluidoEm; }
}
