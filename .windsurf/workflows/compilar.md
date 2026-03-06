---
description: "Compilar APK debug de Deamon DB TEMI"
auto_execution_mode: 3
---

---
description: "Compilar APK debug
auto_execution_mode: 3
---

# Workflow: Compilar APK (Debug)

## Prerrequisitos

- Tener Android Studio instalado en `C:\Program Files\Android\Android Studio\`.
- Estar en la raíz del proyecto:  
- Asegurarse de que existe `gradlew.bat` en la raíz del proyecto.

## Pasos

Ubicacion androoid SDK: 
C:\Users\samir\AppData\Local\Android\Sdk




Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue; $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug --no-daemon 2>&1 | Select-Object -Last 30


IP Temi:  192.168.40.48
  ```

asegurate siempre de antes de instalar la compilacion del apk desintalar la version anterior

ip temi : 192.168.40.48