package com.spatium.deamon.db.temi.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.spatium.deamon.db.temi.ui.AnnouncementActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Manager para el modo anuncio con patrullaje.
 * Hace polling a Supabase para detectar anuncios activos y controla el patrullaje.
 */
class AnnouncementManager(private val context: Context) {

    companion object {
        private const val TAG = "AnnouncementManager"
        private const val SUPABASE_URL = "https://mkakxmjkwcymwosfrwkl.supabase.co"
        private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1rYWt4bWprd2N5bXdvc2Zyd2tsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgyMDQyNzgsImV4cCI6MjA3Mzc4MDI3OH0.fj82uH4qj8SG27YLHafZ6iRKQFBjfEZgKqS0EfJSZBo"
        private const val POLLING_INTERVAL_MS = 30_000L // 30 segundos
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var expirationJob: Job? = null
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Estado actual
    private var isPatrolling = false
    private var currentAnuncioId: String? = null
    private var originalSpeedLevel: TemiController.SpeedLevel? = null
    private var originalVolume: Int? = null
    private var speakingJob: Job? = null
    
    // Intervalo entre repeticiones del anuncio hablado (en ms)
    private val SPEAK_INTERVAL_MS = 15_000L // 15 segundos
    private val ANNOUNCEMENT_VOLUME = 6 // Volumen para anuncios

    /**
     * Inicia el polling para detectar anuncios activos.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) {
            Log.d(TAG, "Polling ya está activo")
            return
        }

        Log.d(TAG, "Iniciando polling de anuncios...")
        
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    checkForActiveAnnouncement()
                } catch (e: Exception) {
                    Log.e(TAG, "Error en polling: ${e.message}")
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    /**
     * Detiene el polling y el patrullaje activo.
     */
    fun stopPolling() {
        Log.d(TAG, "Deteniendo polling...")
        pollingJob?.cancel()
        pollingJob = null
        
        if (isPatrolling) {
            stopPatrol()
        }
    }

    /**
     * Consulta el endpoint anuncio-activo para verificar si hay un anuncio.
     */
    private suspend fun checkForActiveAnnouncement() {
        val request = Request.Builder()
            .url("$SUPABASE_URL/functions/v1/anuncio-activo")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .get()
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return
            
            Log.d(TAG, "Respuesta anuncio-activo: $body")
            
            val json = JSONObject(body)
            val activo = json.optBoolean("activo", false)
            
            if (activo) {
                val anuncio = json.optJSONObject("anuncio")
                if (anuncio != null) {
                    val id = anuncio.optString("id")
                    
                    // Si es un anuncio diferente al actual, iniciar nuevo patrullaje
                    if (id != currentAnuncioId) {
                        Log.d(TAG, "Nuevo anuncio detectado: $id")
                        startPatrol(anuncio)
                    }
                }
            } else {
                // No hay anuncio activo
                if (isPatrolling) {
                    Log.d(TAG, "Anuncio expiró, deteniendo patrullaje...")
                    stopPatrol()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando anuncio: ${e.message}")
        }
    }

    /**
     * Inicia el modo patrullaje con el anuncio dado.
     */
    private fun startPatrol(anuncio: JSONObject) {
        val id = anuncio.optString("id")
        val texto = anuncio.optString("texto")
        val imagenUrl = anuncio.optString("imagen_url", null)
        val expiresAtStr = anuncio.optString("expires_at")
        val waypointsJson = anuncio.optJSONArray("waypoints") ?: JSONArray()
        
        // Convertir waypoints a List<String>
        val waypoints = mutableListOf<String>()
        for (i in 0 until waypointsJson.length()) {
            waypoints.add(waypointsJson.getString(i))
        }
        
        if (waypoints.size < 3) {
            Log.e(TAG, "Error: Se requieren al menos 3 waypoints, recibidos: ${waypoints.size}")
            return
        }

        // Validar waypoints contra ubicaciones guardadas en el robot
        val savedLocations = TemiController.getLocations()
        val invalidWaypoints = waypoints.filter { it !in savedLocations }
        if (invalidWaypoints.isNotEmpty()) {
            Log.w(TAG, "Waypoints no encontrados en robot: $invalidWaypoints")
            // Continuar de todas formas, el SDK manejará el error
        }

        Log.d(TAG, "=== INICIANDO MODO PATRULLAJE ===")
        Log.d(TAG, "ID: $id")
        Log.d(TAG, "Texto: $texto")
        Log.d(TAG, "Imagen: $imagenUrl")
        Log.d(TAG, "Waypoints: $waypoints")
        Log.d(TAG, "Expira: $expiresAtStr")

        currentAnuncioId = id
        isPatrolling = true

        // 1. Guardar velocidad y volumen actuales
        originalSpeedLevel = TemiController.getGoToSpeed()
        originalVolume = TemiController.getVolume()
        Log.d(TAG, "Velocidad original: $originalSpeedLevel, Volumen original: $originalVolume")

        // 2. Configurar velocidad lenta y volumen
        if (TemiController.hasSettingsPermission()) {
            TemiController.setGoToSpeed(TemiController.SpeedLevel.SLOW)
        } else {
            Log.w(TAG, "Sin permiso SETTINGS, no se puede cambiar velocidad")
            TemiController.requestSettingsPermission()
        }
        TemiController.setVolume(ANNOUNCEMENT_VOLUME)

        // 3. Activar Kiosk Mode y ocultar UI de navegación de Temi
        Log.d(TAG, "Activando Kiosk Mode...")
        TemiController.setKioskModeOn(true)
        TemiController.toggleNavigationBillboard(true) // true = ocultar

        // 4. Abrir AnnouncementActivity en el hilo principal
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Log.d(TAG, "Abriendo AnnouncementActivity...")
            val intent = Intent(context, AnnouncementActivity::class.java).apply {
                putExtra(AnnouncementActivity.EXTRA_TEXTO, texto)
                putExtra(AnnouncementActivity.EXTRA_IMAGEN_URL, imagenUrl)
                putExtra(AnnouncementActivity.EXTRA_ANUNCIO_ID, id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }

        // 5. Iniciar loop de TTS que habla continuamente
        startSpeakingLoop(texto)

        // 6. Iniciar patrullaje
        val success = TemiController.patrol(
            locations = waypoints,
            nonstop = false,
            times = 0, // Infinito
            waiting = 10 // 10 segundos en cada ubicación
        )

        if (!success) {
            Log.e(TAG, "Error iniciando patrullaje")
            stopPatrol()
            return
        }

        // 7. Programar verificación de expiración
        scheduleExpirationCheck(expiresAtStr)
    }

    /**
     * Inicia un loop que habla el texto del anuncio continuamente.
     */
    private fun startSpeakingLoop(texto: String) {
        speakingJob?.cancel()
        
        speakingJob = scope.launch {
            // Hablar inmediatamente al iniciar
            Log.d(TAG, "Iniciando loop de TTS...")
            TemiController.speak(texto)
            
            while (isActive && isPatrolling) {
                delay(SPEAK_INTERVAL_MS)
                if (isPatrolling) {
                    Log.d(TAG, "Repitiendo anuncio...")
                    TemiController.speak(texto)
                }
            }
        }
    }
    
    /**
     * Detiene el loop de TTS.
     */
    private fun stopSpeakingLoop() {
        speakingJob?.cancel()
        speakingJob = null
    }

    /**
     * Programa una verificación periódica de expiración.
     */
    private fun scheduleExpirationCheck(expiresAtStr: String) {
        expirationJob?.cancel()
        
        expirationJob = scope.launch {
            while (isActive && isPatrolling) {
                try {
                    val expiresAt = Instant.parse(expiresAtStr)
                    val now = Instant.now()
                    
                    if (now.isAfter(expiresAt)) {
                        Log.d(TAG, "Anuncio expiró, deteniendo patrullaje...")
                        withContext(Dispatchers.Main) {
                            stopPatrol()
                        }
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verificando expiración: ${e.message}")
                }
                delay(30_000) // Verificar cada 30 segundos
            }
        }
    }

    /**
     * Detiene el patrullaje y vuelve a home base.
     */
    private fun stopPatrol() {
        Log.d(TAG, "=== DETENIENDO PATRULLAJE ===")
        
        isPatrolling = false
        currentAnuncioId = null
        expirationJob?.cancel()
        
        // 1. Detener loop de TTS
        stopSpeakingLoop()
        
        // 2. Detener movimiento
        TemiController.stopMovement()
        
        // 3. Restaurar velocidad y volumen originales
        originalSpeedLevel?.let { speed ->
            if (TemiController.hasSettingsPermission()) {
                TemiController.setGoToSpeed(speed)
                Log.d(TAG, "Velocidad restaurada: $speed")
            }
        }
        originalSpeedLevel = null
        
        originalVolume?.let { volume ->
            TemiController.setVolume(volume)
            Log.d(TAG, "Volumen restaurado: $volume")
        }
        originalVolume = null
        
        // 4. Desactivar Kiosk Mode y restaurar UI de Temi
        Log.d(TAG, "Desactivando Kiosk Mode...")
        TemiController.toggleNavigationBillboard(false) // false = mostrar
        TemiController.setKioskModeOn(false)
        
        // 5. Cerrar AnnouncementActivity
        val closeIntent = Intent("com.spatium.deamon.db.temi.CLOSE_ANNOUNCEMENT")
        context.sendBroadcast(closeIntent)
        
        // 6. Volver a home base
        Log.d(TAG, "Volviendo a home base...")
        TemiController.goTo("home base")
    }

    /**
     * Limpia recursos al destruir.
     */
    fun destroy() {
        stopPolling()
        scope.cancel()
    }
}
