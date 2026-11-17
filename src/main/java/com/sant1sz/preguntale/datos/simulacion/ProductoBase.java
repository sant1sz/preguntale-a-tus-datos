package com.sant1sz.preguntale.datos.simulacion;

/**
 * Producto del catálogo simulado.
 *
 * @param popularidad        peso relativo al elegir qué se vende (más alto = se vende más)
 * @param vidaUtilDias       días que dura un lote nuevo (solo perecederos)
 * @param vencimientoForzado si no es null, el primer lote vence en esos días desde hoy (negativo = ya vencido)
 */
public record ProductoBase(
        String nombre,
        String categoria,
        long precio,
        double popularidad,
        boolean perecedero,
        int vidaUtilDias,
        Tendencia tendencia,
        EstadoStock estadoStock,
        Integer vencimientoForzado) {

    public static ProductoBase seco(String nombre, String categoria, long precio, double popularidad) {
        return new ProductoBase(nombre, categoria, precio, popularidad, false, 365,
                Tendencia.ESTABLE, EstadoStock.NORMAL, null);
    }

    public static ProductoBase fresco(String nombre, String categoria, long precio, double popularidad, int vidaUtilDias) {
        return new ProductoBase(nombre, categoria, precio, popularidad, true, vidaUtilDias,
                Tendencia.ESTABLE, EstadoStock.NORMAL, null);
    }

    public ProductoBase conTendencia(Tendencia nueva) {
        return new ProductoBase(nombre, categoria, precio, popularidad, perecedero, vidaUtilDias,
                nueva, estadoStock, vencimientoForzado);
    }

    public ProductoBase conStock(EstadoStock nuevo) {
        return new ProductoBase(nombre, categoria, precio, popularidad, perecedero, vidaUtilDias,
                tendencia, nuevo, vencimientoForzado);
    }

    public ProductoBase venceEn(int dias) {
        return new ProductoBase(nombre, categoria, precio, popularidad, perecedero, vidaUtilDias,
                tendencia, estadoStock, dias);
    }
}
