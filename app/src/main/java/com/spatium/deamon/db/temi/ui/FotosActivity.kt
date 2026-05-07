package com.spatium.deamon.db.temi.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.spatium.deamon.db.temi.R

class FotosActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FotosActivity"
        private const val DETECTION_DISTANCE = 1.5f
        private const val IGNORE_TIMEOUT_MS = 8000L
        private const val PAUSE_BETWEEN_POINTS_MS = 2000L
        private const val PHOTO_MODE_TIMEOUT_MS = 30_000L
        const val EXTRA_LOCATION_PREFIX = "location_prefix"
        const val EXTRA_SELECTED_LOCATIONS = "selected_locations"
        private const val REQUEST_PHOTO = 2001
    }

    enum class State { WANDERING, SPEAKING, WAITING_TOUCH, PHOTO_MODE }

    private var currentState = State.WANDERING
    private var robot: Robot? = null
    private var lastLocation: String? = null
    private var ignoreTimer: Handler? = null
    private var isSayingGoodbye = false
    private var patrolLocations: List<String> = emptyList()
    private var locationManager: LocationManager? = null

    private val phrases = listOf(
        "¡Tírate una foto conmigo! 📸",
        "¡Ey! ¿Nos tomamos una selfie?",
        "¡Inmortaliza este momento con temi!",
    )

    private val byePhrases = listOf(
        "Está bien... seguiré buscando a alguien más fotogénico.",
        "No te preocupes, no me afecta para nada.",
        "Ok, me voy. Tú te lo pierdes.",
        "La próxima persona seguro quiere foto conmigo.",
    )

    // ── Listeners con reflexión para compatibilidad entre versiones del SDK ──
    private var goToListenerProxy: Any? = null
    private var detectionListenerProxy: Any? = null
    private var ttsListenerProxy: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fotos)
        Log.d(TAG, "[LIFECYCLE] FotosActivity.onCreate")

        setupFullscreen()

        robot = try {
            Robot.getInstance()
        } catch (t: Throwable) {
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
        findViewById<FrameLayout>(R.id.btnFotosBack).setOnClickListener {
            Log.d(TAG, "[UI] Botón volver tocado")
            returnToMain()
        }

        findViewById<FrameLayout>(R.id.btnCtaFoto).setOnClickListener {
            Log.d(TAG, "[UI] Botón CTA tocado")
            onScreenTouched()
        }
    }

    private fun loadLocationsAndStart() {
        // Intentar obtener lista de ubicaciones seleccionadas
        val selectedLocations = intent.getStringArrayListExtra(EXTRA_SELECTED_LOCATIONS)

        if (selectedLocations != null && selectedLocations.isNotEmpty()) {
            // Usar ubicaciones seleccionadas directamente
            Log.d(TAG, "[LOCATIONS] Usando ubicaciones seleccionadas: $selectedLocations")
            patrolLocations = selectedLocations
            val names = selectedLocations.joinToString(", ")
            Log.d(TAG, "[LOCATIONS] ✓ ${selectedLocations.size} puntos: $names")
            updateStatusText("✓ ${selectedLocations.size} puntos cargados\nBuscando personas...")
            startWandering()
        } else {
            // Fallback: usar prefijo si no hay ubicaciones seleccionadas
            val selectedPrefix = intent.getStringExtra(EXTRA_LOCATION_PREFIX)
                ?: LocationManager.EVENT_PREFIX

            Log.d(TAG, "[LOCATIONS] Usando prefijo: $selectedPrefix")

            locationManager = LocationManager(
                onLocationsReady = { locations ->
                    runOnUiThread {
                        patrolLocations = locations
                        val names = locations.joinToString(", ") {
                            it.removePrefix(selectedPrefix)
                        }
                        Log.d(TAG, "[LOCATIONS] ✓ ${locations.size} puntos: $names")
                        updateStatusText("✓ ${locations.size} puntos cargados\nBuscando personas...")
                        startWandering()
                    }
                },
                onLocationsEmpty = {
                    runOnUiThread {
                        Log.w(TAG, "[LOCATIONS] Sin puntos configurados para: $selectedPrefix")
                        updateStatusText(
                            "⚠️ No hay puntos configurados.\n\n" +
                                "Agrega puntos en la web de Temi con el prefijo:\n" +
                                "\"$selectedPrefix\"\n\n" +
                                "Ejemplo: ${selectedPrefix}entrada",
                        )
                    }
                },
            )

            val r = robot
            if (r != null && locationManager != null) {
                locationManager?.loadLocationsWithPrefix(r, selectedPrefix)
            } else {
                updateStatusText("⚠️ Robot no disponible")
            }
        }
    }

    // ──────────────────────────────────────────
    // 1. DEAMBULAR
    // ──────────────────────────────────────────
    private fun startWandering() {
        if (patrolLocations.isEmpty()) {
            Log.w(TAG, "[WANDER] Sin puntos de patrulla")
            return
        }
        currentState = State.WANDERING
        isSayingGoodbye = false

        try {
            robot?.let {
                it.javaClass.getMethod(
                    "setDetectionModeOn",
                    Boolean::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                )
                    .invoke(it, true, DETECTION_DISTANCE)
                Log.d(TAG, "[WANDER] ✓ Detección activada a ${DETECTION_DISTANCE}m")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "[WANDER] setDetectionModeOn no disponible: ${t.message}")
        }

        val next = locationManager?.getNextRandom(lastLocation) ?: return
        lastLocation = next
        Log.d(TAG, "[WANDER] Navegando a: $next")
        updateStatusText("🤖 Buscando personas...\n→ $next")

        try {
            robot?.javaClass?.getMethod("goTo", String::class.java)?.invoke(robot, next)
        } catch (t: Throwable) {
            Log.w(TAG, "[WANDER] goTo fallo: ${t.message}")
        }
    }

    // ──────────────────────────────────────────
    // 2. HABLAR a la persona detectada
    // ──────────────────────────────────────────
    private fun speakToDetectedPerson() {
        currentState = State.SPEAKING
        val phrase = phrases.random()
        Log.d(TAG, "[SPEAK] Diciendo: $phrase")
        updateStatusText("💬 $phrase")

        try {
            val ttsRequest = TtsRequest.create(phrase, false)
            robot?.speak(ttsRequest)
        } catch (t: Throwable) {
            Log.w(TAG, "[SPEAK] Error TTS: ${t.message}")
            onTtsCompleted()
        }
    }

    private fun onTtsCompleted() {
        when {
            currentState == State.SPEAKING && !isSayingGoodbye -> {
                currentState = State.WAITING_TOUCH
                startIgnoreTimer()
                showCTAScreen()
            }
            isSayingGoodbye -> {
                isSayingGoodbye = false
                Handler(Looper.getMainLooper()).postDelayed({
                    startWandering()
                }, PAUSE_BETWEEN_POINTS_MS)
            }
        }
    }

    // ──────────────────────────────────────────
    // 3. TIMER de ignorado
    // ──────────────────────────────────────────
    private fun startIgnoreTimer() {
        cancelIgnoreTimer()
        ignoreTimer = Handler(Looper.getMainLooper())
        ignoreTimer?.postDelayed({
            if (currentState == State.WAITING_TOUCH) {
                Log.d(TAG, "[TIMER] Timeout — fue ignorado")
                playIgnoredReaction()
            }
        }, IGNORE_TIMEOUT_MS)
    }

    private fun cancelIgnoreTimer() {
        ignoreTimer?.removeCallbacksAndMessages(null)
        ignoreTimer = null
    }

    private fun playIgnoredReaction() {
        hideCTAScreen()
        val byePhrase = byePhrases.random()
        Log.d(TAG, "[IGNORED] Diciendo: $byePhrase")
        isSayingGoodbye = true
        try {
            val ttsRequest = TtsRequest.create(byePhrase, false)
            robot?.speak(ttsRequest)
        } catch (t: Throwable) {
            Log.w(TAG, "[IGNORED] Error TTS despedida: ${t.message}")
            isSayingGoodbye = false
            startWandering()
        }
    }

    // ──────────────────────────────────────────
    // 4. TOQUE en pantalla → modo foto
    // ──────────────────────────────────────────
    private fun onScreenTouched() {
        if (currentState != State.WAITING_TOUCH) return
        cancelIgnoreTimer()
        currentState = State.PHOTO_MODE
        hideCTAScreen()
        Log.d(TAG, "[TOUCH] ¡Usuario tocó! Abriendo cámara...")
        launchPhotoApp()
    }

    private fun launchPhotoApp() {
        Log.d(TAG, "[PHOTO] Lanzando PartyActivity")
        val intent = Intent(this, PartyActivity::class.java)
        startActivityForResult(intent, REQUEST_PHOTO)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PHOTO) {
            Log.d(TAG, "[PHOTO] Regresando de PartyActivity — retomando deambulación")
            Handler(Looper.getMainLooper()).postDelayed({
                startWandering()
            }, PAUSE_BETWEEN_POINTS_MS)
        }
    }

    // ──────────────────────────────────────────
    // 5. UI helpers
    // ──────────────────────────────────────────
    private fun showCTAScreen() {
        runOnUiThread {
            Log.d(TAG, "[UI] Mostrando pantalla CTA")
            updateStatusText("👆 ¡Tócame para la foto!")
            findViewById<FrameLayout>(R.id.ctaLayout).visibility = View.VISIBLE
        }
    }

    private fun hideCTAScreen() {
        runOnUiThread {
            findViewById<FrameLayout>(R.id.ctaLayout).visibility = View.GONE
        }
    }

    private fun updateStatusText(text: String) {
        runOnUiThread {
            try {
                findViewById<TextView>(R.id.tvFotosStatus)?.text = text
            } catch (t: Throwable) { /* ignorar */ }
        }
    }

    // ──────────────────────────────────────────
    // 6. Listeners del SDK (via reflexión)
    // ──────────────────────────────────────────
    private fun registerListeners() {
        registerGoToListener()
        registerDetectionListener()
        registerTtsListener()
    }

    private fun registerGoToListener() {
        val r = robot ?: return
        try {
            val listenerCls = Class.forName("com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener")
            goToListenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerCls.classLoader,
                arrayOf(listenerCls),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    if (method.name == "onGoToLocationStatusChanged" && args != null && args.size >= 2) {
                        val status = args[1]?.toString()?.uppercase() ?: ""
                        val descId = if (args.size >= 3) (args[2] as? Int) ?: -1 else -1
                        val isComplete = status == "COMPLETE" || descId == 500
                        Log.d(TAG, "[GOTO] status=$status descId=$descId state=$currentState")
                        if (isComplete && currentState == State.WANDERING) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                startWandering()
                            }, PAUSE_BETWEEN_POINTS_MS)
                        }
                    }
                    null
                },
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
                listenerCls.classLoader,
                arrayOf(listenerCls),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    if (method.name == "onDetectionDataChanged" && args != null && args.isNotEmpty()) {
                        val data = args[0] ?: return@InvocationHandler null
                        val isDetected = try {
                            data.javaClass.getMethod("isDetected").invoke(data) as? Boolean ?: false
                        } catch (t: Throwable) {
                            false
                        }

                        Log.d(TAG, "[DETECTION] isDetected=$isDetected state=$currentState")

                        when (currentState) {
                            State.WANDERING -> {
                                if (isDetected) {
                                    Handler(Looper.getMainLooper()).post {
                                        try {
                                            r.javaClass.getMethod("stopMovement").invoke(r)
                                        } catch (t: Throwable) { }
                                        speakToDetectedPerson()
                                    }
                                }
                            }
                            State.WAITING_TOUCH -> {
                                if (!isDetected) {
                                    Log.d(TAG, "[DETECTION] Persona se fue — retomando")
                                    cancelIgnoreTimer()
                                    Handler(Looper.getMainLooper()).post { hideCTAScreen() }
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        startWandering()
                                    }, 1500L)
                                }
                            }
                            else -> { }
                        }
                    }
                    null
                },
            )
            r.javaClass.getMethod("addOnDetectionDataChangedListener", listenerCls)
                .invoke(r, detectionListenerProxy)
            Log.d(TAG, "[LISTENERS] ✓ Detection listener registrado")
        } catch (t: Throwable) {
            Log.w(TAG, "[LISTENERS] Detection listener no disponible: ${t.message}")
        }
    }

    private fun registerTtsListener() {
        val r = robot ?: return
        try {
            val listenerCls = Class.forName("com.robotemi.sdk.Robot\$TtsListener")
            ttsListenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerCls.classLoader,
                arrayOf(listenerCls),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    if (method.name == "onTtsStatusChanged" && args != null && args.isNotEmpty()) {
                        val ttsReq = args[0] ?: return@InvocationHandler null
                        val statusObj = try {
                            ttsReq.javaClass.getMethod("getStatus").invoke(ttsReq)
                        } catch (t: Throwable) {
                            null
                        }
                        val isCompleted = statusObj?.toString()?.uppercase()?.contains("COMPLET") == true
                        Log.d(TAG, "[TTS] status=$statusObj state=$currentState goodbye=$isSayingGoodbye")
                        if (isCompleted) {
                            Handler(Looper.getMainLooper()).post { onTtsCompleted() }
                        }
                    }
                    null
                },
            )
            r.javaClass.getMethod("addTtsListener", listenerCls).invoke(r, ttsListenerProxy)
            Log.d(TAG, "[LISTENERS] ✓ TTS listener registrado")
        } catch (t: Throwable) {
            Log.w(TAG, "[LISTENERS] TTS listener no disponible: ${t.message}")
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
            ttsListenerProxy?.let {
                val cls = Class.forName("com.robotemi.sdk.Robot\$TtsListener")
                r.javaClass.getMethod("removeTtsListener", cls).invoke(r, it)
            }
        } catch (t: Throwable) { }
        try {
            r.javaClass.getMethod(
                "setDetectionModeOn",
                Boolean::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            )
                .invoke(r, false, 1.0f)
        } catch (t: Throwable) { }
        Log.d(TAG, "[LISTENERS] Listeners removidos")
    }

    // ──────────────────────────────────────────
    // 7. Lifecycle
    // ──────────────────────────────────────────
    private fun returnToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelIgnoreTimer()
        unregisterListeners()
        Log.d(TAG, "[LIFECYCLE] FotosActivity destruida")
    }
}
