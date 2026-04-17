# Temi SDK - Documentación Completa de Funcionalidades

**Proyecto**: Temi Deamon DB (com.spatium.deamon.db.temi)  
**Versión SDK**: 1.136.0  
**Fecha**: 2026-04-17

---

## Tabla de Contenidos
- [Requisitos Obligatorios](#requisitos-obligatorios)
- [Configuración Inicial](#configuración-inicial)
- [Funcionalidades del SDK](#funcionalidades-del-sdk)
- [Integración con Supabase](#integración-con-supabase)
- [Ejemplos de Implementación](#ejemplos-de-implementación)

---

## Requisitos Obligatorios

### Requisitos Mínimos del Sistema
- **Android SDK**: Mínimo 26 (Android 8.0), Target 36
- **Kotlin**: 1.9+ con JVM Target 17
- **Java**: JDK 17
- **Gradle**: 8.0+ con plugin Kotlin Serialization

### Dependencias Requeridas

```kotlin
// Temi SDK (obligatorio para comunicación con robot)
releaseImplementation("com.robotemi:sdk:1.136.0")
debugImplementation("com.robotemi:sdk:1.136.0")

// Coroutines para operaciones asíncronas
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

// Librerías opcionales ya incluidas en el proyecto
implementation("com.google.mlkit:barcode-scanning:17.3.0") // QR scanning
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")
implementation("com.airbnb.android:lottie:6.4.0") // Animaciones
```

### Permisos Android obligatorios

En `AndroidManifest.xml` deben estar declarados:

```xml
<!-- Permisos básicos del SDK -->
<uses-permission android:name="com.robotemi.permission.face_recognition" />
<uses-permission android:name="com.robotemi.permission.map" />
<uses-permission android:name="com.robotemi.permission.sequence" />
<uses-permission android:name="com.robotemi.permission.settings" />
<uses-permission android:name="com.robotemi.permission.admin" />

<!-- Permisos de hardware -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Configuración de Build Obligatoria

En `app/build.gradle.kts`:

```kotlin
android {
    namespace = "com.spatium.deamon.db.temi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spatium.deamon.db.temi"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "3.7"
    }
    
    buildFeatures {
        buildConfig = true  // Requerido para Supabase y API keys
    }
}
```

---

## Configuración Inicial

### 1. Local Properties (credenciales)

Crear `local.properties` en la raíz del proyecto:

```properties
# Supabase Configuration
SUPABASE_URL=https://mkakxmjkwcymwosfrwkl.supabase.co
SUPABASE_ANON_KEY=sbp_7de6dd2b2deeca71cdba8257bc10d02be484dff3
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key_here

# Tour Configuration
TOUR_RECEPCION_ID=tour_recepcion_id

# Google TTS API Key (opcional)
GOOGLE_TTS_API_KEY=your_google_tts_api_key
```

⚠️ **IMPORTANTE**: Nunca commitear `local.properties` al control de versiones.

### 2. Inicialización del TemiRobot

```kotlin
class TemiController {
    companion object {
        private var robot: Robot? = null
        
        fun initialize(context: Context) {
            if (robot == null) {
                Robot.getInstance(context).apply {
                    addRobotListener(this@TemiController)
                    robot = this
                }
            }
        }
        
        fun getRobot(): Robot? = robot
    }
}
```

---

## Funcionalidades del SDK

### 1. Follow (Seguimiento)

#### `beWithMe()`
Inicia el modo de seguimiento. El robot sigue a la persona detectada.

```kotlin
TemiController.getRobot()?.beWithMe()
```

#### `stopBeWithMe()`
Detiene el modo de seguimiento.

```kotlin
TemiController.getRobot()?.stopBeWithMe()
```

#### `beWithMeConfiguration()`
Configura el comportamiento de seguimiento.

```kotlin
val config = BeWithMeConfiguration(
    maxFollowingDistance = 1.5f,  // metros
    minFollowingDistance = 0.8f,
    speed = 0.5f
)
TemiController.getRobot()?.setBeWithMeConfiguration(config)
```

---

### 2. Navigation & Map (Navegación y Mapas)

#### `goTo(location: String)`
Navega a un waypoint específico.

```kotlin
TemiController.getRobot()?.goTo("Open_Space")
```

#### `goTo(location: String, callback: OnGoToLocationChangedListener)`
Con callback de progreso.

```kotlin
TemiController.getRobot()?.goTo("Lobby", object : OnGoToLocationChangedListener {
    override fun onGoToLocationChanged(location: String, type: String, description: String) {
        when (type) {
            "start" -> Log.i("Navigation", "Iniciando navegación a $location")
            "calculating" -> Log.i("Navigation", "Calculando ruta...")
            "going" -> Log.i("Navigation", "Navegando a $location")
            "complete" -> Log.i("Navigation", "Llegada completa a $location")
            "abort" -> Log.i("Navigation", "Navegación abortada")
        }
    }
})
```

#### `getLocations()`
Obtiene lista de waypoints disponibles.

```kotlin
val locations = TemiController.getRobot()?.locations
// Resultado: ["Open_Space", "Lobby", "Recepcion", "Gastrobar"]
```

#### `saveLocation(name: String)`
Guarda la posición actual como waypoint.

```kotlin
TemiController.getRobot()?.saveLocation("Nuevo_Waypoint")
```

#### `removeLocation(name: String)`
Elimina un waypoint.

```kotlin
TemiController.getRobot()?.removeLocation("Antiguo_Waypoint")
```

---

### 3. Movement (Movimiento)

#### `turn(angle: Int)`
Gira el robot en grados (positivo = horario).

```kotlin
TemiController.getRobot()?.turn(90)   // Gira 90° a la derecha
TemiController.getRobot()?.turn(-90)  // Gira 90° a la izquierda
```

#### `tilt(angle: Int)`
Inclina la cabeza en grados (-25 a 25).

```kotlin
TemiController.getRobot()?.tilt(15)   // Inclina hacia arriba
TemiController.getRobot()?.tilt(-15)  // Inclina hacia abajo
```

#### `tiltBy(angle: Int)`
Inclina la cabeza relativamente a la posición actual.

```kotlin
TemiController.getRobot()?.tiltBy(5)  // Inclina 5° más arriba
```

#### `stopMovement()`
Detiene cualquier movimiento en curso.

```kotlin
TemiController.getRobot()?.stopMovement()
```

#### `getRobotCurrentPosition()`
Obtiene la posición actual del robot.

```kotlin
val position = TemiController.getRobot()?.getRobotCurrentPosition()
```

#### `patrol(locations, nonstop, times, waiting)`
Inicia patrullaje entre múltiples waypoints.

```kotlin
// Patrullaje infinito entre 3 ubicaciones, esperando 5 segundos en cada una
val locations = listOf("Open_Space", "Lobby", "Recepcion", "Gastrobar")
val success = TemiController.getRobot()?.patrol(
    locations,    // Lista de waypoints (mínimo 3)
    false,        // nonstop: false = hace tilt/turn en cada punto
    0,            // times: 0 = infinito, 1 = una vez, 2 = dos veces...
    5             // waiting: segundos a esperar en cada punto (3-60)
)
```

**Parámetros**:
- `locations: List<String>` - Lista de waypoints (mínimo 3 ubicaciones válidas). No incluir "Home base"
- `nonstop: Boolean` - `true` = llega y va al siguiente sin tilt/turn. `false` = hace tilt/turn en cada punto (default)
- `times: Int` - Veces que repite la ruta. `0` = infinito, `1` = una vez, etc.
- `waiting: Int` - Segundos a esperar en cada punto si `nonstop=false`. Rango 3-60, default 3

**Retorna**: `boolean` - `true` si el patrullaje se inició correctamente

**Versión mínima SDK**: 1.129.1

**Detener patrullaje**: Usar `stopMovement()` o `goTo()` para salir del modo patrullaje

---

### 4. Speech (Voz)

#### `speak(text: String)`
El robot habla un texto.

```kotlin
TemiController.getRobot()?.speak("Hola, bienvenido a Spatium")
```

#### `stopSpeaking()`
Detiene el habla actual.

```kotlin
TemiController.getRobot()?.stopSpeaking()
```

#### `setMaxVolume(level: Int)`
Establece volumen máximo (0-100).

```kotlin
TemiController.getRobot()?.setMaxVolume(80)
```

#### `getMaxVolume()`
Obtiene volumen actual.

```kotlin
val volume = TemiController.getRobot()?.maxVolume
```

---

### 5. Users & Telepresence (Usuarios y Telepresencia)

#### `getAllUsers()`
Obtiene lista de todos los usuarios registrados.

```kotlin
val users = TemiController.getRobot()?.getAllUsers()
```

#### `getCurrentUserInfo()`
Obtiene información del usuario actual.

```kotlin
val userInfo = TemiController.getRobot()?.getCurrentUserInfo()
```

#### `getAsrLanguage()`
Obtiene el idioma del reconocimiento de voz.

```kotlin
val language = TemiController.getRobot()?.getAsrLanguage() // "es-ES", "en-US"
```

---

### 6. System (Sistema)

#### `setHardKeyEnabled(enabled: Boolean)`
Habilita/deshabilita botones físicos.

```kotlin
TemiController.getRobot()?.setHardKeyEnabled(false) // Deshabilita botón trasero
```

#### `isHardKeyEnabled()`
Verifica si botones están habilitados.

```kotlin
val enabled = TemiController.getRobot()?.isHardKeyEnabled()
```

#### `setPrivacyMode(enabled: Boolean)`
Activa modo privacidad (desactiva cámara/micrófono).

```kotlin
TemiController.getRobot()?.setPrivacyMode(true)
```

#### `isPrivacyModeOn()`
Verifica estado del modo privacidad.

```kotlin
val privacyMode = TemiController.getRobot()?.isPrivacyModeOn()
```

#### `getVersion()`
Obtiene versión del SDK.

```kotlin
val version = TemiController.getRobot()?.version
```

#### `getRobotNut()`
Obtiene NUT (Unique Token) del robot.

```kotlin
val nut = TemiController.getRobot()?.robotNut
```

---

### 7. Kiosk Mode (Modo Kiosco)

#### `startKioskMode(packageName: String)`
Inicia modo kiosco con una app específica.

```kotlin
TemiController.getRobot()?.startKioskMode("com.example.kiosk")
```

#### `stopKioskMode()`
Detiene modo kiosco.

```kotlin
TemiController.getRobot()?.stopKioskMode()
```

#### `isKioskModeOn()`
Verifica si modo kiosco está activo.

```kotlin
val isKiosk = TemiController.getRobot()?.isKioskModeOn()
```

---

### 8. Detection & Interaction (Detección e Interacción)

#### `getObstacleDistance()`
Obtiene distancia al obstáculo más cercano.

```kotlin
val distance = TemiController.getRobot()?.getObstacleDistance() // en metros
```

#### `setDetectionMode(mode: String)`
Configura modo de detección.

```kotlin
TemiController.getRobot()?.setDetectionMode("auto") // "auto", "on", "off"
```

#### `startMovementOfDetection()`
Inicia movimiento de detección.

```kotlin
TemiController.getRobot()?.startMovementOfDetection()
```

#### `stopMovementOfDetection()`
Detiene movimiento de detección.

```kotlin
TemiController.getRobot()?.stopMovementOfDetection()
```

---

### 9. Permissions (Permisos)

#### `requestPermission(permission: String)`
Solicita un permiso específico.

```kotlin
TemiController.getRobot()?.requestPermission(
    "com.robotemi.permission.face_recognition"
)
```

#### `checkPermission(permission: String)`
Verifica si un permiso está concedido.

```kotlin
val granted = TemiController.getRobot()?.checkPermission(
    "com.robotemi.permission.sequence"
)
```

---

### 10. Face Recognition (Reconocimiento Facial)

#### `isFaceRecognitionEnabled()`
Verifica si reconocimiento facial está activo.

```kotlin
val enabled = TemiController.getRobot()?.isFaceRecognitionEnabled()
```

#### `setFaceRecognition(enabled: Boolean)`
Activa/desactiva reconocimiento facial.

```kotlin
TemiController.getRobot()?.setFaceRecognition(true)
```

#### `startFaceRecognition()`
Inicia reconocimiento facial.

```kotlin
TemiController.getRobot()?.startFaceRecognition()
```

#### `stopFaceRecognition()`
Detiene reconocimiento facial.

```kotlin
TemiController.getRobot()?.stopFaceRecognition()
```

---

### 11. Activity Stream (Flujo de Actividad)

#### `startActivity(stream: String)`
Inicia una actividad personalizada.

```kotlin
TemiController.getRobot()?.startActivity("com.example.CustomActivity")
```

#### `getTopActivity()`
Obtiene la actividad actual en primer plano.

```kotlin
val activity = TemiController.getRobot()?.topActivity
```

---

### 12. Sequence (Secuencias)

#### `playSequence(name: String)`
Reproduce una secuencia.

```kotlin
TemiController.getRobot()?.playSequence("Open_Space")
```

#### `playSequence(id: String)`
Reproduce una secuencia por ID.

```kotlin
TemiController.getRobot()?.playSequence("sequence_123")
```

#### `stopSequence()`
Detiene la secuencia en reproducción.

```kotlin
TemiController.getRobot()?.stopSequence()
```

#### `pauseSequence()`
Pausa la secuencia.

```kotlin
TemiController.getRobot()?.pauseSequence()
```

#### `resumeSequence()`
Reanuda la secuencia pausada.

```kotlin
TemiController.getRobot()?.resumeSequence()
```

#### `isPlayingSequence()`
Verifica si hay una secuencia reproduciéndose.

```kotlin
val isPlaying = TemiController.getRobot()?.isPlayingSequence()
```

#### `getSequenceNames()`
Obtiene nombres de secuencias disponibles.

```kotlin
val sequences = TemiController.getRobot()?.sequenceNames
```

---

### 13. Tour (Tours)

#### `startDefaultNlu(identifier: String)`
Inicia un tour por nombre o ID.

```kotlin
TemiController.getRobot()?.startDefaultNlu("Spatium_Visita")
```

#### `getTourNames()`
Obtiene lista de tours disponibles.

```kotlin
val tours = TemiController.getRobot()?.tourNames
```

---

### 14. Listeners (Eventos del Robot)

El SDK utiliza listeners para recibir eventos del robot:

```kotlin
class TemiController : RobotListener {
    
    override fun onRobotReady(isReady: Boolean) {
        Log.i("Temi", "Robot listo: $isReady")
    }
    
    override fun onConversationAttaches(attaches: Boolean) {
        Log.i("Temi", "Conversación attaches: $attaches")
    }
    
    override fun onGoToLocationChanged(
        location: String,
        type: String,
        description: String
    ) {
        when (type) {
            "start" -> Log.i("Navigation", "Iniciando navegación")
            "complete" -> Log.i("Navigation", "Navegación completada")
            "abort" -> Log.i("Navigation", "Navegación abortada")
        }
    }
    
    override fun onBatteryStatusChanged(
        batteryPercentage: Int,
        isCharging: Boolean
    ) {
        Log.i("Temi", "Batería: $batteryPercentage%, Cargando: $isCharging")
    }
    
    override fun onDistanceChanged(distance: Float) {
        Log.i("Temi", "Distancia: $distance metros")
    }
    
    override fun onUserInfoChanged(userInfo: UserInfo) {
        Log.i("Temi", "Usuario: ${userInfo.name}")
    }
    
    override fun onPlaySequenceStarted(sequenceName: String) {
        Log.i("Temi", "Secuencia iniciada: $sequenceName")
    }
    
    override fun onPlaySequenceStopped(sequenceName: String) {
        Log.i("Temi", "Secuencia detenida: $sequenceName")
    }
    
    override fun onPlaySequenceEnded(sequenceName: String) {
        Log.i("Temi", "Secuencia finalizada: $sequenceName")
    }
}
```

---

## Integración con Supabase

### Configuración en Build.gradle.kts

```kotlin
// Configuración Supabase desde local.properties
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}

val rawSupabaseUrl: String = localProps.getProperty("SUPABASE_URL", "").trim()
val supabaseUrl: String = rawSupabaseUrl.substringBefore("#").trim()

val rawSupabaseAnonKey: String = localProps.getProperty("SUPABASE_ANON_KEY", "").trim()
val supabaseAnonKey: String = rawSupabaseAnonKey.substringBefore("#").trim()

// Exponer al BuildConfig
buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
```

### Dependencias de Supabase

```kotlin
// Supabase-kt (Postgrest + Realtime) y Ktor client
implementation(platform("io.github.jan-tennert.supabase:bom:2.4.2"))
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:realtime-kt")
implementation("io.ktor:ktor-client-android:2.3.12")
```

### Inicialización de Supabase

```kotlin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {
    private val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Realtime)
    }
    
    fun postgrest() = client.postgrest
    fun realtime() = client.realtime
}
```

### Ejemplos de Uso

#### Consulta de datos (SELECT)

```kotlin
suspend fun fetchPedidosUbicacion(ubicacion: String): List<Pedido> {
    return try {
        SupabaseClient.postgrest().from("pedidos")
            .select {
                filter {
                    eq("ubicacion", ubicacion)
                    eq("estado", "pendiente")
                }
            }
            .decodeList<Pedido>()
    } catch (e: Exception) {
        Log.e("Supabase", "Error fetching pedidos", e)
        emptyList()
    }
}
```

#### Inserción de datos (INSERT)

```kotlin
suspend fun insertLogEvento(evento: LogEvento): Boolean {
    return try {
        SupabaseClient.postgrest().from("logs")
            .insert(evento) {
                select()
            }
        true
    } catch (e: Exception) {
        Log.e("Supabase", "Error inserting log", e)
        false
    }
}
```

#### Actualización de datos (UPDATE)

```kotlin
suspend fun updatePedidoEstado(id: String, nuevoEstado: String): Boolean {
    return try {
        SupabaseClient.postgrest().from("pedidos")
            .update {
                set("estado", nuevoEstado)
                set("actualizado_en", LocalDateTime.now())
            } {
                filter {
                    eq("id", id)
                }
            }
        true
    } catch (e: Exception) {
        Log.e("Supabase", "Error updating pedido", e)
        false
    }
}
```

#### Suscripción a cambios en tiempo real (Realtime)

```kotlin
suspend fun subscribeToPedidosUpdates(
    onUpdate: (Pedido) -> Unit
) {
    SupabaseClient.realtime().channel("pedidos_channel") {
        this.postgrest {
            schema = "public"
            table = "pedidos"
            filter = "ubicacion=eq.Recepcion"
        }
    }.subscribe {
        when (it) {
            is RealtimePostgresAction.Update -> {
                val pedido = it.decodeRecord<Pedido>()
                onUpdate(pedido)
            }
            is RealtimePostgresAction.Insert -> {
                val pedido = it.decodeRecord<Pedido>()
                onUpdate(pedido)
            }
        }
    }
}
```

#### Data Classes para Supabase

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Pedido(
    val id: String,
    val cliente: String,
    val items: List<String>,
    val ubicacion: String,
    val estado: String,
    val creado_en: String,
    val actualizado_en: String? = null
)

@Serializable
data class LogEvento(
    val robot_id: String,
    val evento: String,
    val datos: String,
    val timestamp: String
)
```

---

## Ejemplos de Implementación

### Ejemplo 1: Navegación con callback de llegada

```kotlin
fun navegarConCallback(destino: String, onLlegada: () -> Unit) {
    TemiController.getRobot()?.goTo(destino, object : OnGoToLocationChangedListener {
        override fun onGoToLocationChanged(location: String, type: String, description: String) {
            when (type) {
                "complete" -> {
                    TemiController.getRobot()?.speak("Hemos llegado a $destino")
                    onLlegada()
                }
                "abort" -> {
                    Log.w("Navigation", "Navegación abortada: $description")
                }
            }
        }
    })
}
```

### Ejemplo 2: Bucle de bienvenida con movimiento

```kotlin
fun bucleBienvenida() {
    TemiController.getRobot()?.speak("Bienvenido a Spatium")
    
    Thread.sleep(2000)
    
    TemiController.getRobot()?.tilt(15)
    Thread.sleep(1000)
    
    TemiController.getRobot()?.tilt(-15)
    Thread.sleep(1000)
    
    TemiController.getRobot()?.tilt(0)
}
```

### Ejemplo 3: Verificación de permisos antes de acción

```kotlin
fun reproducirSecuenciaConPermiso(nombre: String) {
    val robot = TemiController.getRobot()
    
    if (robot?.checkPermission("com.robotemi.permission.sequence") == true) {
        robot.playSequence(nombre)
    } else {
        robot?.requestPermission("com.robotemi.permission.sequence")
        robot?.speak("Permiso de secuencias requerido. Por favor, acepta en pantalla.")
    }
}
```

### Ejemplo 4: Integración completa con Supabase

```kotlin
class PedidosManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun iniciarMonitoreoPedidos() {
        scope.launch {
            // Suscribirse a cambios en tiempo real
            SupabaseClient.realtime().channel("pedidos_recepcion") {
                postgrest {
                    schema = "public"
                    table = "pedidos"
                    filter = "ubicacion=eq.Recepcion"
                }
            }.subscribe {
                when (it) {
                    is RealtimePostgresAction.Insert -> {
                        val pedido = it.decodeRecord<Pedido>()
                        procesarNuevoPedido(pedido)
                    }
                }
            }
        }
    }
    
    private suspend fun procesarNuevoPedido(pedido: Pedido) {
        withContext(Dispatchers.Main) {
            TemiController.getRobot()?.speak("Nuevo pedido para ${pedido.cliente}")
            
            // Actualizar estado en Supabase
            updatePedidoEstado(pedido.id, "reconocido")
        }
    }
}
```

---

## Consideraciones Importantes

### Lifecycle Management
- Siempre inicializar el SDK de Temi en `Application.onCreate()` o en el primer `Activity.onCreate()`
- Implementar `RobotListener` correctamente para recibir eventos
- Remover listeners en `onDestroy()` para evitar memory leaks

### Manejo de Errores
- Todas las llamadas al SDK pueden devolver `null` si el robot no está conectado
- Siempre envolver en `try-catch` las operaciones de Supabase
- Implementar reintentos para operaciones de red

### Performance
- Usar coroutines para operaciones asíncronas con Supabase
- No bloquear el hilo principal con llamadas al SDK de Temi
- Implementar timeouts para operaciones de red

### Seguridad
- Nunca hardcodear credenciales de Supabase en el código
- Usar `local.properties` para credenciales sensibles
- Implementar validación de datos antes de enviar a Supabase
- Usar Service Role Key solo en el backend, no en la app Android

---

## Referencias

- **Temi SDK GitHub**: https://github.com/robotemi/sdk
- **Supabase Kotlin**: https://github.com/supabase-community/supabase-kt
- **Documentación Proyecto**: `README.md` y `PLAN.md`

---

## Notas de Versión

- **SDK Temi**: 1.136.0 (última versión estable)
- **Supabase-kt**: 2.4.2
- **Ktor Client**: 2.3.12
- **Compile SDK**: 36
- **Target SDK**: 36

---

**Documento generado para el proyecto Temi Deamon DB - Spatium Group**  
Última actualización: 2026-04-17
