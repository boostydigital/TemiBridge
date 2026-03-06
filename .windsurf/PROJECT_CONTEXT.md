# Windsurf Project Context: Deamon DB TEMI

## Project Overview

**Deamon DB TEMI** (`com.spatium.deamon.db.temi`) is an Android application for Temi robots that provides:
- A bridge/control layer for robot operations via Intents and deep links
- Background processing of orders from Supabase database
- QR code scanning for escort/greeting workflows
- Command queue system for sequential robot actions

**Package:** `com.spatium.deamon.db.temi`
**Min SDK:** 26 (Android 8.0)
**Target SDK:** 36
**Language:** Kotlin
**Architecture:** Component-based with singletons and coroutines

---

## Core Architecture Patterns

### 1. **Singleton Pattern**
All core controllers are implemented as Kotlin `object` singletons:
- `TemiController` - Robot SDK integration
- `CommandQueue` - Command execution queue
- `RobotPedidosOrchestrator` - Order orchestration
- `GoogleTTS` - Text-to-speech service
- `SupabaseClientProvider` - Database client
- `RobotPedidosWorker` - Background worker
- `SkillRegistry` / `SkillManager` - Skills system

### 2. **Sealed Classes for Commands**
```kotlin
sealed class Command {
    data class OpenApp(val context: Context) : Command()
    data class Say(val text: String) : Command()
    data class Web(val context: Context, val url: String?, val place: String? = null) : Command()
    data class Sequence(val sequenceId: String) : Command()
}
```

### 3. **Coroutines for Async Operations**
- Main coroutine scope: `CoroutineScope(Dispatchers.IO + SupervisorJob())`
- UI operations: `withContext(Dispatchers.Main)`
- Background polling in `RobotPedidosWorker`

### 4. **Reflection-Based SDK Integration**
Temi SDK is integrated via Java reflection to avoid direct compile-time dependencies.

---

## Component Overview

### Core Components

| Component | Responsibility | Key Methods |
|-----------|----------------|-------------|
| `TemiController` | Robot SDK wrapper via reflection | `speak()`, `goTo()`, `playSequenceById()`, `playTourByName()`, `setArrivalCallbackOnce()` |
| `CommandQueue` | Sequential command execution with delays | `enqueuePedidoAndWait()`, `enqueue()`, `clear()` |
| `RobotPedidosOrchestrator` | Order execution orchestration | `executePedidoAndWait()`, `executePedido()` |
| `GoogleTTS` | Google Cloud TTS integration | `speak()`, `stop()`, `release()` |
| `SupabaseClientProvider` | Supabase client singleton | `getClient()` |
| `RobotPedidosWorker` | Background polling for orders | `start()`, `stop()` |

### UI Components

| Component | Purpose | Theme |
|-----------|---------|-------|
| `MainActivity` | QR scanning, tour control, sequence execution | `Theme.TemiBridge` |
| `IntentEntryActivity` | Headless intent/deep link receiver | `Theme.Transparent` |
| `KioskWebActivity` | WebView for web content | `Theme.TemiBridge` |
| `PedidosActivity` | Order display interface | `Theme.TemiBridge` (landscape) |
| `SplashActivity` | App launcher | `Theme.TemiBridge` |

### Skills System

```kotlin
interface TemiSkill {
    val skillId: String
    val skillName: String
    val description: String
    suspend fun execute(context: Context, params: Map<String, Any>): SkillResult
    suspend fun canExecute(context: Context): Boolean
    fun getRequiredPermissions(): List<String>
}

abstract class BaseTemiSkill(...) : TemiSkill {
    protected abstract suspend fun executeSkill(...)
}
```

- `SkillRegistry` - Central registration of available skills
- `SkillManager` - Sequential skill execution with queue

---

## Temi SDK Integration (Reflection Pattern)

**Critical:** The Temi SDK is NOT directly imported. All SDK access uses Java reflection.

### Robot Instance
```kotlin
private fun robotInstance(): Any? = try {
    val cls = Class.forName("com.robotemi.sdk.Robot")
    val method = cls.getMethod("getInstance")
    method.invoke(null)
} catch (t: Throwable) {
    null
}
```

### Speech Example
```kotlin
fun speak(text: String) {
    val robot = robotInstance() ?: return
    val req = ttsRequest(text) ?: return
    try {
        val speak = robot.javaClass.getMethod("speak", req.javaClass)
        speak.invoke(robot, req)
    } catch (t: Throwable) {
        Log.w(TAG, "speak fallo: ${t.message}")
    }
}
```

### Listener Pattern with Proxies
```kotlin
goToListenerProxy = Proxy.newProxyInstance(
    listenerCls.classLoader,
    arrayOf(listenerCls),
    InvocationHandler { _, method, args ->
        // Handle callback
        null
    }
)
```

---

## Data Models

### RobotPedido (Supabase)
```kotlin
@Serializable
data class RobotPedido(
    val id: Long,
    val secuencia: String?,
    val comida: String?,
    val say: String?,
    @SerialName("orden_action")
    val ordenAction: String?,
    val place: String? = null
)
```

### SkillResult
```kotlin
sealed class SkillResult {
    object Success : SkillResult()
    data class Error(val message: String, val cause: Throwable? = null) : SkillResult()
}
```

---

## Deep Link Scheme

**Scheme:** `mytemi://`

| Host | Parameters | Action |
|------|------------|--------|
| `go` | `place`, `recepcion`, `telefono` | Navigate to waypoint |
| `say` | `text`, `recepcion`, `telefono` | Speak text |
| `tour` | `name`, `tourId` | Start tour |
| `welcome` | `text`, `place`, `recepcion` | Speak + navigate |
| `sequence` | `id`, `name`, `text` | Execute sequence |
| `escort` | `greeting`, `place`, `arrivalGreeting`, `waitTime`, `returnTo` | Escort flow |

---

## Intent Actions (Explicit)

| Action | Extra Parameters | Purpose |
|--------|-----------------|---------|
| `com.spatium.temibridge.ACTION_GO_TO` | `place` | Navigate |
| `com.spatium.temibridge.ACTION_SAY` | `text` | Speak |
| `com.spatium.temibridge.ACTION_TOUR_START` | `name`, `tourId` | Start tour |
| `com.spatium.temibridge.ACTION_HEAD_TILT` | `angle` (int) | Tilt head |
| `com.spatium.temibridge.ACTION_VOLUME` | `level` (0-10) | Set volume |

---

## Coding Conventions

### Naming
- **Packages:** `com.spatium.deamon.db.temi.{feature}`
- **Activities:** `{Name}Activity`
- **Core objects:** `PascalCase` (e.g., `TemiController`)
- **Private properties:** `camelCase`
- **Constants:** `UPPER_SNAKE_CASE`
- **Tags:** Use class name or component name for logging

### Logging
```kotlin
private const val TAG = "ComponentName"
Log.d(TAG, "Message with context")
Log.w(TAG, "Warning: ${issue}")
Log.e(TAG, "Error: ${error.message}", exception)
```

### Error Handling
```kotlin
try {
    // Operation
} catch (t: Throwable) {
    Log.w(TAG, "Operation failed: ${t.message}")
    // Fallback
}
```

### Coroutines
```kotlin
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

scope.launch {
    withContext(Dispatchers.Main) {
        // UI work
    }
}
```

---

## Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Temi SDK | 1.136.0 | Robot control (via reflection) |
| ML Kit Barcode | 17.3.0 | QR code scanning |
| CameraX | 1.3.1 | Camera management |
| Supabase BOM | 2.4.2 | Database/Realtime |
| Ktor Client | 2.3.12 | HTTP for Supabase |
| OkHttp | 4.12.0 | HTTP client |
| Coil | 2.6.0 | Image loading |
| Lottie | 6.4.0 | Animations |
| Coroutines | 1.8.1 | Async operations |

---

## Common Patterns

### 1. **Delayed Sequential Execution**
```kotlin
Handler(Looper.getMainLooper()).postDelayed({
    // Action
}, delayMs)
```

### 2. **URL Parameter Decoding**
```kotlin
private fun decodeParam(raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    var prev: String = raw
    var curr: String
    repeat(3) { // Handle double encoding
        curr = try {
            URLDecoder.decode(prev, StandardCharsets.UTF_8.name())
        } catch (_: Throwable) {
            prev
        }
        if (curr == prev) return curr
        prev = curr
    }
    return prev
}
```

### 3. **Atomic Operations with Flags**
```kotlin
private val isProcessing = AtomicBoolean(false)

if (!isProcessing.compareAndSet(false, true)) {
    return // Already processing
}
try {
    // Work
} finally {
    isProcessing.set(false)
}
```

### 4. **Permission Checking**
```kotlin
if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
    // Granted
} else {
    requestPermissionLauncher.launch(permission)
}
```

---

## Anti-Patterns to Avoid

### ❌ DON'T: Direct Temi SDK imports
```kotlin
import com.robotemi.sdk.Robot // ❌ NO - Use reflection
```

### ✅ DO: Use reflection via TemiController
```kotlin
TemiController.speak("Hello") // ✅ YES
```

### ❌ DON'T: Hardcode API keys
```kotlin
const val API_KEY = "abc123" // ❌ NO
```

### ✅ DO: Use BuildConfig
```kotlin
BuildConfig.SUPABASE_URL // ✅ YES
```

### ❌ DON'T: Block main thread
```kotlin
val result = slowNetworkCall() // ❌ NO
```

### ✅ DO: Use coroutines
```kotlin
scope.launch {
    val result = withContext(Dispatchers.IO) { slowNetworkCall() }
    // Use result
}
```

### ❌ DON'T: Forget to clear callbacks
```kotlin
TemiController.setArrivalCallbackOnce { /*...*/ } // Auto-clears
```

### ❌ DON'T: Launch activities without flags
```kotlin
startActivity(intent) // ❌ May create duplicates
```

### ✅ DO: Use appropriate flags
```kotlin
intent.addFlags(
    Intent.FLAG_ACTIVITY_NEW_TASK or
    Intent.FLAG_ACTIVITY_CLEAR_TOP or
    Intent.FLAG_ACTIVITY_SINGLE_TOP
)
```

---

## Testing Approach

### Current State
- Minimal test coverage
- Example test files present but not extensively used
- Manual testing via ADB and robot deployment

### Recommended Testing Strategy
1. **Unit Tests:** Core logic in `TemiController`, `CommandQueue`, data models
2. **Integration Tests:** Supabase sync, command queue processing
3. **UI Tests:** Activity launching, intent handling
4. **Manual Tests:** ADB commands for deep links and intents

### ADB Testing Examples
```bash
# Say
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity \
  -a com.spatium.temibridge.ACTION_SAY --es text "Hello"

# Deep link
adb shell am start -n com.spatium.deamon.db.temi/.ui.IntentEntryActivity \
  -a android.intent.action.VIEW -d "mytemi://go?place=Open_Space"
```

---

## Build Configuration

### Build Variants
- `debug`: Debuggable, logs enabled
- `release`: Minified (ProGuard ready), production

### Feature Flags
```kotlin
BuildConfig.ENABLE_SUPABASE_WORKER // Enable/disable worker
BuildConfig.USE_FAKE_ROBOT // Mock robot for testing
```

### Build Directory
**Note:** Build directory is moved outside OneDrive:
```
buildDir = File(System.getProperty("user.home"), "TemiDeamonDBBuild/app")
```

---

## Important Notes for AI Assistants

1. **Always use `TemiController`** for robot operations - never try to import Temi SDK directly
2. **Use `CommandQueue`** for sequential commands - it handles delays and order
3. **Supabase operations** must use `@SerialName` for field mappings
4. **Deep link parsing** requires handling double-encoded URLs
5. **Camera/QR** uses CameraX with ML Kit - requires camera permission
6. **Background work** uses coroutines with proper dispatchers
7. **Singleton objects** are used throughout - access directly without instantiation
8. **Activities should use appropriate flags** to avoid duplicate instances
9. **Always decode parameters** from deep links (may be double-encoded)
10. **Use `applicationContext`** for long-lived references, not activity context

---

## Package Structure

```
com.spatium.deamon.db.temi/
├── TemiDaemonApplication.kt
├── core/
│   ├── TemiController.kt
│   ├── CommandQueue.kt
│   ├── RobotPedidosOrchestrator.kt
│   ├── GoogleTTS.kt
│   ├── SupabaseClientProvider.kt
│   ├── RobotPedidosWorker.kt
│   └── RobotPedidosForegroundService.kt
├── ui/
│   ├── MainActivity.kt
│   ├── IntentEntryActivity.kt
│   ├── KioskWebActivity.kt
│   ├── PedidosActivity.kt
│   └── SplashActivity.kt
└── skills/
    ├── base/
    │   ├── TemiSkill.kt
    │   ├── BaseTemiSkill.kt
    │   └── SkillResult.kt
    ├── impl/
    │   ├── NavigationSkill.kt
    │   └── SpeechSkill.kt
    ├── manager/
    │   └── SkillManager.kt
    └── registry/
        ├── SkillRegistry.kt
        └── SkillConfiguration.kt
```

---

## Common Operations

### Navigate to Location
```kotlin
TemiController.goTo("location_name")
```

### Speak Text
```kotlin
TemiController.speak("Hello") // Native TTS
GoogleTTS.speak(context, "Hello") // Google Cloud TTS
```

### Execute Sequence
```kotlin
TemiController.playSequenceById("sequence_id")
TemiController.playSequenceByName("Sequence Name")
```

### Start Tour
```kotlin
TemiController.playTourById("tour_id")
TemiController.startDefaultNlu("identifier")
```

### Check Permission
```kotlin
if (TemiController.isSequencePermissionGranted()) {
    // Execute sequence
} else {
    TemiController.requestSequencePermission(activity)
}
```

---

## Configuration Files

### local.properties (Not in Git)
```
SUPABASE_URL=https://project.supabase.co
SUPABASE_ANON_KEY=your_anon_key
GOOGLE_TTS_API_KEY=your_tts_api_key
TOUR_RECEPCION_ID=reception_tour_id
```

### gradle.properties
```
org.gradle.jvmargs=-Xmx2048m
```

---

## Last Updated
2025-03-05 - Initial project context documentation for Windsurf AI assistance
