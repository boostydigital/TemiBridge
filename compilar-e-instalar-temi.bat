@echo off
REM Script para compilar e instalar la aplicación en robot Temi
REM Autor: Cascade
REM Fecha: 2026-03-06

setlocal enabledelayedexpansion

REM Colores para output
set "GREEN=[92m"
set "YELLOW=[93m"
set "RED=[91m"
set "RESET=[0m"

echo.
echo %GREEN%========================================%RESET%
echo %GREEN%Compilar e Instalar en Temi Robot%RESET%
echo %GREEN%========================================%RESET%
echo.

REM Verificar que estamos en el directorio correcto
if not exist "gradlew.bat" (
    echo %RED%Error: No se encontró gradlew.bat%RESET%
    echo %YELLOW%Asegúrate de ejecutar este script desde la raíz del proyecto%RESET%
    pause
    exit /b 1
)

REM Paso 1: Compilar
echo %YELLOW%[1/3] Compilando aplicación...%RESET%
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call gradlew.bat clean assembleDebug

if !errorlevel! neq 0 (
    echo %RED%Error: La compilación falló%RESET%
    pause
    exit /b 1
)

echo %GREEN%✓ Compilación exitosa%RESET%
echo.

REM Paso 2: Instalar
echo %YELLOW%[2/3] Instalando en robot Temi...%RESET%
set "APK_PATH=C:\Users\samir\TemiDeamonDBBuild\app\outputs\apk\debug\app-debug.apk"

if not exist "!APK_PATH!" (
    echo %RED%Error: No se encontró el APK en !APK_PATH!%RESET%
    pause
    exit /b 1
)

adb install "!APK_PATH!"

if !errorlevel! neq 0 (
    echo %RED%Error: La instalación falló%RESET%
    echo %YELLOW%Verifica que el robot Temi esté conectado por USB%RESET%
    pause
    exit /b 1
)

echo %GREEN%✓ Instalación exitosa%RESET%
echo.

REM Paso 3: Iniciar aplicación
echo %YELLOW%[3/3] Iniciando aplicación en Temi...%RESET%
adb shell am start -n "com.spatium.deamon.db.temi/.ui.MainActivity"

if !errorlevel! neq 0 (
    echo %RED%Error: No se pudo iniciar la aplicación%RESET%
    pause
    exit /b 1
)

echo %GREEN%✓ Aplicación iniciada%RESET%
echo.

echo %GREEN%========================================%RESET%
echo %GREEN%¡Proceso completado exitosamente!%RESET%
echo %GREEN%========================================%RESET%
echo.
echo %YELLOW%La aplicación está corriendo en el robot Temi%RESET%
echo.

pause
