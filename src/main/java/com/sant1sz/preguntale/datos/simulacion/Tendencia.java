package com.sant1sz.preguntale.datos.simulacion;

/** Cómo cambia la demanda de un producto a lo largo del periodo simulado. */
public enum Tendencia {
    ESTABLE, SUBE, BAJA;

    /** @param progreso 0 al inicio del periodo, 1 al final */
    double factor(double progreso) {
        return switch (this) {
            case ESTABLE -> 1.0;
            case SUBE -> 0.4 + 1.2 * progreso;
            case BAJA -> 1.5 - 1.0 * progreso;
        };
    }
}
