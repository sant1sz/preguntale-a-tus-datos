package com.sant1sz.preguntale.datos.modelo;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Unidades de un producto en bodega que comparten fecha de vencimiento. */
@Entity
@Table(name = "lote")
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    protected Lote() {
    }

    public Long getId() { return id; }
    public Producto getProducto() { return producto; }
    public String getCodigo() { return codigo; }
    public int getCantidad() { return cantidad; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
}
