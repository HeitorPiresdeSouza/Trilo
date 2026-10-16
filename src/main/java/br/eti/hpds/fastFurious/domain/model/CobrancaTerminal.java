package br.eti.hpds.fastFurious.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uma cobrança que o backend pede ao app instalado na maquininha para executar. */
@Entity
public class CobrancaTerminal {

    @Id
    private String id;

    private Long pedidoId;

    /** Valor esperado, em centavos. */
    private Long valorCentavos;

    /** Código da Cielo (DEBITO_AVISTA, CREDITO_AVISTA, PIX...). */
    private String paymentCode;

    @Enumerated(EnumType.STRING)
    private StatusCobranca status;

    private Long valorPagoCentavos;
    private String authCode;
    private String nsu;
    private String bandeira;
    private String mensagem;

    private LocalDateTime criadaEm;
    private LocalDateTime atualizadaEm;

    public CobrancaTerminal() {
    }

    public CobrancaTerminal(Long pedidoId, long valorCentavos, String paymentCode) {
        this.id = UUID.randomUUID().toString();
        this.pedidoId = pedidoId;
        this.valorCentavos = valorCentavos;
        this.paymentCode = paymentCode;
        this.status = StatusCobranca.PENDENTE;
        this.criadaEm = LocalDateTime.now();
        this.atualizadaEm = this.criadaEm;
    }

    public String getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public Long getValorCentavos() { return valorCentavos; }
    public String getPaymentCode() { return paymentCode; }

    public StatusCobranca getStatus() { return status; }
    public void setStatus(StatusCobranca status) { this.status = status; }

    public Long getValorPagoCentavos() { return valorPagoCentavos; }
    public void setValorPagoCentavos(Long valorPagoCentavos) { this.valorPagoCentavos = valorPagoCentavos; }

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }

    public String getNsu() { return nsu; }
    public void setNsu(String nsu) { this.nsu = nsu; }

    public String getBandeira() { return bandeira; }
    public void setBandeira(String bandeira) { this.bandeira = bandeira; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public LocalDateTime getCriadaEm() { return criadaEm; }

    public LocalDateTime getAtualizadaEm() { return atualizadaEm; }
    public void setAtualizadaEm(LocalDateTime atualizadaEm) { this.atualizadaEm = atualizadaEm; }
}