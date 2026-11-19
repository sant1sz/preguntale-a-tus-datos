package com.sant1sz.preguntale.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class VerificadorFirmaTest {

    private static final String SECRETO = "secreto-de-prueba";
    private static final byte[] CUERPO = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
    /** Calculada aparte con Python: hmac.new(b'secreto-de-prueba', CUERPO, hashlib.sha256).hexdigest() */
    private static final String FIRMA_CORRECTA = "sha256=0ead6e9f93d7a610fa15f88bc374dbb9ec94c96251dc0d9b73511589353790bb";

    @Test
    void aceptaLaFirmaCorrecta() {
        assertTrue(VerificadorFirma.esValida(CUERPO, FIRMA_CORRECTA, SECRETO));
        assertEquals(FIRMA_CORRECTA, VerificadorFirma.encabezadoPara(CUERPO, SECRETO));
    }

    @Test
    void aceptaLaFirmaEnMayusculas() {
        String mayusculas = "sha256=" + FIRMA_CORRECTA.substring("sha256=".length()).toUpperCase();
        assertTrue(VerificadorFirma.esValida(CUERPO, mayusculas, SECRETO));
    }

    @Test
    void rechazaSiElCuerpoFueModificado() {
        byte[] modificado = "{\"object\":\"whatsapp_business_accounT\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(VerificadorFirma.esValida(modificado, FIRMA_CORRECTA, SECRETO));
    }

    @Test
    void rechazaSiSeFirmoConOtroSecreto() {
        assertFalse(VerificadorFirma.esValida(CUERPO, FIRMA_CORRECTA, "otro-secreto"));
        assertFalse(VerificadorFirma.esValida(CUERPO, VerificadorFirma.encabezadoPara(CUERPO, "otro-secreto"), SECRETO));
    }

    @Test
    void rechazaEncabezadosAusentesOMalFormados() {
        String hex = FIRMA_CORRECTA.substring("sha256=".length());
        assertFalse(VerificadorFirma.esValida(CUERPO, null, SECRETO));
        assertFalse(VerificadorFirma.esValida(CUERPO, "", SECRETO));
        assertFalse(VerificadorFirma.esValida(CUERPO, hex, SECRETO), "sin el prefijo sha256=");
        assertFalse(VerificadorFirma.esValida(CUERPO, "sha1=" + hex, SECRETO));
        assertFalse(VerificadorFirma.esValida(CUERPO, "sha256=no-es-hexadecimal", SECRETO));
        assertFalse(VerificadorFirma.esValida(CUERPO, "sha256=" + hex.substring(2), SECRETO), "firma truncada");
    }

    @Test
    void rechazaTodoSiNoHaySecretoConfigurado() {
        assertFalse(VerificadorFirma.esValida(CUERPO, FIRMA_CORRECTA, null));
        assertFalse(VerificadorFirma.esValida(CUERPO, FIRMA_CORRECTA, " "));
        assertFalse(VerificadorFirma.esValida(null, FIRMA_CORRECTA, SECRETO));
    }
}
