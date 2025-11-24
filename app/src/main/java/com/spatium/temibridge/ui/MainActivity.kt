package com.spatium.temibridge.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import coil.load
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.spatium.temibridge.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.spatium.temibridge.core.TemiController
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var konfettiView: KonfettiView
    private var camera: Camera? = null
    private var lastScanTime = 0L
    private val SCAN_COOLDOWN_MS = 3000L // 3 segundos entre escaneos

    private fun animateCardIn(view: View, delay: Long) {
        view.alpha = 0f
        view.translationY = 28f
        view.scaleX = 0.97f
        view.scaleY = 0.97f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay)
            .setDuration(450)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun startKenBurns(image: ImageView) {
        image.post {
            image.pivotX = image.width / 2f
            image.pivotY = image.height / 2f
            image.scaleX = 1.0f
            image.scaleY = 1.0f
            image.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(14000)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    // Revertir suave para loop sutil
                    image.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(14000)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction { startKenBurns(image) }
                        .start()
                }
                .start()
        }
    }
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startContinuousScanning()
            } else {
                Toast.makeText(this, "Se requiere cámara para escanear QR codes.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("TemiBridge", "MainActivity.onCreate - setContentView OK")

        // Referencias a vistas
        previewView = findViewById(R.id.previewView)
        konfettiView = findViewById(R.id.konfettiView)
        val cameraContainer = findViewById<View>(R.id.cameraContainer)
        val logoSpatium = findViewById<ImageView>(R.id.logoSpatium)
        val mainText = findViewById<android.widget.TextView>(R.id.mainText)
        
        // Inicializar executor para cámara
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        Log.d("TemiBridge", "[DEBUG] CameraX + ML Kit inicializado")

        // Cargar logo de Spatium 10 Aniversario desde recursos locales
        try {
            logoSpatium.setImageResource(R.drawable.spatium_logo_10)
            Log.d("TemiBridge", "Logo Spatium 10 cargado correctamente")
        } catch (e: Exception) {
            Log.e("TemiBridge", "Error cargando logo: ${e.message}")
            logoSpatium.setImageResource(R.mipmap.ic_launcher)
        }

        // Animación de entrada del logo
        logoSpatium.alpha = 0f
        logoSpatium.translationY = -30f
        logoSpatium.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(1000)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Animación de entrada del escáner QR
        cameraContainer.alpha = 0f
        cameraContainer.scaleX = 0.8f
        cameraContainer.scaleY = 0.8f
        cameraContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(300)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Animación de entrada del texto principal
        mainText.alpha = 0f
        mainText.translationY = 40f
        mainText.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(600)
            .setDuration(900)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Iniciar cámara con CameraX + ML Kit
        Handler(Looper.getMainLooper()).postDelayed({
            ensureCameraPermissionAndStart()
        }, 500)

        // Botón fullscreen - Ahora solo hace zoom en la cámara
        findViewById<View>(R.id.btnFullscreenScanner).setOnClickListener {
            Toast.makeText(this, "Usando ML Kit - Cámara siempre activa", Toast.LENGTH_SHORT).show()
        }

        // Mantener compatibilidad con botón tour (oculto)
        findViewById<android.view.View>(R.id.btnTour).setOnClickListener {
            Log.d("TemiBridge", "btnTour clicked")
            startTour("Spatium_Visita")
        }

        Toast.makeText(this, "TemiBridge cargado", Toast.LENGTH_SHORT).show()
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
                    Log.d("TemiBridge", "[CAMERA] - Cámara $index: ${info}")
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
                        it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrContent ->
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastScanTime > SCAN_COOLDOWN_MS) {
                                lastScanTime = currentTime
                                Log.d("TemiBridge", "[ML_KIT] QR escaneado: $qrContent")
                                runOnUiThread {
                                    handleQrContent(qrContent)
                                }
                            }
                        })
                    }
                
                // Desvincular casos de uso anteriores
                cameraProvider.unbindAll()
                
                // Intentar con diferentes selectores de cámara
                val cameraSelectors = listOf(
                    CameraSelector.DEFAULT_BACK_CAMERA to "trasera",
                    CameraSelector.DEFAULT_FRONT_CAMERA to "frontal"
                )
                
                var cameraStarted = false
                for ((selector, name) in cameraSelectors) {
                    try {
                        Log.d("TemiBridge", "[CAMERA] Intentando con cámara $name...")
                        
                        camera = cameraProvider.bindToLifecycle(
                            this, selector, preview, imageAnalyzer
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun handleQrContent(content: String) {
        // 1) Nuevo formato soportado: deep link mytemi://...
        //    Ej: mytemi://welcome?text=Hola%20Mario...&place=Salon_Duarte
        if (content.startsWith("mytemi://", ignoreCase = true)) {
            runCatching { Uri.parse(content) }.onSuccess { uri ->
                if (uri != null && uri.scheme == "mytemi") {
                    when (uri.host) {
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
                            val name = decodeParam(uri.getQueryParameter("name")).trim()
                            val recepcion = decodeParam(uri.getQueryParameter("recepcion"))
                            val telefono = decodeParam(uri.getQueryParameter("telefono"))
                            if (name.isNotBlank()) {
                                showSuccessAnimation()
                                executeSequence(name)
                            } else {
                                Toast.makeText(this, "QR inválido: falta nombre de secuencia", Toast.LENGTH_LONG).show()
                            }
                            postWebhookAndMaybeOpen(recepcion, telefono)
                            return
                        }
                        "escort" -> {
                            val greeting = decodeParam(uri.getQueryParameter("greeting")).trim()
                            
                            if (greeting.isNotBlank()) {
                                showSuccessAnimation()
                                TemiController.speak(greeting)
                                Log.d("TemiBridge", "[ESCORT] Mensaje: $greeting")
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

    // --- Webhook + flujo de recepciÃ³n ---
    private val httpClient by lazy { OkHttpClient() }

    // Decodifica parÃ¡metros una o mÃ¡s veces si vienen doblemente codificados ("%2520" => "%20" => " ")
    private fun decodeParam(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var prev: String = raw
        var curr: String
        repeat(3) { // hasta 3 pasadas por seguridad
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
            val granted = TemiController.requestSequencePermission(this)
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
            TemiController.speak("Ejecutando secuencia $sequenceName")
            Log.d("TemiBridge", "Secuencia $sequenceName iniciada correctamente")
        } else {
            TemiController.speak("No se encontró la secuencia $sequenceName")
            Log.w("TemiBridge", "Secuencia $sequenceName no encontrada")
        }
    }


    /**
     * Muestra animación EXTRAVAGANTE de éxito cuando se escanea un QR válido
     * ¡CELEBRACIÓN TOTAL CON CONFETI Y TEXTO GIGANTE!
     */
    private fun showSuccessAnimation() {
        val mainText = findViewById<android.widget.TextView>(R.id.mainText)
        val cameraContainer = findViewById<View>(R.id.cameraContainer)
        
        Log.d("TemiBridge", "🎉 ¡INICIANDO CELEBRACIÓN EXTRAVAGANTE!")
        
        // ========== CONFETI EXPLOSIVO ==========
        // Crear múltiples explosiones de confeti desde diferentes posiciones
        val party1 = Party(
            speed = 30f,
            maxSpeed = 50f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xFFD4AF37.toInt(), 0xFFFFD700.toInt(), 0xFFFFA500.toInt(), 0xFFFF6B6B.toInt(), 0xFF4ECDC4.toInt()),
            emitter = Emitter(duration = 3, TimeUnit.SECONDS).max(300),
            position = Position.Relative(0.5, 0.3)
        )
        
        val party2 = Party(
            speed = 25f,
            maxSpeed = 45f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xFFD4AF37.toInt(), 0xFFFFD700.toInt(), 0xFFFFA500.toInt(), 0xFF95E1D3.toInt(), 0xFFF38181.toInt()),
            emitter = Emitter(duration = 3, TimeUnit.SECONDS).max(300),
            position = Position.Relative(0.2, 0.5)
        )
        
        val party3 = Party(
            speed = 25f,
            maxSpeed = 45f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xFFD4AF37.toInt(), 0xFFFFD700.toInt(), 0xFFFFA500.toInt(), 0xFFAA96DA.toInt(), 0xFFFCBAD3.toInt()),
            emitter = Emitter(duration = 3, TimeUnit.SECONDS).max(300),
            position = Position.Relative(0.8, 0.5)
        )
        
        // ¡LANZAR CONFETI!
        konfettiView.start(party1, party2, party3)
        
        // ========== ANIMACIÓN DE CÁMARA - PULSO DRAMÁTICO ==========
        cameraContainer.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .rotation(5f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                cameraContainer.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .rotation(0f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.BounceInterpolator())
                    .start()
            }
            .start()

        // ========== TEXTO GIGANTE EXPANDIDO ==========
        val originalText = mainText.text
        val originalSize = mainText.textSize
        
        // Fase 1: Reducir y preparar
        mainText.animate()
            .scaleX(0.5f)
            .scaleY(0.5f)
            .alpha(0.3f)
            .setDuration(200)
            .withEndAction {
                // Cambiar texto y color
                mainText.text = "🎉 ¡FELICIDADES! 🎉\n✨ QR ESCANEADO ✨\n¡EXITOSAMENTE!"
                mainText.setTextColor(getColor(R.color.gold_light))
                mainText.textSize = 48f // Texto GIGANTE
                
                // Fase 2: EXPLOTAR hacia afuera - SUPER GRANDE
                mainText.animate()
                    .scaleX(2.5f)
                    .scaleY(2.5f)
                    .alpha(1f)
                    .rotation(360f)
                    .setDuration(600)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                    .withEndAction {
                        // Fase 3: Pulsar para llamar atención
                        pulsarTexto(mainText, 0)
                    }
                    .start()
            }
            .start()

        // ========== RESTAURAR DESPUÉS DE 4 SEGUNDOS ==========
        Handler(Looper.getMainLooper()).postDelayed({
            mainText.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .alpha(0f)
                .rotation(0f)
                .setDuration(400)
                .withEndAction {
                    mainText.text = originalText
                    mainText.textSize = originalSize / resources.displayMetrics.scaledDensity
                    mainText.setTextColor(getColor(R.color.gold))
                    mainText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(500)
                        .setInterpolator(android.view.animation.BounceInterpolator())
                        .start()
                }
                .start()
        }, 4000)
    }
    
    /**
     * Hace pulsar el texto repetidamente para efecto dramático
     */
    private fun pulsarTexto(textView: android.widget.TextView, count: Int) {
        if (count >= 4) return // Solo 4 pulsos
        
        textView.animate()
            .scaleX(2.7f)
            .scaleY(2.7f)
            .setDuration(300)
            .withEndAction {
                textView.animate()
                    .scaleX(2.5f)
                    .scaleY(2.5f)
                    .setDuration(300)
                    .withEndAction {
                        pulsarTexto(textView, count + 1)
                    }
                    .start()
            }
            .start()
    }

}
