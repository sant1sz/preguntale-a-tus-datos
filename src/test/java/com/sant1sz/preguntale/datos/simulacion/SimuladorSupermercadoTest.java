package com.sant1sz.preguntale.datos.simulacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.DetalleFila;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.ProductoFila;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.VentaFila;

class SimuladorSupermercadoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 17, 30);
    private static final int DIAS = 90;
    private static DatosSimulados datos;

    @BeforeAll
    static void simular() {
        datos = nuevoSimulador().simular(AHORA, DIAS);
    }

    private static SimuladorSupermercado nuevoSimulador() {
        return new SimuladorSupermercado(2026, CatalogoSupermercado.productos());
    }

    @Test
    void conLaMismaSemillaGeneraLosMismosDatos() {
        DatosSimulados otra = nuevoSimulador().simular(AHORA, DIAS);
        assertEquals(datos, otra);
    }

    @Test
    void noHayVentasEnElFuturoNiAntesDelPeriodo() {
        LocalDateTime inicio = AHORA.toLocalDate().minusDays(DIAS).atStartOfDay();
        for (VentaFila venta : datos.ventas()) {
            assertFalse(venta.fechaHora().isAfter(AHORA), "venta en el futuro: " + venta);
            assertFalse(venta.fechaHora().isBefore(inicio), "venta antes del periodo: " + venta);
        }
    }

    @Test
    void losTotalesCuadranConLosDetalles() {
        Map<Long, Long> sumaPorVenta = datos.detalles().stream()
                .collect(Collectors.groupingBy(DetalleFila::ventaId, Collectors.summingLong(DetalleFila::subtotal)));
        for (VentaFila venta : datos.ventas()) {
            assertEquals(venta.total(), sumaPorVenta.get(venta.id()), "venta " + venta.id());
        }
        for (DetalleFila d : datos.detalles()) {
            assertEquals(d.cantidad() * d.precioUnitario(), d.subtotal());
        }
    }

    @Test
    void losSabadosSeVendeMasQueLosMartes() {
        Map<LocalDate, Long> ventasPorDia = datos.ventas().stream()
                .filter(v -> v.fechaHora().toLocalDate().isBefore(AHORA.toLocalDate()))
                .collect(Collectors.groupingBy(v -> v.fechaHora().toLocalDate(), Collectors.counting()));
        assertTrue(promedio(ventasPorDia, DayOfWeek.SATURDAY) > promedio(ventasPorDia, DayOfWeek.TUESDAY) * 1.3);
    }

    @Test
    void laBebidaEnergizanteVendeMasAlFinalQueAlInicio() {
        long id = idProducto("Bebida energizante 250 ml");
        LocalDate hoy = AHORA.toLocalDate();
        assertTrue(unidadesVendidas(id, hoy.minusDays(30), hoy) > 1.5 * unidadesVendidas(id, hoy.minusDays(DIAS), hoy.minusDays(60)));
    }

    @Test
    void elStockCoincideConLaSumaDeLosLotes() {
        Map<Long, Integer> unidadesEnLotes = datos.lotes().stream()
                .collect(Collectors.groupingBy(l -> l.productoId(), Collectors.summingInt(l -> l.cantidad())));
        for (ProductoFila p : datos.productos()) {
            int enLotes = unidadesEnLotes.getOrDefault(p.id(), 0);
            assertEquals(p.stockActual(), enLotes, p.nombre());
        }
    }

    @Test
    void hayCasosParaAlertar() {
        LocalDate hoy = AHORA.toLocalDate();
        assertTrue(datos.productos().stream().anyMatch(p -> p.stockActual() == 0), "algún producto agotado");
        assertTrue(datos.productos().stream().filter(p -> p.stockActual() < p.stockMinimo()).count() >= 3);
        assertTrue(datos.lotes().stream().anyMatch(l -> l.fechaVencimiento().isBefore(hoy)), "algún lote vencido");
        assertTrue(datos.lotes().stream().filter(l -> !l.fechaVencimiento().isBefore(hoy)
                && !l.fechaVencimiento().isAfter(hoy.plusDays(3))).count() >= 3, "lotes por vencer");
    }

    private static double promedio(Map<LocalDate, Long> ventasPorDia, DayOfWeek dia) {
        return ventasPorDia.entrySet().stream()
                .filter(e -> e.getKey().getDayOfWeek() == dia)
                .mapToLong(Map.Entry::getValue).average().orElseThrow();
    }

    private static long idProducto(String nombre) {
        return datos.productos().stream().filter(p -> p.nombre().equals(nombre)).findFirst().orElseThrow().id();
    }

    private static long unidadesVendidas(long productoId, LocalDate desde, LocalDate hasta) {
        Map<Long, VentaFila> ventas = datos.ventas().stream()
                .collect(Collectors.toMap(VentaFila::id, Function.identity()));
        return datos.detalles().stream()
                .filter(d -> d.productoId() == productoId)
                .filter(d -> {
                    LocalDate dia = ventas.get(d.ventaId()).fechaHora().toLocalDate();
                    return !dia.isBefore(desde) && dia.isBefore(hasta);
                })
                .mapToLong(DetalleFila::cantidad).sum();
    }
}
