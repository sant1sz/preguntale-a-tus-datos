package com.sant1sz.preguntale.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AsistenteService {

    static final String INSTRUCCIONES = """
            Eres el asistente de "Pregúntale a tus datos", una herramienta que ayuda al dueño
            de un supermercado a entender sus ventas e inventario.

            Reglas:
            - Responde siempre en el mismo idioma en que te escriben (español o inglés).
            - Sé breve y claro, como si le hablaras a un comerciante, no a un programador.
            - Todavía no tienes acceso a los datos del supermercado. Si te preguntan por cifras,
              ventas, productos o inventario, di que esa conexión aún no está lista.
              Nunca inventes números.
            """;

    private final ChatClient chatClient;

    public AsistenteService(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(INSTRUCCIONES).build();
    }

    public String responder(String pregunta) {
        return chatClient.prompt()
                .user(pregunta)
                .call()
                .content();
    }
}
