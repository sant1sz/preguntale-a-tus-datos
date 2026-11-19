package com.sant1sz.preguntale.whatsapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del canal de WhatsApp (WhatsApp Cloud API de Meta).
 * Las credenciales van SOLO en application-local.properties.
 *
 * @param habilitado                 activa el webhook y el envío de mensajes
 * @param tokenVerificacion          texto que inventas tú y pegas en Meta al registrar el webhook
 * @param appSecret                  "App Secret" de la app de Meta; sirve para validar la firma de cada aviso
 * @param tokenAcceso                token de acceso de la Graph API para enviar mensajes
 * @param idNumeroTelefono           "Phone number ID" del número de prueba (no es el número en sí)
 * @param numerosAutorizados         números que pueden usar el asistente, con código de país (573001234567)
 * @param versionGraph               versión de la Graph API
 * @param urlGraph                   URL base de la Graph API
 * @param maximoCaracteresPorMensaje respuestas más largas se envían en varios mensajes (WhatsApp acepta 4096)
 */
@ConfigurationProperties("whatsapp")
public record WhatsAppPropiedades(
        boolean habilitado,
        String tokenVerificacion,
        String appSecret,
        String tokenAcceso,
        String idNumeroTelefono,
        @DefaultValue List<String> numerosAutorizados,
        @DefaultValue("v23.0") String versionGraph,
        @DefaultValue("https://graph.facebook.com") String urlGraph,
        @DefaultValue("3500") int maximoCaracteresPorMensaje) {

    /** Los números autorizados solo con dígitos, para comparar con el campo "from" de Meta. */
    public Set<String> numerosAutorizadosNormalizados() {
        return numerosAutorizados.stream()
                .map(WhatsAppPropiedades::soloDigitos)
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Falla al arrancar si el canal está activo pero falta alguna credencial (sin mostrar los valores). */
    public void validar() {
        List<String> faltan = new ArrayList<>();
        if (vacio(tokenVerificacion)) faltan.add("whatsapp.token-verificacion");
        if (vacio(appSecret)) faltan.add("whatsapp.app-secret");
        if (vacio(tokenAcceso)) faltan.add("whatsapp.token-acceso");
        if (vacio(idNumeroTelefono)) faltan.add("whatsapp.id-numero-telefono");
        if (numerosAutorizadosNormalizados().isEmpty()) faltan.add("whatsapp.numeros-autorizados");
        if (!faltan.isEmpty()) {
            throw new IllegalStateException("WhatsApp está habilitado pero faltan propiedades en "
                    + "application-local.properties: " + String.join(", ", faltan));
        }
        if (maximoCaracteresPorMensaje < 200 || maximoCaracteresPorMensaje > 4096) {
            throw new IllegalStateException("whatsapp.maximo-caracteres-por-mensaje debe estar entre 200 y 4096");
        }
    }

    static String soloDigitos(String numero) {
        return numero == null ? "" : numero.replaceAll("\\D", "");
    }

    /** Para los registros: 573001234567 → 5730****4567. */
    static String enmascarar(String numero) {
        String digitos = soloDigitos(numero);
        if (digitos.length() <= 8) {
            return "****";
        }
        return digitos.substring(0, 4) + "****" + digitos.substring(digitos.length() - 4);
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
