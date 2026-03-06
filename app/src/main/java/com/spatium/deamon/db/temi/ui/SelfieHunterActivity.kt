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
import com.robotemi.sdk.TtsRequest
import com.spatium.deamon.db.temi.R

class SelfieHunterActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SelfieHunterActivity"
        private const val DETECTION_DISTANCE = 1.5f
        const val EXTRA_SELECTED_LOCATIONS = "selected_locations"
    }

    enum class State { WANDERING, SPEAKING, WAITING_TOUCH, PHOTO_MODE }

    private var currentState = State.WANDERING
    private var robot: Robot? = null
    private var wanderingController: WanderingController? = null
    private var locationManager: LocationManager? = null

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
    }

    fun onCtaButtonClicked(view: View) {
        Log.d(TAG, "[UI] Botón CTA tocado")
        onCameraButtonPressed()
    }

    private fun loadLocationsAndStart() {
        Log.d(TAG, "[LOAD] Iniciando loadLocationsAndStart()")
        val selectedLocations = intent.getStringArrayListExtra(EXTRA_SELECTED_LOCATIONS) ?: emptyList()
        Log.d(TAG, "[LOAD] Ubicaciones recibidas: ${selectedLocations.size} - $selectedLocations")
        
        if (selectedLocations.isNotEmpty()) {
            Log.d(TAG, "[LOCATIONS] Ubicaciones seleccionadas: ${selectedLocations.size}")
            selectedLocations.forEach { location ->
                Log.d(TAG, "[LOCATIONS] Agregando ubicación: $location")
            }
            
            // Inicializar WanderingController con callbacks
            wanderingController = WanderingController(
                robot = robot,
                onPersonDetected = { 
                    currentState = State.SPEAKING
                    onPersonDetected()
                },
                onNavigationStart = { location ->
                    updateStatusText("🚀 Navegando a: $location")
                },
                onNavigationComplete = {
                    onNavigationComplete()
                },
                onTtsCompleted = {
                    Log.d(TAG, "[TTS] TTS completado - cambiando a WAITING_TOUCH")
                    currentState = State.WAITING_TOUCH
                    Handler(Looper.getMainLooper()).post { 
                        showCTAScreen()
                        // Iniciar deambulación solo cuando el botón selfie es visible
                        wanderingController?.startWandering(selectedLocations)
                    }
                }
            )
            
            // Inicializar listeners del WanderingController
            wanderingController?.initialize()
            
            // NO iniciar deambulación inmediatamente
            // El robot esperará a detectar una persona para comenzar a moverse
            updateStatusText("👀 Esperando personas...")
        } else {
            Log.w(TAG, "[LOCATIONS] Sin ubicaciones seleccionadas")
            updateStatusText("⚠️ Sin ubicaciones")
        }
    }

    private fun onPersonDetected() {
        Log.d(TAG, "[DETECTION] Persona detectada en SelfieHunterActivity")
        currentState = State.SPEAKING
        hideCTAScreen()
        Handler(Looper.getMainLooper()).post {
            showCTAScreen()
        }
    }

    private fun onNavigationStart(location: String) {
        Log.d(TAG, "[NAVIGATION] Navegando a: $location")
        currentState = State.WANDERING
        hideCTAScreen()
    }

    private fun onNavigationComplete() {
        Log.d(TAG, "[NAVIGATION] Navegación completada")
    }

    // Métodos antiguos ya no se usan - la lógica está en WanderingController
    // startWandering(), speakToDetectedPerson(), onTtsCompleted(), etc.
    // fueron reemplazados por WanderingController.kt

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
            // Mostrar contenido principal
            findViewById<LinearLayout>(R.id.mainContent)?.visibility = View.VISIBLE
            // Ocultar overlay CTA
            findViewById<FrameLayout>(R.id.ctaOverlay).visibility = View.GONE
        }
        
        // Detener deambulación cuando el botón selfie se oculta
        wanderingController?.stopWandering()
        updateStatusText("👀 Esperando personas...")
    }

    private fun updateStatusText(text: String) {
        runOnUiThread {
            try {
                findViewById<TextView>(R.id.tvSelfieStatus)?.text = text
            } catch (t: Throwable) { }
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
                                    Handler(Looper.getMainLooper()).post {
                                        try {
                                            r.javaClass.getMethod("stopMovement").invoke(r)
                                        } catch (t: Throwable) { }
                                        wanderingController?.onPersonDetected()
                                    }
                                }
                            }
                            State.WAITING_TOUCH -> {
                                if (!isDetected) {
                                    Log.d(TAG, "[DETECTION] Persona se fue — retomando")
                                    Handler(Looper.getMainLooper()).post { hideCTAScreen() }
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        wanderingController?.resumeWandering()
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
            Log.d(TAG, "[RESULT] Retornando de PartyActivity")
            // Retomar deambulación usando WanderingController
            currentState = State.WANDERING
            wanderingController?.resumeWandering()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wanderingController?.cleanup()
        unregisterListeners()
        Log.d(TAG, "[LIFECYCLE] SelfieHunterActivity destruida")
    }
}
