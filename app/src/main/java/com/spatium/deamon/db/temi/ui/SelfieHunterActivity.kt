package com.spatium.deamon.db.temi.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.robotemi.sdk.Robot
import com.spatium.deamon.db.temi.R

class SelfieHunterActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SelfieHunterActivity"
        private const val DETECTION_DISTANCE = 1.5f
        const val EXTRA_SELECTED_LOCATIONS = "selected_locations"
        private const val FAREWELL_PHRASE = "Nuestro equipo de recepción enviará la foto a tu WhatsApp. ¡Disfruta el evento!"
    }

    enum class State { WANDERING, SPEAKING, WAITING_TOUCH, PHOTO_MODE }

    private var currentState = State.WANDERING
    private var robot: Robot? = null
    private var wanderingController: WanderingController? = null
    private var selectedLocations = listOf<String>()

    // ── Listeners con reflexión para compatibilidad entre versiones del SDK ──
    private var goToListenerProxy: Any? = null
    private var detectionListenerProxy: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selfie_hunter)
        Log.d(TAG, "[LIFECYCLE] ✅ SelfieHunterActivity.onCreate - INICIADA CORRECTAMENTE")
        Log.d(TAG, "[INTENT] Intent recibido: ${intent?.action}")
        Log.d(TAG, "[EXTRAS] Bundle extras: ${intent?.extras}")

        setupFullscreen()

        robot = try { Robot.getInstance() } catch (t: Throwable) {
            Log.e(TAG, "[ROBOT] Error obteniendo instancia: ${t.message}")
            null
        }

        setupButtons()
        registerListeners()
        loadLocationsAndStart()
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupButtons() {
        findViewById<FrameLayout>(R.id.btnSelfieCamera).setOnClickListener {
            Log.d(TAG, "[UI] Botón cámara tocado")
            onCameraButtonPressed()
        }
        // Botón para salir del modo deambulatorio
        findViewById<View>(R.id.btnExitWandering)?.setOnClickListener {
            Log.d(TAG, "[UI] Botón salir tocado")
            exitWanderingMode()
        }
    }

    fun onCtaButtonClicked(view: View) {
        Log.d(TAG, "[UI] Botón CTA tocado")
        onCameraButtonPressed()
    }

    private fun loadLocationsAndStart() {
        Log.d(TAG, "[LOAD] Iniciando loadLocationsAndStart()")
        selectedLocations = intent.getStringArrayListExtra(EXTRA_SELECTED_LOCATIONS) ?: emptyList()
        Log.d(TAG, "[LOAD] Ubicaciones recibidas: ${selectedLocations.size} - $selectedLocations")
        
        if (selectedLocations.isNotEmpty()) {
            Log.d(TAG, "[LOCATIONS] Ubicaciones seleccionadas: ${selectedLocations.size}")
            
            // Inicializar WanderingController con callbacks
            wanderingController = WanderingController(
                robot = robot,
                personDetectedCallback = { 
                    Log.d(TAG, "[DETECTION] Callback personDetected - cambiando a SPEAKING")
                    currentState = State.SPEAKING
                },
                onNavigationStart = { location ->
                    Log.d(TAG, "[NAVIGATION] Navegando a: $location (CTA permanece visible)")
                    // NO ocultar CTA durante navegación - siempre visible
                },  
                onNavigationComplete = {
                    Log.d(TAG, "[NAVIGATION] Navegación completada")
                },
                onTtsCompleted = {
                    Log.d(TAG, "[TTS] TTS completado - cambiando a WAITING_TOUCH")
                    currentState = State.WAITING_TOUCH
                    // CTA ya está visible, solo asegurar
                    runOnUiThread { showCTAScreen() }
                }
            )
            
            // Inicializar listeners del WanderingController
            wanderingController?.initialize()
            
            // Mostrar CTA overlay INMEDIATAMENTE (siempre visible durante deambulación)
            showCTAScreen()
            
            // Iniciar deambulación INMEDIATAMENTE
            wanderingController?.startWandering(selectedLocations)
            Log.d(TAG, "[WANDER] Deambulación iniciada con ${selectedLocations.size} ubicaciones")
        } else {
            Log.w(TAG, "[LOCATIONS] Sin ubicaciones seleccionadas")
            updateStatusText("⚠️ Sin ubicaciones")
        }
    }

    // La lógica de detección y navegación se maneja en WanderingController y en los listeners de SDK

    // ──────────────────────────────────────────
    // 4. BOTÓN CÁMARA presionado
    // ──────────────────────────────────────────
    private fun onCameraButtonPressed() {
        if (currentState != State.WAITING_TOUCH) {
            Log.w(TAG, "[CAMERA] Botón presionado pero no en WAITING_TOUCH state")
            return
        }
        currentState = State.PHOTO_MODE
        hideCTAScreen()
        wanderingController?.stopWandering()
        disableDetectionMode()
        Log.d(TAG, "[CAMERA] ¡Usuario tocó! Abriendo cámara...")
        launchPhotoApp()
    }

    private fun launchPhotoApp() {
        Log.d(TAG, "[PHOTO] Lanzando PartyActivity con flag return_to_selfie_hunter=true")
        try {
            val intent = Intent(this, PartyActivity::class.java).apply {
                putExtra("return_to_selfie_hunter", true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            Log.d(TAG, "[PHOTO] Intent creado, lanzando startActivityForResult...")
            startActivityForResult(intent, 1001)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            Log.d(TAG, "[PHOTO] PartyActivity lanzada exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "[PHOTO] Error lanzando PartyActivity: ${e.message}", e)
        }
    }

    // ──────────────────────────────────────────
    // 5. UI helpers
    // ──────────────────────────────────────────
    private fun showCTAScreen() {
        runOnUiThread {
            Log.d(TAG, "[UI] Mostrando pantalla CTA")
            // Ocultar contenido principal
            findViewById<LinearLayout>(R.id.mainContent)?.visibility = View.GONE
            // Mostrar overlay CTA
            findViewById<FrameLayout>(R.id.ctaOverlay).visibility = View.VISIBLE
        }
    }

    private fun hideCTAScreen() {
        runOnUiThread {
            findViewById<LinearLayout>(R.id.mainContent)?.visibility = View.VISIBLE
            findViewById<FrameLayout>(R.id.ctaOverlay)?.visibility = View.GONE
        }
    }

    private fun updateStatusText(text: String) {
        runOnUiThread {
            try {
                findViewById<TextView>(R.id.tvSelfieStatus)?.text = text
            } catch (t: Throwable) { }
        }
    }

    // ──────────────────────────────────────────
    // Nuevos métodos de control
    // ──────────────────────────────────────────
    private fun exitWanderingMode() {
        Log.d(TAG, "[EXIT] Saliendo del modo deambulatorio")
        wanderingController?.cleanup()
        unregisterListeners()
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun sayFarewellAndResumeWandering() {
        Log.d(TAG, "[FAREWELL] Diciendo frase de despedida y reanudando")
        try {
            val ttsRequest = createTtsRequest(FAREWELL_PHRASE)
            if (ttsRequest != null && robot != null) {
                val speak = robot!!.javaClass.getMethod("speak", ttsRequest.javaClass)
                speak.invoke(robot, ttsRequest)
                Log.d(TAG, "[FAREWELL] TTS enviado: $FAREWELL_PHRASE")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[FAREWELL] Error TTS: ${e.message}")
        }
        // Esperar a que termine de hablar y luego reanudar deambulación
        Handler(Looper.getMainLooper()).postDelayed({
            if (currentState == State.WANDERING) {
                wanderingController?.resumeWandering()
            }
        }, 5000)
    }

    private fun disableDetectionMode() {
        val r = robot ?: return
        try {
            r.javaClass.getMethod("setDetectionModeOn",
                Boolean::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .invoke(r, false, DETECTION_DISTANCE)
            Log.d(TAG, "[DETECTION] Modo detección DESACTIVADO")
        } catch (t: Throwable) {
            Log.w(TAG, "[DETECTION] Error desactivando detección: ${t.message}")
        }
    }

    private fun enableDetectionMode() {
        val r = robot ?: return
        try {
            r.javaClass.getMethod("setDetectionModeOn",
                Boolean::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .invoke(r, true, DETECTION_DISTANCE)
            Log.d(TAG, "[DETECTION] Modo detección ACTIVADO")
        } catch (t: Throwable) {
            Log.w(TAG, "[DETECTION] Error activando detección: ${t.message}")
        }
    }

    private fun createTtsRequest(text: String): Any? {
        return try {
            val cls = Class.forName("com.robotemi.sdk.TtsRequest")
            val create = cls.getMethod("create", String::class.java, Boolean::class.javaPrimitiveType)
            create.invoke(null, text, false)
        } catch (t: Throwable) {
            Log.w(TAG, "[TTS] TtsRequest no disponible: ${t.message}")
            null
        }
    }

    // ──────────────────────────────────────────
    // 6. Listeners del SDK (via reflexión)
    // ──────────────────────────────────────────
    private fun registerListeners() {
        val r = robot ?: return
        
        registerGoToListener()
        registerDetectionListener()
        
        Log.d(TAG, "[LISTENERS] ✓ Todos los listeners registrados")
    }

    private fun registerGoToListener() {
        val r = robot ?: return
        try {
            val listenerCls = Class.forName("com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener")
            goToListenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerCls.classLoader, arrayOf(listenerCls),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    if (method.name == "onGoToLocationStatusChanged" && args != null && args.size >= 2) {
                        val status = args[1]?.toString()?.uppercase() ?: ""
                        val descId = if (args.size >= 3) (args[2] as? Int) ?: -1 else -1
                        val isComplete = status == "COMPLETE" || descId == 500
                        Log.d(TAG, "[GOTO] status=$status descId=$descId state=$currentState")
                        if (isComplete && currentState == State.WANDERING) {
                            wanderingController?.onNavigationCompleted()
                        }
                    }
                    null
                }
            )
            r.javaClass.getMethod("addOnGoToLocationStatusChangedListener", listenerCls)
                .invoke(r, goToListenerProxy)
            Log.d(TAG, "[LISTENERS] ✓ GoTo listener registrado")
        } catch (t: Throwable) {
            Log.w(TAG, "[LISTENERS] GoTo listener no disponible: ${t.message}")
        }
    }

    private fun registerDetectionListener() {
        val r = robot ?: return
        try {
            val listenerCls = Class.forName("com.robotemi.sdk.listeners.OnDetectionDataChangedListener")
            detectionListenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerCls.classLoader, arrayOf(listenerCls),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    if (method.name == "onDetectionDataChanged" && args != null && args.isNotEmpty()) {
                        val data = args[0] ?: return@InvocationHandler null
                        val isDetected = try {
                            data.javaClass.getMethod("isDetected").invoke(data) as? Boolean ?: false
                        } catch (t: Throwable) { false }

                        Log.d(TAG, "[DETECTION] isDetected=$isDetected state=$currentState")

                        when (currentState) {
                            State.WANDERING -> {
                                if (isDetected) {
                                    Log.d(TAG, "[DETECTION] Persona detectada en WANDERING - deteniendo y hablando")
                                    currentState = State.SPEAKING
                                    Handler(Looper.getMainLooper()).post {
                                        wanderingController?.stopWandering()
                                        wanderingController?.onPersonDetected()
                                    }
                                }
                            }
                            State.SPEAKING -> {
                                // Ignorar cambios de detección mientras habla
                            }
                            State.WAITING_TOUCH -> {
                                if (!isDetected) {
                                    Log.d(TAG, "[DETECTION] Persona se fue - reanudando deambulación (CTA permanece)")
                                    currentState = State.WANDERING
                                    // NO ocultar CTA - siempre visible durante deambulación
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (currentState == State.WANDERING) {
                                            wanderingController?.resumeWandering()
                                        }
                                    }, 1500L)
                                }
                            }
                            else -> { }
                        }
                    }
                    null
                }
            )
            r.javaClass.getMethod("addOnDetectionDataChangedListener", listenerCls)
                .invoke(r, detectionListenerProxy)
            Log.d(TAG, "[LISTENERS] ✓ Detection listener registrado")
        } catch (t: Throwable) {
            Log.w(TAG, "[LISTENERS] Detection listener no disponible: ${t.message}")
        }
    }

    
    private fun unregisterListeners() {
        val r = robot ?: return
        try {
            goToListenerProxy?.let {
                val cls = Class.forName("com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener")
                r.javaClass.getMethod("removeOnGoToLocationStatusChangedListener", cls).invoke(r, it)
            }
        } catch (t: Throwable) { }
        try {
            detectionListenerProxy?.let {
                val cls = Class.forName("com.robotemi.sdk.listeners.OnDetectionDataChangedListener")
                r.javaClass.getMethod("removeOnDetectionDataChangedListener", cls).invoke(r, it)
            }
        } catch (t: Throwable) { }
        try {
            r.javaClass.getMethod("setDetectionModeOn",
                Boolean::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .invoke(r, false, 1.0f)
        } catch (t: Throwable) { }
        Log.d(TAG, "[LISTENERS] Listeners removidos")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            Log.d(TAG, "[RESULT] Retornando de PartyActivity - diciendo despedida y reanudando")
            currentState = State.WANDERING
            showCTAScreen() // Volver a mostrar CTA para deambulación
            enableDetectionMode()
            sayFarewellAndResumeWandering()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wanderingController?.cleanup()
        unregisterListeners()
        Log.d(TAG, "[LIFECYCLE] SelfieHunterActivity destruida")
    }
}
