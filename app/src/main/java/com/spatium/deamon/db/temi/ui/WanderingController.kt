package com.spatium.deamon.db.temi.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.robotemi.sdk.Robot
import kotlin.random.Random

/**
 * Controlador modular para la lógica de deambulación del robot Temi
 * Maneja navegación, detección, TTS y estado de deambulación de forma independiente
 */
class WanderingController(
    private val robot: Robot?,
    private val onPersonDetected: () -> Unit,
    private val onNavigationStart: (location: String) -> Unit,
    private val onNavigationComplete: () -> Unit,
    private val onTtsCompleted: () -> Unit
) {
    companion object {
        private const val TAG = "WanderingController"
        private const val DETECTION_DISTANCE = 1.5f
        private const val IGNORE_TIMEOUT = 8000L
        private const val PHOTO_MODE_TIMEOUT = 30000L
    }

    private var isWandering = false
    private var currentLocationIndex = 0
    private var selectedLocations = listOf<String>()
    private var ignoreTimer: Handler? = null
    private var photoModeTimer: Handler? = null
    private var navigationTimer: Handler? = null
    private var isDetectionProcessing = false
    private var ttsListenerProxy: Any? = null

    private val phrases = listOf(
        "¡Capturemos este momento profesional juntos!",
        "¿Podríamos documentar este instante con una fotografía?",
        "Me gustaría registrar este encuentro con una imagen de calidad.",
        "¿Nos tomamos una foto para el recuerdo?",
        "Sería excelente inmortalizar este momento.",
        "¿Podría invitarle a participar en una sesión fotográfica?",
        "Tengo el honor de ofrecerle una fotografía de este instante.",
        "¿Estaría interesado en una captura de este momento?",
        "Permítame documentar este encuentro profesionalmente.",
        "¿Nos uniríamos para una fotografía memorable?"
    )

    private val byePhrases = listOf(
        "Entiendo. Continuaré buscando otros participantes interesados.",
        "Sin problema. Seguiré adelante con mi búsqueda.",
        "Respeto su decisión. Buscaré otras oportunidades.",
        "No hay inconveniente. Continuaré mi recorrido.",
        "Comprendo. Seguiré explorando otras posibilidades.",
        "Está bien. Persistiré en mi objetivo.",
        "Sin preocupación. Seguiré adelante.",
        "Entendido. Continuaré con mi misión.",
        "Perfecto. Buscaré otros interesados.",
        "No importa. Seguiré en mi camino."
    )

    /**
     * Inicializa los listeners necesarios
     */
    fun initialize() {
        registerTtsListener()
        testTtsSystem()
    }
    
    /**
     * Crea un TtsRequest usando reflexión (como TemiController)
     */
    private fun createTtsRequest(text: String): Any? {
        return try {
            val cls = Class.forName("com.robotemi.sdk.TtsRequest")
            val create = cls.getMethod("create", String::class.java, Boolean::class.javaPrimitiveType)
            create.invoke(null, text, false)  // Usar false como TemiController
        } catch (t: Throwable) {
            Log.w(TAG, "[TTS] TtsRequest no disponible: ${t.message}")
            null
        }
    }
    
    /**
     * Prueba el sistema TTS para verificar si funciona
     */
    private fun testTtsSystem() {
        try {
            Log.d(TAG, "[TTS_TEST] Iniciando prueba del sistema TTS")
            
            if (robot == null) {
                Log.e(TAG, "[TTS_TEST] Robot es null - no se puede probar TTS")
                return
            }
            
            val testPhrase = "Sistema TTS activado"
            val ttsRequest = createTtsRequest(testPhrase)
            
            if (ttsRequest != null && robot != null) {
                Log.d(TAG, "[TTS_TEST] Enviando prueba: $testPhrase")
                val speak = robot.javaClass.getMethod("speak", ttsRequest.javaClass)
                speak.invoke(robot, ttsRequest)
                Log.d(TAG, "[TTS_TEST] Prueba TTS enviada exitosamente")
            } else {
                Log.e(TAG, "[TTS_TEST] No se pudo crear TtsRequest o robot es null")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[TTS_TEST] Error en prueba TTS: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Inicia la deambulación con las ubicaciones seleccionadas
     */
    fun startWandering(locations: List<String>) {
        Log.d(TAG, "[WANDER] Iniciando deambulación con ${locations.size} ubicaciones")
        
        if (locations.isEmpty()) {
            Log.w(TAG, "[WANDER] No hay ubicaciones para deambular")
            return
        }

        selectedLocations = locations
        currentLocationIndex = 0
        isWandering = true
        
        navigateToNextLocation()
    }

    /**
     * Navega a la siguiente ubicación en la lista
     */
    private fun navigateToNextLocation() {
        if (!isWandering || selectedLocations.isEmpty()) {
            Log.d(TAG, "[WANDER] Deambulación detenida")
            return
        }

        val nextLocation = selectedLocations[currentLocationIndex]
        Log.d(TAG, "[GOTO] Navegando a: $nextLocation")
        
        onNavigationStart(nextLocation)
        
        try {
            robot?.goTo(nextLocation)
            
            // Configurar timeout para navegación (60 segundos)
            navigationTimer?.removeCallbacksAndMessages(null)
            navigationTimer = Handler(Looper.getMainLooper()).apply {
                postDelayed({
                    Log.d(TAG, "[GOTO] Timeout de navegación alcanzado para $nextLocation")
                    moveToNextLocation()
                }, 60000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[GOTO] Error navegando a $nextLocation: ${e.message}")
            moveToNextLocation()
        }
    }

    /**
     * Mueve al siguiente índice de ubicación
     */
    private fun moveToNextLocation() {
        currentLocationIndex = (currentLocationIndex + 1) % selectedLocations.size
        navigateToNextLocation()
    }

    /**
     * Detiene la deambulación actual
     */
    fun stopWandering() {
        Log.d(TAG, "[WANDER] Deteniendo deambulación")
        isWandering = false
        cancelAllTimers()
        try {
            robot?.stopMovement()
        } catch (e: Exception) {
            Log.e(TAG, "[WANDER] Error deteniendo movimiento: ${e.message}")
        }
    }

    /**
     * Maneja la detección de persona (sincronizado con Mutex)
     */
    fun onPersonDetected() {
        if (isDetectionProcessing) {
            Log.d(TAG, "[DETECTION] Ignorando detección (processing=$isDetectionProcessing)")
            return
        }
        
        // La detección siempre debe procesarse para mostrar el botón selfie
        // independientemente de si la deambulación está activa
        
        isDetectionProcessing = true
        Log.d(TAG, "[DETECTION] Persona detectada - iniciando procesamiento")
        
        // Detener movimiento
        try {
            robot?.stopMovement()
        } catch (e: Exception) {
            Log.e(TAG, "[DETECTION] Error deteniendo movimiento: ${e.message}")
        }

        // Hablar frase aleatoria
        val randomPhrase = phrases.random()
        Log.d(TAG, "[TTS] Hablando: $randomPhrase")
        
        try {
            // Verificar que el robot esté disponible
            if (robot == null) {
                Log.e(TAG, "[TTS] Error: Robot es null")
                isDetectionProcessing = false
                onTtsCompleted()
                return
            }
            
            Log.d(TAG, "[TTS] Robot disponible: ${robot?.javaClass?.simpleName}")
            
            // Crear TtsRequest usando reflexión (como TemiController)
            val ttsRequest = createTtsRequest(randomPhrase)
            if (ttsRequest == null) {
                Log.e(TAG, "[TTS] Error: No se pudo crear TtsRequest")
                isDetectionProcessing = false
                onTtsCompleted()
                return
            }
            
            Log.d(TAG, "[TTS] TtsRequest creado con reflexión: $ttsRequest")
            Log.d(TAG, "[TTS] Llamando a robot.speak() con reflexión")
            
            val speak = robot!!.javaClass.getMethod("speak", ttsRequest.javaClass)
            speak.invoke(robot, ttsRequest)
            Log.d(TAG, "[TTS] robot.speak() ejecutado con reflexión")
            
            // Fallback: si TTS falla, resetear después de 3 segundos
            Handler(Looper.getMainLooper()).postDelayed({
                if (isDetectionProcessing) {
                    Log.w(TAG, "[TTS] Fallback timeout - reseteando isDetectionProcessing")
                    isDetectionProcessing = false
                    onTtsCompleted()
                }
            }, 3000)
        } catch (e: Exception) {
            Log.w(TAG, "[TTS] Error al hablar: ${e.message}")
            isDetectionProcessing = false
            onTtsCompleted()
        }

        // Iniciar timeout de foto
        photoModeTimer?.removeCallbacksAndMessages(null)
        photoModeTimer = Handler(Looper.getMainLooper()).apply {
            postDelayed({
                Log.d(TAG, "[PHOTO_TIMEOUT] Timeout de foto alcanzado")
                isDetectionProcessing = false
                resumeWandering()
            }, PHOTO_MODE_TIMEOUT)
        }

        // Notificar que se detectó persona
        onPersonDetected()
    }

    /**
     * Reanuda la deambulación después de completar el flujo de foto
     */
    fun resumeWandering() {
        Log.d(TAG, "[WANDER] Reanudando deambulación")
        
        cancelPhotoTimer()
        
        if (!isWandering) {
            Log.d(TAG, "[WANDER] Deambulación no está activa - no continuar")
            return
        }
        
        Log.d(TAG, "[WANDER] Deambulación activa - continuando a siguiente ubicación")

        // Hablar frase de despedida
        val byePhrase = byePhrases.random()
        Log.d(TAG, "[TTS] Frase de despedida: $byePhrase")
        
        try {
            val ttsRequest = createTtsRequest(byePhrase)
            if (ttsRequest != null && robot != null) {
                val speak = robot.javaClass.getMethod("speak", ttsRequest.javaClass)
                speak.invoke(robot, ttsRequest)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[TTS] Error al hablar frase de despedida: ${e.message}")
        }

        // Esperar a que termine de hablar y luego continuar
        Handler(Looper.getMainLooper()).postDelayed({
            moveToNextLocation()
        }, 3000)
    }

    /**
     * Maneja la navegación completada
     */
    fun onNavigationCompleted() {
        Log.d(TAG, "[GOTO] Navegación completada")
        navigationTimer?.removeCallbacksAndMessages(null)
        onNavigationComplete()
        
        // Esperar un poco antes de continuar a la siguiente ubicación
        Handler(Looper.getMainLooper()).postDelayed({
            if (isWandering) {
                Log.d(TAG, "[GOTO] Continuando a siguiente ubicación (deambulación activa)")
                moveToNextLocation()
            } else {
                Log.d(TAG, "[GOTO] Deambulación detenida - no continuar a siguiente ubicación")
            }
        }, 2000)
    }

    /**
     * Cancela todos los timers activos
     */
    private fun cancelAllTimers() {
        ignoreTimer?.removeCallbacksAndMessages(null)
        photoModeTimer?.removeCallbacksAndMessages(null)
        navigationTimer?.removeCallbacksAndMessages(null)
    }

    /**
     * Cancela el timer de foto
     */
    private fun cancelPhotoTimer() {
        photoModeTimer?.removeCallbacksAndMessages(null)
    }

    /**
     * Retorna si está en modo deambulación
     */
    fun isWanderingActive(): Boolean = isWandering

    /**
     * Retorna las ubicaciones seleccionadas
     */
    fun getSelectedLocations(): List<String> = selectedLocations

    /**
     * Retorna la ubicación actual
     */
    fun getCurrentLocation(): String {
        return if (selectedLocations.isNotEmpty() && currentLocationIndex < selectedLocations.size) {
            selectedLocations[currentLocationIndex]
        } else {
            ""
        }
    }

    /**
     * Registra el listener de TTS para detectar cuando se completa el habla
     */
    private fun registerTtsListener() {
        val r = robot ?: return
        try {
            // Primero limpiar cualquier listener existente
            try {
                val listenerCls = Class.forName("com.robotemi.sdk.Robot\$TtsListener")
                r.javaClass.getMethod("removeTtsListener", listenerCls).invoke(r, ttsListenerProxy)
                Log.d(TAG, "[LISTENERS] TTS listener existente removido")
            } catch (t: Throwable) {
                Log.d(TAG, "[LISTENERS] No había TTS listener previo que remover")
            }
            
            // Registrar nuevo listener
            val listenerCls = Class.forName("com.robotemi.sdk.Robot\$TtsListener")
            ttsListenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerCls.classLoader, arrayOf(listenerCls),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    if (method.name == "onTtsStatusChanged" && args != null && args.isNotEmpty()) {
                        val ttsReq = args[0] ?: return@InvocationHandler null
                        val statusObj = try {
                            ttsReq.javaClass.getMethod("getStatus").invoke(ttsReq)
                        } catch (t: Throwable) { null }
                        val statusStr = statusObj?.toString() ?: "UNKNOWN"
                        Log.d(TAG, "[TTS] status=$statusStr")
                        
                        // Detectar diferentes estados de completion
                        val isCompleted = statusStr.uppercase().contains("COMPLET") 
                        val isError = statusStr.uppercase().contains("ERROR")
                        val isCanceled = statusStr.uppercase().contains("CANCEL")
                        
                        Log.d(TAG, "[TTS] Estados - Completed: $isCompleted, Error: $isError, Canceled: $isCanceled")
                        
                        if (isCompleted || isError || isCanceled) {
                            Log.d(TAG, "[TTS] TTS finalizado (status: $statusStr) - notificando a SelfieHunterActivity")
                            onTtsCompleted()
                        }
                    }
                    null
                }
            )
            r.javaClass.getMethod("addTtsListener", listenerCls).invoke(r, ttsListenerProxy)
            Log.d(TAG, "[LISTENERS] ✓ TTS listener registrado en WanderingController")
        } catch (t: Throwable) {
            Log.w(TAG, "[LISTENERS] TTS listener no disponible en WanderingController: ${t.message}")
            t.printStackTrace()
        }
    }

    /**
     * Limpia recursos
     */
    fun cleanup() {
        stopWandering()
        cancelAllTimers()
        
        try {
            ttsListenerProxy?.let {
                val cls = Class.forName("com.robotemi.sdk.Robot\$TtsListener")
                robot?.javaClass?.getMethod("removeTtsListener", cls)?.invoke(robot, it)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "[CLEANUP] Error removiendo TTS listener: ${t.message}")
        }
        
        ttsListenerProxy = null
    }
}
