package com.sant1sz.preguntale.datos.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Precios y costos en pesos colombianos (sin decimales). */
@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(name = "precio", nullable = false)
    private long precio;

    @Column(name = "costo", nullable = false)
    private long costo;

    @Column(name = "stock_actual", nullable = false)
    private int stockActual;

    @Column(name = "stock_minimo", nullable = false)
    private int stockMinimo;

    @Column(name = "perecedero", nullable = false)
    private boolean perecedero;

    protected Producto() {
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public Categoria getCategoria() { return categoria; }
    public long getPrecio() { return precio; }
    public long getCosto() { return costo; }
    public int getStockActual() { return stockActual; }
    public int getStockMinimo() { return stockMinimo; }
    public boolean isPerecedero() { return perecedero; }
}
