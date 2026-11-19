package com.sant1sz.preguntale.whatsapp;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Aviso que Meta envía al webhook. Solo se leen los campos que usamos:
 * entry[].changes[].value.messages[] con from, id, type y text.body.
 * Los avisos de estado (entregado, leído) no traen "messages" y se ignoran.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificacionWhatsApp(List<Entrada> entry) {

    public List<MensajeEntrante> mensajes() {
        return flujo(entry)
                .flatMap(e -> flujo(e.changes()))
                .map(Cambio::value)
                .filter(Objects::nonNull)
                .flatMap(v -> flujo(v.messages()))
                .map(m -> new MensajeEntrante(m.id(), m.from(), m.type(), m.text() == null ? null : m.text().body()))
                .toList();
    }

    private static <T> Stream<T> flujo(List<T> lista) {
        return lista == null ? Stream.empty() : lista.stream().filter(Objects::nonNull);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entrada(List<Cambio> changes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cambio(String field, Valor value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Valor(List<Mensaje> messages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mensaje(String id, String from, String type, Texto text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Texto(String body) {
    }

    /** Mensaje recibido, ya simplificado. */
    public record MensajeEntrante(String id, String telefono, String tipo, String texto) {

        public boolean esTexto() {
            return "text".equals(tipo) && texto != null && !texto.isBlank();
        }
    }
}
