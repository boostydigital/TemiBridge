package com.spatium.temibridge.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.spatium.deamon.db.temi.ui.RatingActivity
import com.spatium.deamon.db.temi.core.TemiController
import java.util.concurrent.TimeUnit

/**
 * Gestiona el modo rating/evaluación para reuniones en salones.
 * Hace polling para detectar evaluaciones pendientes y controla el flujo.
 */
class RatingManager(private val context: Context) {

    companion object {
        private const val TAG = "RatingManager"
        
        // Supabase Temi
        private const val SUPABASE_URL = "https://mkakxmjkwcymwosfrwkl.supabase.co"
        private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1rYWt4bWprd2N5bXdvc2Zyd2tsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzY5NjcyMDAsImV4cCI6MjA1MjU0MzIwMH0.CYQD8WoIZy_o9MqJFfKHsxXRlPLQoKLwwPbZOYjbuMY"
        
        // API externa para enviar evaluaciones
        private const val CREATE_EVALUATION_URL = "https://fojrqrkbzsgcefsnwldk.supabase.co/functions/v1/create-evaluation"
        
        private const val POLLING_INTERVAL_MS = 30_000L // 30 segundos
        private const val RATING_TIMEOUT_MS = 15 * 60 * 1000L // 15 minutos
        private const val TTS_INTERVAL_MS = 60_000L // 60 segundos
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var ratingTimeoutJob: Job? = null
    private var ttsLoopJob: Job? = null
    
    private var isRatingActive = false
    private var currentEvaluacion: JSONObject? = null
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Receiver para escuchar cuando el usuario envía un rating
    private val ratingReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val rating = intent?.getIntExtra("rating", 0) ?: 0
            val customer = intent?.getStringExtra("customer_name") ?: ""
            val salonName = intent?.getStringExtra("salon") ?: ""
            
            if (rating > 0) {
                Log.d(TAG, "Rating recibido via broadcast: $rating estrellas")
                onRatingSubmitted(rating, customer, salonName)
            }
        }
    }

    // Mapeo de feedback por rating
    private val feedbackMap = mapOf(
        1 to "Necesita mejorar",
        2 to "Regular",
        3 to "Bueno",
        4 to "Muy bueno",
        5 to "Excelente servicio"
    )

    /**
     * Inicia el polling para detectar evaluaciones pendientes.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) {
            Log.d(TAG, "Polling ya activo")
            return
        }
        
        // Registrar receiver para escuchar ratings
        val filter = IntentFilter("com.spatium.deamon.db.temi.RATING_SUBMITTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(ratingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(ratingReceiver, filter)
        }
        
        Log.d(TAG, "Iniciando polling de evaluaciones...")
        pollingJob = scope.launch {
            while (isActive) {
                if (!isRatingActive) {
                    checkForPendingEvaluation()
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    /**
     * Detiene el polling.
     */
    fun stopPolling() {
        Log.d(TAG, "Deteniendo polling...")
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Consulta si hay evaluaciones pendientes.
     */
    private suspend fun checkForPendingEvaluation() {
        val request = Request.Builder()
            .url("$SUPABASE_URL/functions/v1/evaluacion-pendiente")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .get()
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return
            
            Log.d(TAG, "Respuesta evaluacion-pendiente: $body")
            
            val json = JSONObject(body)
            val pendiente = json.optBoolean("pendiente", false)
            
            if (pendiente) {
                val evaluacion = json.optJSONObject("evaluacion")
                if (evaluacion != null) {
                    Log.d(TAG, "Evaluación pendiente detectada: ${evaluacion.optString("id")}")
                    startRatingMode(evaluacion)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando evaluación: ${e.message}")
        }
    }

    /**
     * Inicia el modo rating: navega al salón y muestra pantalla de evaluación.
     */
    private fun startRatingMode(evaluacion: JSONObject) {
        if (isRatingActive) {
            Log.d(TAG, "Rating ya activo, ignorando")
            return
        }
        
        isRatingActive = true
        currentEvaluacion = evaluacion
        
        val salon = evaluacion.optString("salon")
        val waypoint = evaluacion.optString("waypoint")
        val nombreReserva = evaluacion.optString("nombre_reserva")
        
        Log.d(TAG, "Iniciando modo rating para: $salon (waypoint: $waypoint)")
        
        // 1. Activar Kiosk Mode
        TemiController.setKioskModeOn(true)
        TemiController.toggleNavigationBillboard(true)
        
        // 2. Configurar callback de llegada ANTES de navegar
        TemiController.setArrivalCallbackOnce {
            Log.d(TAG, "Llegó a $waypoint, mostrando pantalla de rating")
            showRatingScreen(salon, nombreReserva)
        }
        
        // 3. Navegar al waypoint
        Log.d(TAG, "Navegando a waypoint: $waypoint")
        TemiController.goTo(waypoint)
    }

    /**
     * Muestra la pantalla de rating y configura timeout.
     */
    private fun showRatingScreen(salon: String, nombreReserva: String) {
        // Abrir RatingActivity en el hilo principal
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Log.d(TAG, "Abriendo RatingActivity para: $salon")
            
            // Guardar preferencias para RatingActivity
            val prefs = context.getSharedPreferences(RatingActivity.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(RatingActivity.KEY_EVENT_NAME, salon)
                .putString("customer_name", nombreReserva)
                .putString("salon", salon)
                .apply()
            
            val intent = Intent(context, RatingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("salon", salon)
                putExtra("customer_name", nombreReserva)
                putExtra("rating_manager_mode", true) // Indica que viene del RatingManager
            }
            context.startActivity(intent)
        }
        
        // Iniciar TTS de invitación
        startTTSLoop(salon)
        
        // Configurar timeout de 15 minutos
        startRatingTimeout()
    }

    /**
     * Loop de TTS que invita a evaluar cada 60 segundos.
     */
    private fun startTTSLoop(salon: String) {
        ttsLoopJob?.cancel()
        ttsLoopJob = scope.launch {
            // TTS inicial
            val ttsText = "Hola, espero que hayas disfrutado tu reunión en $salon. " +
                "Por favor, evalúa nuestro servicio tocando las estrellas en mi pantalla."
            TemiController.speak(ttsText)
            
            // Repetir cada 60 segundos
            while (isActive && isRatingActive) {
                delay(TTS_INTERVAL_MS)
                if (isRatingActive) {
                    TemiController.speak("Por favor, evalúa nuestro servicio tocando las estrellas.")
                }
            }
        }
    }

    /**
     * Configura el timeout de 15 minutos.
     */
    private fun startRatingTimeout() {
        ratingTimeoutJob?.cancel()
        ratingTimeoutJob = scope.launch {
            delay(RATING_TIMEOUT_MS)
            if (isRatingActive) {
                Log.d(TAG, "Timeout de 15 minutos alcanzado sin evaluación")
                finishRatingMode(timeout = true)
            }
        }
    }

    /**
     * Llamado cuando el usuario envía una evaluación.
     */
    fun onRatingSubmitted(rating: Int, customerName: String, salon: String) {
        Log.d(TAG, "Rating recibido: $rating estrellas para $salon")
        
        // Cancelar timeout y TTS
        ratingTimeoutJob?.cancel()
        ttsLoopJob?.cancel()
        
        // Enviar a API externa
        scope.launch {
            sendEvaluationToExternalAPI(rating, customerName, salon)
            
            // Actualizar estado en nuestra BD
            updateEvaluationStatus("completada", rating)
            
            // Agradecer
            withContext(Dispatchers.Main) {
                TemiController.speak("¡Muchas gracias por tu evaluación! Tu opinión nos ayuda a mejorar.")
            }
            
            delay(3000) // Esperar 3 segundos
            
            finishRatingMode(timeout = false)
        }
    }

    /**
     * Envía la evaluación a la API externa.
     */
    private suspend fun sendEvaluationToExternalAPI(rating: Int, customerName: String, salon: String) {
        val feedbackText = feedbackMap[rating] ?: "Gracias"
        
        val jsonBody = JSONObject().apply {
            put("rating", rating)
            put("customer_name", customerName)
            put("salon", salon)
            put("feedback_text", feedbackText)
            put("category", salon) // El category es el nombre del salón
        }
        
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(CREATE_EVALUATION_URL)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        try {
            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "Evaluación enviada a API externa: ${response.code}")
            Log.d(TAG, "Response: ${response.body?.string()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando evaluación a API externa: ${e.message}")
        }
    }

    /**
     * Actualiza el estado de la evaluación en nuestra BD.
     */
    private suspend fun updateEvaluationStatus(estado: String, rating: Int? = null) {
        val evaluacionId = currentEvaluacion?.optString("id") ?: return
        
        val jsonBody = JSONObject().apply {
            put("estado", estado)
            if (rating != null) put("rating", rating)
        }
        
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/evaluaciones_programadas?id=eq.$evaluacionId")
            .patch(requestBody)
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .build()
        
        try {
            httpClient.newCall(request).execute()
            Log.d(TAG, "Estado actualizado a: $estado")
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando estado: ${e.message}")
        }
    }

    /**
     * Finaliza el modo rating y vuelve a base.
     */
    private fun finishRatingMode(timeout: Boolean) {
        Log.d(TAG, "Finalizando modo rating (timeout=$timeout)")
        
        // Cancelar jobs
        ttsLoopJob?.cancel()
        ratingTimeoutJob?.cancel()
        
        scope.launch {
            if (timeout) {
                // Actualizar estado a timeout
                updateEvaluationStatus("timeout")
                
                // Despedirse
                withContext(Dispatchers.Main) {
                    TemiController.speak("Gracias por visitarnos. Hasta pronto.")
                }
                delay(2000)
            }
            
            // Cerrar RatingActivity
            val closeIntent = Intent("com.spatium.deamon.db.temi.CLOSE_RATING")
            context.sendBroadcast(closeIntent)
            
            // Restaurar UI de Temi
            TemiController.toggleNavigationBillboard(false)
            TemiController.setKioskModeOn(false)
            
            // Volver a home base
            Log.d(TAG, "Volviendo a home base...")
            TemiController.goTo("home base")
            
            // Limpiar estado
            isRatingActive = false
            currentEvaluacion = null
            TemiController.clearArrivalCallback()
        }
    }

    /**
     * Limpia recursos al destruir.
     */
    fun destroy() {
        Log.d(TAG, "Destruyendo RatingManager")
        stopPolling()
        ttsLoopJob?.cancel()
        ratingTimeoutJob?.cancel()
        
        // Desregistrar receiver
        try {
            context.unregisterReceiver(ratingReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error desregistrando receiver: ${e.message}")
        }
        
        scope.cancel()
    }
}
