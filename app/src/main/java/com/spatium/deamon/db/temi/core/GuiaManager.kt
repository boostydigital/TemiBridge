package com.spatium.deamon.db.temi.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.spatium.deamon.db.temi.BuildConfig
import com.spatium.deamon.db.temi.net.OkHttpSupabaseGateway
import com.spatium.deamon.db.temi.net.SupabaseGateway
import com.spatium.deamon.db.temi.ui.GuiaActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Manager para el Modo Guia (visita guiada).
 *
 * Hace polling cada 30 s al Edge Function guia-pendiente.
 * Cuando detecta una guia pendiente:
 *   1. Adquiere el lock exclusivo via ExclusiveModeArbiter.
 *   2. Guarda el estado del robot (RobotStateSnapshot).
 *   3. Configura kiosk, velocidad lenta, navigation billboard.
 *   4. Navega a waypoint_inicial → lanza GuiaActivity en WAITING.
 *   5. Al recibir el tap del usuario → navega a waypoint_final (GUIDING).
 *   6. En llegada o expiración → finaliza con POST a finalizar-guia, restaura estado.
 *
 * Usa RobotGateway para aislar el SDK de Temi y permitir tests con FakeRobotGateway.
 */
class GuiaManager(
    private val context: Context? = null,
    private val robot: RobotGateway = DefaultRobotGateway,
    private val gateway: SupabaseGateway = OkHttpSupabaseGateway(
        BuildConfig.TEMI_EDGE_BASE_URL,
        BuildConfig.SUPABASE_ANON_KEY,
    ),
) {

    companion object {
        private const val TAG = "GuiaManager"
        private const val POLLING_INTERVAL_MS = 30_000L
        private const val INVITATION_TTS_INTERVAL_MS = 30_000L

        private val JSON = Json { ignoreUnknownKeys = true }
    }

    // ─────────────────────── Coroutine scope ────────────────────────

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var expirationJob: Job? = null
    private var invitationTtsJob: Job? = null

    // ─────────────────────── State ──────────────────────────────────

    private val stateMachine = GuiaStateMachine()

    @Volatile private var currentGuia: GuiaPayload? = null

    @Volatile private var savedState: RobotStateSnapshot? = null

    @Volatile private var receiverRegistered = false

    // ─────────────────────── Broadcast receiver ─────────────────────

    private val userTappedStartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Broadcast recibido: ACTION_GUIA_USER_TAPPED_START")
            val guia = currentGuia ?: run {
                Log.w(TAG, "onUserTappedStart: currentGuia es null, ignorando")
                return
            }
            scope.launch { onUserTappedStart(guia) }
        }
    }

    // ─────────────────────── Public API ─────────────────────────────

    /**
     * Inicia el polling de guias pendientes.
     * Idempotente: si ya está activo, no hace nada.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) {
            Log.d(TAG, "Polling ya está activo")
            return
        }

        Log.d(TAG, "Iniciando polling de guias...")

        registerUserTapReceiver()

        pollingJob = scope.launch {
            while (isActive) {
                try {
                    checkForPendingGuia()
                } catch (e: Exception) {
                    Log.e(TAG, "Error en polling: ${e.message}")
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    /**
     * Detiene el polling. No cancela una guia activa.
     */
    fun stopPolling() {
        Log.d(TAG, "Deteniendo polling...")
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Cancela la guia activa con el motivo dado.
     * Si no hay guia activa, no hace nada.
     */
    fun cancelCurrentGuia(reason: String = "cancelada") {
        val guia = currentGuia ?: run {
            Log.d(TAG, "cancelCurrentGuia: no hay guia activa")
            return
        }
        scope.launch { forceFinish(guia, reason) }
    }

    /**
     * Limpia stale guias en Supabase. Llamar UNA vez en app start,
     * antes de startPolling, para recuperarse de crashes anteriores.
     */
    suspend fun sweepStaleGuias() {
        Log.d(TAG, "Iniciando crash recovery sweep...")
        try {
            val result = gateway.post("robot-sweep-guias", buildJsonObject {})
            Log.d(TAG, "Sweep completado: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Error en crash recovery sweep: ${e.message}")
        }
    }

    /**
     * Cancela todos los jobs y desregistra receivers.
     * Llamar en onDestroy del componente que posee el GuiaManager.
     */
    fun destroy() {
        Log.d(TAG, "GuiaManager destruyéndose...")
        stopPolling()
        unregisterUserTapReceiver()
        scope.cancel()
    }

    // ─────────────────────── Polling tick ───────────────────────────

    internal suspend fun checkForPendingGuia() {
        // Si hay una guia activa, sólo chequear si fue cancelada externamente
        val currentState = stateMachine.currentState()
        if (currentState !is GuiaState.Idle) {
            checkForExternalCancellation()
            return
        }

        try {
            val response = gateway.get("guia-pendiente")
            val body = response.toString()
            Log.d(TAG, "guia-pendiente: $body")

            val pendienteResponse = JSON.decodeFromString<GuiaPendienteResponse>(body)
            if (pendienteResponse.pendiente && pendienteResponse.guia != null) {
                onGuiaReceived(pendienteResponse.guia)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en checkForPendingGuia(): ${e.message}")
        }
    }

    // ─────────────────────── External cancellation ──────────────────

    /**
     * Detecta si la guia activa fue cancelada externamente via finalizar-guia.
     * El polling sigue corriendo aunque haya una guia activa — si el backend
     * ya no la devuelve como pendiente, significa que fue finalizada externamente.
     */
    private suspend fun checkForExternalCancellation() {
        val guia = currentGuia ?: return
        val currentState = stateMachine.currentState()

        // Solo verificar si estamos en Waiting o Guiding (no Finishing)
        if (currentState !is GuiaState.Waiting && currentState !is GuiaState.Guiding) return

        try {
            val response = gateway.get("guia-pendiente")
            val body = response.toString()
            val pendienteResponse = JSON.decodeFromString<GuiaPendienteResponse>(body)

            // Si pendiente=false y tenemos una guia activa → fue cancelada externamente
            if (!pendienteResponse.pendiente) {
                Log.w(TAG, "Guia cancelada externamente, iniciando teardown")
                forceFinish(guia, "cancelada")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando cancelación externa: ${e.message}")
        }
    }

    // ─────────────────────── Activation ─────────────────────────────

    private suspend fun onGuiaReceived(guia: GuiaPayload) {
        if (stateMachine.currentState() !is GuiaState.Idle) {
            Log.d(TAG, "onGuiaReceived: ignorando — no estamos en Idle")
            return
        }

        if (!ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA)) {
            Log.w(TAG, "onGuiaReceived: otro modo activo, saltando ciclo")
            return
        }

        Log.d(TAG, "=== INICIANDO MODO GUIA === id=${guia.id}")

        // Capturar estado del robot antes de modificarlo
        val snapshot = RobotStateSnapshot.capture(robot)
        savedState = snapshot

        // Aplicar configuración de guia — HIGH speed para todos los goTo del ciclo
        if (robot.hasSettingsPermission()) {
            robot.setGoToSpeed(TemiController.SpeedLevel.HIGH)
        }
        robot.setKioskModeOn(true)
        // Deshabilitar auto-return-to-charger del SDK Temi para que el robot
        // no abandone el waypoint_inicial por inactividad. La guia se queda
        // hasta expires_at o hasta que el usuario toque el botón.
        TemiController.setAutoReturnOn(false)
        // Defensa en profundidad: si el firmware del Temi dispara goTo("home base")
        // por su cuenta, el guard lo intercepta y redirige al waypoint protegido.
        TemiController.enableAutoReturnGuard(guia.waypointInicial)
        // En WAITING el robot debe quedar INMÓVIL aunque alguien se le acerque.
        // Se reactivan en GUIDING para que el Personal User Lead funcione.
        TemiController.setDetectionModeOn(false)
        TemiController.setTrackUserOn(false)
        robot.toggleNavigationBillboard(true) // true = ocultar

        // Transicionar estado
        currentGuia = guia
        stateMachine.transitionTo(GuiaState.Waiting(guia, snapshot))

        // Programar timer de expiración
        scheduleExpirationTimer(guia)

        // Navegar a waypoint_inicial; al llegar lanzar la actividad
        Log.d(TAG, "Navegando a waypoint_inicial: ${guia.waypointInicial}")
        robot.setArrivalCallbackOnce {
            scope.launch {
                launchGuiaActivity(guia)
                // El bienvenida_tts NO se reproduce en WAITING.
                // Solo suena UNA VEZ después de que el usuario toque el botón.
            }
        }
        robot.setAbortCallback {
            scope.launch {
                Log.w(TAG, "Navegación abortada en waypoint_inicial")
                forceFinish(guia, "cancelada")
            }
        }
        robot.goTo(guia.waypointInicial)
    }

    // ─────────────────────── Activity launch ────────────────────────

    private fun launchGuiaActivity(guia: GuiaPayload) {
        val ctx = context ?: return
        Log.d(TAG, "Lanzando GuiaActivity para evento '${guia.nombreEvento}'")
        val intent = Intent(ctx, GuiaActivity::class.java).apply {
            putExtra(GuiaActivity.EXTRA_NOMBRE_EVENTO, guia.nombreEvento)
            putExtra(GuiaActivity.EXTRA_DESCRIPCION, guia.descripcion ?: "")
            putExtra(GuiaActivity.EXTRA_ETIQUETA_BOTON, guia.etiquetaBoton)
            putExtra(GuiaActivity.EXTRA_IMAGEN_FONDO_URL, guia.imagenFondoUrl ?: "")
            putExtra(GuiaActivity.EXTRA_VIDEO_LOOP_URL, guia.videoLoopUrl ?: "")
            putExtra(GuiaActivity.EXTRA_INITIAL_STATE, GuiaActivity.STATE_WAITING)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        ctx.startActivity(intent)
    }

    // ─────────────────────── Invitation TTS loop ────────────────────

    private fun startInvitationTtsLoop(ttsText: String) {
        invitationTtsJob?.cancel()
        invitationTtsJob = scope.launch {
            Log.d(TAG, "Iniciando loop de invitación TTS...")
            robot.speak(ttsText)
            while (isActive && stateMachine.currentState() is GuiaState.Waiting) {
                delay(INVITATION_TTS_INTERVAL_MS)
                if (stateMachine.currentState() is GuiaState.Waiting) {
                    Log.d(TAG, "Repitiendo invitación TTS...")
                    robot.speak(ttsText)
                }
            }
        }
    }

    private fun stopInvitationTtsLoop() {
        invitationTtsJob?.cancel()
        invitationTtsJob = null
    }

    // ─────────────────────── User tap (Waiting → Guiding) ───────────

    private suspend fun onUserTappedStart(guia: GuiaPayload) {
        val currentState = stateMachine.currentState()
        if (currentState !is GuiaState.Waiting) {
            Log.w(TAG, "onUserTappedStart: estado no es Waiting (es $currentState), ignorando")
            return
        }

        val snapshot = currentState.originalState
        if (!stateMachine.tryTransitionTo(GuiaState.Guiding(guia, snapshot))) {
            Log.w(TAG, "onUserTappedStart: transición Waiting→Guiding rechazada")
            return
        }

        Log.d(TAG, "Usuario inició guia — transicionando a GUIDING")
        stopInvitationTtsLoop()

        // Decirle a la Activity que muestre la pantalla de guiding
        val showGuidingIntent = Intent(GuiaActivity.ACTION_GUIA_SHOW_GUIDING).apply {
            `package` = context?.packageName
        }
        context?.sendBroadcast(showGuidingIntent)

        // Hablar bienvenida, luego navegar
        robot.speak(guia.bienvenidaTts)
        delay(2500) // breve pausa antes de navegar

        // Reactivar detección + track user para que Personal User Lead funcione
        // (el robot necesita "ver" al usuario para mantener la pantalla mirándolo).
        TemiController.setDetectionModeOn(true)
        TemiController.setTrackUserOn(true)
        robot.enableFaceTracking()

        // Issue 3: set HIGH speed for the guided trip to waypoint_final
        if (robot.hasSettingsPermission()) {
            robot.setGoToSpeed(TemiController.SpeedLevel.HIGH)
        }

        // Actualizar guardedTarget al destino final (para anti-auto-return durante el viaje)
        TemiController.updateGuardedTarget(guia.waypointFinal)

        Log.d(TAG, "Navegando a waypoint_final: ${guia.waypointFinal}")
        robot.setArrivalCallbackOnce {
            scope.launch { onArrival(guia) }
        }
        robot.setAbortCallback {
            scope.launch {
                Log.w(TAG, "Navegación abortada en waypoint_final")
                forceFinish(guia, "cancelada")
            }
        }
        // Issue 2: navigate backwards so the screen faces the following visitor
        robot.goToBackwards(guia.waypointFinal)
    }

    // ─────────────────────── Arrival ────────────────────────────────

    private suspend fun onArrival(guia: GuiaPayload) {
        val currentState = stateMachine.currentState()
        if (currentState !is GuiaState.Guiding) {
            Log.w(TAG, "onArrival: estado no es Guiding (es $currentState), ignorando")
            return
        }

        val snapshot = currentState.originalState

        Log.d(TAG, "=== LLEGADA AL DESTINO ===")
        robot.disableFaceTracking()

        // TTS de llegada (si hay)
        if (!guia.llegadaTts.isNullOrBlank()) {
            robot.speak(guia.llegadaTts)
            delay(3000)
        }

        // Issue 1: check expiration BEFORE deciding to loop or finalize
        if (System.currentTimeMillis() >= guia.expiresAtInstant.toEpochMilli()) {
            Log.d(TAG, "Guia expirada en llegada — finalizando ciclo")
            stateMachine.transitionTo(GuiaState.Finishing(guia, "completada"))

            // Cerrar la Activity
            val closeIntent = Intent(GuiaActivity.ACTION_GUIA_CLOSE).apply {
                `package` = context?.packageName
            }
            context?.sendBroadcast(closeIntent)

            finalizarGuia(guia.id, "completada")
            cleanup(snapshot)

            Log.d(TAG, "Volviendo a home base...")
            robot.goTo("home base")
        } else {
            // Not yet expired — loop back to waypoint_inicial
            Log.d(TAG, "Guia no expirada — iniciando ciclo de retorno a waypoint_inicial")

            // Transition Guiding → Waiting (loop case)
            stateMachine.transitionTo(GuiaState.Waiting(guia, snapshot))

            // Send waiting broadcast so Activity shows the waiting screen again
            val showWaitingIntent = Intent(GuiaActivity.ACTION_GUIA_SHOW_WAITING).apply {
                `package` = context?.packageName
            }
            context?.sendBroadcast(showWaitingIntent)

            // HIGH speed también al retornar (el ciclo es rápido — visitas seguidas)
            if (robot.hasSettingsPermission()) {
                robot.setGoToSpeed(TemiController.SpeedLevel.HIGH)
            }

            // Actualizar guardedTarget al waypoint_inicial (para anti-auto-return en retorno)
            TemiController.updateGuardedTarget(guia.waypointInicial)

            // Volvemos a WAITING al llegar al inicial: robot inmóvil aunque se le acerquen.
            TemiController.setDetectionModeOn(false)
            TemiController.setTrackUserOn(false)

            Log.d(TAG, "Navegando de regreso a waypoint_inicial: ${guia.waypointInicial}")
            robot.setArrivalCallbackOnce {
                scope.launch { onReturnToInicial(guia, snapshot) }
            }
            robot.setAbortCallback {
                scope.launch {
                    Log.w(TAG, "Navegación abortada en retorno a waypoint_inicial")
                    forceFinish(guia, "cancelada")
                }
            }
            robot.goTo(guia.waypointInicial)
        }
    }

    /** Called when the robot arrives back at waypoint_inicial after completing a guided tour loop. */
    private suspend fun onReturnToInicial(guia: GuiaPayload, snapshot: RobotStateSnapshot) {
        val currentState = stateMachine.currentState()
        if (currentState !is GuiaState.Waiting) {
            Log.w(TAG, "onReturnToInicial: estado no es Waiting (es $currentState), ignorando")
            return
        }

        Log.d(TAG, "=== RETORNO A INICIAL === listo para nuevo visitante")

        // Re-launch GuiaActivity in waiting state for the next visitor.
        // NO TTS loop — el bienvenida_tts solo suena tras el tap del botón.
        launchGuiaActivity(guia)
    }

    // ─────────────────────── Expiration / cancellation ──────────────

    private suspend fun forceFinish(guia: GuiaPayload, reason: String) {
        val currentState = stateMachine.currentState()

        // Evitar double-finish
        if (currentState is GuiaState.Idle || currentState is GuiaState.Finishing) {
            Log.d(TAG, "forceFinish: ya en estado $currentState, ignorando")
            return
        }

        val snapshot = when (currentState) {
            is GuiaState.Waiting -> currentState.originalState
            is GuiaState.Guiding -> currentState.originalState
            else -> savedState
        }

        Log.w(TAG, "=== FORCE FINISH === reason=$reason")
        stateMachine.transitionTo(GuiaState.Finishing(guia, reason))

        robot.stopMovement()
        robot.disableFaceTracking()

        // Cerrar Activity
        val closeIntent = Intent(GuiaActivity.ACTION_GUIA_CLOSE).apply {
            `package` = context?.packageName
        }
        context?.sendBroadcast(closeIntent)

        finalizarGuia(guia.id, reason)
        cleanup(snapshot)
    }

    // ─────────────────────── Cleanup ────────────────────────────────

    private fun cleanup(snapshot: RobotStateSnapshot?) {
        Log.d(TAG, "Limpiando estado de GuiaManager...")

        stopInvitationTtsLoop()

        expirationJob?.cancel()
        expirationJob = null

        snapshot?.restore(robot)
        savedState = null
        currentGuia = null

        // Restaurar auto-return-to-charger del SDK Temi (se había deshabilitado al inicio)
        TemiController.setAutoReturnOn(true)
        // Restaurar detección + track user al estado default (true)
        TemiController.setDetectionModeOn(true)
        TemiController.setTrackUserOn(true)
        // Desactivar el guard anti-auto-return: la guia terminó, el robot puede volver a base si quiere
        TemiController.disableAutoReturnGuard()

        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_GUIA)
        stateMachine.reset()

        Log.d(TAG, "Cleanup completo — regresando a Idle")
    }

    // ─────────────────────── Expiration timer ───────────────────────

    private fun scheduleExpirationTimer(guia: GuiaPayload) {
        expirationJob?.cancel()
        expirationJob = scope.launch {
            val expiresAtMs = guia.expiresAtInstant.toEpochMilli()
            val delayMs = maxOf(0L, expiresAtMs - System.currentTimeMillis())
            Log.d(TAG, "Expiration timer programado en ${delayMs / 1000}s")
            delay(delayMs)

            val state = stateMachine.currentState()
            if (state !is GuiaState.Idle && state !is GuiaState.Finishing) {
                Log.w(TAG, "Guia expirada — iniciando teardown")
                forceFinish(guia, "expirada")
            }
        }
    }

    // ─────────────────────── Gateway: finalizar-guia ────────────────

    /**
     * POST a finalizar-guia con retry lineal (1s, 2s, 3s).
     */
    internal suspend fun finalizarGuia(id: String, estadoFinal: String) {
        val body = buildJsonObject {
            put("id", id)
            put("estado_final", estadoFinal)
        }

        repeat(3) { attempt ->
            try {
                val result = gateway.post("finalizar-guia", body)
                Log.d(TAG, "finalizar-guia OK: estado=$estadoFinal, id=$id — $result")
                return // éxito — salir del retry
            } catch (e: Exception) {
                Log.e(TAG, "finalizar-guia error intento ${attempt + 1}/3: ${e.message}")
            }

            if (attempt < 2) delay((attempt + 1) * 1000L)
        }

        Log.e(TAG, "finalizar-guia falló tras 3 intentos — continuando de todas formas")
    }

    // ─────────────────────── Receiver registration ──────────────────

    private fun registerUserTapReceiver() {
        val ctx = context ?: return
        if (receiverRegistered) return
        val filter = IntentFilter(GuiaActivity.ACTION_GUIA_USER_TAPPED_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(userTappedStartReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(userTappedStartReceiver, filter)
        }
        receiverRegistered = true
        Log.d(TAG, "BroadcastReceiver de inicio de guia registrado")
    }

    private fun unregisterUserTapReceiver() {
        val ctx = context ?: return
        if (!receiverRegistered) return
        try {
            ctx.unregisterReceiver(userTappedStartReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error al desregistrar receiver: ${e.message}")
        }
        receiverRegistered = false
    }
}

// ─────────────────────── Internal DTOs ──────────────────────────

/**
 * Respuesta del Edge Function guia-pendiente.
 * Si pendiente=true, la guia está lista para ser procesada.
 */
@Serializable
private data class GuiaPendienteResponse(
    val pendiente: Boolean,
    val guia: GuiaPayload? = null,
)
