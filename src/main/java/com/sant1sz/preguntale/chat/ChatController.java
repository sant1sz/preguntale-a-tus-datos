package com.sant1sz.preguntale.chat;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AsistenteService asistente;

    public ChatController(AsistenteService asistente) {
        this.asistente = asistente;
    }

    @PostMapping
    public RespuestaChat preguntar(@Valid @RequestBody PreguntaChat solicitud) {
        return new RespuestaChat(asistente.responder(solicitud.pregunta()));
    }
}
