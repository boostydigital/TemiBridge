# Deamon DB TEMI (com.spatium.deamon.db.temi)

Puente ligero para controlar un robot temi desde apps externas (web o nativas) mediante Intents y Deep Links. Proporciona un Activity receptor "headless" (`IntentEntryActivity`) que procesa acciones comunes: hablar, ir a un waypoint, seguir al usuario, parar, inclinar la cabeza, volumen e iniciar un tour (via NLU).

- Package: `com.spatium.deamon.db.temi`
- Entry Activity: `com.spatium.deamon.db.temi.ui.IntentEntryActivity`
- SDK: Temi SDK 1.136.0 (compatible con 1.13x+)

## Tabla de contenidos
- [Instalación en Temi](#instalación-en-temi)
- [Acciones soportadas](#acciones-soportadas)
- [Deep Links (scheme mytemi://)](#deep-links-scheme-mytemi)
- [Intent URIs (navegador/WebView)](#intent-uris-navegadorwebview)
- [Intents Android (nativos)](#intents-android-nativos)
- [Pruebas rápidas con ADB](#pruebas-rápidas-con-adb)
- [Integración Web (Android navegador/WebView)](#integración-web-android-navegadorwebview)
- [Requisitos y permisos](#requisitos-y-permisos)
- [Solución de problemas](#solución-de-problemas)

---

## Instalación en Temi

1) Preparar ADB en Windows (si no lo tienes):
   - Descarga "Platform Tools" y añade `%USERPROFILE%/platform-tools` al PATH.

2) Conectar a Temi por red (activar "ADB over network" en Temi):
```powershell
adb connect <TEMI_IP>:5555
adb devices
```

3) Instalar o reinstalar la APK:
```powershell
adb install -r "<ruta>/Temi Puente.apk"
```

> Si hay conflicto de firma: `adb uninstall com.spatium.deamon.db.temi` y vuelve a instalar.

---

## Acciones soportadas
La Activity receptora reconoce estas acciones. Todas aceptan `category DEFAULT`.

- `com.spatium.temibridge.ACTION_SAY` — Temi habla un texto (`S.text`).
- `com.spatium.temibridge.ACTION_GO_TO` — Ir a un waypoint (`S.place`).
- `com.spatium.temibridge.ACTION_FOLLOW_ME` — Seguir al usuario (beWithMe).
- `com.spatium.temibridge.ACTION_STOP` — Parar movimiento.
- `com.spatium.temibridge.ACTION_HEAD_TILT` — Inclinar cabeza (`i.angle` entero, aprox. -25..25 grados).
- `com.spatium.temibridge.ACTION_VOLUME` — Volumen (`i.level` entero 0..10; se mapea a STREAM_MUSIC).
- `com.spatium.temibridge.ACTION_TOUR_START` — Iniciar un tour por nombre o id (`S.name` o `S.tourId`). Internamente dispara `robot.startDefaultNlu(identifier)`.

---

## Deep Links (scheme `mytemi://`)
El `AndroidManifest.xml` expone un `intent-filter` para `VIEW` con `scheme=mytemi`.

- Hablar:  
  `mytemi://say?text=Bienvenidos%20a%20Spatium`

- Ir a waypoint:  
  `mytemi://go?place=Open_Space`

- Iniciar tour por nombre:  
  `mytemi://tour?name=Spatium_Visita`

> Nota: URL-encodea los parámetros (espacios como `%20`).

---

## Intent URIs (navegador/WebView)
Para navegadores Android o WebViews que bloquean esquemas custom, usa `intent://` con `scheme` y `package`.

- GoTo (waypoint):
```
intent://go?place=Open_Space#Intent;scheme=mytemi;package=com.spatium.deamon.db.temi;end
```

- Tour por nombre:
```
intent://exec#Intent;action=com.spatium.temibridge.ACTION_TOUR_START;S.name=Spatium_Visita;package=com.spatium.deamon.db.temi;end
```

- Opcional: forzar componente si el navegador no resuelve bien:
```
intent://go?place=Open_Space#Intent;scheme=mytemi;package=com.spatium.deamon.db.temi;component=com.spatium.deamon.db.temi/.ui.IntentEntryActivity;end
```

HTML de ejemplo:
```html
<a href="mytemi://go?place=Open_Space">Ir a Open_Space</a>
<a href="intent://exec#Intent;action=com.spatium.temibridge.ACTION_TOUR_START;S.name=Spatium_Visita;package=com.spatium.deamon.db.temi;end">Iniciar tour</a>
```

---

## Intents Android (nativos)
Desde otra app Android, dispara Intents explícitos hacia `IntentEntryActivity`.

Kotlin/Java ejemplo (explícito):
```kotlin
val intent = Intent("com.spatium.temibridge.ACTION_GO_TO").apply {
    setPackage("com.spatium.deamon.db.temi")
    putExtra("place", "Open_Space")
}
startActivity(intent)
```

O forzando el componente:
```kotlin
val intent = Intent("com.spatium.temibridge.ACTION_SAY").apply {
    setClassName(
        "com.spatium.deamon.db.temi",
        "com.spatium.deamon.db.temi.ui.IntentEntryActivity"
    )
    putExtra("text", "Hola desde tu app")
}
startActivity(intent)
```

---

## Pruebas rápidas con ADB
Forzando el componente receptor para evitar ambigüedades:

```powershell
# Hablar
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity -a com.spatium.temibridge.ACTION_SAY --es text "Hola"

# Ir a un waypoint
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity -a com.spatium.temibridge.ACTION_GO_TO --es place "Open_Space"

# Seguirme
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity -a com.spatium.temibridge.ACTION_FOLLOW_ME

# Parar
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity -a com.spatium.temibridge.ACTION_STOP

# Inclinación 15°
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity -a com.spatium.temibridge.ACTION_HEAD_TILT --ei angle 15

# Volumen 7/10
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity -a com.spatium.temibridge.ACTION_VOLUME --ei level 7

# Tour (por nombre)
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity -a com.spatium.temibridge.ACTION_TOUR_START --es name "Spatium_Visita"

# Deep link de tour
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity -a android.intent.action.VIEW -d "mytemi://tour?name=Spatium_Visita"
```

---

## Integración Web (Android navegador/WebView)

- Opción 1: Deep link directo (simple):
```html
<a href="mytemi://go?place=Open_Space">Ir a Open_Space</a>
```

- Opción 2: Intent URI con fallback (React):
```jsx
<button
  onClick={() => {
    const intent = "intent://go?place=Open_Space#Intent;scheme=mytemi;package=com.spatium.deamon.db.temi;end";
    const deep = "mytemi://go?place=Open_Space";
    window.location.href = intent;
    setTimeout(() => { window.location.href = deep; }, 300);
  }}
>
  Ir a Open_Space
</button>
```

- Opción 3: Iframe oculto (algunos navegadores):
```html
<button onclick="var i=document.createElement('iframe');i.style.display='none';i.src='mytemi://go?place=Open_Space';document.body.appendChild(i);setTimeout(()=>document.body.removeChild(i),2000);">Ir a Open_Space (iframe)</button>
```

- WebView Android: `shouldOverrideUrlLoading` debe permitir `mytemi://` e `intent://`. Construye un Intent con `Intent.ACTION_VIEW` y/o `Intent.parseUri(...)` y llama a `startActivity`.

---

## Requisitos y permisos

- Temi SDK configurado en la app bridge.
- Para volumen usamos Android `AudioManager` (STREAM_MUSIC).
- Actividad receptora con `android:exported="true"`, `launchMode="singleTop"` y tema transparente.
- `AndroidManifest.xml` expone:
  - `intent-filter` de Launcher (MAIN/LAUNCHER) para facilidad de ejecución.
  - `intent-filter` para `VIEW` con `scheme=mytemi` (categorías DEFAULT, BROWSABLE).
  - `intent-filter` explícito con todas las `action` soportadas.

---

## Solución de problemas

- No se abre desde navegador:
  - Usa Intent URI con `scheme` y `package`.
  - Asegura un gesto del usuario (click). Muchas WebViews bloquean redirecciones automáticas.
  - En WebView, habilita `shouldOverrideUrlLoading` para `mytemi://`/`intent://`.

- "Activity not started, unable to resolve Intent":
  - Forzar componente: `-n com.spatium.deamon.db.temi/.ui.IntentEntryActivity`.
  - Verificar que el package instalado sea `com.spatium.deamon.db.temi`.

- GoTo no mueve:
  - Confirma que el waypoint existe en Temi con el mismo nombre (sensibilidad a mayúsculas/guiones/espacios).

- Tour no arranca:
  - Asegura que el nombre/ID exista en Temi Center. Esta app dispara `startDefaultNlu(identifier)`; el Launcher debe resolverlo.

- ADB por Wi‑Fi no conecta (10060):
  - Activa "ADB over network" en Temi y usa la IP:PUERTO mostrados.
  - Reintenta por USB: `adb tcpip 5555` y luego `adb connect <IP>:5555`.

---

## Cambios locales relevantes

- `app/src/main/AndroidManifest.xml`:
  - Añadido MAIN/LAUNCHER en `IntentEntryActivity`.
  - `intent-filter` de `VIEW` (mytemi://) y acciones explícitas.
  - Agregada `ACTION_TOUR_START`.

- `app/src/main/java/com/spatium/temibridge/ui/IntentEntryActivity.kt`:
  - Procesa deep links `mytemi://go|say|tour`.
  - Procesa acciones explícitas GO_TO, SAY, FOLLOW_ME, STOP, HEAD_TILT, VOLUME, TOUR_START.
  - Volumen implementado con `AudioManager` (0..10 -> STREAM_MUSIC).
  - `startTour(identifier)` -> `robot.startDefaultNlu(identifier)` + TTS de confirmación.

---

## Licencia
Uso interno de Spatium Group. Ajustar según políticas del proyecto.
