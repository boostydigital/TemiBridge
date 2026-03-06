# Comprehensive Prompt: Generate Temi Robot Skills Implementation

You are an expert Android/Kotlin developer specializing in robot automation and the Temi SDK. Your task is to implement a complete skills system for the "TemiBridge" Android application (package: `com.spatium.deamon.db.temi`) that enables a Temi robot to execute complex, multi-stage workflows.

## Project Context

**Project Overview:**
- **App Name:** Deamon DB TEMI (formerly TemiBridge)
- **Package:** `com.spatium.deamon.db.temi`
- **Location:** `C:\Users\samir\OneDrive\Documents\Spatium Group\Temi Deamon DB`
- **Temi SDK Version:** 1.136.0
- **Primary Languages:** Kotlin, Java (for Temi SDK integration via reflection)

**Existing Architecture:**

1. **Core Components:**
   - `TemiController.kt` - Singleton wrapper around Temi SDK using reflection for SDK independence
   - `CommandQueue.kt` - Thread-safe command queue with sequential execution and configurable delays
   - `RobotPedidosOrchestrator.kt` - High-level orchestrator for robot orders
   - `RobotPedidosWorker.kt` - Background worker that polls Supabase for pending orders
   - `GoogleTTS.kt` - Google Cloud Text-to-Speech integration with fallback to Temi TTS
   - `SupabaseClientProvider.kt` - Supabase client singleton for database operations

2. **Data Model:**
   ```kotlin
   @Serializable
   data class RobotPedido(
       val id: Long,
       val secuencia: String?,      // Temi sequence ID to execute
       val comida: String?,          // URL or "comida" for orders UI
       val say: String?,             // Text to speak
       val ordenAction: String?,     // Execution order (comma-separated: "say,comida,secuencia")
       val place: String? = null     // Location name
   )
   ```

3. **Command System:**
   - Sealed class `Command` with types: `OpenApp`, `Say`, `Web`, `Sequence`
   - Sequential execution with delays between commands
   - Retry logic for web operations
   - CountDownLatch-based synchronization for wait operations

4. **UI Activities:**
   - `MainActivity.kt` - QR code scanner with ML Kit, handles deep links
   - `PedidosActivity.kt` - Product ordering interface with Lottie animations
   - `KioskWebActivity.kt` - WebView for displaying web content

## Technical Requirements

### 1. Architecture Patterns to Follow

**Dependency Injection:**
- Use singleton objects for core services (already implemented)
- Lazy initialization where appropriate
- Thread-safe operations with `@Volatile` and synchronization

**Async Operations:**
- Coroutines with `CoroutineScope(Dispatchers.IO + SupervisorJob())`
- Proper context switching between IO and Main threads
- Timeout handling for network operations

**Error Handling:**
- Try-catch blocks with detailed logging using `Log.d()`, `Log.w()`, `Log.e()`
- Fallback mechanisms (e.g., Google TTS -> Temi TTS)
- User-friendly error messages via Toast

**Reflection-Based Temi Integration:**
- The existing `TemiController` uses Java reflection to avoid direct SDK dependencies
- Follow this pattern for any new Temi SDK integrations
- Example method structure:
  ```kotlin
  private fun robotInstance(): Any? = try {
      val cls = Class.forName("com.robotemi.sdk.Robot")
      val method = cls.getMethod("getInstance")
      method.invoke(null)
  } catch (t: Throwable) {
      Log.w(TAG, "Robot SDK no disponible: ${t.message}")
      null
  }
  ```

### 2. Skills System Specification

Implement a modular skills system with the following components:

#### 2.1 Skill Interface and Base Classes

```kotlin
/**
 * Base interface for all robot skills
 */
interface TemiSkill {
    val skillId: String
    val skillName: String
    val description: String

    suspend fun execute(context: Context, params: Map<String, Any>): SkillResult
    suspend fun canExecute(context: Context): Boolean
    fun getRequiredPermissions(): List<String>
}

/**
 * Result of skill execution
 */
sealed class SkillResult {
    object Success : SkillResult()
    data class PartialSuccess(val message: String) : SkillResult()
    data class Error(val message: String, val exception: Throwable? = null) : SkillResult()
}

/**
 * Abstract base class for skills with common functionality
 */
abstract class BaseTemiSkill(
    override val skillId: String,
    override val skillName: String,
    override val description: String
) : TemiSkill {

    protected abstract suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult

    override suspend fun execute(context: Context, params: Map<String, Any>): SkillResult {
        return try {
            if (!canExecute(context)) {
                return SkillResult.Error("Skill cannot be executed: prerequisites not met")
            }
            executeSkill(context, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing skill $skillId", e)
            SkillResult.Error("Execution failed: ${e.message}", e)
        }
    }

    companion object {
        protected val TAG = "TemiSkill"
    }
}
```

#### 2.2 Skill Registry and Manager

```kotlin
/**
 * Central registry for all available skills
 */
object SkillRegistry {
    private val skills = mutableMapOf<String, TemiSkill>()

    fun register(skill: TemiSkill) {
        skills[skill.skillId] = skill
        Log.d(TAG, "Registered skill: ${skill.skillId} - ${skill.skillName}")
    }

    fun getSkill(skillId: String): TemiSkill? = skills[skillId]

    fun getAllSkills(): List<TemiSkill> = skills.values.toList()

    fun getSkillsByPermission(permission: String): List<TemiSkill> {
        return skills.values.filter { permission in it.getRequiredPermissions() }
    }

    companion object {
        private const val TAG = "SkillRegistry"
    }
}

/**
 * Manager for executing skills with proper lifecycle and error handling
 */
class SkillManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val executionQueue = ConcurrentLinkedQueue<SkillExecution>()

    data class SkillExecution(
        val skillId: String,
        val params: Map<String, Any>,
        val callback: (SkillResult) -> Unit
    )

    fun executeSkill(skillId: String, params: Map<String, Any>, callback: (SkillResult) -> Unit) {
        val execution = SkillExecution(skillId, params, callback)
        executionQueue.offer(execution)
        processNext()
    }

    private fun processNext() {
        // Implementation similar to CommandQueue.processNext()
        // Execute skills sequentially with proper error handling
    }

    fun cancelAll() {
        executionQueue.clear()
    }

    fun shutdown() {
        scope.cancel()
    }
}
```

#### 2.3 Core Skill Implementations

Implement the following essential skills:

**A. Navigation Skill**
```kotlin
/**
 * Skill for navigating to predefined locations
 */
class NavigationSkill : BaseTemiSkill(
    skillId = "navigation",
    skillName = "Navigation",
    description = "Navigate to predefined waypoints on the map"
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val place = params["place"] as? String
            ?: return SkillResult.Error("Missing required parameter: place")

        val onArrivalCallback = params["onArrival"] as? (() -> Unit)

        return withContext(Dispatchers.Main) {
            if (onArrivalCallback != null) {
                TemiController.setArrivalCallbackOnce(onArrivalCallback)
            }

            TemiController.goTo(place)
            SkillResult.Success
        }
    }

    override suspend fun canExecute(context: Context): Boolean {
        // Check if location exists and is accessible
        return true
    }

    override fun getRequiredPermissions(): List<String> = emptyList()
}
```

**B. Speech Skill**
```kotlin
/**
 * Skill for text-to-speech with voice selection
 */
class SpeechSkill : BaseTemiSkill(
    skillId = "speech",
    skillName = "Speech",
    description = "Convert text to speech using Google TTS or Temi TTS fallback"
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val text = params["text"] as? String
            ?: return SkillResult.Error("Missing required parameter: text")

        val useGoogleTTS = params["useGoogleTTS"] as? Boolean ?: true
        val onComplete = params["onComplete"] as? (() -> Unit)

        return withContext(Dispatchers.Main) {
            if (useGoogleTTS) {
                GoogleTTS.speak(context, text, onComplete)
            } else {
                TemiController.speak(text)
                onComplete?.invoke()
            }
            SkillResult.Success
        }
    }

    override suspend fun canExecute(context: Context): Boolean {
        return BuildConfig.GOOGLE_TTS_API_KEY.isNotEmpty()
    }

    override fun getRequiredPermissions(): List<String> = emptyList()
}
```

**C. Sequence Execution Skill**
```kotlin
/**
 * Skill for executing predefined Temi sequences
 */
class SequenceSkill : BaseTemiSkill(
    skillId = "sequence",
    skillName = "Sequence",
    description = "Execute predefined Temi sequences with parameters"
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val sequenceId = params["sequenceId"] as? String
            ?: return SkillResult.Error("Missing required parameter: sequenceId")

        val withPlayer = params["withPlayer"] as? Boolean ?: true
        val repeat = params["repeat"] as? Int ?: 1
        val startFromStep = params["startFromStep"] as? Int ?: 1

        return withContext(Dispatchers.Main) {
            val success = TemiController.playSequenceById(
                sequenceId,
                withPlayer,
                repeat,
                startFromStep
            )

            if (success) {
                SkillResult.Success
            } else {
                SkillResult.Error("Failed to execute sequence: $sequenceId")
            }
        }
    }

    override suspend fun canExecute(context: Context): Boolean {
        return TemiController.isSequencePermissionGranted()
    }

    override fun getRequiredPermissions(): List<String> {
        return listOf("com.robotemi.sdk.permission.Sequence")
    }
}
```

**D. Escort Skill**
```kotlin
/**
 * Complex skill for escorting users to destinations
 */
class EscortSkill : BaseTemiSkill(
    skillId = "escort",
    skillName = "Escort",
    description = "Escort users to destinations with greetings and follow-up actions"
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val greeting = params["greeting"] as? String
            ?: return SkillResult.Error("Missing required parameter: greeting")

        val place = params["place"] as? String
        val arrivalGreeting = params["arrivalGreeting"] as? String
        val waitTimeSeconds = (params["waitTime"] as? Number)?.toLong() ?: 5L
        val returnTo = params["returnTo"] as? String
        val arrivalDelaySeconds = (params["arrivalDelay"] as? Number)?.toLong()

        return withContext(Dispatchers.Main) {
            // Implementation similar to MainActivity.escort handling
            // 1. Speak greeting
            GoogleTTS.speak(context, greeting)

            // 2. Set up arrival callback
            if (place != null && arrivalGreeting != null) {
                TemiController.setArrivalCallbackOnce {
                    TemiController.speak(arrivalGreeting)
                    if (returnTo != null) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            TemiController.goTo(returnTo)
                        }, waitTimeSeconds * 1000)
                    }
                }
            }

            // 3. Navigate to destination
            if (place != null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    TemiController.goTo(place)
                }, 3000)
            }

            SkillResult.Success
        }
    }

    override suspend fun canExecute(context: Context): Boolean {
        return true
    }

    override fun getRequiredPermissions(): List<String> = emptyList()
}
```

**E. Order Taking Skill**
```kotlin
/**
 * Skill for handling product orders with UI
 */
class OrderTakingSkill : BaseTemiSkill(
    skillId = "order_taking",
    skillName = "Order Taking",
    description = "Interactive order taking with product selection and confirmation"
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val place = params["place"] as? String ?: ""
        val products = params["products"] as? List<String>

        return withContext(Dispatchers.Main) {
            val intent = Intent(context, PedidosActivity::class.java).apply {
                putExtra(PedidosActivity.EXTRA_PLACE, place)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
            SkillResult.Success
        }
    }

    override suspend fun canExecute(context: Context): Boolean {
        return true
    }

    override fun getRequiredPermissions(): List<String> = emptyList()
}
```

#### 2.4 Composite Skills

**A. Welcome Workflow**
```kotlin
/**
 * Composite skill for welcoming visitors
 */
class WelcomeWorkflowSkill : BaseTemiSkill(
    skillId = "welcome_workflow",
    skillName = "Welcome Workflow",
    description = "Complete welcome sequence with greeting, navigation, and information display"
) {

    override suspend fun executeSkill(context: Context, params: Map<String, Any>): SkillResult {
        val text = params["text"] as? String ?: "Welcome!"
        val place = params["place"] as? String
        val showInfo = params["showInfo"] as? Boolean ?: false
        val infoUrl = params["infoUrl"] as? String

        return withContext(Dispatchers.IO) {
            // Step 1: Greeting
            val speechResult = SkillRegistry.getSkill("speech")?.execute(context, mapOf(
                "text" to text,
                "useGoogleTTS" to true
            ))

            if (speechResult !is SkillResult.Success) {
                return@withContext SkillResult.Error("Greeting failed")
            }

            delay(3000)

            // Step 2: Navigate if place specified
            if (place != null) {
                val navResult = SkillRegistry.getSkill("navigation")?.execute(context, mapOf(
                    "place" to place
                ))

                if (navResult !is SkillResult.Success) {
                    return@withContext SkillResult.Error("Navigation failed")
                }
            }

            // Step 3: Show information if requested
            if (showInfo && infoUrl != null) {
                withContext(Dispatchers.Main) {
                    val intent = Intent(context, KioskWebActivity::class.java).apply {
                        putExtra(KioskWebActivity.EXTRA_URL, infoUrl)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                }
            }

            SkillResult.Success
        }
    }

    override suspend fun canExecute(context: Context): Boolean {
        return SkillRegistry.getSkill("speech")?.canExecute(context) == true
    }

    override fun getRequiredPermissions(): List<String> = emptyList()
}
```

### 3. Integration with Existing Systems

#### 3.1 Supabase Integration

Extend the `RobotPedido` system to support skills:

```kotlin
@Serializable
data class SkillPedido(
    val id: Long,
    val skillId: String,
    val parameters: Map<String, String>,
    val priority: Int = 0,
    val realizado: Boolean = false
)

object SkillPedidosWorker {

    private const val TAG = "SkillPedidosWorker"

    fun start(context: Context) {
        // Similar to RobotPedidosWorker but for skills
        // Poll Supabase for skill_pedidos table
        // Execute skills using SkillManager
    }

    private suspend fun processSkillPedido(context: Context, pedido: SkillPedido) {
        val skill = SkillRegistry.getSkill(pedido.skillId)
            ?: return

        val params = pedido.parameters.mapValues { it.value }

        skill.execute(context, params) { result ->
            when (result) {
                is SkillResult.Success -> markAsCompleted(pedido.id)
                is SkillResult.Error -> markAsFailed(pedido.id, result.message)
                else -> { /* Handle partial success */ }
            }
        }
    }
}
```

#### 3.2 Command Queue Integration

Extend `CommandQueue` to support skill execution:

```kotlin
sealed class Command {
    // Existing commands...
    data class ExecuteSkill(
        val skillId: String,
        val params: Map<String, Any>,
        val callback: ((SkillResult) -> Unit)? = null
    ) : Command()
}
```

### 4. Configuration and Metadata

#### 4.1 Skill Metadata System

```kotlin
/**
 * Metadata for skill discovery and documentation
 */
data class SkillMetadata(
    val skillId: String,
    val name: String,
    val description: String,
    val category: SkillCategory,
    val parameters: List<ParameterSpec>,
    val requiredPermissions: List<String>,
    val timeoutMillis: Long = 30000,
    val retryable: Boolean = false
)

enum class SkillCategory {
    NAVIGATION,
    SPEECH,
    SEQUENCE,
    INTERACTION,
    COMPOSITE,
    CUSTOM
}

data class ParameterSpec(
    val name: String,
    val type: ParameterType,
    val required: Boolean,
    val description: String,
    val defaultValue: Any? = null
)

enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    LIST,
    MAP
}
```

#### 4.2 Skill Configuration Provider

```kotlin
object SkillConfiguration {

    private val configurations = mutableMapOf<String, SkillMetadata>()

    fun register(metadata: SkillMetadata) {
        configurations[metadata.skillId] = metadata
    }

    fun getMetadata(skillId: String): SkillMetadata? = configurations[skillId]

    fun getAllMetadata(): List<SkillMetadata> = configurations.values.toList()

    fun getSkillsByCategory(category: SkillCategory): List<SkillMetadata> {
        return configurations.values.filter { it.category == category }
    }
}
```

### 5. Testing and Validation

#### 5.1 Skill Testing Framework

```kotlin
/**
 * Base class for skill testing
 */
abstract class SkillTest {
    abstract fun testExecute(): Boolean
    abstract fun testCanExecute(): Boolean
    abstract fun testErrorHandling(): Boolean
}

/**
 * Test runner for skills
 */
object SkillTestRunner {

    fun runTests(skill: TemiSkill, context: Context): TestResults {
        val results = mutableListOf<TestResult>()

        // Test canExecute
        results.add(TestResult("canExecute", skill.canExecute(context)))

        // Test required permissions
        results.add(TestResult("permissions", skill.getRequiredPermissions().isNotEmpty()))

        return TestResults(results)
    }
}

data class TestResults(val results: List<TestResult>)
data class TestResult(val name: String, val passed: Boolean)
```

### 6. Documentation Requirements

Create comprehensive documentation for:

1. **Skill Development Guide**
   - How to create new skills
   - Best practices for skill implementation
   - Common patterns and anti-patterns

2. **API Reference**
   - Complete list of available skills
   - Parameter specifications
   - Return value formats

3. **Integration Examples**
   - Code samples for common use cases
   - Supabase integration patterns
   - Command queue integration

### 7. Code Style and Structure Guidelines

- **Package Structure:**
  ```
  com.spatium.deamon.db.temi.skills/
    ├── base/
    │   ├── TemiSkill.kt
    │   ├── BaseTemiSkill.kt
    │   └── SkillResult.kt
    ├── registry/
    │   ├── SkillRegistry.kt
    │   └── SkillConfiguration.kt
    ├── impl/
    │   ├── NavigationSkill.kt
    │   ├── SpeechSkill.kt
    │   ├── SequenceSkill.kt
    │   ├── EscortSkill.kt
    │   └── OrderTakingSkill.kt
    ├── composite/
    │   └── WelcomeWorkflowSkill.kt
    ├── manager/
    │   └── SkillManager.kt
    └── testing/
        ├── SkillTest.kt
        └── SkillTestRunner.kt
  ```

- **Naming Conventions:**
  - Skill classes: `{Functionality}Skill` (e.g., `NavigationSkill`)
  - Skill IDs: `lowercase_with_underscores` (e.g., `navigation`)
  - Parameters: `camelCase`

- **Logging:**
  - Use consistent TAG format: `{ClassName}` or `{SkillId}`
  - Log entry/exit of major operations
  - Include parameter values in logs (sensitive data masked)

- **Error Handling:**
  - Always catch exceptions and convert to `SkillResult.Error`
  - Include meaningful error messages
  - Log exceptions with stack traces

### 8. Implementation Phases

**Phase 1: Core Infrastructure**
- Implement base classes and interfaces
- Create skill registry and manager
- Set up basic testing framework

**Phase 2: Essential Skills**
- Navigation skill
- Speech skill
- Sequence execution skill

**Phase 3: Advanced Skills**
- Escort skill
- Order taking skill
- Composite skills

**Phase 4: Integration**
- Supabase integration
- Command queue integration
- UI integration

**Phase 5: Documentation and Testing**
- Write comprehensive documentation
- Create test suite
- Performance optimization

### 9. Dependencies to Add

Update `app/build.gradle.kts`:

```kotlin
dependencies {
    // Existing dependencies...

    // Additional dependencies for skills system
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")
}
```

### 10. Configuration Files

Create `skills_config.json` in `res/raw/`:

```json
{
  "skills": [
    {
      "id": "navigation",
      "enabled": true,
      "timeout": 30000,
      "retry_count": 3
    },
    {
      "id": "speech",
      "enabled": true,
      "timeout": 10000,
      "retry_count": 2
    }
  ]
}
```

## Expected Deliverables

1. Complete implementation of all base classes and interfaces
2. Implementation of all core skills (Navigation, Speech, Sequence, Escort, Order Taking)
3. Implementation of composite skills (Welcome Workflow)
4. Skill registry and manager
5. Supabase integration for skill execution
6. Testing framework
7. Comprehensive documentation
8. Configuration files
9. Unit tests for critical components
10. Integration examples

## Testing Requirements

- All skills must handle null/missing parameters gracefully
- Skills must timeout appropriately
- Error conditions must be properly logged
- Concurrent skill execution must be thread-safe
- Memory leaks must be avoided (proper coroutine cleanup)

## Performance Considerations

- Skill execution should not block the main thread
- Network operations must have timeouts
- Long-running operations should show progress indicators
- Skill registry should be initialized lazily

## Security Considerations

- Validate all input parameters
- Sanitize text before speech synthesis
- Check permissions before executing skills
- Don't expose sensitive information in logs
- Validate URLs before opening in WebView

---

This prompt provides a complete specification for implementing a robust skills system for the TemiBridge application. Follow the architecture patterns already established in the codebase, maintain consistency with existing code style, and ensure seamless integration with the CommandQueue, TemiController, and Supabase systems.
