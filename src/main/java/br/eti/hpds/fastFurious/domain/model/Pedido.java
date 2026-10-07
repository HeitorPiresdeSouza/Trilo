package br.eti.hpds.fastFurious.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
public class Pedido {

    // READ_ONLY: o cliente não pode definir esses campos no JSON de entrada

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime dataAbertura;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime dataCancelado;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime dataPronto;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime dataEntregue;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime dataPagamento;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> listaItens;

    @Enumerated(EnumType.STRING)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private StatusPedido status;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TipoConsumo consumo;

    @NotNull
    @Enumerated(EnumType.STRING)
    private OpcaoPagamento pagamento;

    /** Total calculado no servidor a partir dos preços dos produtos. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal valorTotal;

    /** Id do pedido na adquirente (UUID da Cielo). Nulo para pagamento em dinheiro. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String transacaoId;

    public Pedido() {
    }

    public Pedido(Long id, LocalDateTime dataAbertura, LocalDateTime dataCancelado, LocalDateTime dataPronto,
            LocalDateTime dataEntregue, List<ItemPedido> listaItens, StatusPedido status,
            TipoConsumo consumo, OpcaoPagamento pagamento) {
        this.id = id;
        this.dataAbertura = dataAbertura;
        this.dataCancelado = dataCancelado;
        this.dataPronto = dataPronto;
        this.dataEntregue = dataEntregue;
        this.listaItens = listaItens;
        this.status = status;
        this.consumo = consumo;
        this.pagamento = pagamento;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getDataAbertura() { return dataAbertura; }
    public void setDataAbertura(LocalDateTime dataAbertura) { this.dataAbertura = dataAbertura; }

    public LocalDateTime getDataCancelado() { return dataCancelado; }
    public void setDataCancelado(LocalDateTime dataCancelado) { this.dataCancelado = dataCancelado; }

    public LocalDateTime getDataPronto() { return dataPronto; }
    public void setDataPronto(LocalDateTime dataPronto) { this.dataPronto = dataPronto; }

    public LocalDateTime getDataEntregue() { return dataEntregue; }
    public void setDataEntregue(LocalDateTime dataEntregue) { this.dataEntregue = dataEntregue; }

    public LocalDateTime getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDateTime dataPagamento) { this.dataPagamento = dataPagamento; }

    public List<ItemPedido> getListaItens() { return listaItens; }
    public void setListaItens(List<ItemPedido> listaItens) { this.listaItens = listaItens; }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public TipoConsumo getConsumo() { return consumo; }
    public void setConsumo(TipoConsumo consumo) { this.consumo = consumo; }

    public OpcaoPagamento getPagamento() { return pagamento; }
    public void setPagamento(OpcaoPagamento pagamento) { this.pagamento = pagamento; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public String getTransacaoId() { return transacaoId; }
    public void setTransacaoId(String transacaoId) { this.transacaoId = transacaoId; }

    // equals/hashCode por id (os anteriores incluíam a lista lazy de itens)
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Pedido other = (Pedido) obj;
        return id != null && id.equals(other.id);
    }
}