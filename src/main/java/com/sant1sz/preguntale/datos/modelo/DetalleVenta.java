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

@Entity
@Table(name = "detalle_venta")
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private long precioUnitario;

    @Column(name = "subtotal", nullable = false)
    private long subtotal;

    protected DetalleVenta() {
    }

    public Long getId() { return id; }
    public Venta getVenta() { return venta; }
    public Producto getProducto() { return producto; }
    public int getCantidad() { return cantidad; }
    public long getPrecioUnitario() { return precioUnitario; }
    public long getSubtotal() { return subtotal; }
}
