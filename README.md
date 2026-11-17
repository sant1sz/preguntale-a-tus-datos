# Pregúntale a tus datos

Asistente con IA para el dueño de un supermercado: le escribes una pregunta en español o en inglés
("¿qué productos se vendieron más esta semana?", "which items are about to expire?") y te responde
consultando los datos del negocio.

> Estado: en construcción. El asistente ya responde en español e inglés consultando los datos del supermercado; falta la interfaz web.

## Tecnologías

- Java 21 · Spring Boot 4.1
- Spring AI 2.0 con Google Gemini (`gemini-3.5-flash`)
- MySQL + Spring Data JPA con un supermercado simulado
- Herramientas (tool calling) de Spring AI para que el modelo consulte la base de datos
- Pruebas con JUnit 5 y H2 en modo MySQL

## Datos simulados

Al arrancar con la base vacía se genera un supermercado de barrio colombiano: 40 productos en 8 categorías,
unas 5.500 ventas de los últimos 90 días, inventario y lotes con fecha de vencimiento. Los datos siguen
patrones reales para que las preguntas tengan respuestas interesantes:

- Se vende más los fines de semana y en días de quincena.
- Hay horas pico en la mañana, al mediodía y al final de la tarde.
- Algunos productos suben de ventas (aguacate, bebida energizante) y otros bajan (pan de queso).
- Hay productos con stock bajo o agotados, y lotes vencidos o por vencer.

La generación usa una semilla fija (`datos.semilla`), así que siempre produce los mismos datos.
Si al arrancar la última venta es de un día anterior a hoy, la aplicación borra los datos y los vuelve a simular
con la fecha actual (`datos.regenerar-si-desactualizados`), para que "hoy" y "esta semana" siempre tengan ventas.

## Cómo responde con datos reales

El modelo no ve la base de datos ni escribe SQL. En cambio tiene un conjunto de **herramientas**
(métodos Java con `@Tool` en `HerramientasSupermercado`) y decide cuáles llamar según la pregunta.
Spring AI ejecuta la consulta, le devuelve el resultado y el modelo lo explica en lenguaje sencillo.

| Herramienta | Para qué sirve |
|-------------|----------------|
| `fechaActual` | Fecha de hoy y rango con ventas, para entender "ayer", "esta semana", "el mes pasado" |
| `resumenVentas` | Total vendido, número de ventas, ticket promedio, ganancia estimada y medios de pago |
| `ventasPorDia` / `ventasPorHora` | Tendencias, mejores días y horas pico |
| `ventasPorCategoria` | Qué categorías venden y dejan más ganancia |
| `rankingProductos` | Productos más o menos vendidos por unidades, ingresos o ganancia |
| `buscarProductos` / `listarCategorias` | Precio, costo, margen y stock de cualquier producto |
| `productosBajoStock` | Qué pedir al proveedor y para cuántos días alcanza el inventario |
| `lotesPorVencer` | Lotes vencidos o por vencer y el costo de la mercancía en riesgo |

Ejemplos de preguntas:

- "¿Cuánto vendí esta semana?"
- "¿Qué productos debo pedir al proveedor?"
- "¿Cuáles fueron los 3 productos más vendidos el mes pasado?"
- "Which items are about to expire?"

Las herramientas devuelven los totales, porcentajes, promedios y extremos (mejor día, hora pico, costo total en
riesgo) ya calculados en Java: el modelo solo los explica y no tiene que hacer cuentas, que es donde más se equivoca.

Si la pregunta pide datos que no existen (por ejemplo, un mes sin ventas registradas), el asistente lo dice
en lugar de inventar cifras.

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
