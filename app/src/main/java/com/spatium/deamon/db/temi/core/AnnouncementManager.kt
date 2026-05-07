package com.spatium.deamon.db.temi.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.spatium.deamon.db.temi.BuildConfig
import com.spatium.deamon.db.temi.net.OkHttpSupabaseGateway
import com.spatium.deamon.db.temi.net.SupabaseGateway
import com.spatium.deamon.db.temi.ui.AnnouncementActivity
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

class AnnouncementManager(
    private val context: Context?,
    private val gateway: SupabaseGateway = OkHttpSupabaseGateway(
        BuildConfig.TEMI_EDGE_BASE_URL,
        BuildConfig.SUPABASE_ANON_KEY,
    ),
) {

    companion object {
        private const val TAG = "AnnouncementManager"
        private const val POLLING_INTERVAL_MS = 30_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var expirationJob: Job? = null

    internal var isPatrolling = false
    internal var currentAnuncioId: String? = null

    internal var patrolDispatch: ((kotlinx.serialization.json.JsonObject) -> Unit) = ::startPatrol
    private var originalSpeedLevel: TemiController.SpeedLevel? = null
    private var originalVolume: Int? = null
    private var speakingJob: Job? = null

    private val SPEAK_INTERVAL_MS = 15_000L
    private val ANNOUNCEMENT_VOLUME = 6

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

    fun stopPolling() {
        Log.d(TAG, "Deteniendo polling...")
        pollingJob?.cancel()
        pollingJob = null

        if (isPatrolling) {
            stopPatrol()
        }
    }

    internal suspend fun checkForActiveAnnouncement() {
        try {
            val response = gateway.get("anuncio-activo")
            Log.d(TAG, "Respuesta anuncio-activo: $response")

            val json = response.jsonObject
            val activo = json["activo"]?.jsonPrimitive?.boolean ?: false

            if (activo) {
                val anuncio = json["anuncio"]?.jsonObject
                if (anuncio != null) {
                    val id = anuncio["id"]?.jsonPrimitive?.content ?: return

                    if (id != currentAnuncioId) {
                        Log.d(TAG, "Nuevo anuncio detectado: $id")
                        patrolDispatch(anuncio)
                    }
                }
            } else {
                if (isPatrolling) {
                    Log.d(TAG, "Anuncio expiró, deteniendo patrullaje...")
                    stopPatrol()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando anuncio: ${e.message}")
        }
    }

    private fun startPatrol(anuncio: kotlinx.serialization.json.JsonObject) {
        val id = anuncio["id"]?.jsonPrimitive?.content ?: return
        val texto = anuncio["texto"]?.jsonPrimitive?.content ?: ""
        val imagenUrl = anuncio["imagen_url"]?.jsonPrimitive?.content
        val expiresAtStr = anuncio["expires_at"]?.jsonPrimitive?.content ?: ""
        val waypointsJson: JsonArray = anuncio["waypoints"]?.jsonArray ?: JsonArray(emptyList())

        val waypoints = waypointsJson.map { it.jsonPrimitive.content }

        if (waypoints.size < 3) {
            Log.e(TAG, "Error: Se requieren al menos 3 waypoints, recibidos: ${waypoints.size}")
            return
        }

        val acquired = runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT) }
        if (!acquired) {
            Log.w(TAG, "Skipping patrol activation — another mode is active: ${ExclusiveModeArbiter.currentMode()}")
            return
        }

        val savedLocations = TemiController.getLocations()
        val invalidWaypoints = waypoints.filter { it !in savedLocations }
        if (invalidWaypoints.isNotEmpty()) {
            Log.w(TAG, "Waypoints no encontrados en robot: $invalidWaypoints")
        }

        Log.d(TAG, "=== INICIANDO MODO PATRULLAJE ===")
        Log.d(TAG, "ID: $id")
        Log.d(TAG, "Texto: $texto")
        Log.d(TAG, "Imagen: $imagenUrl")
        Log.d(TAG, "Waypoints: $waypoints")
        Log.d(TAG, "Expira: $expiresAtStr")

        currentAnuncioId = id
        isPatrolling = true

        originalSpeedLevel = TemiController.getGoToSpeed()
        originalVolume = TemiController.getVolume()
        Log.d(TAG, "Velocidad original: $originalSpeedLevel, Volumen original: $originalVolume")

        if (TemiController.hasSettingsPermission()) {
            TemiController.setGoToSpeed(TemiController.SpeedLevel.SLOW)
        } else {
            Log.w(TAG, "Sin permiso SETTINGS, no se puede cambiar velocidad")
            TemiController.requestSettingsPermission()
        }
        TemiController.setVolume(ANNOUNCEMENT_VOLUME)

        Log.d(TAG, "Activando Kiosk Mode...")
        TemiController.setKioskModeOn(true)
        TemiController.toggleNavigationBillboard(true)

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Log.d(TAG, "Abriendo AnnouncementActivity...")
            val intent = Intent(context, AnnouncementActivity::class.java).apply {
                putExtra(AnnouncementActivity.EXTRA_TEXTO, texto)
                putExtra(AnnouncementActivity.EXTRA_IMAGEN_URL, imagenUrl)
                putExtra(AnnouncementActivity.EXTRA_ANUNCIO_ID, id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context?.startActivity(intent)
        }

        startSpeakingLoop(texto)

        val success = TemiController.patrol(
            locations = waypoints,
            nonstop = false,
            times = 0,
            waiting = 10,
        )

        if (!success) {
            Log.e(TAG, "Error iniciando patrullaje")
            stopPatrol()
            return
        }

        scheduleExpirationCheck(expiresAtStr)
    }

    private fun startSpeakingLoop(texto: String) {
        speakingJob?.cancel()

        speakingJob = scope.launch {
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

    private fun stopSpeakingLoop() {
        speakingJob?.cancel()
        speakingJob = null
    }

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
                delay(30_000)
            }
        }
    }

    private fun stopPatrol() {
        Log.d(TAG, "=== DETENIENDO PATRULLAJE ===")

        isPatrolling = false
        currentAnuncioId = null
        expirationJob?.cancel()

        stopSpeakingLoop()
        TemiController.stopMovement()

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

        Log.d(TAG, "Desactivando Kiosk Mode...")
        TemiController.toggleNavigationBillboard(false)
        TemiController.setKioskModeOn(false)

        val closeIntent = Intent("com.spatium.deamon.db.temi.CLOSE_ANNOUNCEMENT")
        context?.sendBroadcast(closeIntent)

        Log.d(TAG, "Volviendo a home base...")
        TemiController.goTo("home base")

        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
    }

    fun destroy() {
        stopPolling()
        scope.cancel()
    }
}
