package br.eti.hpds.fastFurious.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Entity
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 60)
    private String name;

    @NotBlank
    @Size(max = 60)
    private String descricao;

    @NotBlank
    @Size(max = 40)
    private String categoria;

    private String imagem;

    private Double qtd;

    // BigDecimal: Double acumula erro de arredondamento e quebra a conversão para centavos
    @NotNull
    @PositiveOrZero
    private BigDecimal valor;

    public Produto() {
    }

    public Produto(Long id, String name, String descricao, String categoria, String imagem, Double qtd, BigDecimal valor) {
        this.id = id;
        this.name = name;
        this.descricao = descricao;
        this.categoria = categoria;
        this.imagem = imagem;
        this.qtd = qtd;
        this.valor = valor;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getImagem() { return imagem; }
    public void setImagem(String imagem) { this.imagem = imagem; }

    public Double getQtd() { return qtd; }
    public void setQtd(Double qtd) { this.qtd = qtd; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    // equals/hashCode por id: os anteriores davam NullPointerException com id nulo
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Produto other = (Produto) obj;
        return id != null && id.equals(other.id);
    }
}