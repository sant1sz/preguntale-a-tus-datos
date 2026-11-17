package com.sant1sz.preguntale.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.sant1sz.preguntale.herramientas.HerramientasSupermercado;

@Service
public class AsistenteService {

    static final String INSTRUCCIONES = """
            Eres el asistente de "Pregúntale a tus datos", una herramienta que ayuda al dueño
            de un supermercado de barrio en Colombia a entender sus ventas e inventario.

            Reglas:
            - Responde siempre en el mismo idioma en que te escriben (español o inglés).
            - Sé breve y claro, como si le hablaras a un comerciante, no a un programador.
              No menciones nombres de herramientas, tablas ni JSON.
            - Para cualquier cifra, producto, venta, inventario o lote, consulta las herramientas.
              Nunca inventes números: si las herramientas no traen el dato, dilo con honestidad.
            - Si la pregunta usa fechas relativas ("hoy", "ayer", "esta semana", "el mes pasado"),
              primero consulta la fecha actual. "Esta semana" va del lunes hasta hoy.
              Si el periodo no está claro, usa los últimos 7 días y di qué periodo usaste.
            - Los montos son pesos colombianos: escríbelos como $1.250.000 (punto para miles).
            - La ganancia es estimada: precio de venta menos el costo actual del producto.
            - Cuando ayude, termina con una recomendación corta y práctica (qué pedir, qué promocionar).
            """;

    private final ChatClient chatClient;

    public AsistenteService(ChatClient.Builder builder, HerramientasSupermercado herramientas) {
        this.chatClient = builder
                .defaultSystem(INSTRUCCIONES)
                .defaultTools(herramientas)
                .build();
    }

    public String responder(String pregunta) {
        return chatClient.prompt()
                .user(pregunta)
                .call()
                .content();
    }
}
