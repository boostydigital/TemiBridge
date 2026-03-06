package com.spatium.deamon.db.temi.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.spatium.deamon.db.temi.R
import java.io.File

/**
 * Activity para previsualizar y compartir fotos por WhatsApp.
 *
 * SOLUCIÓN IMPLEMENTADA: Usa wa.me URL para abrir DIRECTAMENTE el chat,
 * evitando la pantalla de compartir de WhatsApp.
 */
class PhotoPreviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PhotoPreviewActivity"
        private const val WHATSAPP_PHONE = "18492825765"  // Número de teléfono
        private const val WHATSAPP_CONTACT_NAME = "SPATIUM RECEPCION FLOTA"  // Nombre del contacto en WhatsApp
        private const val REQUEST_WHATSAPP = 1001
    }

    private lateinit var photoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_preview)
        Log.d(TAG, "[LIFECYCLE] PhotoPreviewActivity.onCreate")

        photoPath = intent.getStringExtra(PartyActivity.EXTRA_PHOTO_PATH) ?: run {
            Log.e(TAG, "[ERROR] No se recibió ruta de foto")
            finish()
            return
        }

        setupFullscreen()
        loadPhoto()
        setupButtons()
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun loadPhoto() {
        Log.d(TAG, "[PREVIEW] Cargando foto: $photoPath")
        try {
            val bitmap = BitmapFactory.decodeFile(photoPath)
            if (bitmap != null) {
                findViewById<ImageView>(R.id.ivPhotoPreview).setImageBitmap(bitmap)
                findViewById<ImageView>(R.id.ivBackgroundBlur).setImageBitmap(bitmap)
                Log.d(TAG, "[PREVIEW] ✓ Foto cargada")
            } else {
                Log.e(TAG, "[PREVIEW] ✗ Bitmap es null")
                Toast.makeText(this, "No se pudo cargar la foto", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[PREVIEW] Error: ${e.message}", e)
        }
    }

    private fun setupButtons() {
        // Cerrar sin compartir
        findViewById<FrameLayout>(R.id.btnPreviewClose).setOnClickListener {
            Log.d(TAG, "[UI] Cerrar preview")
            deleteCurrentPhoto()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        // Compartir por WhatsApp
        findViewById<FrameLayout>(R.id.btnMeEncanta).setOnClickListener {
            Log.d(TAG, "[UI] Me encanta - compartir por WhatsApp")
            shareToWhatsApp()
        }

        // Repetir foto
        findViewById<FrameLayout>(R.id.btnRepetirFoto).setOnClickListener {
            Log.d(TAG, "[UI] Repetir foto")
            deleteCurrentPhoto()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    /**
     * ESTRATEGIA CORREGIDA: Un solo Intent para abrir chat y adjuntar foto.
     *
     * Usamos un truco: ACTION_SEND con número de teléfono EXTRA.
     * Esto abre el chat específico con la foto ya adjunta.
     */
    private fun shareToWhatsApp() {
        try {
            val photoFile = File(photoPath)
            if (!photoFile.exists()) {
                Log.e(TAG, "[WHATSAPP] ✗ Foto no existe: $photoPath")
                Toast.makeText(this, "No se encontró la foto", Toast.LENGTH_SHORT).show()
                return
            }

            // Verificar servicio de accesibilidad
            if (!isAccessibilityServiceEnabled()) {
                Log.d(TAG, "[WHATSAPP] Servicio no habilitado")
                requestAccessibilityPermission()
                return
            }

            // Detectar WhatsApp
            val detectedPackage = detectWhatsApp()
            if (detectedPackage == null) {
                Log.e(TAG, "[WHATSAPP] ✗ WhatsApp no instalado")
                Toast.makeText(this, "WhatsApp no está instalado", Toast.LENGTH_LONG).show()
                return
            }

            // Generar URI de la foto
            val photoUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )

            Log.d(TAG, "[WHATSAPP] 📱 Nueva estrategia: ACTION_SEND con número")
            Log.d(TAG, "[WHATSAPP] 📞 Número: $WHATSAPP_PHONE")
            Log.d(TAG, "[WHATSAPP] 📷 Foto: $photoPath")

            // PASO ÚNICO: ACTION_SEND con número de teléfono y foto
            val cleanNumber = WHATSAPP_PHONE.replace("[^0-9]".toRegex(), "")
            val jid = "$cleanNumber@s.whatsapp.net"  // Formato JID de WhatsApp

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, photoUri)
                putExtra(Intent.EXTRA_TEXT, "")
                putExtra("jid", jid)  // EXTRA específico de WhatsApp para el destinatario
                setPackage(detectedPackage)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Configurar SharedData
            SharedData.setSending(photoPath, photoUri)

            Log.d(TAG, "[WHATSAPP] 🔗 Enviando ACTION_SEND con JID: $jid")
            startActivity(sendIntent)

            // Iniciar monitoreo del envío
            startMonitoringWhatsAppSending()

        } catch (e: Exception) {
            Log.e(TAG, "[WHATSAPP] ✗ Error: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            SharedData.reset()
        }
    }

    /**
     * Monitorea el estado del envío con timeout.
     */
    private fun startMonitoringWhatsAppSending() {
        Log.d(TAG, "[WHATSAPP] 🔍 Iniciando monitoreo...")

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val checkInterval = 500L
        val maxChecks = 20  // 10 segundos máximo
        var checkCount = 0

        val monitoringRunnable = object : Runnable {
            override fun run() {
                checkCount++

                when (SharedData.sendState) {
                    SharedData.SendState.COMPLETED -> {
                        Log.d(TAG, "[WHATSAPP] ✅ Enviado correctamente!")
                        Toast.makeText(this@PhotoPreviewActivity, "✅ Foto enviada", Toast.LENGTH_SHORT).show()
                        SharedData.reset()

                        handler.postDelayed({
                            // Retornar a PartyActivity sin lanzar nueva instancia
                            Log.d(TAG, "[WHATSAPP] Retornando a PartyActivity")
                            setResult(Activity.RESULT_OK)
                            finish()
                        }, 1000) // Esperar 1 segundo antes de retornar
                        return
                    }
                    SharedData.SendState.ERROR_GENERAL,
                    SharedData.SendState.ERROR_MAX_RETRIES,
                    SharedData.SendState.ERROR_TIMEOUT,
                    SharedData.SendState.ERROR_NO_SEND_BUTTON -> {
                        Log.e(TAG, "[WHATSAPP] ❌ Error: ${SharedData.errorMessage}")
                        Toast.makeText(this@PhotoPreviewActivity, "❌ ${SharedData.errorMessage}", Toast.LENGTH_LONG).show()
                        SharedData.reset()

                        handler.postDelayed({
                            // Retornar a PartyActivity incluso en caso de error
                            Log.d(TAG, "[WHATSAPP] Retornando a PartyActivity (error)")
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }, 2000)
                        return
                    }
                    else -> {
                        Log.d(TAG, "[WHATSAPP] ⏳ Estado: ${SharedData.sendState} ($checkCount/$maxChecks)")

                        if (checkCount >= maxChecks) {
                            Log.w(TAG, "[WHATSAPP] ⏱️ Timeout alcanzado")
                            Toast.makeText(this@PhotoPreviewActivity, "⏱️ Tiempo de espera agotado", Toast.LENGTH_LONG).show()
                            SharedData.reset()

                            handler.postDelayed({
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }, 1000)
                            return
                        }

                        // Continuar monitoreando
                        handler.postDelayed(this, checkInterval)
                    }
                }
            }
        }

        handler.postDelayed(monitoringRunnable, checkInterval)
    }

    private fun detectWhatsApp(): String? {
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: Exception) {
                // Continuar con el siguiente
            }
        }
        return null
    }

    private fun deleteCurrentPhoto() {
        try {
            val file = File(photoPath)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "[CLEANUP] ✓ Foto eliminada")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[CLEANUP] ⚠️ Error eliminando: ${e.message}")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager

        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        for (service in enabledServices) {
            if (service.id.contains("WhatsAppAccessibilityService", ignoreCase = true)) {
                Log.d(TAG, "[ACCESSIBILITY] ✓ Servicio habilitado: ${service.id}")
                return true
            }
        }

        Log.d(TAG, "[ACCESSIBILITY] ✗ Servicio NO habilitado")
        return false
    }

    private fun requestAccessibilityPermission() {
        AlertDialog.Builder(this).apply {
            setTitle("Habilitar Accesibilidad")
            setMessage(
                "Para enviar automáticamente la foto por WhatsApp, necesitas habilitar el servicio de accesibilidad.\n\n" +
                "1. Toca 'Configurar'\n" +
                "2. Ve a Accesibilidad\n" +
                "3. Busca 'WhatsAppAccessibilityService'\n" +
                "4. Actívalo\n" +
                "5. Toca 'Reintentar'"
            )
            setPositiveButton("Configurar") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            setNeutralButton("Reintentar") { dialog, _ ->
                dialog.dismiss()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isAccessibilityServiceEnabled()) {
                        Toast.makeText(this@PhotoPreviewActivity, "✓ Servicio habilitado", Toast.LENGTH_SHORT).show()
                        shareToWhatsApp()
                    } else {
                        requestAccessibilityPermission()
                    }
                }, 500)
            }
            setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            setCancelable(false)
            show()
        }
    }

    override fun onBackPressed() {
        deleteCurrentPhoto()
        setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }
}
