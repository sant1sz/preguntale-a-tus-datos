package com.sant1sz.preguntale.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> solicitudInvalida(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Solicitud inválida");
        return ResponseEntity.badRequest().body(Map.of("error", mensaje));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> errorInesperado(Exception ex) {
        // Errores de Spring que ya traen su código (ruta inexistente, método no permitido...): se respetan
        if (ex instanceof ErrorResponse respuesta) {
            HttpStatusCode codigo = respuesta.getStatusCode();
            String mensaje = codigo.value() == 404 ? "No existe esa ruta" : "Solicitud no válida";
            return ResponseEntity.status(codigo).body(Map.of("error", mensaje));
        }
        log.error("Error al responder la pregunta", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "No pude obtener respuesta del modelo de IA. Revisa la clave de Gemini y la conexión."));
    }
}
