package com.sant1sz.preguntale.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class RegistroMensajesTest {

    @Test
    void soloLaPrimeraVezEsNuevo() {
        RegistroMensajes registro = new RegistroMensajes();
        assertTrue(registro.esNuevo("wamid.1"));
        assertFalse(registro.esNuevo("wamid.1"));
        assertFalse(registro.esNuevo("wamid.1"));
        assertTrue(registro.esNuevo("wamid.2"));
    }

    @Test
    void losIdsVaciosNoSeProcesan() {
        RegistroMensajes registro = new RegistroMensajes();
        assertFalse(registro.esNuevo(null));
        assertFalse(registro.esNuevo(""));
    }

    @Test
    void alLlenarseOlvidaLosMasAntiguos() {
        RegistroMensajes registro = new RegistroMensajes(3);
        registro.esNuevo("a");
        registro.esNuevo("b");
        registro.esNuevo("c");
        registro.esNuevo("d"); // "a" sale del registro

        assertFalse(registro.esNuevo("d"));
        assertFalse(registro.esNuevo("c"));
        assertTrue(registro.esNuevo("a"));
    }

    @Test
    void conAvisosSimultaneosDelMismoMensajeSoloUnoPasa() throws Exception {
        RegistroMensajes registro = new RegistroMensajes();
        try (ExecutorService hilos = Executors.newFixedThreadPool(8)) {
            var resultados = IntStream.range(0, 200)
                    .mapToObj(i -> hilos.submit(() -> registro.esNuevo("wamid.repetido")))
                    .toList();
            long nuevos = 0;
            for (Future<Boolean> r : resultados) {
                if (r.get()) {
                    nuevos++;
                }
            }
            assertEquals(1, nuevos);
        }
    }
}
