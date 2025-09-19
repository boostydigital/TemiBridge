package com.spatium.temibridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.load
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.spatium.temibridge.R
import com.spatium.temibridge.core.TemiController

class MainActivity : AppCompatActivity() {

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleQrContent(result.contents!!)
        } else {
            Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show()
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
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startQrScan() else Toast.makeText(this, "Se requiere cámara para escanear.", Toast.LENGTH_SHORT).show()
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
        // Animación de entrada del logo (fade + scale)
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

        // Confirmación visual de depuración
        Toast.makeText(this, "MainActivity cargada", Toast.LENGTH_SHORT).show()

        // Animar tarjetas (entrada con fade + slide + scale)
        val cardScan = findViewById<View>(R.id.btnScan)
        val cardTour = findViewById<View>(R.id.btnTour)
        animateCardIn(cardScan, delay = 50L)
        animateCardIn(cardTour, delay = 180L)

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
            .setPrompt("Apunta la cámara al código")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        qrLauncher.launch(options)
    }

    private fun handleQrContent(content: String) {
        // Expected format: name=María González;salon=Salon_Duarte
        val map = content.split(';')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx).trim().lowercase() to pair.substring(idx + 1).trim()
            }
            .toMap()

        val name = map["name"].orEmpty()
        val salon = map["salon"].orEmpty()
        if (name.isBlank() || salon.isBlank()) {
            Toast.makeText(this, "QR inválido. Formato esperado name=...;salon=...", Toast.LENGTH_LONG).show()
            return
        }

        val saludo = "Hola ${name.split(' ', limit = 2).firstOrNull() ?: name}, Bienvenido a Spatium, Por favor sígueme para guiarte a tu destino"
        TemiController.speak(saludo)
        TemiController.goTo(salon)
    }

    private fun startTour(identifier: String) {
        if (identifier.isNotBlank()) {
            TemiController.startDefaultNlu(identifier)
            TemiController.speak("Iniciando tour $identifier")
        }
    }
}
