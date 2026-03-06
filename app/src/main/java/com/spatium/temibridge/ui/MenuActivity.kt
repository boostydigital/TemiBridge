package com.spatium.deamon.db.temi.ui

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.spatium.deamon.db.temi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class MenuActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MenuActivity"
        private const val PREFS_NAME = "menu_prefs"
        private const val KEY_FAREWELL_SEQUENCE_ID = "farewell_sequence_id"
        private const val KEY_LAST_PLACE = "last_place"
        private const val DEFAULT_FAREWELL_SEQUENCE_ID = "694063d5bd16eddf28b772d8"
        const val EXTRA_PLACE = "place"
    }

    private lateinit var prefs: SharedPreferences
    private var lastPlace: String = ""

    // Sabor seleccionado actualmente para el Té
    private var selectedSabor: String = "Verde"
    
    // Flag para evitar activación duplicada de face tracking
    private var isFaceTrackingEnabled = false
    
    // Job para control de coroutines
    private var confirmOrderJob: Job? = null
    
    // Animator para variantes del Té
    private lateinit var teaVariantAnimator: TeaVariantAnimator
    
    // Animator para café espresso
    private lateinit var coffeeEspressoAnimator: CoffeeEspressoAnimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lastPlace = intent.getStringExtra(EXTRA_PLACE) ?: prefs.getString(KEY_LAST_PLACE, "") ?: ""
        if (lastPlace.isNotEmpty()) {
            prefs.edit().putString(KEY_LAST_PLACE, lastPlace).apply()
        }

        setupFullscreen()
        setupCategoryButtons()
        setupTeFlavorButtons()
        setupConfirmButtons()
        setupSettingsButton()
        setupBackButton()

        // Solicitar permiso de secuencias al abrir la aplicación
        requestSequencePermissionOnStartup()

        // Inicializar animators
        teaVariantAnimator = TeaVariantAnimator(lifecycleScope)
        coffeeEspressoAnimator = CoffeeEspressoAnimator(lifecycleScope)

        // Aplicar animaciones a los iconos de categorías
        applyAnimationsToIcons()

        // Aplicar animaciones complejas a las variantes del Té
        applyTeaVariantAnimations()
        
        // Aplicar animaciones complejas al café espresso
        applyCoffeeEspressoAnimations()

        // Mostrar panel de Té por defecto
        showTePanel()

        Log.d(TAG, "MenuActivity iniciada. lastPlace=$lastPlace")
    }

    override fun onResume() {
        super.onResume()
        // No reactivar face tracking automáticamente en onResume
        // Solo se activa cuando el usuario presiona "Pedir"
    }

    override fun onPause() {
        super.onPause()
        // No desactivar face tracking en onPause
        // Permitir que permanezca activo durante la navegación
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancelar Job para evitar memory leaks
        confirmOrderJob?.cancel()
        confirmOrderJob = null
        Log.d(TAG, "MenuActivity destruida, Job cancelado")
    }

    private fun applyCoffeeEspressoAnimations() {
        try {
            val coffeeCup = findViewById<ImageView>(R.id.ivCoffeeCup)

            // Aplicar animación principal del café espresso (escala, rotación, opacidad)
            coffeeEspressoAnimator.animateCoffeeEspresso(coffeeCup, duration = 3000)

            Log.d(TAG, "Animaciones de café espresso aplicadas")
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando animaciones de café: ${e.message}", e)
        }
    }

    private fun applyTeaVariantAnimations() {
        try {
            // Las animaciones de variantes del Té ya están configuradas en el layout con LottieAnimationView
            // No necesitamos aplicar animaciones programáticas adicionales
            Log.d(TAG, "Animaciones de variantes del Té configuradas en layout")
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando animaciones de variantes: ${e.message}", e)
        }
    }

    private fun applyAnimationsToIcons() {
        try {
            val teaCup = findViewById<ImageView>(R.id.ivTeaCup)
            val coffeeCup = findViewById<ImageView>(R.id.ivCoffeeCup)
            val waterGlass = findViewById<ImageView>(R.id.ivWaterGlass)

            // Aplicar animación de escala continua a los iconos
            val scaleAnimation = android.view.animation.ScaleAnimation(
                1f, 1.05f, 1f, 1.05f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 2000
                repeatCount = android.view.animation.Animation.INFINITE
                repeatMode = android.view.animation.Animation.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }

            teaCup.startAnimation(scaleAnimation)
            coffeeCup.startAnimation(scaleAnimation)
            waterGlass.startAnimation(scaleAnimation)

            Log.d(TAG, "Animaciones de iconos aplicadas")
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando animaciones: ${e.message}", e)
        }
    }

    private fun requestSequencePermissionOnStartup() {
        Log.d(TAG, "=== SOLICITANDO PERMISO DE SECUENCIAS AL INICIAR ===")
        try {
            // Intentar solicitar permiso directamente
            val granted = com.spatium.deamon.db.temi.core.TemiController.requestSequencePermission(this)
            Log.d(TAG, "requestSequencePermission retornó: $granted")
            
            if (com.spatium.deamon.db.temi.core.TemiController.isSequencePermissionGranted()) {
                Log.d(TAG, "✓ Permiso de secuencias ya está otorgado")
            } else {
                Log.w(TAG, "⚠ Permiso de secuencias aún no está otorgado, se solicitará en el siguiente intento")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando permiso de secuencias: ${e.message}", e)
        }
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupCategoryButtons() {
        // Botón Té → muestra panel de sabores
        findViewById<FrameLayout>(R.id.btnOrderTe).setOnClickListener {
            Log.d(TAG, "Categoría Té tocada")
            showTePanel()
            highlightCategory("te")
        }

        // Botón Café → panel genérico sin sabores
        findViewById<FrameLayout>(R.id.btnOrderCafe).setOnClickListener {
            Log.d(TAG, "Categoría Café tocada")
            showGenericoPanel("Café", R.drawable.ic_coffee)
            highlightCategory("cafe")
        }

        // Botón Agua → panel genérico sin sabores
        findViewById<FrameLayout>(R.id.btnOrderAgua).setOnClickListener {
            Log.d(TAG, "Categoría Agua tocada")
            showGenericoPanel("Agua", R.drawable.ic_water)
            highlightCategory("agua")
        }
    }

    private fun setupTeFlavorButtons() {
        val btnFrutos = findViewById<FrameLayout>(R.id.btnSaborFrutos)
        val btnVerde = findViewById<FrameLayout>(R.id.btnSaborVerde)
        val btnManzana = findViewById<FrameLayout>(R.id.btnSaborManzana)

        btnFrutos.setOnClickListener {
            selectedSabor = "Frutos"
            Log.d(TAG, "Sabor seleccionado: $selectedSabor")
            updateSaborSelection(btnFrutos, btnVerde, btnManzana)
        }
        btnVerde.setOnClickListener {
            selectedSabor = "Verde"
            Log.d(TAG, "Sabor seleccionado: $selectedSabor")
            updateSaborSelection(btnVerde, btnFrutos, btnManzana)
        }
        btnManzana.setOnClickListener {
            selectedSabor = "Manzana"
            Log.d(TAG, "Sabor seleccionado: $selectedSabor")
            updateSaborSelection(btnManzana, btnFrutos, btnVerde)
        }
    }

    private fun updateSaborSelection(selected: FrameLayout, vararg others: FrameLayout) {
        selected.setBackgroundResource(R.drawable.bg_category_active)
        others.forEach { it.setBackgroundResource(R.drawable.bg_glass_button) }
    }

    private fun setupConfirmButtons() {
        // Confirmar Té
        findViewById<Button>(R.id.btnConfirmarTe).setOnClickListener {
            Log.d(TAG, "Confirmar Té: sabor=$selectedSabor")
            showConfirmDialog("Té", R.drawable.ic_tea, selectedSabor)
        }

        // Confirmar genérico (Café / Agua)
        findViewById<Button>(R.id.btnConfirmarGenerico).setOnClickListener {
            val nombre = findViewById<TextView>(R.id.tvGenericoNombre).text.toString()
            val iconRes = if (nombre == "Café") R.drawable.ic_coffee else R.drawable.ic_water
            Log.d(TAG, "Confirmar genérico: $nombre")
            showConfirmDialog(nombre, iconRes, null)
        }
    }

    private fun setupSettingsButton() {
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun setupBackButton() {
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            Log.d(TAG, "Volviendo a MainActivity")
            returnToMain()
        }
    }

    private fun showTePanel() {
        findViewById<LinearLayout>(R.id.panelTe).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.panelGenerico).visibility = View.GONE
    }

    private fun showGenericoPanel(nombre: String, iconRes: Int) {
        findViewById<LinearLayout>(R.id.panelTe).visibility = View.GONE
        val panelGenerico = findViewById<LinearLayout>(R.id.panelGenerico)
        panelGenerico.visibility = View.VISIBLE
        panelGenerico.findViewById<TextView>(R.id.tvGenericoNombre).text = nombre
        panelGenerico.findViewById<ImageView>(R.id.ivGenericoIcon).setImageResource(iconRes)
        val tint = if (nombre == "Café") R.color.amber_400 else R.color.blue_400
        panelGenerico.findViewById<ImageView>(R.id.ivGenericoIcon).setColorFilter(
            resources.getColor(tint, theme)
        )
        val sub = if (nombre == "Café") "Espresso • Latte" else "Mineral • Gas"
        panelGenerico.findViewById<TextView>(R.id.tvGenericoSub).text = sub
    }

    private fun highlightCategory(active: String) {
        val btnTe = findViewById<FrameLayout>(R.id.btnOrderTe)
        val btnCafe = findViewById<FrameLayout>(R.id.btnOrderCafe)
        val btnAgua = findViewById<FrameLayout>(R.id.btnOrderAgua)

        btnTe.setBackgroundResource(if (active == "te") R.drawable.bg_category_active else R.drawable.bg_glass_button)
        btnCafe.setBackgroundResource(if (active == "cafe") R.drawable.bg_category_active else R.drawable.bg_glass_button)
        btnAgua.setBackgroundResource(if (active == "agua") R.drawable.bg_category_active else R.drawable.bg_glass_button)
    }

    private fun showConfirmDialog(productName: String, iconRes: Int, sabor: String?) {
        Log.d(TAG, "=== MOSTRANDO PANTALLA DE CONFIRMACION === Producto=$productName sabor=$sabor")
        
        // Activar face tracking para orientar al robot hacia el usuario cuando presiona "Pedir"
        Log.d(TAG, "=== USUARIO PRESIONÓ PEDIR - ACTIVANDO FACE TRACKING ===")
        enableFaceTrackingAndHeadTilting()
        
        val nombreCompleto = if (sabor != null) "$productName $sabor" else productName
        val intent = Intent(this, OrderConfirmationActivity::class.java).apply {
            putExtra("productName", nombreCompleto)
            putExtra("iconRes", iconRes)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        // Aplicar transición de Material Design si es Android 5.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val options = ActivityOptions.makeSceneTransitionAnimation(this)
            startActivity(intent, options.toBundle())
        } else {
            startActivity(intent)
        }
    }

    private fun confirmOrder(productName: String, sabor: String?) {
        // Cancelar job anterior si existe
        confirmOrderJob?.cancel()
        
        val farewellSequenceId = prefs.getString(KEY_FAREWELL_SEQUENCE_ID, DEFAULT_FAREWELL_SEQUENCE_ID)
            ?: DEFAULT_FAREWELL_SEQUENCE_ID

        val normalizedName = removeAccents(productName).lowercase()
        val normalizedSabor = sabor?.let { removeAccents(it).lowercase() }

        Log.d(TAG, "=== CONFIRMANDO PEDIDO ===")
        Log.d(TAG, "Producto: $productName -> $normalizedName | Sabor: $sabor -> $normalizedSabor")
        Log.d(TAG, "Place: $lastPlace | SecuenciaDespedida: $farewellSequenceId")

        confirmOrderJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val despedidaEncoded = java.net.URLEncoder.encode(lastPlace.ifEmpty { "sin_lugar" }, "UTF-8")
                val comidaEncoded = java.net.URLEncoder.encode(normalizedName, "UTF-8")

                var webhookUrl = "https://hook.us1.make.com/ei3fb5lpstgw8s8sygvyvnda9klzq0y3?despedida=$despedidaEncoded&comida=$comidaEncoded"
                if (normalizedSabor != null) {
                    webhookUrl += "&sabor=${java.net.URLEncoder.encode(normalizedSabor, "UTF-8")}"
                }

                Log.d(TAG, "=== ENVIANDO WEBHOOK === URL: $webhookUrl")

                // Enviar webhook con reintentos
                sendWebhookWithRetry(webhookUrl, maxRetries = 3)

                withContext(Dispatchers.Main) {
                    // Ejecutar secuencia ANTES de cambiar de Activity
                    // porque finish() cancela lifecycleScope
                    Log.d(TAG, "Ejecutando secuencia de despedida ANTES de cambiar de pantalla")
                    executeFarewellSequence(farewellSequenceId)
                    
                    // Ahora abrir la pantalla de éxito
                    delay(500)
                    openSuccessScreen(if (normalizedSabor != null) "$productName $sabor" else productName)
                }

            } catch (e: Exception) {
                Log.e(TAG, "=== ERROR EN PEDIDO === ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MenuActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    returnToMain()
                }
            } finally {
                confirmOrderJob = null
            }
        }
    }

    private suspend fun sendWebhookWithRetry(webhookUrl: String, maxRetries: Int = 3) {
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                val connection = java.net.URL(webhookUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Webhook response (intento $attempt): $responseCode")
                connection.disconnect()
                
                // Si la respuesta es exitosa (2xx), retornar
                if (responseCode in 200..299) {
                    Log.d(TAG, "✓ Webhook exitoso en intento $attempt")
                    return
                }
                
                lastException = Exception("HTTP $responseCode")
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Intento $attempt falló: ${e.message}")
            }
            
            // Si no es el último intento, esperar con exponential backoff
            if (attempt < maxRetries) {
                val delayMs = (1000 * Math.pow(2.0, (attempt - 1).toDouble())).toLong()
                Log.d(TAG, "Reintentando en ${delayMs}ms...")
                delay(delayMs)
            }
        }
        
        // Si llegamos aquí, todos los reintentos fallaron
        throw lastException ?: Exception("Webhook falló después de $maxRetries intentos")
    }

    private fun openSuccessScreen(productName: String) {
        val intent = Intent(this, OrderSuccessActivity::class.java).apply {
            putExtra(OrderSuccessActivity.EXTRA_PRODUCT_NAME, productName)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
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
        Log.d(TAG, "=== EJECUTANDO SECUENCIA DE DESPEDIDA === ID: $sequenceId")
        try {
            // Verificar si tenemos permiso de secuencias
            var hasPermission = com.spatium.deamon.db.temi.core.TemiController.isSequencePermissionGranted()
            Log.d(TAG, "Permiso de secuencias verificado (1er intento): $hasPermission")
            
            if (!hasPermission) {
                Log.w(TAG, "Permiso de secuencias no concedido, solicitando...")
                val granted = com.spatium.deamon.db.temi.core.TemiController.requestSequencePermission(this)
                Log.d(TAG, "Resultado de solicitud de permiso: $granted")
                
                // Esperar un poco para que el permiso se procese
                Thread.sleep(500)
                
                // Verificar nuevamente
                hasPermission = com.spatium.deamon.db.temi.core.TemiController.isSequencePermissionGranted()
                Log.d(TAG, "Permiso de secuencias verificado (2do intento): $hasPermission")
                
                if (!hasPermission) {
                    Log.e(TAG, "ERROR: No se pudo obtener permiso de secuencias después de 2 intentos")
                    return
                }
                Log.d(TAG, "✓ Permiso de secuencias otorgado en 2do intento")
            }
            
            Log.d(TAG, "Llamando a playSequenceById con ID: $sequenceId")
            val success = com.spatium.deamon.db.temi.core.TemiController.playSequenceById(sequenceId)
            Log.d(TAG, "playSequenceById resultado: $success")
            
            if (success) {
                Log.d(TAG, "✓✓✓ SECUENCIA DE DESPEDIDA EJECUTADA EXITOSAMENTE ✓✓✓")
            } else {
                Log.e(TAG, "❌ ERROR: No se pudo ejecutar la secuencia $sequenceId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción ejecutando secuencia: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun showSettingsDialog() {
        val currentId = prefs.getString(KEY_FAREWELL_SEQUENCE_ID, DEFAULT_FAREWELL_SEQUENCE_ID) ?: DEFAULT_FAREWELL_SEQUENCE_ID
        val ratingPrefs = getSharedPreferences(RatingActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val currentEventName = ratingPrefs.getString(RatingActivity.KEY_EVENT_NAME, "EVENTO SPATIUM") ?: "EVENTO SPATIUM"
        val currentWebhookUrl = ratingPrefs.getString(RatingActivity.KEY_WEBHOOK_URL, "") ?: ""

        val scrollView = android.widget.ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 32, 56, 16)
        }
        scrollView.addView(layout)

        // ── Sección: Secuencia de Despedida ──
        layout.addView(TextView(this).apply {
            text = "SECUENCIA DE DESPEDIDA"
            setTextColor(android.graphics.Color.parseColor("#94a3b8"))
            textSize = 11f
            letterSpacing = 0.15f
            setPadding(0, 0, 0, 12)
        })
        val etSequenceId = EditText(this).apply {
            setText(currentId)
            hint = "ID de secuencia"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#64748b"))
            setBackgroundColor(android.graphics.Color.parseColor("#1e293b"))
            setPadding(24, 20, 24, 20)
        }
        layout.addView(etSequenceId)

        // Separador
        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.topMargin = 28; it.bottomMargin = 20 }
            setBackgroundColor(android.graphics.Color.parseColor("#334155"))
        })

        // ── Sección: Rating ──
        layout.addView(TextView(this).apply {
            text = "CONFIGURACIÓN RATING"
            setTextColor(android.graphics.Color.parseColor("#94a3b8"))
            textSize = 11f
            letterSpacing = 0.15f
            setPadding(0, 0, 0, 12)
        })

        layout.addView(TextView(this).apply {
            text = "Nombre del evento"
            setTextColor(android.graphics.Color.parseColor("#cbd5e1"))
            textSize = 13f
            setPadding(0, 0, 0, 6)
        })
        val etEventName = EditText(this).apply {
            setText(currentEventName)
            hint = "Ej: TALLER DE INNOVACIÓN 2026"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#64748b"))
            setBackgroundColor(android.graphics.Color.parseColor("#1e293b"))
            setPadding(24, 20, 24, 20)
        }
        layout.addView(etEventName)

        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0
            ).also { it.topMargin = 16 }
        })

        layout.addView(TextView(this).apply {
            text = "URL Webhook (envío de rating)"
            setTextColor(android.graphics.Color.parseColor("#cbd5e1"))
            textSize = 13f
            setPadding(0, 0, 0, 6)
        })
        val etWebhookUrl = EditText(this).apply {
            setText(currentWebhookUrl)
            hint = "https://hook.us1.make.com/..."
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#64748b"))
            setBackgroundColor(android.graphics.Color.parseColor("#1e293b"))
            setPadding(24, 20, 24, 20)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(etWebhookUrl)

        val dialog = AlertDialog.Builder(this, R.style.Theme_TemiBridge)
            .setTitle("Configuración")
            .setView(scrollView)
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit().putString(KEY_FAREWELL_SEQUENCE_ID, etSequenceId.text.toString().trim()).apply()
                ratingPrefs.edit()
                    .putString(RatingActivity.KEY_EVENT_NAME, etEventName.text.toString().trim())
                    .putString(RatingActivity.KEY_WEBHOOK_URL, etWebhookUrl.text.toString().trim())
                    .apply()
                Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "[SETTINGS] Guardado: seq=${etSequenceId.text}, event=${etEventName.text}, webhook=${etWebhookUrl.text}")
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
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

    private fun enableFaceTrackingAndHeadTilting() {
        // Evitar activación duplicada
        if (isFaceTrackingEnabled) {
            Log.d(TAG, "Face tracking ya está habilitado, ignorando activación duplicada")
            return
        }
        
        Log.d(TAG, "=== HABILITANDO FACE TRACKING ===")
        
        // Habilitar face tracking automático
        val faceTrackingEnabled = com.spatium.deamon.db.temi.core.TemiController.enableFaceTracking()
        Log.d(TAG, "Face tracking habilitado: $faceTrackingEnabled")
        
        if (faceTrackingEnabled) {
            isFaceTrackingEnabled = true
            Log.d(TAG, "✓ Face tracking activado exitosamente")
        } else {
            Log.w(TAG, "⚠ Face tracking no pudo ser activado")
        }
    }

    private fun disableFaceTrackingAndHeadTilting() {
        // Evitar desactivación si no está habilitado
        if (!isFaceTrackingEnabled) {
            Log.d(TAG, "Face tracking no está habilitado, ignorando desactivación")
            return
        }
        
        Log.d(TAG, "=== DESHABILITANDO FACE TRACKING ===")
        
        // Deshabilitar face tracking
        val faceTrackingDisabled = com.spatium.deamon.db.temi.core.TemiController.disableFaceTracking()
        Log.d(TAG, "Face tracking deshabilitado: $faceTrackingDisabled")
        
        if (faceTrackingDisabled) {
            isFaceTrackingEnabled = false
            Log.d(TAG, "✓ Face tracking desactivado exitosamente")
        }
    }
}
