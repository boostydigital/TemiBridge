package com.spatium.deamon.db.temi.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.airbnb.lottie.LottieAnimationView
import com.spatium.deamon.db.temi.R
import com.spatium.deamon.db.temi.core.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PedidosActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PedidosActivity"
        private const val PREFS_NAME = "pedidos_prefs"
        private const val KEY_FAREWELL_SEQUENCE_ID = "farewell_sequence_id"
        private const val KEY_LAST_PLACE = "last_place"
        private const val DEFAULT_FAREWELL_SEQUENCE_ID = "694063d5bd16eddf28b772d8"
        const val EXTRA_PLACE = "place"
    }

    private lateinit var prefs: SharedPreferences
    private var lastPlace: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pedidos)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lastPlace = intent.getStringExtra(EXTRA_PLACE) ?: prefs.getString(KEY_LAST_PLACE, "") ?: ""
        // Guardar el place para futuras referencias
        if (lastPlace.isNotEmpty()) {
            prefs.edit().putString(KEY_LAST_PLACE, lastPlace).apply()
        }

        setupFullscreen()
        setupProductButtons()
        setupSettingsButton()
        loadLottieAnimations()

        Log.d(TAG, "PedidosActivity iniciada. lastPlace=$lastPlace")
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupProductButtons() {
        Log.d(TAG, "Configurando botones de productos")
        findViewById<Button>(R.id.btnOrderTe).setOnClickListener {
            Log.d(TAG, "Botón Té tocado")
            showConfirmDialog("Té", R.drawable.ic_tea)
        }

        findViewById<Button>(R.id.btnOrderAgua).setOnClickListener {
            Log.d(TAG, "Botón Agua tocado")
            showConfirmDialog("Agua", R.drawable.ic_water)
        }

        findViewById<Button>(R.id.btnOrderCafe).setOnClickListener {
            Log.d(TAG, "Botón Café tocado")
            showConfirmDialog("Café", R.drawable.ic_coffee)
        }
    }

    private fun setupSettingsButton() {
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun loadLottieAnimations() {
        // Té
        findViewById<LottieAnimationView>(R.id.lottieTe)?.apply {
            setAnimation(R.raw.tea)
            playAnimation()
        }

        // Agua
        findViewById<LottieAnimationView>(R.id.lottieAgua)?.apply {
            setAnimation(R.raw.water)
            playAnimation()
        }

        // Café
        findViewById<LottieAnimationView>(R.id.lottieCafe)?.apply {
            setAnimation(R.raw.coffee)
            playAnimation()
        }
    }

    private fun showConfirmDialog(productName: String, iconRes: Int) {
        Log.d(TAG, "=== CONFIRMANDO PEDIDO DIRECTAMENTE ===")
        Log.d(TAG, "Producto: $productName")
        
        // Si es Té, usar sabor por defecto (Verde)
        if (productName.equals("Té", ignoreCase = true) || productName.lowercase().contains("te")) {
            Log.d(TAG, "Detectado Té - usando sabor por defecto: Verde")
            confirmOrder(productName, "Verde")
            return
        }
        
        confirmOrder(productName, null)
    }
    

    private fun confirmOrder(productName: String, sabor: String?) {
        val farewellSequenceId = prefs.getString(KEY_FAREWELL_SEQUENCE_ID, DEFAULT_FAREWELL_SEQUENCE_ID) ?: DEFAULT_FAREWELL_SEQUENCE_ID
        
        // Normalizar nombre: sin acentos y en minúscula
        val normalizedName = removeAccents(productName).lowercase()
        val normalizedSabor = sabor?.let { removeAccents(it).lowercase() }

        Log.d(TAG, "=== CONFIRMANDO PEDIDO ===")
        Log.d(TAG, "Producto: $productName -> $normalizedName")
        Log.d(TAG, "Sabor: $sabor -> $normalizedSabor")
        Log.d(TAG, "Place: $lastPlace")
        Log.d(TAG, "Secuencia despedida: $farewellSequenceId")

        // Mostrar toast inmediatamente
        Toast.makeText(this, "Procesando pedido...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Enviar GET al webhook con parámetros despedida y comida
                val despedidaEncoded = java.net.URLEncoder.encode(lastPlace.ifEmpty { "sin_lugar" }, "UTF-8")
                val comidaEncoded = java.net.URLEncoder.encode(normalizedName, "UTF-8")
                
                // Construir URL con parámetro sabor solo si es Té
                var webhookUrl = "https://hook.us1.make.com/ei3fb5lpstgw8s8sygvyvnda9klzq0y3?despedida=$despedidaEncoded&comida=$comidaEncoded"
                if (normalizedSabor != null) {
                    val saborEncoded = java.net.URLEncoder.encode(normalizedSabor, "UTF-8")
                    webhookUrl += "&sabor=$saborEncoded"
                }
                
                Log.d(TAG, "=== ENVIANDO WEBHOOK ===")
                Log.d(TAG, "URL: $webhookUrl")
                
                val url = java.net.URL(webhookUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Webhook response code: $responseCode")
                connection.disconnect()

                // Ejecutar secuencia de despedida
                Log.d(TAG, "=== EJECUTANDO SECUENCIA DE DESPEDIDA ===")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PedidosActivity, "¡Pedido confirmado! Preparando tu $productName", Toast.LENGTH_SHORT).show()
                    executeFarewellSequence(farewellSequenceId)
                }

            } catch (e: Exception) {
                Log.e(TAG, "=== ERROR EN PEDIDO ===")
                Log.e(TAG, "Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PedidosActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    returnToMain()
                }
            }
        }
    }
    
    private fun removeAccents(input: String): String {
        val accents = mapOf(
            'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u',
            'Á' to 'A', 'É' to 'E', 'Í' to 'I', 'Ó' to 'O', 'Ú' to 'U',
            'ñ' to 'n', 'Ñ' to 'N', 'ü' to 'u', 'Ü' to 'U'
        )
        return input.map { accents[it] ?: it }.joinToString("")
    }
    
    private fun executeFarewellSequence(sequenceId: String) {
        Log.d(TAG, "=== EJECUTANDO SECUENCIA DE DESPEDIDA ===")
        Log.d(TAG, "Secuencia ID: $sequenceId")
        
        try {
            val success = com.spatium.deamon.db.temi.core.TemiController.playSequenceById(sequenceId)
            Log.d(TAG, "playSequenceById resultado: $success")
            
            if (!success) {
                Log.e(TAG, "ERROR: No se pudo ejecutar la secuencia $sequenceId")
                Toast.makeText(this, "Error ejecutando secuencia de despedida", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción ejecutando secuencia: ${e.message}", e)
        }
        
        // Esperar un poco y luego volver a MainActivity
        window.decorView.postDelayed({
            Log.d(TAG, "Volviendo a MainActivity después de secuencia")
            returnToMain()
        }, 5000) // 5 segundos para que inicie la secuencia
    }

    private fun showSettingsDialog() {
        val currentFarewellSequenceId = prefs.getString(KEY_FAREWELL_SEQUENCE_ID, DEFAULT_FAREWELL_SEQUENCE_ID) ?: DEFAULT_FAREWELL_SEQUENCE_ID
        Log.d(TAG, "Configuración: Secuencia de despedida actual = $currentFarewellSequenceId")
        Toast.makeText(this, "ID actual: $currentFarewellSequenceId", Toast.LENGTH_SHORT).show()
    }

    private fun returnToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    fun setLastPlace(place: String) {
        lastPlace = place
        prefs.edit().putString(KEY_LAST_PLACE, place).apply()
        Log.d(TAG, "lastPlace actualizado: $place")
    }
}
