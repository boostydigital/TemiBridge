<# 
 Script para compilar e instalar Deamon DB TEMI en Temi
 Uso:    .\install-to-temi.ps1 [IP_DEL_TEMI]
 Ejemplo: .\install-to-temi.ps1 192.168.41.157
#>

param(
    [string]$TemiIP = "192.168.41.157"
)

$ErrorActionPreference = "Stop"

# Rutas y constantes
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$APK = Join-Path $env:USERPROFILE "TemiDeamonDBBuild\app\outputs\apk\debug\app-debug.apk"
$PACKAGE = "com.spatium.deamon.db.temi"
$JAVA_HOME_DEFAULT = "C:\Program Files\Android\Android Studio\jbr"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Instalador Deamon DB TEMI para Temi" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $ADB)) {
    Write-Host "[ERROR] No se encontró adb en: $ADB" -ForegroundColor Red
    exit 1
}

# Asegurar JAVA_HOME
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = $JAVA_HOME_DEFAULT
}

Write-Host "[1/6] Compilando APK (assembleDebug)..." -ForegroundColor Yellow
& .\gradlew.bat assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Falló la compilación (assembleDebug)" -ForegroundColor Red
    exit 1
}

# Verificar que existe el APK ya compilado
if (-not (Test-Path $APK)) {
    Write-Host "[ERROR] No se encontró el APK en: $APK" -ForegroundColor Red
    exit 1
}

Write-Host "[2/6] Conectando a Temi en $TemiIP..." -ForegroundColor Yellow
& $ADB connect "${TemiIP}:5555"

Write-Host "[3/6] Verificando conexión..." -ForegroundColor Yellow
& $ADB devices

Write-Host "[4/6] Desinstalando versión anterior (si existe)..." -ForegroundColor Yellow
& $ADB -s "${TemiIP}:5555" uninstall $PACKAGE 2>$null

Write-Host "[5/6] Instalando nueva versión..." -ForegroundColor Yellow
& $ADB -s "${TemiIP}:5555" install -r $APK
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Falló la instalación" -ForegroundColor Red
    exit 1
}

Write-Host "[6/6] Iniciando aplicación..." -ForegroundColor Yellow
& $ADB -s "${TemiIP}:5555" shell am start -n "$PACKAGE/.ui.MainActivity"

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Instalacion completada" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

