package com.spatium.deamon.db.temi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.spatium.deamon.db.temi.BuildConfig
import com.spatium.deamon.db.temi.R
import com.spatium.deamon.db.temi.core.AnnouncementManager
import com.spatium.deamon.db.temi.core.CheckinHandler
import com.spatium.deamon.db.temi.core.GoogleTTS
import com.spatium.deamon.db.temi.core.GuiaManager
import com.spatium.deamon.db.temi.core.TemiController
import com.spatium.deamon.db.temi.net.OkHttpSupabaseGateway
import com.spatium.temibridge.core.RatingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity :
    AppCompatActivity(),
    OnRobotReadyListener {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var lastScanTime = 0L
    private val SCAN_COOLDOWN_MS = 3000L // 3 segundos entre escaneos
    private var pendingFotosLaunch = false

    // Manager para modo anuncio con patrullaje
    private lateinit var announcementManager: AnnouncementManager

    // Manager para modo rating/evaluación de salones
    private lateinit var ratingManager: RatingManager

    // Manager para modo guia (visita guiada)
    private lateinit var guiaManager: GuiaManager

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            when {
                isGranted && pendingFotosLaunch -> {
                    TemiController.disableFaceTracking()
                    val intent = Intent(this, com.spatium.deamon.db.temi.ui.MapSelectorActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    pendingFotosLaunch = false
                }
                !isGranted && pendingFotosLaunch -> {
                    Toast.makeText(this, "Se requiere permiso de cámara para usar Fotos", Toast.LENGTH_LONG).show()
                    pendingFotosLaunch = false
                }
                isGranted -> {
                    startContinuousScanning()
                }
                else -> {
                    Toast.makeText(this, "Se requiere cámara para escanear QR codes.", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("TemiBridge", "MainActivity.onCreate - setContentView OK")

        setupFullscreen()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupTiles()
        setupBottomNav()

        // Iniciar AnnouncementManager para polling de anuncios
        Log.d("TemiBridge", "Inicializando AnnouncementManager...")
        announcementManager = AnnouncementManager(this)
        announcementManager.startPolling()
        Log.d("TemiBridge", "AnnouncementManager polling iniciado")

        // Iniciar RatingManager para polling de evaluaciones de salones
        Log.d("TemiBridge", "Inicializando RatingManager...")
        ratingManager = RatingManager(this)
        ratingManager.startPolling()
        Log.d("TemiBridge", "RatingManager polling iniciado")

        // Iniciar GuiaManager para modo guia (visita guiada)
        Log.d("TemiBridge", "Inicializando GuiaManager...")
        guiaManager = GuiaManager(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            guiaManager.sweepStaleGuias()
        }
        guiaManager.startPolling()
        Log.d("TemiBridge", "GuiaManager polling iniciado")

        checkCameraOnStart()
        Log.d("TemiBridge", "MainActivity iniciada con nuevo diseño grid")
    }

    override fun onStart() {
        super.onStart()
        Robot.getInstance().addOnRobotReadyListener(this)
        Log.d("TemiBridge", "onStart - OnRobotReadyListener registrado")
    }

    override fun onStop() {
        super.onStop()
        // No removemos el listener para que el ready event no se pierda si MainActivity
        // pasa a background mientras el SDK aún no emitió ready (workaround del cold-boot).
        Log.d("TemiBridge", "onStop - OnRobotReadyListener mantenido para no perder ready event")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Robot.getInstance().removeOnRobotReadyListener(this)
            Log.d("TemiBridge", "onDestroy - OnRobotReadyListener removido")
        } catch (t: Throwable) {
            Log.w("TemiBridge", "onDestroy - error removiendo listener: ${t.message}")
        }
        cameraExecutor.shutdown()
        announcementManager.destroy()
        ratingManager.destroy()
        guiaManager.destroy()
    }

    override fun onRobotReady(isReady: Boolean) {
        Log.d("TemiBridge", "Robot ready: $isReady")
        TemiController.notifyRobotReady(isReady)
        if (isReady) {
            try {
                val activityInfo = packageManager.getActivityInfo(
                    android.content.ComponentName(this, MainActivity::class.java),
                    PackageManager.GET_META_DATA,
                )
                Robot.getInstance().onStart(activityInfo)
                Log.d("TemiBridge", "Robot.onStart(activityInfo) llamado — handshake SDK completo")
            } catch (t: Throwable) {
                Log.w("TemiBridge", "Robot.onStart(activityInfo) fallo: ${t.message}", t)
            }
            // Activar Kiosk Mode apenas el SDK esté listo (asegura que la app
            // se inicie en kiosko aunque onResume aún no haya corrido).
            try {
                TemiController.setKioskModeOn(true)
                Log.d("TemiBridge", "Kiosk Mode activado en onRobotReady (boot)")
            } catch (t: Throwable) {
                Log.w("TemiBridge", "setKioskModeOn fallo: ${t.message}", t)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Asegurar que Kiosk Mode esté activo para mantener la app en primer plano
        TemiController.setKioskModeOn(true)
        Log.d("TemiBridge", "onResume - Kiosk Mode activado")
    }

    /**
     * Solicita el permiso de cámara en el primer arranque si aún no fue concedido.
     * Solo pide una vez de forma proactiva; no nag al usuario si ya denegó permanentemente.
     */
    private fun checkCameraOnStart() {
        val granted = checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) return
        val prefs = getSharedPreferences("camera_perm", android.content.Context.MODE_PRIVATE)
        val askedOnce = prefs.getBoolean("asked_once", false)
        if (!askedOnce) {
            requestCameraPermission.launch(android.Manifest.permission.CAMERA)
            prefs.edit().putBoolean("asked_once", true).apply()
        }
        // If askedOnce=true and not granted → permanently denied; no Settings nag on cold start
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupTiles() {
        // Check-In: escaneo QR para bienvenida
        findViewById<FrameLayout>(R.id.tileCheckin).setOnClickListener {
            Log.d("TemiBridge", "[TILE] Check-In tocado")
            ensureCameraPermissionAndStart()
            showTileAnimation(it)
        }

        // Rating: abrir pantalla de valoración
        findViewById<FrameLayout>(R.id.tileOpinar).setOnClickListener {
            Log.d("TemiBridge", "[TILE] Rating tocado")
            showTileAnimation(it)
            val intent = Intent(this, RatingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Pedir: abrir MenuActivity
        findViewById<FrameLayout>(R.id.tilePedir).setOnClickListener {
            Log.d("TemiBridge", "[TILE] Pedir tocado")
            showTileAnimation(it)
            val intent = Intent(this, MenuActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Gestionar: admin
        findViewById<FrameLayout>(R.id.tileGestionar).setOnClickListener {
            Log.d("TemiBridge", "[TILE] Gestionar tocado")
            showTileAnimation(it)
            openWebView("https://spatium-desk.lovable.app")
        }

        // Explorar: tour del robot
        findViewById<FrameLayout>(R.id.tileExplorar).setOnClickListener {
            Log.d("TemiBridge", "[TILE] Explorar tocado")
            showTileAnimation(it)
            val tourId = "68ac8a6466a57fda1359c414"
            val ok = TemiController.playTourById(tourId)
            if (!ok) Toast.makeText(this, "No se pudo iniciar el tour", Toast.LENGTH_LONG).show()
        }

        // Tour: ejecutar tour del robot
        findViewById<FrameLayout>(R.id.tileGuiar).setOnClickListener {
            Log.d("TemiBridge", "[TILE] Tour tocado")
            showTileAnimation(it)
            val tourId = "68ac8a6466a57fda1359c414"
            val ok = TemiController.playTourById(tourId)
            if (!ok) {
                TemiController.speak("¿A dónde te guío hoy?")
            }
        }

        // Party: cámara fotográfica con envío por WhatsApp
        findViewById<FrameLayout>(R.id.tileParty).setOnClickListener {
            Log.d("TemiBridge", "[TILE] Party tocado - abriendo cámara party")
            showTileAnimation(it)
            // Desactivar face tracking inmediatamente
            TemiController.disableFaceTracking()
            val intent = Intent(this, PartyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Fotos: Selfie Hunter — selector de ubicaciones del mapa + deambulación
        findViewById<FrameLayout>(R.id.tileFotos).setOnClickListener {
            Log.d("TemiBridge", "[TILE] Fotos tocado - verificando permiso de cámara")
            showTileAnimation(it)
            val prefs = getSharedPreferences("camera_perm", android.content.Context.MODE_PRIVATE)
            val previouslyAsked = prefs.getBoolean("asked_once", false)
            val granted = checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val shouldShowRationale = shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)
            when (com.spatium.deamon.db.temi.core.CameraPermissionGate.decide(granted, shouldShowRationale, previouslyAsked)) {
                is com.spatium.deamon.db.temi.core.CameraPermissionGate.Decision.Allow -> {
                    TemiController.disableFaceTracking()
                    val intent = Intent(this, com.spatium.deamon.db.temi.ui.MapSelectorActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
                is com.spatium.deamon.db.temi.core.CameraPermissionGate.Decision.Request -> {
                    pendingFotosLaunch = true
                    prefs.edit().putBoolean("asked_once", true).apply()
                    requestCameraPermission.launch(android.Manifest.permission.CAMERA)
                }
                is com.spatium.deamon.db.temi.core.CameraPermissionGate.Decision.ShowSettings -> {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Permiso de cámara requerido")
                        .setMessage("Para usar Fotos, habilitá el permiso de cámara en Configuración.")
                        .setPositiveButton("Configuración") { _, _ ->
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
                is com.spatium.deamon.db.temi.core.CameraPermissionGate.Decision.Deny -> {
                    Toast.makeText(this, "Se requiere permiso de cámara para usar Fotos", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupBottomNav() {
        // Inicio: ya estamos aquí
        findViewById<LinearLayout>(R.id.navInicio).setOnClickListener {
            Log.d("TemiBridge", "[NAV] Inicio")
        }
        // Servicios: tile Pedir
        findViewById<LinearLayout>(R.id.navServicios).setOnClickListener {
            Log.d("TemiBridge", "[NAV] Servicios")
            val intent = Intent(this, MenuActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
        // Actividad: historial (web)
        findViewById<LinearLayout>(R.id.navActividad).setOnClickListener {
            Log.d("TemiBridge", "[NAV] Actividad")
            openWebView("https://spatium-desk.lovable.app/actividad")
        }
        // QR: escaneo manual
        findViewById<LinearLayout>(R.id.navQr).setOnClickListener {
            Log.d("TemiBridge", "[NAV] QR Scanner")
            ensureCameraPermissionAndStart()
        }
    }

    private fun openWebView(url: String) {
        try {
            val intent = Intent(this, KioskWebActivity::class.java).apply {
                putExtra(KioskWebActivity.EXTRA_URL, url)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (t: Throwable) {
            Log.w("TemiBridge", "Error abriendo WebView: ${t.message}")
        }
    }

    private fun showTileAnimation(view: View) {
        playSuccessSound()
        view.animate()
            .scaleX(0.95f).scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(150)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }.start()
    }

    /**
     * Verifica permiso de cámara e inicia el escáner
     */
    private fun ensureCameraPermissionAndStart() {
        val permission = Manifest.permission.CAMERA
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                startContinuousScanning()
            }
            else -> {
                requestCameraPermission.launch(permission)
            }
        }
    }

    /**
     * Inicia la cámara usando CameraX + ML Kit para escaneo de QR
     */
    private fun startContinuousScanning() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                Log.d("TemiBridge", "[CAMERA] Cámaras disponibles:")
                cameraProvider.availableCameraInfos.forEachIndexed { index, info ->
                    Log.d("TemiBridge", "[CAMERA] - Cámara $index: $info")
                }

                // Preview
                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Image analysis para ML Kit con resolución más baja
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            QRCodeAnalyzer { qrContent ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastScanTime > SCAN_COOLDOWN_MS) {
                                    lastScanTime = currentTime
                                    Log.d("TemiBridge", "[ML_KIT] QR escaneado: $qrContent")
                                    runOnUiThread {
                                        handleQrContent(qrContent)
                                    }
                                }
                            },
                        )
                    }

                // Desvincular casos de uso anteriores
                cameraProvider.unbindAll()

                // Intentar con diferentes selectores de cámara
                val cameraSelectors = listOf(
                    CameraSelector.DEFAULT_BACK_CAMERA to "trasera",
                    CameraSelector.DEFAULT_FRONT_CAMERA to "frontal",
                )

                var cameraStarted = false
                for ((selector, name) in cameraSelectors) {
                    try {
                        Log.d("TemiBridge", "[CAMERA] Intentando con cámara $name...")

                        camera = cameraProvider.bindToLifecycle(
                            this,
                            selector,
                            preview,
                            imageAnalyzer,
                        )

                        Log.d("TemiBridge", "[CAMERA] ========== CameraX + ML Kit iniciado (cámara $name) ==========")
                        Toast.makeText(this, "Cámara QR activa ✓ (ML Kit - $name)", Toast.LENGTH_SHORT).show()
                        cameraStarted = true
                        break
                    } catch (e: Exception) {
                        Log.w("TemiBridge", "[CAMERA] Cámara $name no disponible: ${e.message}")
                    }
                }

                if (!cameraStarted) {
                    throw Exception("No se pudo iniciar ninguna cámara")
                }
            } catch (e: Exception) {
                Log.e("TemiBridge", "[CAMERA] ERROR FATAL: ${e.message}", e)
                e.printStackTrace()
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Analizador de imágenes para detectar códigos QR con ML Kit
     */
    private class QRCodeAnalyzer(private val onQRCodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                onQRCodeDetected(value)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("TemiBridge", "[ML_KIT] Error: ${e.message}")
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun handleQrContent(content: String) {
        // 1) Nuevo formato soportado: deep link mytemi://...
        //    Ej: mytemi://welcome?text=Hola%20Mario...&place=Salon_Duarte
        if (content.startsWith("mytemi://", ignoreCase = true)) {
            runCatching { Uri.parse(content) }.onSuccess { uri ->
                if (uri != null && uri.scheme == "mytemi") {
                    when (uri.host) {
                        "guest" -> {
                            showSuccessAnimation()
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val result = checkinHandler.handle(uri)
                                    if (result != null) {
                                        GoogleTTS.speak(applicationContext, result.messageToSpeak)
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            val intent = Intent(this@MainActivity, MenuActivity::class.java).apply {
                                                putExtra(MenuActivity.EXTRA_PLACE, "Recepcion")
                                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                            }
                                            startActivity(intent)
                                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                                        }, 1_000)
                                    }
                                } catch (t: Throwable) {
                                    Log.w("TemiBridge", "[CHECKIN] Error procesando check-in: ${t.message}", t)
                                    Toast.makeText(this@MainActivity, "Error al procesar check-in", Toast.LENGTH_LONG).show()
                                }
                            }
                            return
                        }
                        "welcome" -> {
                            val text = decodeParam(uri.getQueryParameter("text")).trim()
                            val place = decodeParam(uri.getQueryParameter("place")).trim()
                            val recepcion = decodeParam(uri.getQueryParameter("recepcion"))
                            val telefono = decodeParam(uri.getQueryParameter("telefono"))
                            showSuccessAnimation()
                            if (text.isNotBlank()) TemiController.speak(text)
                            if (place.isNotBlank()) TemiController.goTo(place)
                            postWebhookAndMaybeOpen(recepcion, telefono)
                            return
                        }
                        "say" -> {
                            val text = decodeParam(uri.getQueryParameter("text")).trim()
                            val recepcion = decodeParam(uri.getQueryParameter("recepcion"))
                            val telefono = decodeParam(uri.getQueryParameter("telefono"))
                            if (text.isNotBlank()) {
                                showSuccessAnimation()
                                TemiController.speak(text)
                            } else {
                                Toast.makeText(this, "QR inválido: falta text", Toast.LENGTH_LONG).show()
                            }
                            postWebhookAndMaybeOpen(recepcion, telefono)
                            return
                        }
                        "go" -> {
                            val place = decodeParam(uri.getQueryParameter("place")).trim()
                            val recepcion = decodeParam(uri.getQueryParameter("recepcion"))
                            val telefono = decodeParam(uri.getQueryParameter("telefono"))
                            val recBool = recepcion.equals("true", ignoreCase = true)
                            if (place.isNotBlank()) {
                                showSuccessAnimation()
                                // Si NO es recepción, al llegar anuncia y luego va a "entrada" tras 10s
                                if (!recBool) {
                                    Log.d("TemiBridge", "[GO] recepcion=false: programando arrival callback para place=$place")
                                    TemiController.setArrivalCallbackOnce {
                                        // Garantizar ejecución en hilo principal
                                        Handler(Looper.getMainLooper()).post {
                                            Log.d("TemiBridge", "[GO] arrival callback ejecutado: anunciando llegada y preparando retorno a entrada")
                                            TemiController.speak("Hemos llegado a tu destino, tu anfitrión te atenderá. Gracias")
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                Log.d("TemiBridge", "[GO] 10s transcurridos: ejecutando goTo('entrada')")
                                                TemiController.goTo("entrada")
                                            }, 10_000)
                                        }
                                    }
                                }
                                // Volver al comportamiento anterior: navegar al lugar usando goTo
                                Log.d("TemiBridge", "[GO] Llamando goTo(place=$place) recBool=$recBool")
                                TemiController.goTo(place)
                            } else {
                                Toast.makeText(this, "QR inválido: falta place", Toast.LENGTH_LONG).show()
                            }
                            postWebhookAndMaybeOpen(recepcion, telefono)
                            return
                        }
                        "tour" -> {
                            val name = decodeParam(uri.getQueryParameter("name")).trim()
                            val tourId = decodeParam(uri.getQueryParameter("tourId")).trim()
                            val recepcion = decodeParam(uri.getQueryParameter("recepcion"))
                            val telefono = decodeParam(uri.getQueryParameter("telefono"))
                            val identifier = if (name.isNotBlank()) name else tourId
                            if (identifier.isNotBlank()) {
                                showSuccessAnimation()
                                startTour(identifier)
                            } else {
                                Toast.makeText(this, "QR inválido: falta name o tourId", Toast.LENGTH_LONG).show()
                            }
                            postWebhookAndMaybeOpen(recepcion, telefono)
                            return
                        }
                        "sequence" -> {
                            val idParam = decodeParam(uri.getQueryParameter("id")).trim()
                            val altIdParam = decodeParam(uri.getQueryParameter("sequenceId")).trim()
                            val rawName = decodeParam(uri.getQueryParameter("name")).trim()
                            val text = decodeParam(uri.getQueryParameter("text")).trim()
                            val nameLooksLikeId = rawName.length == 24 && rawName.all { it in "0123456789abcdefABCDEF" }
                            val sequenceId = when {
                                idParam.isNotBlank() -> idParam
                                altIdParam.isNotBlank() -> altIdParam
                                nameLooksLikeId -> rawName
                                else -> ""
                            }
                            val name = if (sequenceId.isNotBlank() && sequenceId == rawName) "" else rawName
                            if (sequenceId.isNotBlank()) {
                                showSuccessAnimation()
                                if (text.isNotBlank()) {
                                    GoogleTTS.speak(applicationContext, text)
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        executeSequenceById(sequenceId)
                                    }, 3000)
                                } else {
                                    executeSequenceById(sequenceId)
                                }
                            } else if (name.isNotBlank()) {
                                showSuccessAnimation()
                                if (text.isNotBlank()) {
                                    GoogleTTS.speak(applicationContext, text)
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        executeSequence(name)
                                    }, 3000)
                                } else {
                                    executeSequence(name)
                                }
                            } else {
                                Toast.makeText(this, "QR inválido: falta id de secuencia", Toast.LENGTH_LONG).show()
                            }
                            return
                        }
                        "escort" -> {
                            // Formato: mytemi://escort?greeting=Bienvenido...&waitTime=5&place=E2&arrivalGreeting=Disfruta...&returnTo=E1&arrivalDelay=35
                            val initialGreeting = decodeParam(uri.getQueryParameter("greeting")).trim()
                            val arrivalGreeting = decodeParam(uri.getQueryParameter("arrivalGreeting")).trim()
                            val waitTimeStr = decodeParam(uri.getQueryParameter("waitTime")).trim()
                            val place = decodeParam(uri.getQueryParameter("place")).trim()
                            val returnTo = decodeParam(uri.getQueryParameter("returnTo")).trim()
                            val arrivalDelayStr = decodeParam(uri.getQueryParameter("arrivalDelay")).trim()

                            val waitTimeSeconds = waitTimeStr.toLongOrNull() ?: 5L
                            val arrivalDelaySeconds = arrivalDelayStr.toLongOrNull() // puede ser null si no se envía

                            Log.d("TemiBridge", "========== ESCORT INICIADO ==========")
                            Log.d("TemiBridge", "[ESCORT] initialGreeting=$initialGreeting")
                            Log.d("TemiBridge", "[ESCORT] place=$place")
                            Log.d("TemiBridge", "[ESCORT] arrivalGreeting=$arrivalGreeting")
                            Log.d("TemiBridge", "[ESCORT] waitTime=$waitTimeSeconds, returnTo=$returnTo, arrivalDelay=$arrivalDelaySeconds")

                            if (initialGreeting.isNotBlank()) {
                                showSuccessAnimation()

                                // Usar applicationContext para evitar problemas de lifecycle
                                val appContext = applicationContext

                                // Guardar variables para el callback y temporizadores
                                val savedPlace = place
                                val savedArrivalGreeting = arrivalGreeting
                                val savedWaitTime = waitTimeSeconds
                                val savedReturnTo = returnTo
                                var arrivalGreetingSpoken = false

                                // Helper para programar el regreso (returnTo)
                                val scheduleReturn: () -> Unit = {
                                    if (savedReturnTo.isNotBlank()) {
                                        Log.d("TemiBridge", "[ESCORT] Programando regreso a $savedReturnTo en ${savedWaitTime}s")
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            Log.d("TemiBridge", "[ESCORT] Regresando a $savedReturnTo")
                                            TemiController.goTo(savedReturnTo)
                                        }, savedWaitTime * 1000)
                                    }
                                }

                                // Configurar callback para cuando llegue ANTES de hablar
                                if (savedPlace.isNotBlank()) {
                                    TemiController.setArrivalCallbackOnce {
                                        Log.d("TemiBridge", "========== CALLBACK LLEGADA EJECUTADO ==========")
                                        Log.d("TemiBridge", "[ESCORT] Llegamos a $savedPlace")
                                        Log.d("TemiBridge", "[ESCORT] arrivalGreeting a reproducir: $savedArrivalGreeting")

                                        // Ejecutar en hilo principal
                                        Handler(Looper.getMainLooper()).post {
                                            Log.d("TemiBridge", "[ESCORT] En hilo principal (callback de llegada)")

                                            if (savedArrivalGreeting.isNotBlank()) {
                                                if (!arrivalGreetingSpoken) {
                                                    arrivalGreetingSpoken = true
                                                    Log.d("TemiBridge", "[ESCORT] arrivalGreeting por callback de llegada")
                                                    TemiController.speak(savedArrivalGreeting)
                                                    scheduleReturn()
                                                } else {
                                                    Log.d("TemiBridge", "[ESCORT] arrivalGreeting ya fue dicho por temporizador, no repetir")
                                                }
                                            } else {
                                                // Sin arrivalGreeting, solo programar regreso si aún no se hizo
                                                if (!arrivalGreetingSpoken) {
                                                    Log.d("TemiBridge", "[ESCORT] Sin arrivalGreeting, solo scheduleReturn desde callback")
                                                    scheduleReturn()
                                                }
                                            }
                                        }
                                    }
                                }

                                // Temporizador opcional basado en arrivalDelay (segundos)
                                if (arrivalDelaySeconds != null && arrivalDelaySeconds > 0 && savedArrivalGreeting.isNotBlank()) {
                                    Log.d("TemiBridge", "[ESCORT] Programando temporizador de arrivalGreeting en ${arrivalDelaySeconds}s")
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (!arrivalGreetingSpoken) {
                                            arrivalGreetingSpoken = true
                                            Log.d("TemiBridge", "[ESCORT] arrivalGreeting disparado por temporizador (${arrivalDelaySeconds}s)")
                                            TemiController.speak(savedArrivalGreeting)
                                            scheduleReturn()
                                        } else {
                                            Log.d("TemiBridge", "[ESCORT] Temporizador de arrivalGreeting ignorado (ya fue dicho)")
                                        }
                                    }, arrivalDelaySeconds * 1000)
                                }

                                // 1. Decir saludo inicial con voz natural de Google
                                Log.d("TemiBridge", "[ESCORT] Reproduciendo greeting inicial con GoogleTTS...")
                                GoogleTTS.speak(appContext, initialGreeting)

                                // 2. Navegar al destino después de un delay
                                if (savedPlace.isNotBlank()) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        Log.d("TemiBridge", "[ESCORT] Ejecutando goTo($savedPlace)")
                                        TemiController.goTo(savedPlace)
                                    }, 3000) // 3 segundos para que termine de hablar
                                }
                            } else {
                                Toast.makeText(this, "QR inválido: falta greeting", Toast.LENGTH_LONG).show()
                            }
                            return
                        }
                    }
                }
            }
            Toast.makeText(this, "QR inválido. Deep link no reconocido", Toast.LENGTH_LONG).show()
            return
        }

        // Formato legacy eliminado: ahora solo se aceptan deep links mytemi://...
        Toast.makeText(this, "QR invÃ¡lido. Formato no soportado", Toast.LENGTH_LONG).show()
        return
    }

    // --- CheckinHandler para flujo mytemi://guest ---
    private val checkinHandler by lazy {
        CheckinHandler(OkHttpSupabaseGateway(BuildConfig.TEMI_EDGE_BASE_URL, BuildConfig.SUPABASE_ANON_KEY))
    }

    // --- Webhook + flujo de recepciÃ³n ---
    private val httpClient by lazy { OkHttpClient() }

    // Decodifica parÃ¡metros una o mÃ¡s veces si vienen doblemente codificados ("%2520" => "%20" => " ")
    private fun decodeParam(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var prev: String = raw
        var curr: String
        repeat(3) {
            // hasta 3 pasadas por seguridad
            curr = try {
                URLDecoder.decode(prev, StandardCharsets.UTF_8.name())
            } catch (_: Throwable) {
                prev
            }
            if (curr == prev) return curr
            prev = curr
        }
        return prev
    }

    private fun postWebhookAndMaybeOpen(recepcion: String?, telefono: String?) {
        // Construir JSON simple con las variables si estÃ¡n presentes
        val recBool = recepcion?.equals("true", ignoreCase = true) ?: false
        val sb = StringBuilder().append('{')
        sb.append("\"recepcion\":").append(if (recBool) "true" else "false")
        if (!telefono.isNullOrBlank()) {
            sb.append(',').append("\"telefono\":\"").append(telefono).append('\"')
        }
        sb.append('}')

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = sb.toString().toRequestBody(mediaType)
        val req = Request.Builder()
            .url("https://hook.us1.make.com/rpr19yvr51pufln58pwln4rdgz0dl6hq")
            .post(body)
            .build()
        httpClient.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.w("TemiBridge", "Webhook fallo: ${e.message}")
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
                Log.d("TemiBridge", "Webhook enviado: ${response.code}")
            }
        })

        if (recBool) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val url = "https://spatium-desk.lovable.app/pedidos-publicos?ubicacion=Recepcion"
                    val intent = Intent(this, KioskWebActivity::class.java).apply {
                        putExtra(KioskWebActivity.EXTRA_URL, url)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                } catch (t: Throwable) {
                    Log.w("TemiBridge", "Abrir KioskWebActivity fallo: ${t.message}")
                }
            }, 5_000)
        }
    }

    private fun startTour(identifier: String) {
        if (identifier.isNotBlank()) {
            TemiController.startDefaultNlu(identifier)
            TemiController.speak("Iniciando tour $identifier")
        }
    }

    /**
     * Ejecuta una secuencia del robot Temi por nombre
     */
    private fun executeSequence(sequenceName: String) {
        Log.d("TemiBridge", "Ejecutando secuencia: $sequenceName")

        // Verificar si tiene permiso de secuencias
        if (!TemiController.isSequencePermissionGranted()) {
            Log.w("TemiBridge", "Permiso de secuencias no concedido, solicitando...")
            val granted = TemiController.requestSequencePermission()
            if (granted) {
                TemiController.speak("Por favor, acepta el permiso de secuencias y vuelve a escanear el código")
            } else {
                TemiController.speak("No se pudo solicitar el permiso de secuencias")
            }
            return
        }

        // Ejecutar la secuencia
        val success = TemiController.playSequenceByName(sequenceName)
        if (success) {
            Log.d("TemiBridge", "Secuencia $sequenceName iniciada correctamente")
        } else {
            TemiController.speak("No se encontró la secuencia $sequenceName")
            Log.w("TemiBridge", "Secuencia $sequenceName no encontrada")
        }
    }

    /**
     * Ejecuta una secuencia del robot Temi por ID
     */
    private fun executeSequenceById(sequenceId: String) {
        Log.d("TemiBridge", "Ejecutando secuencia por ID: $sequenceId")

        // Verificar si tiene permiso de secuencias
        if (!TemiController.isSequencePermissionGranted()) {
            Log.w("TemiBridge", "Permiso de secuencias no concedido, solicitando...")
            val granted = TemiController.requestSequencePermission()
            if (granted) {
                TemiController.speak("Por favor, acepta el permiso de secuencias y vuelve a escanear el código")
            } else {
                TemiController.speak("No se pudo solicitar el permiso de secuencias")
            }
            return
        }

        // Ejecutar la secuencia por ID
        val success = TemiController.playSequenceById(sequenceId)
        if (success) {
            Log.d("TemiBridge", "Secuencia con id=$sequenceId iniciada correctamente")
        } else {
            TemiController.speak("No se encontró la secuencia solicitada")
            Log.w("TemiBridge", "Secuencia con id=$sequenceId no encontrada")
        }
    }

    /**
     * Reproduce sonido de éxito al escanear QR
     */
    private fun playSuccessSound() {
        try {
            // Usar ToneGenerator para un beep de confirmación
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200) // Beep corto de confirmación

            // Liberar después de reproducir
            Handler(Looper.getMainLooper()).postDelayed({
                toneGenerator.release()
            }, 300)

            Log.d("TemiBridge", " Sonido de éxito reproducido")
        } catch (e: Exception) {
            Log.w("TemiBridge", "No se pudo reproducir sonido: ${e.message}")
        }
    }

    /**
     * Muestra animación de éxito cuando se escanea un QR válido
     * Pulso sutil en el frame de la cámara + sonido
     */
    private fun showSuccessAnimation() {
        val cameraContainer = findViewById<View>(R.id.cameraContainer)

        Log.d("TemiBridge", " QR escaneado exitosamente")

        // Reproducir sonido de éxito
        playSuccessSound()

        // Animación de pulso en el frame de la cámara
        cameraContainer.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                cameraContainer.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.BounceInterpolator())
                    .start()
            }
            .start()
    }
}
