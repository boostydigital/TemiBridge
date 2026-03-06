package com.spatium.deamon.db.temi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.robotemi.sdk.Robot
import com.spatium.deamon.db.temi.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PartyActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PartyActivity"
        const val EXTRA_PHOTO_PATH = "photo_path"
        private const val REQUEST_COUNTDOWN = 1001
        private const val REQUEST_PREVIEW = 1002
    }

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var robot: Robot? = null
    private var isCameraReady = false

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d(TAG, "[CAMERA] Permiso concedido, iniciando cámara")
                startCamera()
            } else {
                Log.w(TAG, "[CAMERA] Permiso denegado")
                Toast.makeText(this, "Se requiere permiso de cámara para tomar fotos.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_party)
        Log.d(TAG, "PartyActivity.onCreate")

        setupFullscreen()
        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.partyPreviewView)

        // Inicializar Robot SDK
        robot = Robot.getInstance()
        
        // Desactivar seguimiento de cara y hacer que el robot hable
        disableFaceTrackingAndSpeak()

        setupButtons()
        checkCameraPermission()
    }

    private fun disableFaceTrackingAndSpeak() {
        try {
            // Detener movimiento (desactiva seguimiento de cara del usuario)
            robot?.stopMovement()
            Log.d(TAG, "[ROBOT] ✓ Movimiento detenido - seguimiento de cara desactivado")

            // Hacer que el robot diga el mensaje
            val message = "Ajusta mi cabeza para una mejor foto"
            try {
                val ttsRequest = com.robotemi.sdk.TtsRequest.create(message, false)
                robot?.speak(ttsRequest)
                Log.d(TAG, "[ROBOT] ✓ Robot hablando: $message")
            } catch (e: Exception) {
                Log.w(TAG, "[ROBOT] ⚠️ Error al hablar (continuando sin audio): ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[ROBOT] ⚠️ Error deteniendo movimiento: ${e.message}")
        }
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
        findViewById<FrameLayout>(R.id.btnPartyBack).setOnClickListener {
            Log.d(TAG, "[UI] Volver a MainActivity")
            returnToMain()
        }

        findViewById<FrameLayout>(R.id.btnCapture).setOnClickListener {
            Log.d(TAG, "[UI] Botón captura tocado - iniciando countdown")
            launchCountdown()
        }

        findViewById<LinearLayout>(R.id.btnFlipCamera).setOnClickListener {
            Log.d(TAG, "[UI] Girar cámara")
            flipCamera()
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "[CAMERA] Permiso ya concedido")
                startCamera()
            }
            else -> {
                Log.d(TAG, "[CAMERA] Solicitando permiso de cámara")
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, currentCameraSelector, preview, imageCapture
                )
                isCameraReady = true
                Log.d(TAG, "[CAMERA] ✓ Cámara lista: $currentCameraSelector")
            } catch (e: Exception) {
                isCameraReady = false
                Log.e(TAG, "[CAMERA] ✗ Error iniciando cámara: ${e.message}", e)
                Toast.makeText(this, "Error al iniciar cámara: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            Log.d(TAG, "[CAMERA] Cambiando a cámara trasera")
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            Log.d(TAG, "[CAMERA] Cambiando a cámara frontal")
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        startCamera()
    }

    private fun launchCountdown() {
        Log.d(TAG, "[COUNTDOWN] Lanzando CountdownActivity")
        val intent = Intent(this, CountdownActivity::class.java)
        startActivityForResult(intent, REQUEST_COUNTDOWN)
    }

    fun takePictureAndProceed() {
        if (!isCameraReady) {
            Log.w(TAG, "[CAPTURE] Cámara no está lista aún")
            Toast.makeText(this, "Cámara no lista, espera un momento...", Toast.LENGTH_SHORT).show()
            return
        }

        val capture = imageCapture ?: run {
            Log.e(TAG, "[CAPTURE] ✗ imageCapture es null (cámara no inicializada)")
            isCameraReady = false
            Toast.makeText(this, "Error: cámara no inicializada. Reiniciando...", Toast.LENGTH_SHORT).show()
            startCamera()
            return
        }

        val photoFile = try {
            createPhotoFile()
        } catch (e: Exception) {
            Log.e(TAG, "[CAPTURE] ✗ Error creando archivo de foto: ${e.message}", e)
            Toast.makeText(this, "Error: no se puede guardar foto (permisos)", Toast.LENGTH_SHORT).show()
            return
        }

        if (photoFile.parentFile?.exists() != true) {
            Log.e(TAG, "[CAPTURE] ✗ Directorio de almacenamiento no existe")
            Toast.makeText(this, "Error: directorio de almacenamiento no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "[CAPTURE] Capturando foto en: ${photoFile.absolutePath}")
        playShutterSound()

        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(photoFile).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "[CAPTURE] ✓ Foto guardada: ${photoFile.absolutePath}")
                    openPhotoPreview(photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "[CAPTURE] ✗ Error capturando foto: ${exception.message}", exception)
                    Toast.makeText(this@PartyActivity, "Error al tomar foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun playShutterSound() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            // Sonido de obturador: tono doble para simular clic de cámara
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100)
            Handler(Looper.getMainLooper()).postDelayed({
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                toneGen.release()
            }, 120)
            Log.d(TAG, "[SOUND] Sonido de obturador reproducido")
        } catch (e: Exception) {
            Log.w(TAG, "[SOUND] Error reproduciendo sonido: ${e.message}")
        }
    }

    private fun createPhotoFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            ?: throw IllegalStateException("No se puede acceder al directorio de almacenamiento")
        
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            throw IllegalStateException("No se puede crear directorio de almacenamiento")
        }
        
        return File.createTempFile("PARTY_${timestamp}_", ".jpg", storageDir).also {
            Log.d(TAG, "[FILE] Archivo de foto creado: ${it.absolutePath}")
        }
    }

    private fun openPhotoPreview(photoPath: String) {
        Log.d(TAG, "[PREVIEW] Abriendo PhotoPreviewActivity con foto: $photoPath")
        val intent = Intent(this, PhotoPreviewActivity::class.java).apply {
            putExtra(EXTRA_PHOTO_PATH, photoPath)
        }
        startActivityForResult(intent, REQUEST_PREVIEW)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_COUNTDOWN -> {
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "[RESULT] Countdown completado, reiniciando cámara antes de tomar foto")
                    // Reinicializar la cámara después de que CountdownActivity se cierre
                    startCamera()
                    // Esperar a que la cámara se inicialice antes de tomar la foto
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "[RESULT] Cámara reiniciada, tomando foto")
                        takePictureAndProceed()
                    }, 500)
                } else {
                    Log.d(TAG, "[RESULT] Countdown cancelado")
                }
            }
            REQUEST_PREVIEW -> {
                Log.d(TAG, "[RESULT] Resultado de PhotoPreviewActivity recibido")
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "[RESULT] Usuario aprobó foto, volviendo a Main/SelfieHunter")
                    returnToMain()
                } else {
                    Log.d(TAG, "[RESULT] Usuario quiere repetir foto - reiniciando cámara")
                    startCamera()
                }
            }
        }
    }

    private fun returnToMain() {
        // Si vinimos de SelfieHunterActivity, retornar allá sin lanzar nueva instancia
        // Si vinimos de MainActivity, retornar allá sin lanzar nueva instancia
        if (this.intent.getBooleanExtra("return_to_selfie_hunter", false)) {
            Log.d(TAG, "[RETURN] Retornando a SelfieHunterActivity (sin startActivity)")
            setResult(RESULT_OK)
        } else {
            Log.d(TAG, "[RETURN] Retornando a MainActivity (sin startActivity)")
            setResult(RESULT_OK)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        Log.d(TAG, "PartyActivity destruida")
    }
}
