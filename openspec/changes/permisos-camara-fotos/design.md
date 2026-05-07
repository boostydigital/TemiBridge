# Design — permisos-camara-fotos

## 1. Architecture Decision: extract `CameraPermissionGate` as a pure class

### Context
The current camera permission check fires inside `PartyActivity.onCreate`, two activity transitions after the user taps the Fotos tile. By the time the system dialog appears, the user has already crossed `MainActivity → MapSelectorActivity → PartyActivity`. If the user denies, the back stack is broken and the flow is unrecoverable without manual navigation.

We need to gate the flow earlier (at `MainActivity`) AND the decision logic must be testable without an Android device.

### Decision
Extract the **decision** (what should the app do given a permission state) into a pure Kotlin class `CameraPermissionGate` under `com.spatium.deamon.db.temi.core`. The **effects** (request the permission, show the dialog, launch the intent) stay in `MainActivity`.

```
+-----------------------------+      +------------------------------+
|  MainActivity (effects)     | ---> |  CameraPermissionGate         |
|  - reads checkSelfPermission|      |  decide(granted, rationale)   |
|  - calls launcher.launch()  | <--- |  -> Decision (sealed)         |
|  - shows AlertDialog        |      |  PURE — no Android imports    |
|  - startActivity            |      +------------------------------+
+-----------------------------+
```

### Rationale
- **Testability**: the gate is pure — JUnit 4 tests run on the JVM with no Robolectric / no Android context. Strict TDD applies cleanly.
- **Single source of truth**: both entry points (`onCreate` and `tileFotos` click) feed into the same `decide()` function. No duplicated `if (granted) ... else if (rationale) ... else ...` ladder in two places.
- **Effects isolated**: `MainActivity` only translates `Decision` variants into Android API calls. Easy to read, hard to break.

### Rejected Alternatives
- **Inline both checks in `MainActivity`**: duplicates the rationale/permanent-denial logic, and the branching is exactly the kind of thing a unit test catches and an emulator misses.
- **Move all to a `PermissionsManager` singleton**: overkill for one permission. Adds lifecycle ownership concerns we don't need.
- **Coroutine/Flow-based gate**: the Activity Result API is callback-based; wrapping it in a flow adds ceremony without value here.

---

## 2. Sequence Diagram — Permission Flow

### A. App start (MainActivity.onCreate)

```
User              MainActivity                CameraPermissionGate         System
 |                     |                              |                       |
 | launches app        |                              |                       |
 |-------------------->|                              |                       |
 |                     | onCreate()                   |                       |
 |                     | checkSelfPermission(CAMERA)  |                       |
 |                     |--------------------------------------------------->  |
 |                     |<--- granted? ----------------------------------------|
 |                     |                              |                       |
 |                     | if NOT granted:              |                       |
 |                     | shouldShowRationale()        |                       |
 |                     |--------------------------------------------------->  |
 |                     |<--- bool --------------------------------------------|
 |                     | gate.decide(granted=false, rationale=?)              |
 |                     |----------------------------->|                       |
 |                     |<--- Decision.Request --------|                       |
 |                     | requestCameraPermission.launch(CAMERA)               |
 |                     |--------------------------------------------------->  |
 |                     |                              |  system dialog        |
 |                     |<--- callback(granted) -------------------------------|
 |                     | (no pendingFotosLaunch — just record state)          |
```

If permission is already granted on `onCreate`, `decide()` returns `Allow` and nothing happens — silent no-op.

### B. tileFotos tap

```
User              MainActivity                CameraPermissionGate         System
 |                     |                              |                       |
 | tap Fotos           |                              |                       |
 |-------------------->|                              |                       |
 |                     | tileFotos.onClick            |                       |
 |                     | checkSelfPermission()        |                       |
 |                     |--------------------------------------------------->  |
 |                     |<--- granted? ----------------------------------------|
 |                     | shouldShowRationale()        |                       |
 |                     |--------------------------------------------------->  |
 |                     |<--- bool --------------------------------------------|
 |                     | gate.decide(...)             |                       |
 |                     |----------------------------->|                       |
 |                     |<--- Decision.X --------------|                       |
 |                     |                              |                       |
 |                     | switch on Decision:          |                       |
 |                     |  Allow      -> launch MapSelectorActivity            |
 |                     |  Request    -> pendingFotosLaunch=true; launcher()   |
 |                     |  ShowSettings -> AlertDialog -> Settings intent      |
 |                     |  Deny       -> Toast (defensive, not expected here)  |
```

### C. Permission result callback (after Request from tile)

```
System            MainActivity
 |                     |
 | callback(granted)   |
 |-------------------->|
 |                     | if pendingFotosLaunch:
 |                     |   pendingFotosLaunch = false
 |                     |   if granted: startActivity(MapSelectorActivity)
 |                     |   else: Toast "Se requiere cámara para Fotos"
 |                     | else (start-of-app request):
 |                     |   startContinuousScanning() if granted (existing behavior)
```

---

## 3. `CameraPermissionGate` API

**Package**: `com.spatium.deamon.db.temi.core`
**File**: `CameraPermissionGate.kt`

```kotlin
package com.spatium.deamon.db.temi.core

object CameraPermissionGate {

    sealed class Decision {
        /** Permission is granted — caller may proceed with the camera flow. */
        object Allow : Decision()

        /** Permission not granted but requestable — caller should launch the system request. */
        object Request : Decision()

        /** User permanently denied (don't-ask-again) — caller should show settings dialog. */
        object ShowSettings : Decision()

        /** Defensive terminal state — caller should abort and inform the user. */
        object Deny : Decision()
    }

    /**
     * Pure decision function. No Android imports.
     *
     * @param granted result of ContextCompat.checkSelfPermission == GRANTED
     * @param shouldShowRationale result of ActivityCompat.shouldShowRequestPermissionRationale
     * @param previouslyAsked whether the app has requested CAMERA at least once in this install
     *                       (caller tracks via SharedPreferences or in-memory flag)
     */
    fun decide(
        granted: Boolean,
        shouldShowRationale: Boolean,
        previouslyAsked: Boolean
    ): Decision = when {
        granted -> Decision.Allow
        shouldShowRationale -> Decision.Request          // user denied once, still askable
        !previouslyAsked -> Decision.Request             // first time — system will show dialog
        else -> Decision.ShowSettings                    // permanent denial path
    }
}
```

### Decision matrix

| granted | shouldShowRationale | previouslyAsked | Decision      |
|---------|---------------------|-----------------|---------------|
| true    | (any)               | (any)           | Allow         |
| false   | true                | (any)           | Request       |
| false   | false               | false           | Request       |
| false   | false               | true            | ShowSettings  |

The `previouslyAsked` flag disambiguates the two false-rationale cases that Android conflates: "never asked yet" vs "asked and don't-ask-again". Stored in `SharedPreferences("camera_perm", MODE_PRIVATE)` under key `asked_once`.

---

## 4. `MainActivity` Changes — exact hooks

### 4.1 New private state
```kotlin
private var pendingFotosLaunch: Boolean = false
private val cameraPrefs by lazy { getSharedPreferences("camera_perm", MODE_PRIVATE) }
```

### 4.2 `requestCameraPermission` launcher (modify lines 71-78)
The existing launcher must persist `asked_once = true` and dispatch to either the QR scanner path (existing) or the Fotos path (new). Pseudocode:

```
on result(granted):
    cameraPrefs.edit().putBoolean("asked_once", true).apply()
    if pendingFotosLaunch:
        pendingFotosLaunch = false
        if granted: launchMapSelector()
        else: Toast("Se requiere cámara para Fotos.")
    else:
        if granted: startContinuousScanning()
        else: Toast("Se requiere cámara para escanear QR codes.")
```

### 4.3 `onCreate` hook
After `setupTiles()` and `setupBottomNav()`, call a new private `checkCameraOnStart()`:

```
checkCameraOnStart():
    granted = ContextCompat.checkSelfPermission(this, CAMERA) == GRANTED
    if granted: return                          // silent no-op, no spam on rotate
    rationale = ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA)
    askedOnce = cameraPrefs.getBoolean("asked_once", false)
    when (CameraPermissionGate.decide(false, rationale, askedOnce)):
        Request      -> requestCameraPermission.launch(CAMERA)   // pendingFotosLaunch stays false
        ShowSettings -> /* do nothing on app start; we don't nag */
        Deny / Allow -> /* unreachable here */
```

Rationale for not showing Settings dialog on `onCreate`: app start is not a user-initiated camera action. Nagging on every cold start is hostile. The dialog only appears when the user explicitly taps Fotos.

### 4.4 `tileFotos` click handler (modify lines 254-263)
Replace the direct `startActivity(MapSelectorActivity)` with a gated version:

```
tileFotos.onClick:
    showTileAnimation(it)
    granted = checkSelfPermission(CAMERA) == GRANTED
    rationale = shouldShowRequestPermissionRationale(CAMERA)
    askedOnce = cameraPrefs.getBoolean("asked_once", false)
    when (CameraPermissionGate.decide(granted, rationale, askedOnce)):
        Allow        -> launchMapSelector()
        Request      -> pendingFotosLaunch = true
                        requestCameraPermission.launch(CAMERA)
        ShowSettings -> showCameraSettingsDialog()
        Deny         -> Toast("No se puede acceder a la cámara.")
```

### 4.5 New private helpers
```
launchMapSelector():
    TemiController.disableFaceTracking()
    Intent(this, MapSelectorActivity::class.java).apply {
        addFlags(FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP)
    }.also { startActivity(it) }
    overridePendingTransition(slide_in_right, slide_out_left)

showCameraSettingsDialog():
    AlertDialog.Builder(this)
        .setTitle("Permiso de cámara requerido")
        .setMessage("Para tomar fotos, habilitá el permiso de cámara en Ajustes.")
        .setPositiveButton("Abrir Ajustes") { _, _ ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }.also { startActivity(it) }
        }
        .setNegativeButton("Cancelar", null)
        .show()
```

---

## 5. State Tracking — `pendingFotosLaunch`

The Activity Result callback fires asynchronously and has no idea WHO triggered the request. Two callers exist:
- `checkCameraOnStart()` — wants `startContinuousScanning()` on grant
- `tileFotos.onClick` — wants `launchMapSelector()` on grant

A single `Boolean` flag (`pendingFotosLaunch`) is set to `true` only when the tile triggers the request. The callback reads-and-clears it. This is sufficient because:
- The user can only have one pending request at a time (the system dialog is modal).
- If process death intervenes, the flag is lost — but so is the user's tap intent. On return, the user is in `MainActivity` with permission either granted (re-tap works) or denied (re-tap goes through gate again). Acceptable.
- No need for `SavedStateHandle` persistence — the flag is purely transient.

---

## 6. `shouldShowRequestPermissionRationale` Handling

| Returns | Meaning                                                       | Gate output                                |
|---------|---------------------------------------------------------------|--------------------------------------------|
| `true`  | User denied once but did not check "don't ask again"          | `Request` — system will show dialog again  |
| `false` AND `previouslyAsked == false` | First-ever request, never asked      | `Request` — system will show dialog        |
| `false` AND `previouslyAsked == true`  | User checked "don't ask again" OR policy-denied | `ShowSettings` — only Settings can fix it |

The Android API does not distinguish "first time" from "permanent denial" via `shouldShowRequestPermissionRationale` alone — both return `false`. The `previouslyAsked` flag (persisted in `SharedPreferences` after the first `launch`) closes that ambiguity.

---

## 7. Non-Goals

This change explicitly does NOT touch:
- **`MapSelectorActivity`** — no permission logic added; assumes upstream gate has cleared the path.
- **`SelfieHunterActivity`** — same as above.
- **`PartyActivity.checkCameraPermission()`** — left as a defensive backstop. If somehow the user reaches Party without camera (e.g., revoked from notifications mid-flow), the existing check still fires.
- **Storage / `READ_MEDIA_IMAGES` / `WRITE_EXTERNAL_STORAGE`** permissions — out of scope.
- **Microphone, location, Bluetooth** permissions — unchanged.
- **`AndroidManifest.xml`** — `CAMERA` is already declared, no edits needed.
- **QR scanning flow** — `startContinuousScanning()` continues to be called from the launcher result on grant, preserving existing behavior.

---

## 8. Test Plan (informs sdd-tasks)

`CameraPermissionGateTest.kt` covers the full decision matrix:
- `Allow` when granted regardless of other flags
- `Request` when not granted + rationale true
- `Request` when not granted + rationale false + not previously asked
- `ShowSettings` when not granted + rationale false + previously asked

No Android instrumentation. Pure JVM JUnit 4. Run via `./gradlew test`.

`MainActivity` is not unit-tested in this change — its job is reduced to translating `Decision` variants into Android API calls, which is verified by manual smoke testing on the Temi device.
