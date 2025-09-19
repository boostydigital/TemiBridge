package com.spatium.temibridge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.load
import com.google.zxing.integration.android.IntentIntegrator
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.spatium.temibridge.R

class MainActivity : AppCompatActivity() {

    private val robot by lazy { Robot.getInstance() }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startQrScan() else Toast.makeText(this, "Se requiere cámara para escanear.", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Load background image with dark overlay via Coil
        val bg = findViewById<ImageView>(R.id.bgImage)
        bg.load("https://cdn.prod.website-files.com/6892254c55b94994927b7f75/68b5ad5d069da781c2a4e992_GastroBarRD.jpg") {
            crossfade(true)
        }

        findViewById<android.view.View>(R.id.btnScan).setOnClickListener {
            ensureCameraAndScan()
        }
        findViewById<android.view.View>(R.id.btnTour).setOnClickListener {
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
        IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt("Apunta la cámara al código")
            setBeepEnabled(false)
            setOrientationLocked(false)
            initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                handleQrContent(result.contents)
            } else {
                Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show()
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
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
        robot.speak(TtsRequest.create(saludo, false))
        // Ejecutar Go To al salón
        robot.goTo(salon)
    }

    private fun startTour(identifier: String) {
        if (identifier.isNotBlank()) {
            robot.startDefaultNlu(identifier)
            robot.speak(TtsRequest.create("Iniciando tour $identifier", false))
        }
    }
}
