package com.sant1sz.preguntale.whatsapp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.sant1sz.preguntale.whatsapp.NotificacionWhatsApp.MensajeEntrante;

import tools.jackson.databind.json.JsonMapper;

class WhatsAppWebhookControllerTest {

    private static final String SECRETO = "app-secret-de-prueba";
    private static final String TOKEN = "token-de-verificacion";

    /** Aviso real de Meta (recortado) con un mensaje de texto. */
    private static final String AVISO_MENSAJE = """
            {"object":"whatsapp_business_account","entry":[{"id":"102290129340398","changes":[{"value":{
              "messaging_product":"whatsapp",
              "metadata":{"display_phone_number":"15550783881","phone_number_id":"106540352242922"},
              "contacts":[{"profile":{"name":"Don Pedro"},"wa_id":"573001234567"}],
              "messages":[{"from":"573001234567","id":"wamid.HBgLMTU1","timestamp":"1763500000",
                           "text":{"body":"¿Cuánto vendí hoy?"},"type":"text"}]},
              "field":"messages"}]}]}""";

    /** Aviso de estado (entregado): no trae mensajes. */
    private static final String AVISO_ESTADO = """
            {"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{"value":{
              "messaging_product":"whatsapp","statuses":[{"id":"wamid.X","status":"delivered"}]},
              "field":"messages"}]}]}""";

    private ProcesadorMensajes procesador;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        procesador = mock(ProcesadorMensajes.class);
        var propiedades = new WhatsAppPropiedades(true, TOKEN, SECRETO, "token-acceso", "106540352242922",
                List.of("573001234567"), "v23.0", "https://graph.facebook.com", 3500);
        var controlador = new WhatsAppWebhookController(propiedades, new RegistroMensajes(), procesador,
                JsonMapper.builder().build());
        mvc = MockMvcBuilders.standaloneSetup(controlador).build();
    }

    // ---------- GET: verificación ----------

    @Test
    void laVerificacionConElTokenCorrectoDevuelveElReto() throws Exception {
        mvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", TOKEN)
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                .andExpect(content().string("1158201444"));
    }

    @Test
    void laVerificacionConTokenIncorrectoOIncompletaSeRechaza() throws Exception {
        mvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "otro")
                        .param("hub.challenge", "123"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/webhook/whatsapp")).andExpect(status().isForbidden());
    }

    // ---------- POST: firma ----------

    @Test
    void unAvisoConFirmaValidaSeAceptaYSeEncola() throws Exception {
        enviar(AVISO_MENSAJE, firmar(AVISO_MENSAJE)).andExpect(status().isOk());
        verify(procesador).encolar(new MensajeEntrante("wamid.HBgLMTU1", "573001234567", "text", "¿Cuánto vendí hoy?"));
    }

    @Test
    void unAvisoSinFirmaOConFirmaInvalidaSeRechazaYNoSeProcesa() throws Exception {
        enviar(AVISO_MENSAJE, null).andExpect(status().isUnauthorized());
        enviar(AVISO_MENSAJE, "sha256=" + "0".repeat(64)).andExpect(status().isUnauthorized());
        enviar(AVISO_MENSAJE, VerificadorFirma.encabezadoPara(bytes(AVISO_MENSAJE), "otro-secreto"))
                .andExpect(status().isUnauthorized());
        // Firma de otro cuerpo: alguien cambió el mensaje en el camino
        enviar(AVISO_MENSAJE.replace("hoy", "ayer"), firmar(AVISO_MENSAJE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(procesador);
    }

    // ---------- POST: duplicados ----------

    @Test
    void siMetaReenviaElMismoMensajeSoloSeProcesaUnaVez() throws Exception {
        for (int i = 0; i < 3; i++) {
            enviar(AVISO_MENSAJE, firmar(AVISO_MENSAJE)).andExpect(status().isOk());
        }
        verify(procesador, times(1)).encolar(any());
    }

    @Test
    void mensajesDistintosSeProcesanTodos() throws Exception {
        String otro = AVISO_MENSAJE.replace("wamid.HBgLMTU1", "wamid.OTRO");
        enviar(AVISO_MENSAJE, firmar(AVISO_MENSAJE)).andExpect(status().isOk());
        enviar(otro, firmar(otro)).andExpect(status().isOk());
        verify(procesador, times(2)).encolar(any());
    }

    // ---------- POST: avisos sin mensajes ----------

    @Test
    void losAvisosDeEstadoYLosMalFormadosRespondenOkSinProcesar() throws Exception {
        enviar(AVISO_ESTADO, firmar(AVISO_ESTADO)).andExpect(status().isOk());
        enviar("esto no es json", firmar("esto no es json")).andExpect(status().isOk());
        verify(procesador, never()).encolar(any());
    }

    private org.springframework.test.web.servlet.ResultActions enviar(String cuerpo, String firma) throws Exception {
        var peticion = post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(bytes(cuerpo));
        if (firma != null) {
            peticion.header("X-Hub-Signature-256", firma);
        }
        return mvc.perform(peticion);
    }

    private static String firmar(String cuerpo) {
        return VerificadorFirma.encabezadoPara(bytes(cuerpo), SECRETO);
    }

    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }
}
