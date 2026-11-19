package com.sant1sz.preguntale.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FormateadorWhatsAppTest {

    @Test
    void laNegritaUsaUnSoloAsterisco() {
        assertEquals("*Total vendido:* $1.294.400", FormateadorWhatsApp.formatear("**Total vendido:** $1.294.400"));
        assertEquals("Pide *leche* y *huevos*", FormateadorWhatsApp.formatear("Pide **leche** y __huevos__"));
    }

    @Test
    void laCursivaUsaGuionBajo() {
        assertEquals("Esto es _importante_ hoy", FormateadorWhatsApp.formatear("Esto es *importante* hoy"));
    }

    @Test
    void losTitulosSeVuelvenNegritaSinNumerales() {
        assertEquals("*📊 Resumen de ventas*", FormateadorWhatsApp.formatear("### 📊 Resumen de ventas"));
        assertEquals("*Productos agotados*", FormateadorWhatsApp.formatear("## **Productos agotados**"));
        assertFalse(FormateadorWhatsApp.formatear("# Uno\n## Dos\n###### Seis").contains("#"));
    }

    @Test
    void lasLineasHorizontalesDesaparecen() {
        assertEquals("Arriba\n\nAbajo", FormateadorWhatsApp.formatear("Arriba\n\n---\n\nAbajo"));
    }

    @Test
    void lasVinetasConAsteriscoNoSeConfundenConNegrita() {
        String markdown = """
                * **Leche entera 1 L:** 21 unidades
                * Tomate (kg)
                  - Vence hoy
                - Pan tajado""";
        String esperado = """
                • *Leche entera 1 L:* 21 unidades
                • Tomate (kg)
                  • Vence hoy
                • Pan tajado""";
        assertEquals(esperado, FormateadorWhatsApp.formatear(markdown));
    }

    @Test
    void lasListasNumeradasSeConservan() {
        assertEquals("1. *Leche* (422 unidades)\n2. Arepas",
                FormateadorWhatsApp.formatear("1. **Leche** (422 unidades)\n2. Arepas"));
    }

    @Test
    void lasTablasSeVuelvenLineasLegibles() {
        String tabla = """
                | Producto | Unidades |
                |----------|---------:|
                | Leche    | 422      |""";
        assertEquals("Producto · Unidades\nLeche · 422", FormateadorWhatsApp.formatear(tabla));
    }

    @Test
    void losEnlacesMuestranLaUrl() {
        assertEquals("Mira la guía (https://ejemplo.com/guia)",
                FormateadorWhatsApp.formatear("Mira [la guía](https://ejemplo.com/guia)"));
    }

    @Test
    void unaRespuestaRealDelAsistenteQuedaLimpia() {
        String respuesta = """
                ### 🚨 Producto agotado (Pedir con urgencia)
                * **Chocolate de mesa 500 g** (Despensa)
                  * **Stock actual:** 0 unidades (Mínimo: 8)

                ---



                💡 **Recomendación:** Da prioridad al **Chocolate de mesa**.""";
        String formateada = FormateadorWhatsApp.formatear(respuesta);

        assertFalse(formateada.contains("**"));
        assertFalse(formateada.contains("#"));
        assertFalse(formateada.contains("---"));
        assertFalse(formateada.contains("\n\n\n"), "no deja más de una línea en blanco seguida");
        assertTrue(formateada.startsWith("*🚨 Producto agotado (Pedir con urgencia)*\n• *Chocolate de mesa 500 g*"));
        assertTrue(formateada.contains("  • *Stock actual:* 0 unidades"));
        assertTrue(formateada.endsWith("💡 *Recomendación:* Da prioridad al *Chocolate de mesa*."));
    }

    @Test
    void textoNuloOVacio() {
        assertEquals("", FormateadorWhatsApp.formatear(null));
        assertEquals("", FormateadorWhatsApp.formatear("  \n\n "));
    }

    // ---------- División de mensajes largos ----------

    @Test
    void unMensajeCortoNoSeDivide() {
        assertEquals(List.of("Hola"), FormateadorWhatsApp.dividir("Hola", 200));
    }

    @Test
    void unMensajeLargoSeDivideEntreParrafosYSeNumera() {
        String parrafo = "Este es un párrafo con información de ventas del supermercado. ".repeat(4).strip();
        String texto = String.join("\n\n", parrafo + " A", parrafo + " B", parrafo + " C", parrafo + " D");
        List<String> partes = FormateadorWhatsApp.dividir(texto, 600);

        assertTrue(partes.size() > 1);
        for (int i = 0; i < partes.size(); i++) {
            String parte = partes.get(i);
            assertTrue(parte.length() <= 600, "parte " + (i + 1) + " mide " + parte.length());
            assertTrue(parte.endsWith("\n(" + (i + 1) + "/" + partes.size() + ")"));
        }
        // Cortó entre párrafos: ninguno quedó partido a la mitad
        assertTrue(partes.getFirst().contains(parrafo + " A"));
        assertEquals(texto, unir(partes));
    }

    @Test
    void sinParrafosCortaEntrePalabras() {
        String texto = "palabra ".repeat(200).strip();
        List<String> partes = FormateadorWhatsApp.dividir(texto, 300);

        assertTrue(partes.size() > 1);
        for (String parte : partes) {
            assertTrue(parte.length() <= 300);
            String sinNumero = parte.substring(0, parte.lastIndexOf('\n'));
            assertTrue(sinNumero.matches("palabra( palabra)*"), "no partió ninguna palabra: " + sinNumero);
        }
        assertEquals(200, unir(partes).split("\\s+").length, "no se perdió ninguna palabra");
    }

    @Test
    void unTextoSinEspaciosSeCortaAlLimite() {
        String texto = "x".repeat(1000);
        List<String> partes = FormateadorWhatsApp.dividir(texto, 300);
        assertTrue(partes.stream().allMatch(p -> p.length() <= 300));
        assertEquals(texto, unir(partes).replace("\n", ""));
    }

    /** Quita la numeración y vuelve a juntar las partes. */
    private static String unir(List<String> partes) {
        return String.join("\n\n", partes.stream().map(p -> p.substring(0, p.lastIndexOf('\n'))).toList());
    }
}
