package com.sant1sz.preguntale.herramientas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.VentaFila;
import com.sant1sz.preguntale.datos.simulacion.SimuladorSupermercado;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.AlertaStock;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.CriterioRanking;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.InfoProducto;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.LoteVencimiento;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.OrdenRanking;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.ResumenVentas;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.VentasDia;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado.VentasProducto;

/** Carga el supermercado simulado en H2 y compara lo que responden las herramientas con cálculos en Java. */
class HerramientasSupermercadoTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 17, 30);
    private static final LocalDate HOY = AHORA.toLocalDate();
    private static final long SEMILLA = 2026;
    private static final int DIAS = 90;

    private static DatosSimulados datos;
    private static HerramientasSupermercado herramientas;

    @BeforeAll
    static void cargarBase() {
        JdbcDataSource fuente = new JdbcDataSource();
        fuente.setURL("jdbc:h2:mem:herramientas;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("esquema-pruebas.sql")).execute(fuente);

        Clock reloj = Clock.fixed(AHORA.atZone(BOGOTA).toInstant(), BOGOTA);
        new GeneradorDatos(new JdbcTemplate(fuente), reloj, true, DIAS, SEMILLA).run(null);

        datos = new SimuladorSupermercado(SEMILLA, CatalogoSupermercado.productos()).simular(AHORA, DIAS);
        herramientas = new HerramientasSupermercado(JdbcClient.create(fuente), reloj);
    }

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
    void lasVentasPorDiaSumanLoMismoQueElResumen() {
        String desde = HOY.minusDays(13).toString();
        String hasta = HOY.toString();
        List<VentasDia> dias = herramientas.ventasPorDia(desde, hasta);
        assertEquals(14, dias.size());
        assertEquals(herramientas.resumenVentas(desde, hasta).totalVendido(),
                dias.stream().mapToLong(VentasDia::total).sum());

        LocalDate ayer = HOY.minusDays(1);
        long esperadoAyer = datos.ventas().stream()
                .filter(v -> v.fechaHora().toLocalDate().equals(ayer)).mapToLong(VentaFila::total).sum();
        VentasDia diaAyer = dias.stream().filter(d -> d.fecha().equals(ayer)).findFirst().orElseThrow();
        assertEquals(esperadoAyer, diaAyer.total());
        assertEquals("domingo", diaAyer.diaSemana());
    }

    @Test
    void lasVentasPorHoraYCategoriaCuadranConElTotal() {
        String desde = HOY.minusDays(30).toString();
        String hasta = HOY.toString();
        long total = herramientas.resumenVentas(desde, hasta).totalVendido();
        assertEquals(total, herramientas.ventasPorHora(desde, hasta).stream().mapToLong(h -> h.total()).sum());
        assertEquals(total, herramientas.ventasPorCategoria(desde, hasta).stream().mapToLong(c -> c.totalVendido()).sum());
    }

    @Test
    void elProductoMasVendidoCoincideConElCalculoEnJava() {
        LocalDate desde = HOY.minusDays(29);
        Map<Long, VentaFila> ventas = datos.ventas().stream()
                .collect(Collectors.toMap(VentaFila::id, Function.identity()));
        Map<Long, Long> unidades = datos.detalles().stream()
                .filter(d -> !ventas.get(d.ventaId()).fechaHora().toLocalDate().isBefore(desde))
                .collect(Collectors.groupingBy(DetalleFila::productoId, Collectors.summingLong(DetalleFila::cantidad)));
        long idMasVendido = unidades.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        String esperado = datos.productos().stream().filter(p -> p.id() == idMasVendido)
                .findFirst().orElseThrow().nombre();

        List<VentasProducto> ranking = herramientas.rankingProductos(desde.toString(), HOY.toString(),
                CriterioRanking.UNIDADES, OrdenRanking.MAS_VENDIDOS, null, 5);
        assertEquals(5, ranking.size());
        assertEquals(esperado, ranking.getFirst().producto());
        assertEquals(unidades.get(idMasVendido), ranking.getFirst().unidades());
    }

    @Test
    void elRankingDeMenosVendidosVaDeMenorAMayorYFiltraPorCategoria() {
        List<VentasProducto> ranking = herramientas.rankingProductos(HOY.minusDays(6).toString(), HOY.toString(),
                CriterioRanking.INGRESOS, OrdenRanking.MENOS_VENDIDOS, "Bebidas", 40);
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
    void losProductosBajoStockSonLosMismosQueEnLosDatos() {
        List<AlertaStock> alertas = herramientas.productosBajoStock();
        long esperados = datos.productos().stream().filter(p -> p.stockActual() < p.stockMinimo()).count();
        assertEquals(esperados, alertas.size());
        assertTrue(alertas.stream().anyMatch(AlertaStock::agotado));
        assertTrue(alertas.stream().filter(AlertaStock::agotado).allMatch(a -> a.stockActual() == 0));
    }

    @Test
    void losLotesPorVencerIncluyenLosVencidos() {
        List<LoteVencimiento> lotes = herramientas.lotesPorVencer(7);
        long esperados = datos.lotes().stream()
                .filter(l -> l.cantidad() > 0 && !l.fechaVencimiento().isAfter(HOY.plusDays(7))).count();
        assertEquals(esperados, lotes.size());
        assertTrue(lotes.stream().anyMatch(LoteVencimiento::vencido));
        assertTrue(lotes.stream().allMatch(l -> l.vencido() == (l.diasRestantes() < 0)));
    }

    @Test
    void lasFechasInvalidasDanUnMensajeClaro() {
        var error = assertThrows(IllegalArgumentException.class, () -> herramientas.resumenVentas("16/11/2026", HOY.toString()));
        assertTrue(error.getMessage().contains("AAAA-MM-DD"));
        assertThrows(IllegalArgumentException.class, () -> herramientas.resumenVentas(HOY.toString(), HOY.minusDays(1).toString()));
        assertThrows(IllegalArgumentException.class, () -> herramientas.ventasPorDia(HOY.minusDays(200).toString(), HOY.toString()));
    }
}
