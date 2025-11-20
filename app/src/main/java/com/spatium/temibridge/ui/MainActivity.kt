package com.spatium.temibridge.ui

import android.Manifest
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
import androidx.core.content.ContextCompat
import coil.load
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.spatium.temibridge.R
import com.spatium.temibridge.core.TemiController
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity() {

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleQrContent(result.contents!!)
        } else {
            Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show()
        }
    }

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
            if (granted) startQrScan() else Toast.makeText(this, "Se requiere cÃ¡mara para escanear.", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("TemiBridge", "MainActivity.onCreate - setContentView OK")

        // Referencias a vistas
        val btnScan = findViewById<View>(R.id.btnScan)
        val logoSpatium = findViewById<ImageView>(R.id.logoSpatium)
        val qrIconCenter = findViewById<ImageView>(R.id.qrIconCenter)
        val mainText = findViewById<android.widget.TextView>(R.id.mainText)
        val scanFrame = findViewById<View>(R.id.scanFrame)
        val scanLine = findViewById<View>(R.id.scanLine)
        val scanStatus = findViewById<android.widget.TextView>(R.id.scanStatus)

        // Cargar logo de Spatium 10 Aniversario
        // NOTA: Reemplazar esta URL con la ubicación real del logo
        // Por ahora, usar un placeholder o la imagen local si está disponible
        logoSpatium.load("https://cdn.prod.website-files.com/6892254c55b94994927b7f75/68938a95d4da97a6a402f2bd_Spatium-logo-vertical.avif") {
            crossfade(true)
            placeholder(android.R.color.transparent)
            error(android.R.color.transparent)
        }

        // Animación de entrada del logo
        logoSpatium.alpha = 0f
        logoSpatium.translationY = -20f
        logoSpatium.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(900)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Animación de entrada elegante para elementos centrales
        qrIconCenter.alpha = 0f
        qrIconCenter.scaleX = 0.5f
        qrIconCenter.scaleY = 0.5f
        qrIconCenter.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(200)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()

        mainText.alpha = 0f
        mainText.translationY = 30f
        mainText.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(400)
            .setDuration(700)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Animación sutil de pulsación en el botón QR del header
        animateQrButtonPulse(btnScan)

        // Click en botón QR del header
        btnScan.setOnClickListener {
            Log.d("TemiBridge", "btnScan clicked")
            showScanningAnimation(scanFrame, scanLine, scanStatus)
            ensureCameraAndScan()
        }

        // Mantener compatibilidad con botón tour (oculto)
        findViewById<android.view.View>(R.id.btnTour).setOnClickListener {
            Log.d("TemiBridge", "btnTour clicked")
            startTour("Spatium_Visita")
        }

        Toast.makeText(this, "TemiBridge cargado", Toast.LENGTH_SHORT).show()
    }

    private fun ensureCameraAndScan() {
        val permission = Manifest.permission.CAMERA
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> startQrScan()
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission) -> requestCameraPermission.launch(permission)
            else -> requestCameraPermission.launch(permission)
        }
    }

    private fun startQrScan() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Apunta la cÃ¡mara al cÃ³digo")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        qrLauncher.launch(options)
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
     * Muestra animación de éxito cuando se escanea un QR válido
     */
    private fun showSuccessAnimation() {
        val qrIconCenter = findViewById<ImageView>(R.id.qrIconCenter)
        val mainText = findViewById<android.widget.TextView>(R.id.mainText)

        // Animación de pulso en el icono central
        qrIconCenter.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(200)
            .withEndAction {
                qrIconCenter.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
            .start()

        // Cambiar temporalmente el texto
        val originalText = mainText.text
        mainText.text = "✓ QR Escaneado"
        mainText.setTextColor(getColor(R.color.gold_light))

        // Restaurar después de 2 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            mainText.text = originalText
            mainText.setTextColor(getColor(R.color.gold))
        }, 2000)
    }

    /**
     * Animación de pulsación sutil en el botón QR del header
     * Efecto de "respiración" para llamar la atención
     */
    private fun animateQrButtonPulse(button: View) {
        button.post {
            button.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(1200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    button.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(1200)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            // Repetir la animación después de una pausa
                            Handler(Looper.getMainLooper()).postDelayed({
                                animateQrButtonPulse(button)
                            }, 2000)
                        }
                        .start()
                }
                .start()
        }
    }

    /**
     * Muestra el marco de escaneo con animación de línea que se mueve de arriba a abajo
     * Simula el efecto de escaneo de un lector QR
     */
    private fun showScanningAnimation(scanFrame: View, scanLine: View, scanStatus: android.widget.TextView) {
        // Hacer visible el marco de escaneo
        scanFrame.visibility = View.VISIBLE
        scanFrame.alpha = 0f
        scanFrame.scaleX = 0.8f
        scanFrame.scaleY = 0.8f
        
        scanFrame.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // Iniciar animación de la línea de escaneo
                animateScanLine(scanLine, scanFrame.height)
            }
            .start()

        // Mostrar texto de estado
        scanStatus.visibility = View.VISIBLE
        scanStatus.text = "Escaneando..."
        scanStatus.alpha = 0f
        scanStatus.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        // Ocultar después del escaneo (simulado)
        Handler(Looper.getMainLooper()).postDelayed({
            hideScanningAnimation(scanFrame, scanLine, scanStatus)
        }, 3000)
    }

    /**
     * Anima la línea de escaneo moviéndola de arriba a abajo repetidamente
     */
    private fun animateScanLine(scanLine: View, frameHeight: Int) {
        scanLine.translationY = 0f
        scanLine.animate()
            .translationY(frameHeight.toFloat() - 32f) // Restar padding
            .setDuration(1500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                // Volver al inicio y repetir
                scanLine.translationY = 0f
                scanLine.animate()
                    .translationY(frameHeight.toFloat() - 32f)
                    .setDuration(1500)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
    }

    /**
     * Oculta el marco de escaneo con animación
     */
    private fun hideScanningAnimation(scanFrame: View, scanLine: View, scanStatus: android.widget.TextView) {
        scanFrame.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(300)
            .withEndAction {
                scanFrame.visibility = View.GONE
            }
            .start()

        scanStatus.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                scanStatus.visibility = View.GONE
            }
            .start()
    }
}
