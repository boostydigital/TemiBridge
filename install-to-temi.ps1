# Script para instalar TemiBridge en Temi
# Uso: .\install-to-temi.ps1 [IP_DEL_TEMI]
# Ejemplo: .\install-to-temi.ps1 192.168.1.103

param(
    [string]$TemiIP = "192.168.1.103"
)

$ADB = "C:\Users\samir\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$APK = "app\build\outputs\apk\debug\app-debug.apk"
$PACKAGE = "com.spatium.temibridge"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Instalador TemiBridge para Temi" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Verificar que existe el APK
if (-not (Test-Path $APK)) {
    Write-Host "[ERROR] No se encontró el APK en: $APK" -ForegroundColor Red
    Write-Host "Ejecuta primero: .\gradlew.bat assembleDebug" -ForegroundColor Yellow
    exit 1
}

Write-Host "[1/5] Conectando a Temi en $TemiIP..." -ForegroundColor Yellow
& $ADB connect "${TemiIP}:5555"

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] No se pudo conectar al Temi" -ForegroundColor Red
    Write-Host "Verifica que:" -ForegroundColor Yellow
    Write-Host "  - El Temi esté encendido" -ForegroundColor Yellow
    Write-Host "  - La depuración USB esté habilitada" -ForegroundColor Yellow
    Write-Host "  - La IP sea correcta: $TemiIP" -ForegroundColor Yellow
    exit 1
}

Write-Host "[2/5] Verificando conexión..." -ForegroundColor Yellow
& $ADB devices

Write-Host "[3/5] Desinstalando versión anterior (si existe)..." -ForegroundColor Yellow
& $ADB uninstall $PACKAGE 2>$null

Write-Host "[4/5] Instalando nueva versión..." -ForegroundColor Yellow
& $ADB install -r $APK

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Falló la instalación" -ForegroundColor Red
    exit 1
}

Write-Host "[5/5] Iniciando aplicación..." -ForegroundColor Yellow
& $ADB shell am start -n "$PACKAGE/.ui.MainActivity"

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✓ Instalación completada" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Formato QR soportado:" -ForegroundColor Cyan
Write-Host "  mytemi://escort?greeting=Bienvenido a Spatium" -ForegroundColor White
Write-Host ""
