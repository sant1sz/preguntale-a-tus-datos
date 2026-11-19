package com.sant1sz.preguntale.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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

    static final String INSTRUCCIONES_WHATSAPP = """

            Estás respondiendo por WhatsApp, en una conversación que continúa: puedes apoyarte en
            los mensajes anteriores ("¿y ayer?", "¿y de esos cuál deja más ganancia?").
            - Mensajes cortos, fáciles de leer en el celular.
            - Para resaltar usa *un solo asterisco*. No uses títulos con #, ni tablas, ni líneas ---.
            - Para listas usa guiones o números.
            """;

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoria;

    public AsistenteService(ChatClient.Builder builder, HerramientasSupermercado herramientas, ChatMemory memoria) {
        this.chatClient = builder
                .defaultSystem(INSTRUCCIONES)
                .defaultTools(herramientas)
                .build();
        this.memoria = MessageChatMemoryAdvisor.builder(memoria).build();
    }

    /** Pregunta suelta, sin memoria (la usa la API REST /api/chat). */
    public String responder(String pregunta) {
        return chatClient.prompt()
                .user(pregunta)
                .call()
                .content();
    }

    /** Pregunta dentro de la conversación de WhatsApp de ese número: recuerda los últimos mensajes. */
    public String responderPorWhatsApp(String pregunta, String telefono) {
        return chatClient.prompt()
                .system(INSTRUCCIONES + INSTRUCCIONES_WHATSAPP)
                .user(pregunta)
                .advisors(memoria)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "whatsapp:" + telefono))
                .call()
                .content();
    }
}
