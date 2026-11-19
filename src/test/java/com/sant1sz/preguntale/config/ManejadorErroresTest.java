package com.sant1sz.preguntale.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ManejadorErroresTest {

    private final ManejadorErrores manejador = new ManejadorErrores();

    @Test
    void unaRutaInexistenteEs404YNoUnErrorDeGemini() {
        var respuesta = manejador.errorInesperado(new NoResourceFoundException(HttpMethod.GET, "/webhook/whatsapp", "webhook/whatsapp"));
        assertEquals(404, respuesta.getStatusCode().value());
        assertEquals("No existe esa ruta", respuesta.getBody().get("error"));
    }

    @Test
    void unMetodoNoPermitidoConservaSuCodigo() {
        var respuesta = manejador.errorInesperado(new HttpRequestMethodNotSupportedException("DELETE"));
        assertEquals(405, respuesta.getStatusCode().value());
    }

    @Test
    void losDemasErroresSiguenSiendo502() {
        var respuesta = manejador.errorInesperado(new RuntimeException("Gemini no responde"));
        assertEquals(502, respuesta.getStatusCode().value());
    }
}
