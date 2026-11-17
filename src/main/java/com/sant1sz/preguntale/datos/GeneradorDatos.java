package com.sant1sz.preguntale.datos;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sant1sz.preguntale.datos.simulacion.CatalogoSupermercado;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados;
import com.sant1sz.preguntale.datos.simulacion.SimuladorSupermercado;

/** Al arrancar, si la base de datos está vacía, la llena con un supermercado simulado. */
@Component
public class GeneradorDatos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GeneradorDatos.class);
    private static final int TAMANO_LOTE_SQL = 1_000;

    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final boolean generar;
    private final int diasHistorial;
    private final long semilla;

    public GeneradorDatos(JdbcTemplate jdbc, Clock reloj,
            @Value("${datos.generar}") boolean generar,
            @Value("${datos.dias-historial}") int diasHistorial,
            @Value("${datos.semilla}") long semilla) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.generar = generar;
        this.diasHistorial = diasHistorial;
        this.semilla = semilla;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!generar) {
            return;
        }
        Long productos = jdbc.queryForObject("SELECT COUNT(*) FROM producto", Long.class);
        if (productos != null && productos > 0) {
            log.info("La base ya tiene datos ({} productos); no se generan nuevos.", productos);
            return;
        }

        long inicio = System.currentTimeMillis();
        DatosSimulados datos = new SimuladorSupermercado(semilla, CatalogoSupermercado.productos())
                .simular(LocalDateTime.now(reloj), diasHistorial);
        guardar(datos);
        log.info("Supermercado simulado: {} productos, {} ventas, {} líneas de venta y {} lotes ({} ms).",
                datos.productos().size(), datos.ventas().size(), datos.detalles().size(), datos.lotes().size(),
                System.currentTimeMillis() - inicio);
    }

    private void guardar(DatosSimulados datos) {
        insertar("INSERT INTO categoria (id, nombre) VALUES (?, ?)",
                datos.categorias().stream().map(c -> new Object[] { c.id(), c.nombre() }).toList());

        insertar("""
                INSERT INTO producto (id, nombre, categoria_id, precio, costo, stock_actual, stock_minimo, perecedero)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                datos.productos().stream().map(p -> new Object[] { p.id(), p.nombre(), p.categoriaId(), p.precio(),
                        p.costo(), p.stockActual(), p.stockMinimo(), p.perecedero() }).toList());

        insertar("INSERT INTO venta (id, fecha_hora, total, medio_pago) VALUES (?, ?, ?, ?)",
                datos.ventas().stream().map(v -> new Object[] { v.id(), Timestamp.valueOf(v.fechaHora()),
                        v.total(), v.medioPago().name() }).toList());

        insertar("""
                INSERT INTO detalle_venta (id, venta_id, producto_id, cantidad, precio_unitario, subtotal)
                VALUES (?, ?, ?, ?, ?, ?)""",
                datos.detalles().stream().map(d -> new Object[] { d.id(), d.ventaId(), d.productoId(),
                        d.cantidad(), d.precioUnitario(), d.subtotal() }).toList());

        insertar("INSERT INTO lote (id, producto_id, codigo, cantidad, fecha_vencimiento) VALUES (?, ?, ?, ?, ?)",
                datos.lotes().stream().map(l -> new Object[] { l.id(), l.productoId(), l.codigo(), l.cantidad(),
                        Date.valueOf(l.fechaVencimiento()) }).toList());
    }

    private void insertar(String sql, List<Object[]> filas) {
        for (int desde = 0; desde < filas.size(); desde += TAMANO_LOTE_SQL) {
            jdbc.batchUpdate(sql, filas.subList(desde, Math.min(desde + TAMANO_LOTE_SQL, filas.size())));
        }
    }
}
