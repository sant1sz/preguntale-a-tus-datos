# Envía una pregunta al asistente y muestra la respuesta (con tildes bien).
param([string]$Pregunta = "Hola, ¿qué puedes hacer por mi supermercado?")

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$cuerpo = @{ pregunta = $Pregunta } | ConvertTo-Json
$bytes = [System.Text.Encoding]::UTF8.GetBytes($cuerpo)

Write-Host "Tú: $Pregunta" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri 'http://localhost:8080/api/chat' -Method Post `
        -ContentType 'application/json; charset=utf-8' -Body $bytes -UseBasicParsing
    $texto = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    Write-Host "Asistente: $(($texto | ConvertFrom-Json).respuesta)" -ForegroundColor Green
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails) { Write-Host $_.ErrorDetails.Message -ForegroundColor Red }
    Write-Host "¿Está corriendo la tarea 'Pregúntale: ejecutar API'?" -ForegroundColor Yellow
}
