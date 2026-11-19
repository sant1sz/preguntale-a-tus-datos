# Pregúntale a tus datos

Asistente con IA para el dueño de un supermercado: le escribes una pregunta en español o en inglés
("¿qué productos se vendieron más esta semana?", "which items are about to expire?") y te responde
consultando los datos del negocio.

> Estado: en construcción. El asistente responde en español e inglés consultando los datos del supermercado, por API REST y por WhatsApp.

## Tecnologías

- Java 21 · Spring Boot 4.1
- Spring AI 2.0 con Google Gemini (`gemini-3.5-flash`)
- MySQL + Spring Data JPA con un supermercado simulado
- Herramientas (tool calling) de Spring AI para que el modelo consulte la base de datos
- WhatsApp Cloud API de Meta (webhook con firma HMAC, memoria de conversación por número)
- Pruebas con JUnit 5, Mockito y H2 en modo MySQL

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
| GET    | `/webhook/whatsapp` | parámetros `hub.*` de Meta | El `hub.challenge` si el token coincide |
| POST   | `/webhook/whatsapp` | aviso de Meta firmado | `200` inmediato; la respuesta llega por WhatsApp |

## Canal de WhatsApp

El dueño del supermercado le escribe al asistente por WhatsApp, como a cualquier contacto, y recibe la respuesta
en el mismo chat. El asistente recuerda los últimos mensajes de cada número, así que entiende preguntas de
seguimiento como "¿y ayer?".

### Cómo funciona

1. Meta envía cada mensaje nuevo al webhook `POST /webhook/whatsapp`.
2. La aplicación valida la firma `X-Hub-Signature-256` (HMAC-SHA256 del cuerpo con el App Secret). Si no
   coincide, responde `401`.
3. Descarta los avisos repetidos por id de mensaje (Meta reintenta si no recibe respuesta a tiempo) y responde
   `200` de inmediato.
4. En segundo plano: ignora los números que no están autorizados, marca el mensaje como leído, muestra
   "escribiendo…", consulta al asistente y envía la respuesta con la Graph API.
5. La respuesta se adapta a WhatsApp: `*negrita*` con un asterisco, títulos convertidos en negrita, viñetas `•`,
   tablas convertidas en líneas, y los mensajes de más de 3.500 caracteres se parten en varios numerados `(1/2)`.

### Configurar la app de Meta (número de prueba)

1. En [developers.facebook.com](https://developers.facebook.com/apps) crea una app de tipo **Business** y
   agrégale el producto **WhatsApp**.
2. En **WhatsApp > API Setup**:
   - copia el **Phone number ID** del número de prueba (no es el número en sí);
   - genera un **token de acceso** (el temporal dura 24 horas; para algo estable se crea un usuario del sistema
     con token permanente en Meta Business Suite);
   - en **To**, agrega y verifica tu número de celular. El número de prueba solo puede escribirle a máximo 5
     números verificados.
3. En **App settings > Basic** copia el **App secret**.
4. Agrega todo en `src/main/resources/application-local.properties` (está en `.gitignore`, nunca se sube):
   ```properties
   whatsapp.habilitado=true
   whatsapp.token-verificacion=una-frase-que-tu-inventes
   whatsapp.app-secret=EL_APP_SECRET
   whatsapp.token-acceso=EL_TOKEN_DE_ACCESO
   whatsapp.id-numero-telefono=EL_PHONE_NUMBER_ID
   whatsapp.numeros-autorizados=573001234567
   ```
   Los números van con código de país y sin `+` (se aceptan espacios y guiones). Si falta alguna propiedad,
   la aplicación no arranca y dice cuál es, sin mostrar los valores.

### Exponer la API con un túnel gratuito

Meta necesita una URL pública con HTTPS para llamar al webhook. Para probar desde tu computador sirve un túnel:

**Opción A: Cloudflare Tunnel (sin cuenta)**
```powershell
winget install --id Cloudflare.cloudflared
.\mvnw.cmd spring-boot:run          # en una terminal
cloudflared tunnel --url http://localhost:8080   # en otra
```
Imprime una URL como `https://palabras-al-azar.trycloudflare.com`. Cambia cada vez que lo reinicias, así que
hay que actualizarla en Meta.

**Opción B: ngrok (con cuenta gratuita, URL fija)**
```powershell
winget install ngrok.ngrok
ngrok config add-authtoken TU_AUTHTOKEN        # una sola vez, desde dashboard.ngrok.com
ngrok http 8080 --url=tu-dominio.ngrok-free.app  # el dominio gratis que te asigna ngrok
```
Como la URL no cambia, el webhook se configura en Meta una sola vez.

Después, en Meta, entra a **WhatsApp > Configuration > Webhook**:

1. **Callback URL**: `https://TU-URL-DEL-TUNEL/webhook/whatsapp`
2. **Verify token**: el mismo valor de `whatsapp.token-verificacion`
3. Pulsa **Verify and save**. En `logs/app.log` debe aparecer `WhatsApp: webhook verificado por Meta.`
4. En **Webhook fields**, suscríbete a `messages`.
5. Desde tu celular escríbele al número de prueba: "¿Cuánto vendí esta semana?"

Para comprobar que el túnel llega a tu aplicación antes de ir a Meta:
```powershell
curl "https://TU-URL-DEL-TUNEL/webhook/whatsapp?hub.mode=subscribe&hub.verify_token=una-frase-que-tu-inventes&hub.challenge=123"
```
Debe responder `123`.

> Ten en cuenta la cuota gratuita de Gemini (20 solicitudes por día): cada pregunta gasta 2 o 3. Cuando se agota,
> el asistente responde por WhatsApp que lo intentes al día siguiente.
