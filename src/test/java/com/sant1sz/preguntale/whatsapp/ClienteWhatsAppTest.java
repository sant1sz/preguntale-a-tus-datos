package com.sant1sz.preguntale.whatsapp;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClienteWhatsAppTest {

    private static final String URL = "https://graph.facebook.com/v23.0/106540352242922/messages";

    private MockRestServiceServer servidor;
    private ClienteWhatsApp cliente;

    @BeforeEach
    void preparar() {
        var propiedades = new WhatsAppPropiedades(true, "token", "secreto", "token-de-acceso", "106540352242922",
                List.of("573001234567"), "v23.0", "https://graph.facebook.com", 3500);
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new ClienteWhatsApp(propiedades, builder);
    }

    @Test
    void enviaElTextoConElFormatoQuePideLaGraphApi() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-de-acceso"))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.to").value("573001234567"))
                .andExpect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.text.body").value("*Total:* $1.294.400"))
                .andExpect(jsonPath("$.text.preview_url").value(false))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.R\"}]}", MediaType.APPLICATION_JSON));

        cliente.enviarTexto("573001234567", "*Total:* $1.294.400");
        servidor.verify();
    }

    @Test
    void marcaComoLeidoYMuestraEscribiendo() {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.status").value("read"))
                .andExpect(jsonPath("$.message_id").value("wamid.1"))
                .andExpect(jsonPath("$.typing_indicator.type").value("text"))
                .andExpect(jsonPath("$.to").doesNotExist())
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        cliente.marcarLeidoYEscribiendo("wamid.1");
        servidor.verify();
    }

    @Test
    void elTokenNoApareceEnElCuerpo() {
        servidor.expect(requestTo(URL))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(Matchers.not(Matchers.containsString("token-de-acceso"))))
                .andRespond(withSuccess());
        cliente.enviarTexto("573001234567", "Hola");
        servidor.verify();
    }
}
