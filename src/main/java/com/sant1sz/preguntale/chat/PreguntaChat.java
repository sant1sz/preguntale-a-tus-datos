package com.sant1sz.preguntale.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PreguntaChat(
        @NotBlank(message = "La pregunta no puede estar vacía")
        @Size(max = 500, message = "La pregunta no puede tener más de 500 caracteres")
        String pregunta) {
}
