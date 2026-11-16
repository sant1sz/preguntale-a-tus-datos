# Pregúntale a tus datos

Asistente con IA para el dueño de un supermercado: le escribes una pregunta en español o en inglés
("¿qué productos se vendieron más esta semana?", "which items are about to expire?") y te responde
consultando los datos del negocio.

> Estado: en construcción. Día 1 — el chat ya responde en ambos idiomas; la conexión con los datos llega después.

## Tecnologías

- Java 21 · Spring Boot 4.1
- Spring AI 2.0 con Google Gemini (`gemini-3.5-flash`)
- Próximamente: MySQL con datos simulados de un supermercado y herramientas (tool calling) para consultarlos

## Cómo ejecutarlo

1. Crea una clave gratuita en [Google AI Studio](https://aistudio.google.com/apikey).
2. Crea `src/main/resources/application-local.properties` (está en `.gitignore`) con:
   ```properties
   spring.ai.google.genai.api-key=TU_CLAVE
   ```
   o define la variable de entorno `GEMINI_API_KEY`.
3. Ejecuta:
   ```bash
   ./mvnw spring-boot:run
   ```
4. Pregunta:
   ```bash
   curl -X POST http://localhost:8080/api/chat \
     -H "Content-Type: application/json" \
     -d '{"pregunta": "Hola, ¿qué puedes hacer por mi supermercado?"}'
   ```

## API

| Método | Ruta        | Cuerpo                      | Respuesta                    |
|--------|-------------|-----------------------------|------------------------------|
| POST   | `/api/chat` | `{ "pregunta": "..." }`     | `{ "respuesta": "..." }`     |
