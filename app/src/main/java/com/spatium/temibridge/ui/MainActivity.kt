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

        // Load background image with dark overlay via Coil
        val bg = findViewById<ImageView>(R.id.bgImage)
        bg.load("https://cdn.prod.website-files.com/6892254c55b94994927b7f75/68b5ad5d069da781c2a4e992_GastroBarRD.jpg") {
            crossfade(true)
            placeholder(android.R.color.black)
            error(android.R.color.darker_gray)
        }

        // Load Spatium logo in header
        val headerLogo = findViewById<ImageView>(R.id.logo)
        headerLogo.load("https://cdn.prod.website-files.com/6892254c55b94994927b7f75/68938a95d4da97a6a402f2bd_Spatium-logo-vertical.avif") {
            crossfade(true)
            placeholder(android.R.color.transparent)
            error(android.R.color.transparent)
        }
        // AnimaciÃ³n de entrada del logo (fade + scale)
        headerLogo.alpha = 0f
        headerLogo.scaleX = 0.85f
        headerLogo.scaleY = 0.85f
        headerLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(650)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // ConfirmaciÃ³n visual de depuraciÃ³n
        Toast.makeText(this, "MainActivity cargada", Toast.LENGTH_SHORT).show()

        // Animar tarjetas (entrada con fade + slide + scale)
        val cardScan = findViewById<View>(R.id.btnScan)
        val cardTour = findViewById<View>(R.id.btnTour)
        animateCardIn(cardScan, delay = 50L)
        animateCardIn(cardTour, delay = 180L)
        val cardSeqTest = findViewById<View>(R.id.btnSeqTest)
        animateCardIn(cardSeqTest, delay = 300L)

        // Efecto Ken Burns sutil al fondo
        startKenBurns(bg)

        findViewById<android.view.View>(R.id.btnScan).setOnClickListener {
            Log.d("TemiBridge", "btnScan clicked")
            ensureCameraAndScan()
        }
        findViewById<android.view.View>(R.id.btnTour).setOnClickListener {
            Log.d("TemiBridge", "btnTour clicked")
            startTour("Spatium_Visita")
        }
        // Probar reproducir una Sequence por nombre usando deep link mytemi://sequence-play?name=...
        findViewById<android.view.View>(R.id.btnSeqPlay).setOnClickListener {
            Log.d("TemiBridge", "btnSeqPlay clicked -> abrir pedidos-publicos (Recepcion)")
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
        }

        // Boton: probar secuencia por ID (Temi Center)
        findViewById<android.view.View>(R.id.btnSeqTest).setOnClickListener {
            val seqId = "68d1859a217743075f4f9a44"
            Log.d("TemiBridge", "btnSeqTest clicked -> playSequenceById(${'$'}seqId)")
            if (TemiController.hasSequencePermission()) {
                val ok = TemiController.playSequenceById(seqId)
                val msg = if (ok) "Secuencia iniciada" else "No se pudo iniciar la secuencia"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (!ok) TemiController.speak(msg) else TemiController.speak("Ejecutando secuencia")
            } else {
                val okReq = TemiController.requestSequencePermission(this)
                val msg = if (okReq) {
                    "Permiso de secuencias requerido. Acepta en pantalla y vuelve a pulsar"
                } else {
                    "No se pudo solicitar el permiso de secuencias"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                TemiController.speak(msg)
            }
        }
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
                            if (text.isNotBlank()) TemiController.speak(text)
                            if (place.isNotBlank()) TemiController.goTo(place)
                            postWebhookAndMaybeOpen(recepcion, telefono)
                            return
                        }
                        "say" -> {
                            val text = decodeParam(uri.getQueryParameter("text")).trim()
                            val recepcion = decodeParam(uri.getQueryParameter("recepcion"))
                            val telefono = decodeParam(uri.getQueryParameter("telefono"))
                            if (text.isNotBlank()) TemiController.speak(text) else Toast.makeText(this, "QR invÃ¡lido: falta text", Toast.LENGTH_LONG).show()
                            postWebhookAndMaybeOpen(recepcion, telefono)
                            return
                        }
                        "go" -> {
                            val place = decodeParam(uri.getQueryParameter("place")).trim()
                            val recepcion = decodeParam(uri.getQueryParameter("recepcion"))
                            val telefono = decodeParam(uri.getQueryParameter("telefono"))
                            val recBool = recepcion.equals("true", ignoreCase = true)
                            if (place.isNotBlank()) {
                                // Si NO es recepciÃ³n, al llegar anuncia y luego va a "entrada" tras 10s
                                if (!recBool) {
                                    Log.d("TemiBridge", "[GO] recepcion=false: programando arrival callback para place=$place")
                                    TemiController.setArrivalCallbackOnce {
                                        // Garantizar ejecuciÃ³n en hilo principal
                                        Handler(Looper.getMainLooper()).post {
                                            Log.d("TemiBridge", "[GO] arrival callback ejecutado: anunciando llegada y preparando retorno a entrada")
                                            TemiController.speak("Hemos llegado a tu destino, tu anfitriÃ³n te atenderÃ¡. Gracias")
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
                            } else Toast.makeText(this, "QR invÃ¡lido: falta place", Toast.LENGTH_LONG).show()
                            postWebhookAndMaybeOpen(recepcion, telefono)
                            return
                        }
                        "tour" -> {
                            val name = decodeParam(uri.getQueryParameter("name")).trim()
                            val tourId = decodeParam(uri.getQueryParameter("tourId")).trim()
                            val recepcion = decodeParam(uri.getQueryParameter("recepcion"))
                            val telefono = decodeParam(uri.getQueryParameter("telefono"))
                            val identifier = if (name.isNotBlank()) name else tourId
                            if (identifier.isNotBlank()) startTour(identifier) else Toast.makeText(this, "QR invÃ¡lido: falta name o tourId", Toast.LENGTH_LONG).show()
                            postWebhookAndMaybeOpen(recepcion, telefono)
                            return
                        }
                    }
                }
            }
            Toast.makeText(this, "QR invÃ¡lido. Deep link no reconocido", Toast.LENGTH_LONG).show()
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
}
