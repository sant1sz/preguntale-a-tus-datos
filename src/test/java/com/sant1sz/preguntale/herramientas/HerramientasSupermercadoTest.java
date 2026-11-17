package com.sant1sz.preguntale.herramientas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.sant1sz.preguntale.datos.GeneradorDatos;
import com.sant1sz.preguntale.datos.simulacion.CatalogoSupermercado;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.DetalleFila;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.LoteFila;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.ProductoFila;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.VentaFila;
import com.sant1sz.preguntale.datos.simulacion.SimuladorSupermercado;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.AlertaStock;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.CriterioRanking;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.InfoProducto;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.LoteVencimiento;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.OrdenRanking;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.ProductoEnRiesgo;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.RankingProductos;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.ReporteCategorias;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.ReporteStock;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.ReporteVencimientos;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.ReporteVentasDiarias;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.ReporteVentasPorHora;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.ResumenVentas;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.VentasDia;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.VentasHora;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.VentasProducto;

/** Carga el supermercado simulado en H2 y compara lo que responden las herramientas con cálculos en Java. */
class HerramientasSupermercadoTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 17, 30);
    private static final LocalDate HOY = AHORA.toLocalDate();
    private static final long SEMILLA = 2026;
    private static final int DIAS = 90;

    private static DatosSimulados datos;
    private static Map<Long, ProductoFila> productosPorId;
    private static HerramientasSupermercado herramientas;

    @BeforeAll
    static void cargarBase() {
        JdbcDataSource fuente = new JdbcDataSource();
        fuente.setURL("jdbc:h2:mem:herramientas;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("esquema-pruebas.sql")).execute(fuente);

        Clock reloj = Clock.fixed(AHORA.atZone(BOGOTA).toInstant(), BOGOTA);
        new GeneradorDatos(new JdbcTemplate(fuente), reloj, true, true, DIAS, SEMILLA).run(null);

        datos = new SimuladorSupermercado(SEMILLA, CatalogoSupermercado.productos()).simular(AHORA, DIAS);
        productosPorId = datos.productos().stream().collect(Collectors.toMap(ProductoFila::id, Function.identity()));
        herramientas = new HerramientasSupermercado(JdbcClient.create(fuente), reloj);
    }

    // ---------- Consultas básicas ----------

    @Test
    void laFechaActualYElRangoDeVentasSonCorrectos() {
        var fecha = herramientas.fechaActual();
        assertEquals(HOY, fecha.hoy());
        assertEquals("lunes", fecha.diaSemana());
        assertEquals(HOY, fecha.ultimoDiaConVentas());
        assertFalse(fecha.primerDiaConVentas().isBefore(HOY.minusDays(DIAS)));
    }

    @Test
    void elResumenDeTodoElPeriodoCuadraConLasVentas() {
        ResumenVentas resumen = herramientas.resumenVentas(HOY.minusDays(DIAS).toString(), HOY.toString());
        assertEquals(datos.ventas().size(), resumen.numeroVentas());
        assertEquals(datos.ventas().stream().mapToLong(VentaFila::total).sum(), resumen.totalVendido());
        assertEquals(resumen.totalVendido(), resumen.porMedioPago().stream().mapToLong(m -> m.total()).sum());
        assertTrue(resumen.gananciaEstimada() > 0 && resumen.gananciaEstimada() < resumen.totalVendido());
    }

    @Test
    void elProductoMasVendidoCoincideConElCalculoEnJava() {
        LocalDate desde = HOY.minusDays(29);
        Map<Long, Long> unidades = detallesDesde(desde).stream()
                .collect(Collectors.groupingBy(DetalleFila::productoId, Collectors.summingLong(DetalleFila::cantidad)));
        long idMasVendido = unidades.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();

        List<VentasProducto> ranking = herramientas.rankingProductos(desde.toString(), HOY.toString(),
                CriterioRanking.UNIDADES, OrdenRanking.MAS_VENDIDOS, null, 5).productos();
        assertEquals(5, ranking.size());
        assertEquals(productosPorId.get(idMasVendido).nombre(), ranking.getFirst().producto());
        assertEquals(unidades.get(idMasVendido), ranking.getFirst().unidades());
    }

    @Test
    void elRankingDeMenosVendidosVaDeMenorAMayorYFiltraPorCategoria() {
        List<VentasProducto> ranking = herramientas.rankingProductos(HOY.minusDays(6).toString(), HOY.toString(),
                CriterioRanking.INGRESOS, OrdenRanking.MENOS_VENDIDOS, "Bebidas", 40).productos();
        assertEquals(5, ranking.size());
        assertTrue(ranking.stream().allMatch(p -> p.categoria().equals("Bebidas")));
        for (int i = 1; i < ranking.size(); i++) {
            assertTrue(ranking.get(i - 1).totalVendido() <= ranking.get(i).totalVendido());
        }
        assertThrows(IllegalArgumentException.class, () -> herramientas.rankingProductos(HOY.toString(),
                HOY.toString(), CriterioRanking.UNIDADES, OrdenRanking.MAS_VENDIDOS, "Juguetes", null));
    }

    @Test
    void laBusquedaIgnoraTildesYMayusculas() {
        List<InfoProducto> cafe = herramientas.buscarProductos("CAFE", null);
        assertEquals(List.of("Café molido 500 g"), cafe.stream().map(InfoProducto::nombre).toList());
        assertEquals(10, herramientas.buscarProductos(null, "despensa").size());
        assertEquals(List.of("Leche entera 1 L"),
                herramientas.buscarProductos("leche 1 l", "lácteos").stream().map(InfoProducto::nombre).toList());
        assertEquals(datos.productos().size(), herramientas.buscarProductos(null, null).size());
    }

    @Test
    void lasFechasInvalidasDanUnMensajeClaro() {
        var error = assertThrows(IllegalArgumentException.class, () -> herramientas.resumenVentas("16/11/2026", HOY.toString()));
        assertTrue(error.getMessage().contains("AAAA-MM-DD"));
        assertThrows(IllegalArgumentException.class, () -> herramientas.resumenVentas(HOY.toString(), HOY.minusDays(1).toString()));
        assertThrows(IllegalArgumentException.class, () -> herramientas.ventasPorDia(HOY.minusDays(200).toString(), HOY.toString()));
    }

    // ---------- Totales ya calculados (el modelo no debe sumar) ----------

    @Test
    void losLotesPorVencerTraenElCostoTotalEnRiesgoYaSumado() {
        ReporteVencimientos reporte = herramientas.lotesPorVencer(7);

        List<LoteFila> esperados = datos.lotes().stream()
                .filter(l -> l.cantidad() > 0 && !l.fechaVencimiento().isAfter(HOY.plusDays(7))).toList();
        long costoEsperado = esperados.stream().mapToLong(this::costoLote).sum();
        long costoVencidoEsperado = esperados.stream()
                .filter(l -> l.fechaVencimiento().isBefore(HOY)).mapToLong(this::costoLote).sum();

        assertEquals(esperados.size(), reporte.totalLotes());
        assertEquals(esperados.size(), reporte.lotes().size());
        assertEquals(esperados.stream().mapToLong(LoteFila::cantidad).sum(), reporte.totalUnidades());
        assertEquals(costoEsperado, reporte.costoTotalEnRiesgo());
        assertTrue(costoVencidoEsperado > 0, "los datos simulados tienen lotes vencidos");
        assertEquals(costoVencidoEsperado, reporte.costoYaVencido());
        assertEquals(reporte.lotes().stream().filter(LoteVencimiento::vencido).count(), reporte.lotesVencidos());

        // El resumen por producto reparte exactamente el mismo total, sin repetir productos
        assertEquals(costoEsperado, reporte.porProducto().stream().mapToLong(ProductoEnRiesgo::costoEnRiesgo).sum());
        assertEquals(reporte.totalUnidades(), reporte.porProducto().stream().mapToLong(ProductoEnRiesgo::unidades).sum());
        assertEquals(reporte.porProducto().size(),
                reporte.porProducto().stream().map(ProductoEnRiesgo::producto).distinct().count());
        assertOrdenadoDeMayorAMenor(reporte.porProducto(), ProductoEnRiesgo::costoEnRiesgo);
        assertTrue(reporte.lotes().stream().allMatch(l -> l.vencido() == (l.diasRestantes() < 0)));
    }

    @Test
    void elReporteDeStockTraeLosConteosYLasUnidadesFaltantes() {
        ReporteStock reporte = herramientas.productosBajoStock();
        List<ProductoFila> bajos = datos.productos().stream().filter(p -> p.stockActual() < p.stockMinimo()).toList();

        assertEquals(bajos.size(), reporte.productosEnAlerta());
        assertEquals(bajos.size(), reporte.productos().size());
        assertEquals(bajos.stream().filter(p -> p.stockActual() == 0).count(), reporte.productosAgotados());
        assertTrue(reporte.productosAgotados() > 0);
        assertEquals(bajos.stream().mapToInt(p -> p.stockMinimo() - p.stockActual()).sum(),
                reporte.unidadesFaltantesParaElMinimo());
        assertTrue(reporte.productos().stream().filter(AlertaStock::agotado).allMatch(a -> a.stockActual() == 0));
    }

    @Test
    void lasVentasPorDiaTraenTotalPromedioYMejorYPeorDia() {
        String desde = HOY.minusDays(13).toString();
        ReporteVentasDiarias reporte = herramientas.ventasPorDia(desde, HOY.toString());
        ResumenVentas resumen = herramientas.resumenVentas(desde, HOY.toString());

        assertEquals(14, reporte.diasConVentas());
        assertEquals(resumen.totalVendido(), reporte.totalVendido());
        assertEquals(resumen.numeroVentas(), reporte.numeroVentas());

        // Hoy va marcado como día en curso y no cuenta para promedio, mejor ni peor día
        VentasDia hoy = reporte.dias().getLast();
        assertEquals(HOY, hoy.fecha());
        assertTrue(hoy.enCurso());
        List<VentasDia> completos = reporte.dias().subList(0, 13);
        assertTrue(completos.stream().noneMatch(VentasDia::enCurso));
        assertEquals(completos.stream().max(Comparator.comparingLong(VentasDia::total)).orElseThrow(), reporte.mejorDia());
        assertEquals(completos.stream().min(Comparator.comparingLong(VentasDia::total)).orElseThrow(), reporte.peorDia());
        assertEquals(Math.round(completos.stream().mapToLong(VentasDia::total).average().orElseThrow()),
                reporte.promedioDiarioDiasCompletos());

        // Y el total de ayer coincide con las ventas simuladas
        LocalDate ayer = HOY.minusDays(1);
        long esperadoAyer = datos.ventas().stream()
                .filter(v -> v.fechaHora().toLocalDate().equals(ayer)).mapToLong(VentaFila::total).sum();
        assertEquals(esperadoAyer, completos.getLast().total());
        assertEquals("domingo", completos.getLast().diaSemana());
    }

    @Test
    void lasVentasPorHoraTraenLaHoraPicoYPorcentajes() {
        ReporteVentasPorHora reporte = herramientas.ventasPorHora(HOY.minusDays(30).toString(), HOY.toString());
        assertEquals(herramientas.resumenVentas(HOY.minusDays(30).toString(), HOY.toString()).totalVendido(),
                reporte.totalVendido());
        assertEquals(reporte.horas().stream().max(Comparator.comparingLong(VentasHora::total)).orElseThrow(),
                reporte.horaPico());
        assertEquals(reporte.horas().stream().min(Comparator.comparingLong(VentasHora::total)).orElseThrow(),
                reporte.horaMasFloja());
        assertSumaCien(reporte.horas(), VentasHora::porcentajeDelTotal);
    }

    @Test
    void lasCategoriasYLosMediosDePagoTraenTotalesYPorcentajes() {
        String desde = HOY.minusDays(30).toString();
        ResumenVentas resumen = herramientas.resumenVentas(desde, HOY.toString());
        ReporteCategorias categorias = herramientas.ventasPorCategoria(desde, HOY.toString());

        assertEquals(resumen.totalVendido(), categorias.totalVendido());
        assertEquals(resumen.gananciaEstimada(), categorias.gananciaEstimada());
        assertEquals(categorias.totalVendido(), categorias.categorias().stream().mapToLong(c -> c.totalVendido()).sum());
        assertOrdenadoDeMayorAMenor(categorias.categorias(), c -> c.totalVendido());
        assertSumaCien(categorias.categorias(), c -> c.porcentajeDelTotal());
        assertSumaCien(resumen.porMedioPago(), m -> m.porcentajeDelTotal());
        assertEquals(Math.round(1000.0 * resumen.gananciaEstimada() / resumen.totalVendido()) / 10.0,
                resumen.margenPorcentaje());
    }

    @Test
    void elRankingTraeElTotalDelPeriodoYElPorcentajeDeCadaProducto() {
        String desde = HOY.minusDays(6).toString();
        RankingProductos ranking = herramientas.rankingProductos(desde, HOY.toString(),
                CriterioRanking.INGRESOS, OrdenRanking.MAS_VENDIDOS, null, 40);
        long total = herramientas.resumenVentas(desde, HOY.toString()).totalVendido();

        assertEquals(total, ranking.totalVendidoPeriodo());
        assertEquals(datos.productos().size(), ranking.productos().size());
        assertSumaCien(ranking.productos(), VentasProducto::porcentajeDelTotal);
        VentasProducto primero = ranking.productos().getFirst();
        assertEquals(Math.round(1000.0 * primero.totalVendido() / total) / 10.0, primero.porcentajeDelTotal());
    }

    // ---------- Apoyo ----------

    private long costoLote(LoteFila lote) {
        return lote.cantidad() * productosPorId.get(lote.productoId()).costo();
    }

    private static List<DetalleFila> detallesDesde(LocalDate desde) {
        Map<Long, VentaFila> ventas = datos.ventas().stream()
                .collect(Collectors.toMap(VentaFila::id, Function.identity()));
        return datos.detalles().stream()
                .filter(d -> !ventas.get(d.ventaId()).fechaHora().toLocalDate().isBefore(desde))
                .toList();
    }

    /** Los porcentajes vienen redondeados a un decimal, así que la suma puede alejarse un poco de 100. */
    private static <T> void assertSumaCien(List<T> filas, ToDoubleFunction<T> porcentaje) {
        double suma = filas.stream().mapToDouble(porcentaje).sum();
        assertEquals(100.0, suma, 0.05 * filas.size() + 0.01, "suma de porcentajes");
    }

    private static <T> void assertOrdenadoDeMayorAMenor(List<T> filas, java.util.function.ToLongFunction<T> valor) {
        for (int i = 1; i < filas.size(); i++) {
            assertTrue(valor.applyAsLong(filas.get(i - 1)) >= valor.applyAsLong(filas.get(i)), "orden en fila " + i);
        }
    }
}
