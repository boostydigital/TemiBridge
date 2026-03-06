<# 
 Script para compilar (release) e instalar Deamon DB TEMI en Temi
 Uso:    .\install-to-temi-release.ps1 [IP_DEL_TEMI]
 Ejemplo: .\install-to-temi-release.ps1 192.168.41.157
 
 NOTA: requiere que la build de release esté correctamente firmada
       en Gradle para poder instalarla en el Temi.
#>

param(
    [string]$TemiIP = "192.168.41.157"
)

$ErrorActionPreference = "Stop"

# Rutas y constantes
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$APK = Join-Path $env:USERPROFILE "TemiDeamonDBBuild\app\outputs\apk\release\app-release.apk"
$PACKAGE = "com.spatium.deamon.db.temi"
$JAVA_HOME_DEFAULT = "C:\Program Files\Android\Android Studio\jbr"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Instalador RELEASE Deamon DB TEMI" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $ADB)) {
    Write-Host "[ERROR] No se encontrИ adb en: $ADB" -ForegroundColor Red
    exit 1
}

# Asegurar JAVA_HOME
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = $JAVA_HOME_DEFAULT
}

Write-Host "[1/6] Compilando APK (assembleRelease)..." -ForegroundColor Yellow
& .\gradlew.bat assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] FallИ la compilaciИn (assembleRelease)" -ForegroundColor Red
    exit 1
}

# Verificar que existe el APK ya compilado
if (-not (Test-Path $APK)) {
    Write-Host "[ERROR] No se encontrИ el APK de release en: $APK" -ForegroundColor Red
    Write-Host "Verifica la configuraciИn de signingConfig en app/build.gradle.kts" -ForegroundColor Yellow
    exit 1
}

Write-Host "[2/6] Conectando a Temi en $TemiIP..." -ForegroundColor Yellow
& $ADB connect "${TemiIP}:5555"

Write-Host "[3/6] Verificando conexiИn..." -ForegroundColor Yellow
& $ADB devices

Write-Host "[4/6] Desinstalando versiИn anterior (si existe)..." -ForegroundColor Yellow
& $ADB -s "${TemiIP}:5555" uninstall $PACKAGE 2>$null

Write-Host "[5/6] Instalando nueva versiИn (release)..." -ForegroundColor Yellow
& $ADB -s "${TemiIP}:5555" install -r $APK
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] FallИ la instalaciИn de release" -ForegroundColor Red
    exit 1
}

Write-Host "[6/6] Iniciando aplicaciИn..." -ForegroundColor Yellow
& $ADB -s "${TemiIP}:5555" shell am start -n "$PACKAGE/.ui.MainActivity"

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Instalacion RELEASE completada" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

