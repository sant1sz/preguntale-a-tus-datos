package com.sant1sz.preguntale.whatsapp;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Recuerda los ids de los últimos mensajes recibidos. Meta puede enviar el mismo aviso más de una vez
 * (por ejemplo, si no respondimos a tiempo), y no queremos contestar dos veces ni gastar cuota de Gemini.
 */
@Component
public class RegistroMensajes {

    static final int CAPACIDAD = 5_000;

    private final Map<String, Boolean> vistos;

    public RegistroMensajes() {
        this(CAPACIDAD);
    }

    RegistroMensajes(int capacidad) {
        // Orden de inserción: cuando se llena, se olvida el más antiguo
        this.vistos = new LinkedHashMap<>(capacidad, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> masAntiguo) {
                return size() > capacidad;
            }
        };
    }

    /** Devuelve true la primera vez que ve un id y false si ya lo había visto. */
    public synchronized boolean esNuevo(String idMensaje) {
        if (idMensaje == null || idMensaje.isBlank()) {
            return false;
        }
        return vistos.putIfAbsent(idMensaje, Boolean.TRUE) == null;
    }
}
