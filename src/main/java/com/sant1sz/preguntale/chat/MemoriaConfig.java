package com.sant1sz.preguntale.chat;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Memoria de conversación en RAM: guarda los últimos mensajes de cada conversación (se pierde al reiniciar). */
@Configuration
public class MemoriaConfig {

    @Bean
    public ChatMemory memoriaConversaciones(@Value("${asistente.memoria.mensajes:10}") int mensajes) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(mensajes)
                .build();
    }
}
