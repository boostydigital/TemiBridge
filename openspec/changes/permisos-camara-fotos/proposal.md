# Proposal: Permisos de Cámara para Flujo de Fotos

**Change name**: `permisos-camara-fotos`
**Project**: temi-deamon-db
**Status**: proposed
**Date**: 2026-05-01

---

## 1. Intent

### Problem
El permiso de cámara se solicita demasiado tarde en el flujo de fotos. Hoy, al tocar `tileFotos` en `MainActivity`, el usuario navega a `MapSelectorActivity` y luego a `SelfieHunterActivity` antes de que aparezca cualquier diálogo de permisos. La solicitud real ocurre recién en `PartyActivity.onCreate`, dos transiciones de Activity más adelante.

Esto produce dos fallos concretos:

1. **Primera instalación**: el usuario ve dos pantallas (selector de mapa + cazador de selfies) antes de que el sistema pida permiso de cámara. UX desorientadora.
2. **Permiso denegado permanentemente** (o revocado por MDM): `requestCameraPermission.launch()` en `PartyActivity` falla silenciosamente — Android no vuelve a mostrar el diálogo — la activity ejecuta `finish()` y el usuario aterriza de vuelta en `SelfieHunterActivity` sin explicación.

Adicionalmente, no existe ningún chequeo de permiso al inicio de la app: `SplashActivity` es solo animación y `MainActivity.onCreate` no toca permisos.

### Why now
El robot Temi opera en modo kiosko sobre Android 13+ (`targetSdk=36`). En estas versiones, un permiso "denegado permanentemente" no vuelve a mostrar diálogo nativo, lo que rompe el flujo sin feedback visible. El comportamiento actual es aceptable solo en el caso feliz (primera instalación + usuario que acepta).

### Success criteria
- El permiso de cámara se solicita al arranque de `MainActivity` (cada inicio de app, idempotente).
- El tile de fotos no permite avanzar a `MapSelectorActivity` si el permiso no está concedido.
- Cuando el permiso está denegado permanentemente, el usuario recibe feedback claro (diálogo o toast) y un camino hacia Settings.
- `PartyActivity.checkCameraPermission()` permanece como red de contención defensiva.
- Cero regresiones en el flujo feliz existente.

---

## 2. Scope

### In scope
- Modificar `MainActivity.onCreate` para disparar `requestCameraPermission.launch(CAMERA)` al arranque (si aún no está concedido).
- Modificar el click handler de `tileFotos` en `MainActivity` para gatear la navegación detrás de un check de permiso.
- Detectar denegación permanente vía `shouldShowRequestPermissionRationale` y mostrar diálogo/toast con intent hacia los Settings de la app.
- Reusar el `ActivityResultLauncher` ya registrado (`requestCameraPermission`, líneas 71-78 de `MainActivity`).
- Tests unitarios JUnit 4 para la lógica de decisión (estado de permiso → acción) extrayéndola a una función pura testeable.

### Out of scope
- Cambios en `PartyActivity` (su check existente queda como fallback intencional).
- Cambios en `MapSelectorActivity`, `SelfieHunterActivity` o `SplashActivity`.
- Modificar `AndroidManifest.xml` (el permiso `CAMERA` ya está declarado).
- Permisos distintos a cámara (storage, ubicación, micrófono).
- UI de onboarding/educación previa al diálogo de sistema.
- Lógica MDM o auto-aprobación en kiosko Temi (untestable, queda fuera).

---

## 3. Approach

### Decisión arquitectónica
**Doble gate**: una solicitud temprana al arrancar `MainActivity` (proactiva, mejora UX en primer uso) + un gate sincrónico en el click de `tileFotos` (correctness, garantiza que nadie llegue a `MapSelectorActivity` sin permiso). `PartyActivity` mantiene su check actual como tercera línea de defensa.

### Por qué esta opción (C) y no las alternativas
- **Solo en `MainActivity.onCreate`**: insuficiente — si el usuario deniega y luego toca el tile, navegaría sin permiso.
- **Solo en el click del tile**: cumple correctness pero no resuelve la UX del primer arranque (igual de tardío que hoy, solo movido un nivel).
- **Mover el check a `MapSelectorActivity`**: viola la regla "legacy `com.spatium.temibridge.ui.*` es extend-only" de la peor manera posible (agregaría lógica nueva a una activity legacy distinta). Además dispersa responsabilidad.
- **Doble gate (elegida)**: temprana para UX, en el click para correctness, `PartyActivity` como backstop. Cada capa tiene una razón concreta.

### Manejo de denegación permanente
Tras un resultado denegado del launcher, evaluar:
- Si `shouldShowRequestPermissionRationale(CAMERA)` es `true` → el usuario denegó una vez pero puede reintentarse; mostrar rationale corta y reintentar al próximo click.
- Si es `false` y el permiso no está concedido → denegación permanente o políticas MDM. Mostrar diálogo con botón "Abrir ajustes" que dispare un `Intent(ACTION_APPLICATION_DETAILS_SETTINGS)`.

### Testabilidad (Strict TDD)
La decisión "dado el estado del permiso, ¿qué hago?" se extrae a una función pura (por ejemplo `CameraPermissionGate.decide(granted, shouldShowRationale): Decision`) que retorna un sealed class `Decision { Proceed, RequestPermission, ShowRationale, OpenSettings }`. Esto se prueba con JUnit 4 sin dependencias de Android. La invocación real de los efectos (launcher, intent, diálogo) queda en `MainActivity` y es untested-by-design (capa de framework).

### Archivos afectados
- `app/src/main/java/com/spatium/temibridge/ui/MainActivity.kt` — modificar `onCreate` y click handler de `tileFotos`. Permitido por la regla "MainActivity is in this package — modify it directly".
- **Nuevo**: `app/src/main/java/com/spatium/deamon/db/temi/core/CameraPermissionGate.kt` — lógica pura de decisión (paquete nuevo, respeta convención).
- **Nuevo**: `app/src/test/java/com/spatium/deamon/db/temi/core/CameraPermissionGateTest.kt` — tests JUnit 4.
- `app/src/main/java/com/spatium/deamon/db/temi/ui/PartyActivity.kt` — sin cambios (backstop).
- `app/src/main/AndroidManifest.xml` — sin cambios (CAMERA ya declarado).

---

## 4. Risks & Open Questions

1. **Kiosko Temi + MDM**: el dispositivo puede auto-aprobar o auto-denegar permisos vía política. La rama "denegación permanente" puede dispararse en producción aunque nunca pasemos por el diálogo nativo. El intent a Settings puede estar bloqueado en kiosko. Mitigación: probar en hardware real antes de release; el toast/diálogo debe ser informativo aunque Settings no abra.
2. **Re-solicitar en cada `onCreate`**: si el usuario rota la pantalla o vuelve a `MainActivity` desde otra activity, no queremos spammear el diálogo. La llamada al launcher al arranque debe ser condicional: solo si `checkSelfPermission != GRANTED`.
3. **Race con la inicialización de managers**: `MainActivity.onCreate` ya inicializa `AnnouncementManager`, `RatingManager`, `TemiController`. El permiso de cámara no debe bloquear esa inicialización (la cámara no se usa hasta `PartyActivity`). Disparar la solicitud asíncronamente, no esperar resultado.

---

## 5. Next Phase Inputs

- `sdd-spec`: definir contratos de `CameraPermissionGate.decide` y casos de UX (rationale, settings).
- `sdd-design`: decidir API exacta del sealed class `Decision`, dónde vive el diálogo de "abrir ajustes" (helper en `MainActivity` vs. extensión), y cómo se compone con el launcher existente.
