package com.spatium.temibridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import android.util.Log
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

        // Confirmación visual de depuración
        Toast.makeText(this, "MainActivity cargada", Toast.LENGTH_SHORT).show()

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
