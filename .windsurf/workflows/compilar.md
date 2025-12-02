---
description: "Compilar APK debug de TemiBridge"
auto_execution_mode: 3
---

# Workflow: Compilar APK (Debug)

## Prerrequisitos

- Tener Android Studio instalado en `C:\Program Files\Android\Android Studio\`.
- Estar en la raíz del proyecto:  
  `C:\Users\samir\OneDrive\Documents\Spatium Group\TemiBridge`
- Asegurarse de que existe `gradlew.bat` en la raíz del proyecto.

## Pasos

1. Abrir una ventana de **PowerShell**.
2. Navegar a la carpeta del proyecto:

   ```powershell
   cd "C:\Users\samir\OneDrive\Documents\Spatium Group\TemiBridge"
   ```

3. Ejecutar el comando de compilación (limpia `app\build`, configura `JAVA_HOME` y genera el APK debug):

   ```powershell
   Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue; `
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; `
   .\gradlew.bat assembleDebug --no-daemon 2>&1 | Select-Object -Last 20
   ```

4. Al finalizar, el APK generado se encuentra normalmente en:

   - `app\build\outputs\apk\debug\app-debug.apk`

## Notas

- Si necesitas ver el log completo de Gradle (en lugar de solo las últimas 20 líneas), ejecuta:

  ```powershell
  .\gradlew.bat assembleDebug --no-daemon
  ```

asegurate siempre de antes de instalar la compilacion del apk desintalar la version anterior