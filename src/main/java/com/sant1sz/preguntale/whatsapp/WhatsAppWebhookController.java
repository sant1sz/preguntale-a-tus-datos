package com.sant1sz.preguntale.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sant1sz.preguntale.whatsapp.NotificacionWhatsApp.MensajeEntrante;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Webhook de la WhatsApp Cloud API.
 * GET: Meta lo llama una vez para verificar que el webhook es nuestro.
 * POST: Meta avisa cada mensaje nuevo; se valida la firma, se descartan repetidos y se responde 200 enseguida.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
@ConditionalOnBooleanProperty("whatsapp.habilitado")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppPropiedades propiedades;
    private final RegistroMensajes registro;
    private final ProcesadorMensajes procesador;
    private final JsonMapper json;

    public WhatsAppWebhookController(WhatsAppPropiedades propiedades, RegistroMensajes registro,
            ProcesadorMensajes procesador, JsonMapper json) {
        this.propiedades = propiedades;
        this.registro = registro;
        this.procesador = procesador;
        this.json = json;
    }

    @GetMapping
    public ResponseEntity<String> verificar(
            @RequestParam(name = "hub.mode", required = false) String modo,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String reto) {
        if ("subscribe".equals(modo) && reto != null && mismoTexto(token, propiedades.tokenVerificacion())) {
            log.info("WhatsApp: webhook verificado por Meta.");
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(reto);
        }
        log.warn("WhatsApp: intento de verificación del webhook con token incorrecto.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> recibir(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String firma,
            @RequestBody(required = false) byte[] cuerpo) {
        if (!VerificadorFirma.esValida(cuerpo, firma, propiedades.appSecret())) {
            log.warn("WhatsApp: aviso rechazado por firma inválida o ausente.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        NotificacionWhatsApp notificacion;
        try {
            notificacion = json.readValue(cuerpo, NotificacionWhatsApp.class);
        } catch (JacksonException e) {
            // La firma es válida, así que viene de Meta: respondemos 200 para que no lo reintente
            log.warn("WhatsApp: aviso con formato inesperado; se ignora.", e);
            return ResponseEntity.ok().build();
        }

        for (MensajeEntrante mensaje : notificacion.mensajes()) {
            if (registro.esNuevo(mensaje.id())) {
                procesador.encolar(mensaje);
            } else {
                log.info("WhatsApp: mensaje repetido {}; se ignora.", mensaje.id());
            }
        }
        return ResponseEntity.ok().build();
    }

    private static boolean mismoTexto(String recibido, String esperado) {
        if (recibido == null || esperado == null || esperado.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(recibido.getBytes(StandardCharsets.UTF_8), esperado.getBytes(StandardCharsets.UTF_8));
    }
}
