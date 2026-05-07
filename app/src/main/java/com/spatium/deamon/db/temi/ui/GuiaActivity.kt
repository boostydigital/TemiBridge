package com.spatium.deamon.db.temi.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.android.material.button.MaterialButton
import com.spatium.deamon.db.temi.R

/**
 * Activity fullscreen de doble estado (WAITING / GUIDING) para el Modo Guia.
 *
 * Recibe todos los datos via Intent extras (Strings). No depende de GuiaPayload,
 * GuiaState, GuiaManager ni ninguna clase de dominio — sigue el mismo patrón que
 * AnnouncementActivity y RatingActivity.
 *
 * Estados:
 *   WAITING  — muestra imagen de fondo + nombre del evento + botón CTA.
 *   GUIDING  — muestra video en loop mudo a pantalla completa.
 *
 * El switch entre estados se controla via LocalBroadcast (mismo patrón que
 * AnnouncementActivity usa para cierre).
 */
class GuiaActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GuiaActivity"

        // Intent extras (todos String)
        const val EXTRA_NOMBRE_EVENTO = "extra_nombre_evento"
        const val EXTRA_DESCRIPCION = "extra_descripcion"
        const val EXTRA_ETIQUETA_BOTON = "extra_etiqueta_boton"
        const val EXTRA_IMAGEN_FONDO_URL = "extra_imagen_fondo_url"
        const val EXTRA_VIDEO_LOOP_URL = "extra_video_loop_url"
        const val EXTRA_INITIAL_STATE = "extra_initial_state"

        // Estado inicial
        const val STATE_WAITING = "WAITING"
        const val STATE_GUIDING = "GUIDING"

        // Broadcast actions (publicadas aquí para que GuiaManager las use como constantes compartidas)
        const val ACTION_GUIA_SHOW_WAITING = "com.spatium.deamon.db.temi.action.GUIA_SHOW_WAITING"
        const val ACTION_GUIA_SHOW_GUIDING = "com.spatium.deamon.db.temi.action.GUIA_SHOW_GUIDING"
        const val ACTION_GUIA_CLOSE = "com.spatium.deamon.db.temi.action.GUIA_CLOSE"
        const val ACTION_GUIA_USER_TAPPED_START = "com.spatium.deamon.db.temi.action.GUIA_USER_TAPPED_START"
    }

    // Views — waiting
    private lateinit var guiaRoot: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var waitingContainer: FrameLayout
    private lateinit var waitingBackground: ImageView
    private lateinit var eventNameText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var startButton: MaterialButton

    // Views — guiding
    private lateinit var guidingContainer: FrameLayout
    private lateinit var guidingVideo: VideoView
    private lateinit var guidingLoading: ProgressBar
    private lateinit var guidingEventName: TextView

    // Datos del evento
    private var nombreEvento: String = ""
    private var descripcion: String = ""
    private var etiquetaBoton: String = "Iniciar"
    private var imagenFondoUrl: String = ""
    private var videoLoopUrl: String = ""

    // Receiver: cambiar a guiding
    private val showGuidingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Broadcast recibido: SHOW_GUIDING")
            showGuiding()
        }
    }

    // Receiver: volver a waiting
    private val showWaitingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Broadcast recibido: SHOW_WAITING")
            showWaiting()
        }
    }

    // Receiver: cerrar activity
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Broadcast recibido: CLOSE — cerrando GuiaActivity")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyFullscreenFlags()

        setContentView(R.layout.activity_guia)

        bindViews()
        readExtras()
        populateWaitingView()
        wireStartButton()
        registerReceivers()

        // Estado inicial (por defecto WAITING)
        val initialState = intent.getStringExtra(EXTRA_INITIAL_STATE) ?: STATE_WAITING
        if (initialState == STATE_GUIDING) {
            // Ir directo a guiding sin animación
            waitingContainer.visibility = View.GONE
            guidingContainer.visibility = View.VISIBLE
            startVideoPlayback()
        }

        Log.d(TAG, "GuiaActivity creada — evento='$nombreEvento', estado_inicial=$initialState")
    }

    // ─────────────────────────── Fullscreen ────────────────────────────

    private fun applyFullscreenFlags() {
        // Mismo patrón que AnnouncementActivity (systemUiVisibility + window flags)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
    }

    // ─────────────────────────── Views ────────────────────────────

    private fun bindViews() {
        guiaRoot = findViewById(R.id.guiaRoot)
        waitingContainer = findViewById(R.id.waitingContainer)
        waitingBackground = findViewById(R.id.waitingBackground)
        eventNameText = findViewById(R.id.eventNameText)
        descriptionText = findViewById(R.id.descriptionText)
        startButton = findViewById(R.id.startButton)
        guidingContainer = findViewById(R.id.guidingContainer)
        guidingVideo = findViewById(R.id.guidingVideo)
        guidingLoading = findViewById(R.id.guidingLoading)
        guidingEventName = findViewById(R.id.guidingEventName)
    }

    private fun readExtras() {
        nombreEvento = intent.getStringExtra(EXTRA_NOMBRE_EVENTO) ?: ""
        descripcion = intent.getStringExtra(EXTRA_DESCRIPCION) ?: ""
        etiquetaBoton = intent.getStringExtra(EXTRA_ETIQUETA_BOTON) ?: "Iniciar"
        imagenFondoUrl = intent.getStringExtra(EXTRA_IMAGEN_FONDO_URL) ?: ""
        videoLoopUrl = intent.getStringExtra(EXTRA_VIDEO_LOOP_URL) ?: ""
    }

    private fun populateWaitingView() {
        eventNameText.text = nombreEvento

        if (descripcion.isNotBlank()) {
            descriptionText.visibility = View.VISIBLE
            descriptionText.text = descripcion
        } else {
            descriptionText.visibility = View.GONE
        }

        startButton.text = etiquetaBoton

        // Cargar imagen de fondo con Coil — crossfade 400ms, fallback a drawable
        if (imagenFondoUrl.isNotBlank()) {
            waitingBackground.load(imagenFondoUrl) {
                crossfade(true)
                crossfade(400)
                error(R.drawable.bg_guia_fallback)
                listener(
                    onSuccess = { _, _ -> Log.d(TAG, "Imagen de fondo cargada") },
                    onError = { _, throwable ->
                        Log.e(TAG, "Error cargando imagen de fondo: ${throwable.throwable.message}")
                    },
                )
            }
        } else {
            waitingBackground.setImageResource(R.drawable.bg_guia_fallback)
        }

        // Nombre del evento también en la pantalla de guiding (etiqueta inferior)
        guidingEventName.text = nombreEvento
    }

    // ─────────────────────────── Button ────────────────────────────

    private fun wireStartButton() {
        startButton.setOnClickListener { v ->
            v.isEnabled = false // Prevenir double-tap

            // Animación de feedback táctil
            ObjectAnimator.ofPropertyValuesHolder(
                v,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 0.95f, 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 0.95f, 1f),
            ).apply { duration = 150 }.start()

            Log.d(TAG, "Botón pulsado — enviando broadcast GUIA_USER_TAPPED_START")

            // Broadcast global (mismo patrón que RatingActivity.notifyRatingManager)
            sendBroadcast(
                Intent(ACTION_GUIA_USER_TAPPED_START).apply {
                    `package` = packageName
                },
            )
        }
    }

    // ─────────────────────────── Receivers ────────────────────────────

    private fun registerReceivers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(showGuidingReceiver, IntentFilter(ACTION_GUIA_SHOW_GUIDING), Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(showWaitingReceiver, IntentFilter(ACTION_GUIA_SHOW_WAITING), Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(closeReceiver, IntentFilter(ACTION_GUIA_CLOSE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(showGuidingReceiver, IntentFilter(ACTION_GUIA_SHOW_GUIDING))
            registerReceiver(showWaitingReceiver, IntentFilter(ACTION_GUIA_SHOW_WAITING))
            registerReceiver(closeReceiver, IntentFilter(ACTION_GUIA_CLOSE))
        }
    }

    // ─────────────────────────── View-state switchers ────────────────────────────

    private fun showGuiding() {
        val transition = TransitionSet().apply {
            addTransition(Fade(Fade.OUT).apply { duration = 250 }.addTarget(waitingContainer))
            addTransition(Slide(Gravity.BOTTOM).apply { duration = 350 }.addTarget(guidingContainer))
            ordering = TransitionSet.ORDERING_TOGETHER
        }
        TransitionManager.beginDelayedTransition(guiaRoot, transition)
        waitingContainer.visibility = View.GONE
        guidingContainer.visibility = View.VISIBLE
        startVideoPlayback()
    }

    private fun showWaiting() {
        val transition = TransitionSet().apply {
            addTransition(Fade(Fade.OUT).apply { duration = 250 }.addTarget(guidingContainer))
            addTransition(Slide(Gravity.BOTTOM).apply { duration = 350 }.addTarget(waitingContainer))
            ordering = TransitionSet.ORDERING_TOGETHER
        }
        TransitionManager.beginDelayedTransition(guiaRoot, transition)
        stopVideoPlayback()
        guidingContainer.visibility = View.GONE
        waitingContainer.visibility = View.VISIBLE
        startButton.isEnabled = true
    }

    // ─────────────────────────── Video ────────────────────────────

    private fun startVideoPlayback() {
        if (videoLoopUrl.isBlank()) {
            Log.w(TAG, "videoLoopUrl vacío — mostrando fallback de imagen")
            guidingLoading.visibility = View.GONE
            guidingVideo.visibility = View.GONE
            showFallbackImage()
            return
        }

        Log.d(TAG, "Iniciando reproducción de video: $videoLoopUrl")
        guidingLoading.visibility = View.VISIBLE

        guidingVideo.setVideoURI(Uri.parse(videoLoopUrl))

        guidingVideo.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f) // Silenciar — el TTS es dueño del audio
            guidingLoading.visibility = View.GONE
            guidingVideo.start()
            Log.d(TAG, "Video listo y reproduciéndose en loop")
        }

        guidingVideo.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "Error en VideoView: what=$what extra=$extra — mostrando fallback")
            guidingLoading.visibility = View.GONE
            guidingVideo.visibility = View.GONE
            showFallbackImage()
            true // Error manejado
        }
    }

    private fun stopVideoPlayback() {
        if (guidingVideo.isPlaying) {
            guidingVideo.stopPlayback()
            Log.d(TAG, "Video detenido")
        }
    }

    /**
     * Fallback: si el video no carga, mostrar la imagen de fondo via Coil
     * como una ImageView programática en guidingContainer.
     */
    private fun showFallbackImage() {
        Log.d(TAG, "Mostrando imagen de fallback en guidingContainer")
        val fallback = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        if (imagenFondoUrl.isNotBlank()) {
            fallback.load(imagenFondoUrl) {
                error(R.drawable.bg_guia_fallback)
            }
        } else {
            fallback.setImageResource(R.drawable.bg_guia_fallback)
        }
        guidingContainer.addView(fallback, 0)
    }

    // ─────────────────────────── Back / Home block ────────────────────────────

    @Deprecated("Overriding for back-press lock during guia session")
    override fun onBackPressed() {
        // No-op — bloqueado durante toda la sesión de guia
        Log.d(TAG, "Back button bloqueado en GuiaActivity")
    }

    override fun onUserLeaveHint() {
        // No-op — prevenir que el Home button minimice la app
        Log.d(TAG, "onUserLeaveHint bloqueado en GuiaActivity")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            Log.d(TAG, "Home key consumida")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ─────────────────────────── Lifecycle ────────────────────────────

    override fun onDestroy() {
        // Desregistrar receivers — mismo patrón defensivo que AnnouncementActivity
        for (receiver in listOf(showGuidingReceiver, showWaitingReceiver, closeReceiver)) {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignorar si ya fue desregistrado
            }
        }

        // Liberar VideoView
        try {
            guidingVideo.stopPlayback()
        } catch (e: Exception) {
            // Puede fallar si el video nunca se inició
        }

        super.onDestroy()
        Log.d(TAG, "GuiaActivity destruida")
    }
}
