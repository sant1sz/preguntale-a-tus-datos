package com.sant1sz.preguntale.datos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.sant1sz.preguntale.datos.simulacion.CatalogoSupermercado;
import com.sant1sz.preguntale.datos.simulacion.SimuladorSupermercado;
import com.sant1sz.preguntale.herramientas.HerramientasSupermercado;

/** Comprueba que al arrancar los datos simulados siempre lleguen hasta hoy. */
class GeneradorDatosTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDateTime PRIMER_ARRANQUE = LocalDateTime.of(2026, 11, 16, 17, 30);
    private static final long SEMILLA = 2026;
    private static final int DIAS = 90;

    private DataSource fuente;
    private JdbcTemplate jdbc;

    @BeforeEach
    void baseNueva() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("esquema-pruebas.sql")).execute(h2);
        fuente = h2;
        jdbc = new JdbcTemplate(h2);
    }

    @Test
    void siLaBaseEstaVaciaLaLlena() {
        arrancar(PRIMER_ARRANQUE, true);
        assertEquals(40, contar("producto"));
        assertEquals(PRIMER_ARRANQUE.toLocalDate(), ultimoDiaConVentas());
    }

    @Test
    void siLaUltimaVentaEsDeUnDiaAnteriorRegeneraHastaHoy() {
        arrancar(PRIMER_ARRANQUE, true);
        LocalDateTime tresDiasDespues = PRIMER_ARRANQUE.plusDays(3).withHour(12).withMinute(0);

        arrancar(tresDiasDespues, true);

        // No se duplicaron datos: hay exactamente lo que simula el día nuevo
        var esperado = new SimuladorSupermercado(SEMILLA, CatalogoSupermercado.productos()).simular(tresDiasDespues, DIAS);
        assertEquals(esperado.productos().size(), contar("producto"));
        assertEquals(esperado.categorias().size(), contar("categoria"));
        assertEquals(esperado.ventas().size(), contar("venta"));
        assertEquals(esperado.detalles().size(), contar("detalle_venta"));
        assertEquals(esperado.lotes().size(), contar("lote"));

        // Las ventas llegan hasta hoy, sin pasar de la hora actual
        assertEquals(tresDiasDespues.toLocalDate(), ultimoDiaConVentas());
        assertFalse(ultimaVenta().isAfter(tresDiasDespues));

        // Y las preguntas por "hoy" y "esta semana" (lunes a hoy) tienen ventas
        var herramientas = new HerramientasSupermercado(JdbcClient.create(fuente), reloj(tresDiasDespues));
        LocalDate hoy = tresDiasDespues.toLocalDate();
        assertTrue(herramientas.resumenVentas(hoy.toString(), hoy.toString()).numeroVentas() > 0);
        assertTrue(herramientas.resumenVentas(PRIMER_ARRANQUE.toLocalDate().toString(), hoy.toString()).totalVendido() > 0);
    }

    @Test
    void siYaHayVentasDeHoyNoTocaLosDatos() {
        arrancar(PRIMER_ARRANQUE, true);
        long ventas = contar("venta");
        LocalDateTime ultima = ultimaVenta();

        arrancar(PRIMER_ARRANQUE.plusHours(3), true);

        assertEquals(ventas, contar("venta"));
        assertEquals(ultima, ultimaVenta());
    }

    @Test
    void conLaOpcionApagadaNoRegeneraAunqueLosDatosSeanViejos() {
        arrancar(PRIMER_ARRANQUE, true);
        long ventas = contar("venta");

        arrancar(PRIMER_ARRANQUE.plusDays(5), false);

        assertEquals(ventas, contar("venta"));
        assertEquals(PRIMER_ARRANQUE.toLocalDate(), ultimoDiaConVentas());
    }

    private void arrancar(LocalDateTime ahora, boolean regenerarSiDesactualizados) {
        new GeneradorDatos(jdbc, reloj(ahora), true, regenerarSiDesactualizados, DIAS, SEMILLA).run(null);
    }

    private static Clock reloj(LocalDateTime ahora) {
        return Clock.fixed(ahora.atZone(BOGOTA).toInstant(), BOGOTA);
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
    }

    private LocalDateTime ultimaVenta() {
        return jdbc.queryForObject("SELECT MAX(fecha_hora) FROM venta", Timestamp.class).toLocalDateTime();
    }

    private LocalDate ultimoDiaConVentas() {
        return ultimaVenta().toLocalDate();
    }
}
