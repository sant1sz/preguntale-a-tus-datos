# Mide cuánto tarda el asistente en responder unas preguntas fijas.
# Ojo: el nivel gratuito de Gemini permite 20 solicitudes por día y cada pregunta gasta 2 o 3.
#
# Para comparar niveles de razonamiento, arranca la API con el nivel que quieras medir:
#   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.ai.google.genai.chat.thinking-level=HIGH"
# y luego ejecuta:  powershell -File scripts\medir-latencia.ps1 -Etiqueta HIGH
param(
    [string]$Etiqueta = "medicion",
    [string]$Url = "http://localhost:8080/api/chat",
    [int]$PausaSegundos = 15
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$preguntas = @(
    "¿Cuánto vendí esta semana?",
    "¿Qué productos debo pedir al proveedor?",
    "Which items are about to expire?"
)

$tiempos = @()
foreach ($pregunta in $preguntas) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes((@{ pregunta = $pregunta } | ConvertTo-Json))
    $reloj = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        Invoke-WebRequest -Uri $Url -Method Post -ContentType 'application/json; charset=utf-8' `
            -Body $bytes -UseBasicParsing | Out-Null
        $reloj.Stop()
        $tiempos += $reloj.Elapsed.TotalSeconds
        Write-Host ("{0,6:N1} s  {1}" -f $reloj.Elapsed.TotalSeconds, $pregunta) -ForegroundColor Green
    } catch {
        $reloj.Stop()
        Write-Host ("  error  {0}: {1}" -f $pregunta, $_.Exception.Message) -ForegroundColor Red
    }
    Start-Sleep -Seconds $PausaSegundos
}

if ($tiempos.Count -gt 0) {
    $promedio = ($tiempos | Measure-Object -Average).Average
    Write-Host ("[{0}] promedio: {1:N1} s en {2} respuestas" -f $Etiqueta, $promedio, $tiempos.Count) -ForegroundColor Cyan
}
