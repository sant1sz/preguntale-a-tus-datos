package com.sant1sz.preguntale.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Valida el encabezado X-Hub-Signature-256 que Meta envía con cada aviso del webhook:
 * "sha256=" + HMAC-SHA256 del cuerpo exacto de la petición, firmado con el App Secret.
 * Así sabemos que el aviso viene de Meta y que nadie lo modificó.
 */
public final class VerificadorFirma {

    private static final String PREFIJO = "sha256=";
    private static final String ALGORITMO = "HmacSHA256";

    private VerificadorFirma() {
    }

    public static boolean esValida(byte[] cuerpo, String encabezadoFirma, String appSecret) {
        if (cuerpo == null || encabezadoFirma == null || appSecret == null || appSecret.isBlank()
                || !encabezadoFirma.startsWith(PREFIJO)) {
            return false;
        }
        byte[] recibida;
        try {
            recibida = HexFormat.of().parseHex(encabezadoFirma.substring(PREFIJO.length()).strip());
        } catch (IllegalArgumentException e) {
            return false;
        }
        // Comparación en tiempo constante para no dar pistas sobre la firma correcta
        return MessageDigest.isEqual(firmar(cuerpo, appSecret), recibida);
    }

    /** Calcula el valor que debería traer el encabezado (útil también en las pruebas). */
    public static String encabezadoPara(byte[] cuerpo, String appSecret) {
        return PREFIJO + HexFormat.of().formatHex(firmar(cuerpo, appSecret));
    }

    private static byte[] firmar(byte[] cuerpo, String appSecret) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return mac.doFinal(cuerpo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC-SHA256", e);
        }
    }
}
