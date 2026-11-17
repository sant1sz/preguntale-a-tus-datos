package com.sant1sz.preguntale.datos;

import java.time.LocalDateTime;

public record ResumenDatos(
        long categorias,
        long productos,
        long ventas,
        LocalDateTime primeraVenta,
        LocalDateTime ultimaVenta,
        long ventasTotalesPesos,
        long productosBajoStock,
        long lotesVencidos,
        long lotesPorVencerEn7Dias) {
}
