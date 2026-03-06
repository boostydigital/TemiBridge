---
name: temi-sdk
description: Guía completa del SDK de Temi para Android/Kotlin. Usar cuando se necesite implementar navegación, TTS/speech, seguimiento de usuario (follow/constraintBeWith), detección, face tracking, secuencias, o cualquier integración con el robot Temi. Incluye patrones con reflexión para compatibilidad entre versiones del SDK.
---

# Temi SDK - Guía Completa Android/Kotlin

## Fuentes oficiales
- Wiki: https://github.com/robotemi/sdk/wiki
- Repo: https://github.com/robotemi/sdk
- SDK Version actual en proyecto: 1.136.0

---

## 1. SETUP INICIAL

### build.gradle.kts
```kotlin
dependencies {
    implementation("com.robotemi:sdk:1.136.0")
}
```

### AndroidManifest.xml (estructura completa)
```xml
<manifest>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="com.robotemi.permission.sequence" />
    <uses-permission android:name="com.robotemi.permission.face_recognition" />
    <uses-permission android:name="com.robotemi.permission.map" />
    <uses-permission android:name="com.robotemi.permission.settings" />

    <application>
        <!-- OBLIGATORIO: declara capacidades/permisos para la UI del robot -->
        <meta-data
            android:name="@string/metadata_permissions"
            android:value="com.robotemi.permission.sequence,com.robotemi.permission.face_recognition,com.robotemi.permission.map,com.robotemi.permission.settings" />

        <!-- OBLIGATORIO: declara la app como skill de Temi -->
        <meta-data
            android:name="@string/metadata_skill"
            android:value="LauncherType.KIOSK" />
    </application>
</manifest>
```

### Obtener instancia del robot
```kotlin
import com.robotemi.sdk.Robot

// En Activity: implementar Robot.Callback
class MainActivity : AppCompatActivity(), Robot.Callback {
    private val robot by lazy { Robot.getInstance() }

    override fun onStart() {
        super.onStart()
        robot?.addOnRobotReadyListener(this)  // o el listener correspondiente
    }

    override fun onStop() {
        super.onStop()
        robot?.removeOnRobotReadyListener(this)
    }
}
```

---

## 2. FOLLOW / FACE TRACKING

### ⚡ MÉTODOS CRÍTICOS (elegir el correcto según caso de uso)

| Método | Comportamiento | Permisos | SDK desde |
|---|---|---|---|
| `constraintBeWith()` | Robot **gira y tilta** hacia el usuario **sin moverse** ✅ | Ninguno | 0.10.53 |
| `beWithMe()` | Robot **sigue físicamente** al usuario moviéndose | Ninguno | 0.10.36 |
| `stopMovement()` | **Detiene** constraintBeWith y cualquier movimiento | Ninguno | - |
| `setTrackUserOn(true)` | Activa modo Track User (modo automático tras inactividad) | SETTINGS | 0.10.70 |

### constraintBeWith() - RECOMENDADO para orientar al usuario
```kotlin
// El robot gira su cabeza/cuerpo hacia el usuario sin moverse del lugar
fun activarOrientacionHaciaUsuario() {
    val robot = Robot.getInstance() ?: return
    robot.constraintBeWith()
}

// Para detenerlo
fun desactivarOrientacion() {
    val robot = Robot.getInstance() ?: return
    robot.stopMovement()
}
```

### beWithMe() - Para seguir físicamente al usuario
```kotlin
fun seguirAlUsuario() {
    val robot = Robot.getInstance() ?: return
    robot.beWithMe()  // Robot se mueve siguiendo al usuario
}
```

### Con reflexión (patrón del proyecto TemiBridge)
```kotlin
fun constraintBeWithReflection(): Boolean {
    val robot = Robot.getInstance() ?: return false
    return try {
        val method = robot.javaClass.getMethod("constraintBeWith")
        method.invoke(robot)
        true
    } catch (t: Throwable) {
        Log.e("TemiSDK", "constraintBeWith falló: ${t.message}", t)
        false
    }
}

fun stopMovementReflection(): Boolean {
    val robot = Robot.getInstance() ?: return false
    return try {
        val method = robot.javaClass.getMethod("stopMovement")
        method.invoke(robot)
        true
    } catch (t: Throwable) {
        Log.e("TemiSDK", "stopMovement falló: ${t.message}", t)
        false
    }
}
```

### Listener de estado constraintBeWith
```kotlin
class MainActivity : AppCompatActivity(), OnConstraintBeWithStatusChangedListener {

    override fun onStart() {
        super.onStart()
        robot?.addOnConstraintBeWithStatusChangedListener(this)
    }

    override fun onConstraintBeWithStatusChanged(status: String) {
        when (status) {
            "searching" -> Log.d("Temi", "Buscando usuario...")
            "lock_on"   -> Log.d("Temi", "✅ Usuario detectado, orientando")
            "abort"     -> Log.d("Temi", "Abortado")
        }
    }
}
```

---

## 3. NAVEGACIÓN

### goTo(location)
```kotlin
// Navegar a un waypoint guardado
robot?.goTo("recepcion")

// Con listener de estado
class MainActivity : AppCompatActivity(), OnGoToLocationStatusChangedListener {

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        when (status) {
            OnGoToLocationStatusChangedListener.COMPLETE  -> Log.d("Nav", "✅ Llegó a $location")
            OnGoToLocationStatusChangedListener.ABORT     -> Log.d("Nav", "❌ Abortó navegación a $location")
            OnGoToLocationStatusChangedListener.GOING     -> Log.d("Nav", "🚀 Navegando a $location")
            OnGoToLocationStatusChangedListener.START     -> Log.d("Nav", "Iniciando hacia $location")
        }
    }
}
```

### getLocations() - Obtener waypoints guardados
```kotlin
val locations: List<String> = robot?.locations ?: emptyList()
Log.d("Nav", "Waypoints disponibles: $locations")
```

### getPosition() - Posición actual
```kotlin
// Debe ejecutarse en WorkerThread
val position = robot?.position
Log.d("Nav", "x=${position?.x} y=${position?.y} yaw=${position?.yaw}")
```

### stopMovement()
```kotlin
robot?.stopMovement()  // Detiene cualquier movimiento activo
```

---

## 4. SPEECH / TTS

### speak() - Hacer hablar al robot
```kotlin
import com.robotemi.sdk.TtsRequest

// Forma básica
robot?.speak(TtsRequest.create("Hola, bienvenido"))

// Con opciones avanzadas
val ttsRequest = TtsRequest.create(
    speech = "¿En qué puedo ayudarte?",
    isShowOnConversationLayer = false  // No mostrar en la capa de conversación
)
robot?.speak(ttsRequest)
```

### Con listener de estado TTS
```kotlin
class MainActivity : AppCompatActivity(), TtsListener {

    override fun onStart() {
        super.onStart()
        robot?.addTtsListener(this)
    }

    override fun ttsStatusChanged(ttsRequest: TtsRequest) {
        when (ttsRequest.status) {
            TtsRequest.Status.STARTED   -> Log.d("TTS", "Hablando...")
            TtsRequest.Status.COMPLETED -> Log.d("TTS", "✅ TTS completado")
            TtsRequest.Status.ERROR     -> Log.d("TTS", "❌ Error TTS")
        }
    }
}
```

### cancelAllTtsRequests()
```kotlin
robot?.cancelAllTtsRequests()  // Cancela todo TTS en curso
```

---

## 5. DETECCIÓN DE PERSONAS (Detection & Interaction)

### Reglas importantes (desde SDK 0.10.77)
- Activar `TrackUser` → también activa `DetectionMode`
- Desactivar `DetectionMode` → también desactiva `TrackUser`
- `setTrackUserOn` requiere permiso **SETTINGS**
- **No funciona si está activo Greet Mode**

### setDetectionModeOn()
```kotlin
// Activar detección de personas (distancia máxima por defecto: 0.8m)
robot?.setDetectionModeOn(true)

// Con distancia personalizada (0.5 - 2.0 metros válidos)
robot?.setDetectionModeOn(true, 1.5f)

// Desactivar
robot?.setDetectionModeOn(false)
```

### setTrackUserOn()
```kotlin
// Track User: después de inactividad, sigue automáticamente al usuario detectado
// ⚠️ REQUIERE permiso SETTINGS y estar en Kiosk Mode
robot?.setTrackUserOn(true)
```

### Listener de detección
```kotlin
class MainActivity : AppCompatActivity(),
    OnDetectionStateChangedListener,
    OnDetectionDataChangedListener {

    override fun onDetectionStateChanged(state: Int) {
        when (state) {
            OnDetectionStateChangedListener.IDLE      -> Log.d("Detection", "Sin detección")
            OnDetectionStateChangedListener.DETECTED  -> Log.d("Detection", "👤 Persona detectada")
            OnDetectionStateChangedListener.LOST      -> Log.d("Detection", "Persona perdida")
        }
    }

    override fun onDetectionDataChanged(detectionData: DetectionData) {
        Log.d("Detection", "Ángulo: ${detectionData.angle} Distancia: ${detectionData.distance}")
    }
}
```

---

## 6. FACE RECOGNITION

### startFaceRecognition() / stopFaceRecognition()
```kotlin
// Iniciar reconocimiento facial continuo
// ⚠️ REQUIERE permiso FACE_RECOGNITION activado en Settings > Permissions del robot
robot?.startFaceRecognition()

// Detener
robot?.stopFaceRecognition()
```

### Listener de cara reconocida
```kotlin
class MainActivity : AppCompatActivity(), OnFaceRecognizedListener {

    override fun onFaceRecognized(contactModelList: List<ContactModel>) {
        contactModelList.forEach { contact ->
            Log.d("Face", "Cara reconocida: id=${contact.userId} nombre=${contact.name}")
        }
    }
}
```

### Listener continuo de reconocimiento facial
```kotlin
class MainActivity : AppCompatActivity(), OnContinuousFaceRecognizedListener {

    override fun onContinuousFaceRecognized(contactModelList: List<ContactModel>) {
        Log.d("Face", "Caras detectadas: ${contactModelList.size}")
    }
}
```

---

## 7. SECUENCIAS

```kotlin
// Ejecutar una secuencia guardada en el robot
// ⚠️ REQUIERE permiso SEQUENCE
fun playSequence(sequenceId: String) {
    val robot = Robot.getInstance() ?: return
    try {
        val method = robot.javaClass.getMethod("playSequence", String::class.java)
        method.invoke(robot, sequenceId)
        Log.d("TemiSDK", "✅ Secuencia '$sequenceId' iniciada")
    } catch (e: Exception) {
        Log.e("TemiSDK", "Error ejecutando secuencia: ${e.message}", e)
    }
}
```

---

## 8. PATRÓN COMPLETO CON REFLEXIÓN (TemiBridge)

Usar reflexión cuando no se importa directamente el SDK o para mayor compatibilidad:

```kotlin
object TemiController {
    private const val TAG = "TemiController"

    private fun robotInstance() = try {
        val robotClass = Class.forName("com.robotemi.sdk.Robot")
        val getInstance = robotClass.getMethod("getInstance")
        getInstance.invoke(null)
    } catch (e: Exception) {
        Log.e(TAG, "Robot.getInstance() falló: ${e.message}")
        null
    }

    fun invokeMethod(methodName: String, vararg args: Any?): Any? {
        val robot = robotInstance() ?: return null
        return try {
            val paramTypes = args.map { it?.javaClass }.toTypedArray()
            val method = robot.javaClass.getMethod(methodName, *paramTypes)
            method.invoke(robot, *args)
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "Método '$methodName' no encontrado en SDK")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error invocando '$methodName': ${e.message}", e)
            null
        }
    }

    // Face tracking - orienta robot sin moverse
    fun constraintBeWith() = invokeMethod("constraintBeWith") != null

    // Detener orientación
    fun stopMovement() = invokeMethod("stopMovement") != null

    // Seguir físicamente al usuario
    fun beWithMe() = invokeMethod("beWithMe") != null

    // Navegar a waypoint
    fun goTo(location: String) = invokeMethod("goTo", location) != null

    // TTS
    fun speak(text: String) {
        val robot = robotInstance() ?: return
        try {
            val ttsClass = Class.forName("com.robotemi.sdk.TtsRequest")
            val createMethod = ttsClass.getMethod("create", String::class.java, Boolean::class.javaPrimitiveType)
            val ttsRequest = createMethod.invoke(null, text, false)
            val speakMethod = robot.javaClass.getMethod("speak", ttsClass)
            speakMethod.invoke(robot, ttsRequest)
        } catch (e: Exception) {
            Log.e(TAG, "speak() falló: ${e.message}", e)
        }
    }
}
```

---

## 9. CICLO DE VIDA CORRECTO EN ACTIVITY

```kotlin
class MainActivity : AppCompatActivity(),
    Robot.Callback,
    OnGoToLocationStatusChangedListener,
    OnBeWithMeStatusChangedListener,
    OnConstraintBeWithStatusChangedListener {

    private val robot by lazy { Robot.getInstance() }

    override fun onStart() {
        super.onStart()
        robot?.apply {
            addOnGoToLocationStatusChangedListener(this@MainActivity)
            addOnBeWithMeStatusChangedListener(this@MainActivity)
            addOnConstraintBeWithStatusChangedListener(this@MainActivity)
        }
    }

    override fun onStop() {
        super.onStop()
        robot?.apply {
            removeOnGoToLocationStatusChangedListener(this@MainActivity)
            removeOnBeWithMeStatusChangedListener(this@MainActivity)
            removeOnConstraintBeWithStatusChangedListener(this@MainActivity)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        robot?.stopMovement()  // Siempre detener movimiento al destruir
    }
}
```

---

## 10. DIAGNÓSTICO RÁPIDO - PROBLEMAS COMUNES

| Síntoma | Causa probable | Solución |
|---|---|---|
| `constraintBeWith()` no hace nada | Robot no detecta personas cerca | Acercarse al robot, buena iluminación |
| `setTrackUserOn()` no funciona | Falta permiso SETTINGS o Greet Mode activo | Verificar permiso, desactivar Greet Mode |
| `startFaceRecognition()` lanza excepción | Permiso FACE_RECOGNITION no habilitado | Habilitar en Settings > Permissions del robot |
| Método no encontrado por reflexión | Nombre incorrecto o versión SDK no compatible | Verificar nombre exacto en [Robot.kt](https://github.com/robotemi/sdk/blob/master/sdk/src/main/java/com/robotemi/sdk/Robot.kt) |
| Robot no aparece en permisos | Formato incorrecto en manifest | Ver skill `temi-permissions` |
| `goTo()` aborta inmediatamente | Mapa no cargado o waypoint no existe | Verificar `robot.locations` |
