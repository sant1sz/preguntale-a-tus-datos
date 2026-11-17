package com.sant1sz.preguntale.datos.simulacion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.sant1sz.preguntale.datos.modelo.MedioPago;

/** Filas listas para insertar en la base de datos. */
public record DatosSimulados(
        List<CategoriaFila> categorias,
        List<ProductoFila> productos,
        List<VentaFila> ventas,
        List<DetalleFila> detalles,
        List<LoteFila> lotes) {

    public record CategoriaFila(long id, String nombre) {
    }

    public record ProductoFila(long id, String nombre, long categoriaId, long precio, long costo,
            int stockActual, int stockMinimo, boolean perecedero) {
    }

    public record VentaFila(long id, LocalDateTime fechaHora, long total, MedioPago medioPago) {
    }

    public record DetalleFila(long id, long ventaId, long productoId, int cantidad, long precioUnitario,
            long subtotal) {
    }

    public record LoteFila(long id, long productoId, String codigo, int cantidad, LocalDate fechaVencimiento) {
    }
}
