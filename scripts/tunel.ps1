# Abre un túnel público hacia la API local (puerto 8080) con cloudflared.
$exe = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $exe) {
    $candidatos = @(
        "$PSScriptRoot\..\.herramientas\cloudflared.exe",
        "$env:ProgramFiles\cloudflared\cloudflared.exe",
        "${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe",
        "$env:LOCALAPPDATA\Microsoft\WinGet\Links\cloudflared.exe"
    ) + (Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter cloudflared.exe -ErrorAction SilentlyContinue | ForEach-Object FullName)
    $exe = $candidatos | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
}
if (-not $exe) { Write-Host "No encontré cloudflared. Corre la tarea 'Túnel: instalar cloudflared'." -ForegroundColor Red; exit 1 }
Write-Host "Usando $exe" -ForegroundColor Cyan
& $exe tunnel --url http://localhost:8080 --logfile "$PSScriptRoot\..\logs\tunel.log"
