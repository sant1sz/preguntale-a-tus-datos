package com.sant1sz.preguntale.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.sant1sz.preguntale.chat.AsistenteService;
import com.sant1sz.preguntale.whatsapp.NotificacionWhatsApp.MensajeEntrante;

class ProcesadorMensajesTest {

    private static final String AUTORIZADO = "573001234567";

    private AsistenteService asistente;
    private ClienteWhatsApp cliente;
    private ProcesadorMensajes procesador;

    @BeforeEach
    void preparar() {
        asistente = mock(AsistenteService.class);
        cliente = mock(ClienteWhatsApp.class);
        procesador = new ProcesadorMensajes(asistente, cliente, propiedades(List.of("+57 300 123 4567"), 300));
    }

    @Test
    void respondeAUnNumeroAutorizadoConFormatoDeWhatsApp() {
        when(asistente.responderPorWhatsApp("¿Cuánto vendí hoy?", AUTORIZADO))
                .thenReturn("### Ventas de hoy\n**Total:** $1.294.400");

        procesador.procesar(texto("wamid.1", AUTORIZADO, "  ¿Cuánto vendí hoy?  "));

        verify(cliente).marcarLeidoYEscribiendo("wamid.1");
        verify(cliente).enviarTexto(AUTORIZADO, "*Ventas de hoy*\n*Total:* $1.294.400");
    }

    @Test
    void ignoraLosNumerosNoAutorizados() {
        procesador.procesar(texto("wamid.1", "573009999999", "Hola"));
        verifyNoInteractions(asistente, cliente);
    }

    @Test
    void lasRespuestasLargasSeEnvianEnVariosMensajes() {
        String larga = "Este es un párrafo largo sobre las ventas del supermercado.\n\n".repeat(20);
        when(asistente.responderPorWhatsApp(anyString(), anyString())).thenReturn(larga);

        procesador.procesar(texto("wamid.1", AUTORIZADO, "Dame un informe completo"));

        ArgumentCaptor<String> partes = ArgumentCaptor.forClass(String.class);
        verify(cliente, org.mockito.Mockito.atLeast(2)).enviarTexto(eq(AUTORIZADO), partes.capture());
        assertTrue(partes.getAllValues().stream().allMatch(p -> p.length() <= 300));
        assertTrue(partes.getAllValues().getFirst().endsWith("(1/" + partes.getAllValues().size() + ")"));
    }

    @Test
    void losMensajesQueNoSonTextoRecibenUnaExplicacion() {
        procesador.procesar(new MensajeEntrante("wamid.1", AUTORIZADO, "audio", null));
        verify(cliente).enviarTexto(AUTORIZADO, ProcesadorMensajes.SOLO_TEXTO);
        verifyNoInteractions(asistente);
    }

    @Test
    void losMensajesMuyLargosNoGastanCuota() {
        procesador.procesar(texto("wamid.1", AUTORIZADO, "a".repeat(ProcesadorMensajes.MAXIMO_CARACTERES_PREGUNTA + 1)));
        verify(cliente).enviarTexto(AUTORIZADO, ProcesadorMensajes.MUY_LARGO);
        verifyNoInteractions(asistente);
    }

    @Test
    void siSeAgotaLaCuotaDeGeminiLoExplica() {
        when(asistente.responderPorWhatsApp(anyString(), anyString())).thenThrow(new RuntimeException(
                "Failed to generate content", new RuntimeException("429 . You exceeded your current quota")));

        procesador.procesar(texto("wamid.1", AUTORIZADO, "Hola"));

        verify(cliente).enviarTexto(AUTORIZADO, ProcesadorMensajes.SIN_CUOTA);
    }

    @Test
    void siFallaOtraCosaEnviaUnMensajeGenerico() {
        when(asistente.responderPorWhatsApp(anyString(), anyString())).thenThrow(new IllegalStateException("sin conexión"));

        procesador.procesar(texto("wamid.1", AUTORIZADO, "Hola"));

        verify(cliente).enviarTexto(AUTORIZADO, ProcesadorMensajes.ERROR);
    }

    @Test
    void siNoSePuedeMarcarComoLeidoIgualResponde() {
        doThrow(new RuntimeException("Graph API caída")).when(cliente).marcarLeidoYEscribiendo(any());
        when(asistente.responderPorWhatsApp(anyString(), anyString())).thenReturn("Listo");

        procesador.procesar(texto("wamid.1", AUTORIZADO, "Hola"));

        verify(cliente, times(1)).enviarTexto(AUTORIZADO, "Listo");
        verify(cliente, never()).enviarTexto(AUTORIZADO, ProcesadorMensajes.ERROR);
    }

    @Test
    void noArrancaSiFaltanCredencialesYNoMuestraLosValores() {
        var incompletas = new WhatsAppPropiedades(true, "token", null, "secreto-muy-privado", " ",
                List.of(), "v23.0", "https://graph.facebook.com", 3500);
        var error = assertThrows(IllegalStateException.class,
                () -> new ProcesadorMensajes(asistente, cliente, incompletas));
        assertTrue(error.getMessage().contains("whatsapp.app-secret"));
        assertTrue(error.getMessage().contains("whatsapp.id-numero-telefono"));
        assertTrue(error.getMessage().contains("whatsapp.numeros-autorizados"));
        assertFalse(error.getMessage().contains("secreto-muy-privado"));
    }

    @Test
    void losNumerosSeEnmascaranEnLosRegistros() {
        assertEquals("5730****4567", WhatsAppPropiedades.enmascarar("573001234567"));
        assertEquals("****", WhatsAppPropiedades.enmascarar("123"));
    }

    private static MensajeEntrante texto(String id, String telefono, String texto) {
        return new MensajeEntrante(id, telefono, "text", texto);
    }

    private static WhatsAppPropiedades propiedades(List<String> autorizados, int maximo) {
        return new WhatsAppPropiedades(true, "token", "secreto", "acceso", "106540352242922",
                autorizados, "v23.0", "https://graph.facebook.com", maximo);
    }
}
