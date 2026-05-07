package com.spatium.deamon.db.temi.ui

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.spatium.deamon.db.temi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class OrderConfirmationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OrderConfirmationActivity"
        private const val PREFS_NAME = "menu_prefs"
        private const val KEY_FAREWELL_SEQUENCE_ID = "farewell_sequence_id"
        private const val DEFAULT_FAREWELL_SEQUENCE_ID = "694063d5bd16eddf28b772d8"
        const val EXTRA_PLACE = "place"
    }

    private lateinit var prefs: SharedPreferences
    private var productName: String = ""
    private var iconRes: Int = 0
    private var lastPlace: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_confirmation)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        productName = intent.getStringExtra("productName") ?: ""
        iconRes = intent.getIntExtra("iconRes", R.drawable.ic_tea)
        lastPlace = intent.getStringExtra(EXTRA_PLACE) ?: ""

        setupFullscreen()
        setupUI()
        setupButtons()

        Log.d(TAG, "OrderConfirmationActivity iniciada. Producto=$productName")
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupUI() {
        // Cambiar icono Lottie según el producto
        val lottieView = findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.ivProductIcon)

        // Pausar la animación actual
        lottieView.pauseAnimation()

        val lottieRes = when {
            productName.contains("Café", ignoreCase = true) -> R.raw.coffee
            productName.contains("Agua", ignoreCase = true) -> R.raw.water
            else -> R.raw.tea
        }

        Log.d(TAG, "Cambiando animación Lottie a: $productName -> recurso: $lottieRes")

        // Establecer la nueva animación
        lottieView.setAnimation(lottieRes)
        lottieView.cancelAnimation()
        lottieView.playAnimation()

        findViewById<TextView>(R.id.tvProductName).text = productName

        val desc = when {
            productName.contains("Té", ignoreCase = true) -> "Infusión aromática y saludable"
            productName.contains("Café", ignoreCase = true) -> "Espresso o Latte, según disponibilidad"
            productName.contains("Agua", ignoreCase = true) -> "Mineral o con gas"
            else -> "Tu bebida favorita"
        }
        findViewById<TextView>(R.id.tvProductDesc).text = desc
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            Log.d(TAG, "Cancelar tocado")
            returnToMenu()
        }

        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            Log.d(TAG, "Confirmar tocado")
            confirmOrder()
        }
    }

    private fun confirmOrder() {
        val farewellSequenceId = prefs.getString(KEY_FAREWELL_SEQUENCE_ID, DEFAULT_FAREWELL_SEQUENCE_ID)
            ?: DEFAULT_FAREWELL_SEQUENCE_ID

        Log.d(TAG, "=== USUARIO CONFIRMÓ PEDIDO - DESACTIVANDO FACE TRACKING ===")
        disableFaceTracking()

        Log.d(TAG, "=== CONFIRMANDO PEDIDO === Producto=$productName")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val comida = productName.split(" ")[0].lowercase()
                val lugar = lastPlace.ifEmpty { "sin_lugar" }
                val jsonBody = """{"lugar":"$lugar","comida":"$comida"}"""
                val webhookUrl = "https://hook.us1.make.com/ei3fb5lpstgw8s8sygvyvnda9klzq0y3"

                Log.d(TAG, "=== ENVIANDO WEBHOOK === URL: $webhookUrl | Body: $jsonBody")

                val connection = java.net.URL(webhookUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                val responseCode = connection.responseCode
                Log.d(TAG, "Webhook response: $responseCode")
                connection.disconnect()

                withContext(Dispatchers.Main) {
                    openSuccessScreen()
                }
            } catch (e: Exception) {
                Log.e(TAG, "=== ERROR EN PEDIDO === ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OrderConfirmationActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    returnToMenu()
                }
            }
        }
    }

    private fun openSuccessScreen() {
        val intent = Intent(this, OrderSuccessActivity::class.java).apply {
            putExtra(OrderSuccessActivity.EXTRA_PRODUCT_NAME, productName)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // Aplicar transición de Material Design si es Android 5.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val options = ActivityOptions.makeSceneTransitionAnimation(this)
            startActivity(intent, options.toBundle())
        } else {
            startActivity(intent)
        }
        finish()
    }

    private fun returnToMenu() {
        val intent = Intent(this, MenuActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun disableFaceTracking() {
        try {
            Log.d(TAG, "=== DESHABILITANDO FACE TRACKING ===")
            val faceTrackingDisabled = com.spatium.deamon.db.temi.core.TemiController.disableFaceTracking()
            Log.d(TAG, "Face tracking deshabilitado: $faceTrackingDisabled")

            if (faceTrackingDisabled) {
                Log.d(TAG, "✓ Face tracking desactivado correctamente")
            } else {
                Log.w(TAG, "⚠ Face tracking no pudo ser desactivado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deshabilitando face tracking: ${e.message}", e)
        }
    }
}
