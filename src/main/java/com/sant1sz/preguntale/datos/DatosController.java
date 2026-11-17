package com.sant1sz.preguntale.datos;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Para revisar rápidamente qué datos tiene el supermercado simulado. */
@RestController
@RequestMapping("/api/datos")
public class DatosController {

    private final JdbcClient jdbc;
    private final Clock reloj;

    public DatosController(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @GetMapping("/resumen")
    public ResumenDatos resumen() {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDateTime[] rango = jdbc.sql("SELECT MIN(fecha_hora), MAX(fecha_hora) FROM venta")
                .query((rs, fila) -> new LocalDateTime[] {
                        rs.getObject(1, LocalDateTime.class), rs.getObject(2, LocalDateTime.class) })
                .single();
        return new ResumenDatos(
                contar("SELECT COUNT(*) FROM categoria"),
                contar("SELECT COUNT(*) FROM producto"),
                contar("SELECT COUNT(*) FROM venta"),
                rango[0],
                rango[1],
                contar("SELECT COALESCE(SUM(total), 0) FROM venta"),
                contar("SELECT COUNT(*) FROM producto WHERE stock_actual < stock_minimo"),
                jdbc.sql("SELECT COUNT(*) FROM lote WHERE fecha_vencimiento < ?")
                        .param(Date.valueOf(hoy)).query(Long.class).single(),
                jdbc.sql("SELECT COUNT(*) FROM lote WHERE fecha_vencimiento BETWEEN ? AND ?")
                        .param(Date.valueOf(hoy)).param(Date.valueOf(hoy.plusDays(7)))
                        .query(Long.class).single());
    }

    private long contar(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
