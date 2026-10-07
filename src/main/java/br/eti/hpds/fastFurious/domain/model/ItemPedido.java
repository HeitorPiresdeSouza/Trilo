package br.eti.hpds.fastFurious.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Preenchido pelo servidor a partir do Produto (o valor enviado pelo cliente é ignorado)
    @NotNull
    private BigDecimal vUnit;

    @NotNull
    @Min(1)
    private Integer quantidade = 1;

    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    @JsonIgnore
    private Pedido pedido;

    public ItemPedido() {
    }

    public ItemPedido(Long id, BigDecimal vUnit, Integer quantidade, Produto produto) {
        this.id = id;
        this.vUnit = vUnit;
        this.quantidade = quantidade;
        this.produto = produto;
    }

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigDecimal getvUnit() { return vUnit; }
    public void setvUnit(BigDecimal vUnit) { this.vUnit = vUnit; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ItemPedido other = (ItemPedido) obj;
        return id != null && id.equals(other.id);
    }
}