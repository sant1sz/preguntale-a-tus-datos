# Pregúntale a tus datos

Asistente con IA para el dueño de un supermercado: le escribes una pregunta en español o en inglés
("¿qué productos se vendieron más esta semana?", "which items are about to expire?") y te responde
consultando los datos del negocio.

> Estado: en construcción. El chat ya responde en ambos idiomas y la base de datos simulada está lista; falta conectar el asistente a los datos.

## Tecnologías

- Java 21 · Spring Boot 4.1
- Spring AI 2.0 con Google Gemini (`gemini-3.5-flash`)
- MySQL + Spring Data JPA con un supermercado simulado
- Próximamente: herramientas (tool calling) para que el asistente consulte los datos

## Datos simulados

Al arrancar con la base vacía se genera un supermercado de barrio colombiano: 40 productos en 8 categorías,
unas 5.500 ventas de los últimos 90 días, inventario y lotes con fecha de vencimiento. Los datos siguen
patrones reales para que las preguntas tengan respuestas interesantes:

- Se vende más los fines de semana y en días de quincena.
- Hay horas pico en la mañana, al mediodía y al final de la tarde.
- Algunos productos suben de ventas (aguacate, bebida energizante) y otros bajan (pan de queso).
- Hay productos con stock bajo o agotados, y lotes vencidos o por vencer.

La generación usa una semilla fija (`datos.semilla`), así que siempre produce los mismos datos.

## Cómo ejecutarlo

1. Crea una clave gratuita en [Google AI Studio](https://aistudio.google.com/apikey).
2. Ten MySQL corriendo en `localhost:3306` (la base `preguntale_db` se crea sola).
3. Crea `src/main/resources/application-local.properties` (está en `.gitignore`) con:
   ```properties
   spring.ai.google.genai.api-key=TU_CLAVE
   spring.datasource.password=TU_CONTRASEÑA_DE_MYSQL
   ```
   o define las variables de entorno `GEMINI_API_KEY` y `DB_PASSWORD`.
4. Ejecuta:
   ```bash
   ./mvnw spring-boot:run
   ```
5. Pregunta:
   ```bash
   curl -X POST http://localhost:8080/api/chat \
     -H "Content-Type: application/json" \
     -d '{"pregunta": "Hola, ¿qué puedes hacer por mi supermercado?"}'
   ```

## API

| Método | Ruta        | Cuerpo                      | Respuesta                    |
|--------|-------------|-----------------------------|------------------------------|
| POST   | `/api/chat` | `{ "pregunta": "..." }`     | `{ "respuesta": "..." }`     |
| GET    | `/api/datos/resumen` | —                  | Conteos, rango de fechas, ventas totales y alertas |
