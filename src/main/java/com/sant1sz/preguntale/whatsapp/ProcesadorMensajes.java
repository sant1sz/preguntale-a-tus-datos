package com.sant1sz.preguntale.whatsapp;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import com.sant1sz.preguntale.chat.AsistenteService;
import com.sant1sz.preguntale.whatsapp.NotificacionWhatsApp.MensajeEntrante;

import jakarta.annotation.PreDestroy;

/**
 * Responde los mensajes de WhatsApp en segundo plano, para que el webhook conteste a Meta de inmediato
 * aunque Gemini tarde varios segundos.
 */
@Component
@ConditionalOnBooleanProperty("whatsapp.habilitado")
public class ProcesadorMensajes {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorMensajes.class);

    static final int MAXIMO_CARACTERES_PREGUNTA = 1_000;
    static final String SOLO_TEXTO = "Por ahora solo entiendo mensajes de texto. Escríbeme tu pregunta, "
            + "por ejemplo: ¿cuánto vendí esta semana?";
    static final String MUY_LARGO = "Tu mensaje es muy largo. ¿Me lo puedes escribir más corto?";
    static final String SIN_CUOTA = "Ya usé el límite gratuito de consultas a la IA por hoy. "
            + "Vuelve a intentarlo mañana.";
    static final String ERROR = "No pude responder en este momento. Intenta de nuevo en unos minutos.";

    private final AsistenteService asistente;
    private final ClienteWhatsApp cliente;
    private final Set<String> numerosAutorizados;
    private final int maximoCaracteres;
    private final ExecutorService ejecutor = Executors.newVirtualThreadPerTaskExecutor();
    /** Un candado por número: los mensajes de una misma persona se responden en orden. */
    private final Map<String, ReentrantLock> candados = new ConcurrentHashMap<>();

    public ProcesadorMensajes(AsistenteService asistente, ClienteWhatsApp cliente, WhatsAppPropiedades propiedades) {
        propiedades.validar();
        this.asistente = asistente;
        this.cliente = cliente;
        this.numerosAutorizados = propiedades.numerosAutorizadosNormalizados();
        this.maximoCaracteres = propiedades.maximoCaracteresPorMensaje();
        log.info("Canal de WhatsApp activo para {} número(s) autorizado(s).", numerosAutorizados.size());
    }

    /** Pone el mensaje en cola y vuelve enseguida. */
    public void encolar(MensajeEntrante mensaje) {
        ejecutor.execute(() -> procesar(mensaje));
    }

    void procesar(MensajeEntrante mensaje) {
        String telefono = WhatsAppPropiedades.soloDigitos(mensaje.telefono());
        if (!numerosAutorizados.contains(telefono)) {
            log.warn("WhatsApp: se ignora un mensaje de un número no autorizado ({}).",
                    WhatsAppPropiedades.enmascarar(telefono));
            return;
        }
        ReentrantLock candado = candados.computeIfAbsent(telefono, t -> new ReentrantLock());
        candado.lock();
        try {
            responder(mensaje, telefono);
        } finally {
            candado.unlock();
        }
    }

    private void responder(MensajeEntrante mensaje, String telefono) {
        long inicio = System.currentTimeMillis();
        try {
            avisarQueEstaEscribiendo(mensaje.id());
            String respuesta;
            if (!mensaje.esTexto()) {
                respuesta = SOLO_TEXTO;
            } else if (mensaje.texto().length() > MAXIMO_CARACTERES_PREGUNTA) {
                respuesta = MUY_LARGO;
            } else {
                log.info("WhatsApp: pregunta de {}", WhatsAppPropiedades.enmascarar(telefono));
                respuesta = FormateadorWhatsApp.formatear(asistente.responderPorWhatsApp(mensaje.texto().strip(), telefono));
            }
            var partes = FormateadorWhatsApp.dividir(respuesta, maximoCaracteres);
            for (String parte : partes) {
                cliente.enviarTexto(telefono, parte);
            }
            log.info("WhatsApp: respuesta enviada a {} en {} mensaje(s), {} ms.",
                    WhatsAppPropiedades.enmascarar(telefono), partes.size(), System.currentTimeMillis() - inicio);
        } catch (Exception e) {
            log.error("WhatsApp: no se pudo responder a {}", WhatsAppPropiedades.enmascarar(telefono), e);
            enviarSinFallar(telefono, esLimiteDeCuota(e) ? SIN_CUOTA : ERROR);
        }
    }

    private void avisarQueEstaEscribiendo(String idMensaje) {
        try {
            cliente.marcarLeidoYEscribiendo(idMensaje);
        } catch (Exception e) {
            log.debug("WhatsApp: no se pudo marcar el mensaje como leído", e);
        }
    }

    private void enviarSinFallar(String telefono, String texto) {
        try {
            cliente.enviarTexto(telefono, texto);
        } catch (Exception e) {
            log.error("WhatsApp: tampoco se pudo enviar el mensaje de error a {}",
                    WhatsAppPropiedades.enmascarar(telefono), e);
        }
    }

    /** Gemini responde 429 cuando se agota la cuota gratuita. */
    static boolean esLimiteDeCuota(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String mensaje = t.getMessage();
            if (mensaje != null && (mensaje.contains("429") || mensaje.contains("RESOURCE_EXHAUSTED"))) {
                return true;
            }
        }
        return false;
    }

    @PreDestroy
    void detener() {
        ejecutor.shutdown();
    }
}
