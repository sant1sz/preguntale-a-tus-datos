package com.sant1sz.preguntale.datos.modelo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "venta", indexes = @Index(name = "idx_venta_fecha", columnList = "fecha_hora"))
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "total", nullable = false)
    private long total;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, length = 20)
    private MedioPago medioPago;

    protected Venta() {
    }

    public Long getId() { return id; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public long getTotal() { return total; }
    public MedioPago getMedioPago() { return medioPago; }
}
