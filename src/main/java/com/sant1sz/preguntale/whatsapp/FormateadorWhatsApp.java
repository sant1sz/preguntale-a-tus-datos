package com.sant1sz.preguntale.whatsapp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte el Markdown que escribe el modelo al formato de WhatsApp y parte los mensajes largos.
 * WhatsApp usa *negrita* con un asterisco y _cursiva_ con guion bajo, y no tiene títulos, tablas ni líneas.
 */
public final class FormateadorWhatsApp {

    private static final Pattern TITULO = Pattern.compile("^#{1,6}\\s+(.+?)\\s*#*$");
    private static final Pattern LINEA_HORIZONTAL = Pattern.compile("^(-{3,}|\\*{3,}|_{3,})$");
    private static final Pattern SEPARADOR_TABLA = Pattern.compile("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$");
    private static final Pattern VINETA = Pattern.compile("^(\\s*)[*+-]\\s+(.*)$");
    private static final Pattern ENLACE = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern CURSIVA = Pattern.compile("(?<![*\\w])\\*(?![*\\s])([^*\\n]+?)(?<![*\\s])\\*(?![*\\w])");
    private static final Pattern NEGRITA = Pattern.compile("\\*\\*(?!\\s)(.+?)(?<!\\s)\\*\\*");
    private static final Pattern NEGRITA_GUIONES = Pattern.compile("__(?!\\s)(.+?)(?<!\\s)__");

    /** Espacio reservado para la numeración " (2/3)" al final de cada parte. */
    private static final int ESPACIO_NUMERACION = 10;

    private FormateadorWhatsApp() {
    }

    public static String formatear(String markdown) {
        if (markdown == null) {
            return "";
        }
        List<String> lineas = new ArrayList<>();
        for (String linea : markdown.replace("\r\n", "\n").split("\n", -1)) {
            String limpia = linea.strip();
            if (LINEA_HORIZONTAL.matcher(limpia).matches() || SEPARADOR_TABLA.matcher(limpia).matches()) {
                continue;
            }
            Matcher titulo = TITULO.matcher(limpia);
            if (titulo.matches()) {
                lineas.add("*" + titulo.group(1).replace("**", "").replace("__", "").strip() + "*");
                continue;
            }
            if (limpia.startsWith("|") && limpia.endsWith("|") && limpia.length() > 1) {
                linea = String.join(" · ", celdas(limpia));
            } else {
                Matcher vineta = VINETA.matcher(linea);
                if (vineta.matches()) {
                    linea = vineta.group(1) + "• " + vineta.group(2);
                }
            }
            lineas.add(formatoEnLinea(linea.stripTrailing()));
        }
        return String.join("\n", lineas)
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    /**
     * Parte el texto en mensajes de máximo {@code maximo} caracteres, cortando preferiblemente entre párrafos,
     * luego entre líneas y por último entre palabras. Si hay varias partes, cada una termina con "(n/total)".
     */
    public static List<String> dividir(String texto, int maximo) {
        if (texto.length() <= maximo) {
            return List.of(texto);
        }
        int limite = maximo - ESPACIO_NUMERACION;
        List<String> partes = new ArrayList<>();
        String resto = texto;
        while (resto.length() > limite) {
            int corte = ultimoCorte(resto, limite);
            partes.add(resto.substring(0, corte).strip());
            resto = resto.substring(corte).strip();
        }
        if (!resto.isEmpty()) {
            partes.add(resto);
        }
        List<String> numeradas = new ArrayList<>();
        for (int i = 0; i < partes.size(); i++) {
            numeradas.add(partes.get(i) + "\n(" + (i + 1) + "/" + partes.size() + ")");
        }
        return numeradas;
    }

    private static int ultimoCorte(String texto, int limite) {
        int minimo = limite / 2;
        for (String separador : List.of("\n\n", "\n", " ")) {
            int posicion = texto.lastIndexOf(separador, limite - separador.length());
            if (posicion >= minimo) {
                return posicion;
            }
        }
        return limite;
    }

    private static String formatoEnLinea(String linea) {
        String resultado = ENLACE.matcher(linea).replaceAll("$1 ($2)");
        resultado = CURSIVA.matcher(resultado).replaceAll("_$1_");
        resultado = NEGRITA.matcher(resultado).replaceAll("*$1*");
        return NEGRITA_GUIONES.matcher(resultado).replaceAll("*$1*");
    }

    private static List<String> celdas(String fila) {
        String interior = fila.substring(1, fila.length() - 1);
        List<String> celdas = new ArrayList<>();
        for (String celda : interior.split("\\|")) {
            if (!celda.isBlank()) {
                celdas.add(celda.strip());
            }
        }
        return celdas;
    }
}
