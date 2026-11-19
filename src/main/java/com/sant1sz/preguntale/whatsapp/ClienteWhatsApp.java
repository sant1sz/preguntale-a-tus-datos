package com.sant1sz.preguntale.whatsapp;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Envía mensajes por la WhatsApp Cloud API (Graph API de Meta). */
@Component
@ConditionalOnBooleanProperty("whatsapp.habilitado")
public class ClienteWhatsApp {

    private final RestClient http;
    private final WhatsAppPropiedades propiedades;

    @Autowired
    public ClienteWhatsApp(WhatsAppPropiedades propiedades) {
        this(propiedades, RestClient.builder());
    }

    ClienteWhatsApp(WhatsAppPropiedades propiedades, RestClient.Builder builder) {
        this.propiedades = propiedades;
        this.http = builder
                .baseUrl(propiedades.urlGraph())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + propiedades.tokenAcceso())
                .build();
    }

    /** Envía un mensaje de texto. Lanza una excepción si Meta lo rechaza. */
    public void enviarTexto(String telefono, String texto) {
        enviar(Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", telefono,
                "type", "text",
                "text", Map.of("preview_url", false, "body", texto)));
    }

    /** Marca el mensaje como leído y muestra "escribiendo…" mientras el asistente piensa. */
    public void marcarLeidoYEscribiendo(String idMensaje) {
        enviar(Map.of(
                "messaging_product", "whatsapp",
                "status", "read",
                "message_id", idMensaje,
                "typing_indicator", Map.of("type", "text")));
    }

    private void enviar(Map<String, Object> cuerpo) {
        http.post()
                .uri("/{version}/{idNumero}/messages", propiedades.versionGraph(), propiedades.idNumeroTelefono())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity();
    }
}
