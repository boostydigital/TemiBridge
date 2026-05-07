package com.spatium.temibridge.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.spatium.deamon.db.temi.BuildConfig
import com.spatium.deamon.db.temi.core.ExclusiveModeArbiter
import com.spatium.deamon.db.temi.core.TemiController
import com.spatium.deamon.db.temi.net.OkHttpSupabaseGateway
import com.spatium.deamon.db.temi.net.SupabaseGateway
import com.spatium.deamon.db.temi.ui.RatingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gestiona el modo rating/evaluación para reuniones en salones.
 * Hace polling para detectar evaluaciones pendientes y controla el flujo.
 */
class RatingManager(
    private val context: Context?,
    private val gateway: SupabaseGateway = OkHttpSupabaseGateway(
        BuildConfig.TEMI_EDGE_BASE_URL,
        BuildConfig.SUPABASE_ANON_KEY,
    ),
) {

    companion object {
        private const val TAG = "RatingManager"

        // API externa para enviar evaluaciones (proyecto Supabase distinto — no usar gateway)
        private const val CREATE_EVALUATION_URL = "https://fojrqrkbzsgcefsnwldk.supabase.co/functions/v1/create-evaluation"

        private const val POLLING_INTERVAL_MS = 30_000L // 30 segundos
        private const val CONSTRAINT_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutos
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var ratingTimeoutJob: Job? = null
    private var ttsLoopJob: Job? = null

    private var isRatingActive = false
    internal var currentEvaluacion: JSONObject? = null

    /** Injection point for tests — replaces TemiController navigation side effects. */
    internal var navigationDispatch: ((JSONObject) -> Unit) = ::startRatingMode

    @Volatile private var ratingSubmitted = false
    private var constraintTimeoutJob: Job? = null

    // OkHttp usado exclusivamente para la API externa (fojrqrkbzsgcefsnwldk.supabase.co)
    private val externalHttpClient = OkHttpClient.Builder()
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
        5 to "Excelente servicio",
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
            context?.registerReceiver(ratingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context?.registerReceiver(ratingReceiver, filter)
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
    internal suspend fun checkForPendingEvaluation() {
        try {
            val response = gateway.get("evaluacion-pendiente")
            val responseObj = response.jsonObject

            Log.d(TAG, "Respuesta evaluacion-pendiente: $response")

            val pendiente = responseObj["pendiente"]?.jsonPrimitive?.boolean == true

            if (pendiente) {
                val evaluacionEl = responseObj["evaluacion"]
                if (evaluacionEl != null && evaluacionEl is JsonObject) {
                    val id = evaluacionEl["id"]?.jsonPrimitive?.content ?: ""
                    val salon = evaluacionEl["salon"]?.jsonPrimitive?.content ?: ""
                    val waypoint = evaluacionEl["waypoint"]?.jsonPrimitive?.content ?: ""
                    val horaLlegada = evaluacionEl["hora_llegada"]?.jsonPrimitive?.content ?: ""
                    val nombreReserva = evaluacionEl["nombre_reserva"]?.jsonPrimitive?.content ?: ""

                    // Convertir a JSONObject para mantener compatibilidad con el resto del flujo
                    val evaluacionJson = JSONObject().apply {
                        put("id", id)
                        put("salon", salon)
                        put("waypoint", waypoint)
                        put("hora_llegada", horaLlegada)
                        put("nombre_reserva", nombreReserva)
                    }

                    Log.d(TAG, "Evaluación pendiente detectada: $id")
                    navigationDispatch(evaluacionJson)
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

        // Arbiter: garantiza exclusividad de modo antes de mutar cualquier estado
        val acquired = runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_RATING) }
        if (!acquired) {
            Log.w(TAG, "Skipping rating activation — another mode is active: ${ExclusiveModeArbiter.currentMode()}")
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
            Log.d(TAG, "Llegó a $waypoint, activando constraintBeWith y mostrando rating")
            // Activar constraintBeWith para evitar retorno automático del OS
            TemiController.enableFaceTracking()
            // Timeout de 10 min para liberar constraintBeWith si nadie evalúa
            startConstraintTimeout()
            showRatingScreen(salon, nombreReserva)
        }

        // Configurar callback de abort (OS interrumpió la navegación)
        TemiController.setAbortCallback {
            Log.d(TAG, "Navegación abortada por OS antes de llegar a $waypoint, limpiando modo rating")
            emergencyCleanup()
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
            context?.getSharedPreferences(RatingActivity.PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(RatingActivity.KEY_EVENT_NAME, salon)
                ?.putString("customer_name", nombreReserva)
                ?.putString("salon", salon)
                ?.apply()

            val intent = Intent(context ?: return@post, RatingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("salon", salon)
                putExtra("customer_name", nombreReserva)
                putExtra("rating_manager_mode", true) // Indica que viene del RatingManager
            }
            context?.startActivity(intent)
        }

        // TTS inicial de bienvenida
        scope.launch {
            TemiController.speak(
                "Hola, espero que hayas disfrutado tu reunión en $salon. " +
                    "Por favor, evalúa nuestro servicio tocando las estrellas en mi pantalla.",
            )
        }
    }

    /**
     * Timeout de 10 minutos para liberar constraintBeWith si nadie evalúa.
     */
    private fun startConstraintTimeout() {
        constraintTimeoutJob?.cancel()
        constraintTimeoutJob = scope.launch {
            delay(CONSTRAINT_TIMEOUT_MS)
            if (isRatingActive && !ratingSubmitted) {
                Log.d(TAG, "Timeout de 10 min alcanzado, liberando constraintBeWith")
                updateEvaluationStatus("timeout")
                finishRatingMode(timeout = true)
            }
        }
    }

    /**
     * Llamado cuando el usuario envía una evaluación.
     */
    fun onRatingSubmitted(rating: Int, customerName: String, salon: String) {
        if (ratingSubmitted) return
        ratingSubmitted = true
        constraintTimeoutJob?.cancel()
        Log.d(TAG, "Rating recibido: $rating estrellas para $salon")

        scope.launch {
            sendEvaluationToExternalAPI(rating, customerName, salon)
            updateEvaluationStatus("completada", rating)

            withContext(Dispatchers.Main) {
                TemiController.speak("¡Muchas gracias por tu evaluación! Tu opinión nos ayuda a mejorar.")
            }

            delay(3000)
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
            val response = externalHttpClient.newCall(request).execute()
            Log.d(TAG, "Evaluación enviada a API externa: ${response.code}")
            Log.d(TAG, "Response: ${response.body?.string()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando evaluación a API externa: ${e.message}")
        }
    }

    /**
     * Actualiza el estado de la evaluación en nuestra BD vía edge function.
     */
    internal suspend fun updateEvaluationStatus(estado: String, rating: Int? = null) {
        val evaluacionId = currentEvaluacion?.optString("id") ?: return

        val body = buildJsonObject {
            put("id", evaluacionId)
            put("estado", estado)
            if (rating != null) put("rating", rating)
        }

        try {
            gateway.post("programar-evaluacion", body)
            Log.d(TAG, "Estado actualizado a: $estado")
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando estado: ${e.message}")
        }
    }

    /**
     * Limpieza de emergencia cuando el OS aborta la navegación al salón.
     * No intenta navegar a ningún lado, solo detiene todo.
     */
    private fun emergencyCleanup() {
        Log.d(TAG, "emergencyCleanup: deteniendo todo sin navegar")
        ttsLoopJob?.cancel()
        ratingTimeoutJob?.cancel()
        constraintTimeoutJob?.cancel()

        scope.launch {
            updateEvaluationStatus("cancelada")

            // Cerrar RatingActivity si estaba abierta
            val closeIntent = Intent("com.spatium.deamon.db.temi.CLOSE_RATING")
            context?.sendBroadcast(closeIntent)

            // Restaurar UI
            withContext(Dispatchers.Main) {
                // Desactivar constraintBeWith para permitir movimiento
                TemiController.disableFaceTracking()
                TemiController.toggleNavigationBillboard(false)
                // NO desactivar Kiosk Mode para mantener la app en primer plano

                // Volver a MainActivity
                val mainIntent = android.content.Intent(context ?: return@withContext, com.spatium.deamon.db.temi.ui.MainActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context?.startActivity(mainIntent)
            }

            isRatingActive = false
            currentEvaluacion = null

            // Liberar el modo exclusivo
            ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_RATING)
        }
    }

    /**
     * Finaliza el modo rating limpiamente.
     */
    private fun finishRatingMode(timeout: Boolean) {
        Log.d(TAG, "Finalizando modo rating (timeout=$timeout)")

        ttsLoopJob?.cancel()
        ratingTimeoutJob?.cancel()
        constraintTimeoutJob?.cancel()

        scope.launch {
            // Cerrar RatingActivity
            val closeIntent = Intent("com.spatium.deamon.db.temi.CLOSE_RATING")
            context?.sendBroadcast(closeIntent)

            // Restaurar UI
            withContext(Dispatchers.Main) {
                // Desactivar constraintBeWith para permitir movimiento
                TemiController.disableFaceTracking()
                TemiController.toggleNavigationBillboard(false)
                // NO desactivar Kiosk Mode para mantener la app en primer plano

                // Volver a MainActivity
                val mainIntent = android.content.Intent(context ?: return@withContext, com.spatium.deamon.db.temi.ui.MainActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context?.startActivity(mainIntent)
            }

            isRatingActive = false
            ratingSubmitted = false
            currentEvaluacion = null
            TemiController.clearArrivalCallback()

            // Retornar a home base
            delay(500)
            Log.d(TAG, "Retornando a home base...")
            TemiController.goTo("home base")

            // Liberar el modo exclusivo (después de goTo para evitar que otro modo
            // tome el arbiter mientras el robot aún está navegando a home base)
            ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_RATING)
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
        constraintTimeoutJob?.cancel()

        // Desregistrar receiver
        try {
            context?.unregisterReceiver(ratingReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error desregistrando receiver: ${e.message}")
        }

        scope.cancel()
    }
}
