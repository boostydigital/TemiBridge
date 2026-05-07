package com.spatium.deamon.db.temi.ui

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.spatium.deamon.db.temi.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CountdownActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CountdownActivity"
        private const val COUNTDOWN_SECONDS = 3
    }

    private lateinit var tvCountdown: TextView
    private lateinit var ringView: CountdownRingView
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private val handler = Handler(Looper.getMainLooper())
    private var currentCount = COUNTDOWN_SECONDS
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown)
        Log.d(TAG, "CountdownActivity.onCreate - iniciando countdown $COUNTDOWN_SECONDS")

        setupFullscreen()

        tvCountdown = findViewById(R.id.tvCountdownNumber)
        ringView = findViewById(R.id.countdownRing)
        previewView = findViewById(R.id.countdownPreviewView)
        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()

        toneGenerator = try {
            // Usar STREAM_MUSIC para mejor audibilidad, volumen máximo (100)
            ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear ToneGenerator: ${e.message}")
            null
        }

        findViewById<FrameLayout>(R.id.btnCountdownClose).setOnClickListener {
            Log.d(TAG, "[UI] Countdown cancelado por usuario")
            cancelCountdown()
        }

        startCountdown()
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    currentCameraSelector,
                    preview,
                )

                Log.d(TAG, "[CAMERA] Cámara iniciada en countdown: $currentCameraSelector")
            } catch (e: Exception) {
                Log.e(TAG, "[CAMERA] Error iniciando cámara en countdown: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCountdown() {
        Log.d(TAG, "[COUNTDOWN] Iniciando en $currentCount")
        updateUI(currentCount)
        scheduleNextTick()
    }

    private fun scheduleNextTick() {
        handler.postDelayed({
            currentCount--
            Log.d(TAG, "[COUNTDOWN] Tick: $currentCount")

            if (currentCount > 0) {
                playBeep()
                updateUI(currentCount)
                scheduleNextTick()
            } else {
                playFinalBeep()
                Log.d(TAG, "[COUNTDOWN] Completado - enviando resultado OK")
                finishWithSuccess()
            }
        }, 1000L)
    }

    private fun updateUI(count: Int) {
        tvCountdown.text = count.toString()

        val progress = count.toFloat() / COUNTDOWN_SECONDS.toFloat()
        ringView.progress = progress

        tvCountdown.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(100)
            .withEndAction {
                tvCountdown.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }.start()
    }

    private fun playBeep() {
        try {
            Log.d(TAG, "[SOUND] Reproduciendo beep para número (TONE_CDMA_ALERT_CALL_GUARD, 200ms)")
            if (toneGenerator == null) {
                Log.w(TAG, "[SOUND] ToneGenerator es null, no se puede reproducir beep")
                return
            }
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
            Log.d(TAG, "[SOUND] Beep iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "[SOUND] Error reproduciendo beep: ${e.message}", e)
        }
    }

    private fun playFinalBeep() {
        try {
            Log.d(TAG, "[SOUND] Reproduciendo beep final (TONE_PROP_ACK, 500ms)")
            if (toneGenerator == null) {
                Log.w(TAG, "[SOUND] ToneGenerator es null, no se puede reproducir beep final")
                return
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 500)
            Log.d(TAG, "[SOUND] Beep final iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "[SOUND] Error reproduciendo beep final: ${e.message}", e)
        }
    }

    private fun finishWithSuccess() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun cancelCountdown() {
        handler.removeCallbacksAndMessages(null)
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        toneGenerator?.release()
        toneGenerator = null
        cameraExecutor.shutdown()
        Log.d(TAG, "CountdownActivity destruida")
    }

    override fun onBackPressed() {
        cancelCountdown()
        super.onBackPressed()
    }
}
