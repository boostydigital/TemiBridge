package com.spatium.deamon.db.temi.core

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.spatium.deamon.db.temi.ui.KioskWebActivity
import com.spatium.deamon.db.temi.ui.MainActivity
import com.spatium.deamon.db.temi.ui.MenuActivity
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sistema de cola para comandos del robot Temi.
 * Garantiza ejecución secuencial con delays entre comandos para evitar sobrecarga.
 */
object CommandQueue {

    private const val TAG = "CommandQueue"

    // Delays configurables entre tipos de comando (en ms)
    private const val DELAY_OPEN_APP = 1500L // 1.5 segundos para que la app se abra
    private const val DELAY_AFTER_SAY = 3000L // 3 segundos después de hablar
    private const val DELAY_AFTER_WEB = 2000L // 2 segundos después de abrir web
    private const val DELAY_AFTER_SEQUENCE = 8000L // 8 segundos después de secuencia (para que cargue)
    private const val DELAY_DEFAULT = 1500L // 1.5 segundos por defecto
    private const val WEB_RETRY_DELAY = 500L // 500ms entre reintentos de web
    private const val WEB_MAX_RETRIES = 3 // Máximo 3 reintentos para abrir web
    private const val MAX_WAIT_SECONDS = 120L // Máximo 2 minutos de espera total

    private val handler = Handler(Looper.getMainLooper())
    private val commandQueue = ConcurrentLinkedQueue<Command>()
    private val isProcessing = AtomicBoolean(false)

    // Latch para sincronización - permite que el caller espere a que termine todo el pedido
    @Volatile
    private var completionLatch: CountDownLatch? = null

    sealed class Command {
        data class OpenApp(val context: Context) : Command()
        data class Say(val text: String) : Command()
        data class Web(val context: Context, val url: String?, val place: String? = null) : Command()
        data class Sequence(val sequenceId: String) : Command()
    }

    /**
     * Encola un pedido completo y ESPERA a que termine.
     * SIEMPRE abre la app primero, luego ejecuta los comandos en orden.
     * Si orden_action está definido, usa ese orden.
     * Si no, ejecuta automáticamente todos los campos que tengan datos (say, comida, secuencia).
     * IMPORTANTE: Si orden_action contiene "comida" pero el campo comida está vacío,
     * se abre MainActivity de forma forzosa.
     *
     * @return true si todos los comandos se ejecutaron, false si hubo timeout
     */
    fun enqueuePedidoAndWait(context: Context, pedido: RobotPedido): Boolean {
        val hasSay = !pedido.say.isNullOrBlank()
        val hasComida = !pedido.comida.isNullOrBlank()
        val hasSecuencia = !pedido.secuencia.isNullOrBlank()

        // Verificar si orden_action incluye "comida" (para forzar apertura de app si comida está vacío)
        val ordenActionIncludesComida = pedido.ordenAction?.lowercase()?.contains("comida") == true

        // Determinar el orden de ejecución
        val steps = parseStepsWithData(pedido.ordenAction, hasSay, hasComida, hasSecuencia, ordenActionIncludesComida)
        Log.d(TAG, "Encolando pedido id=${pedido.id} con steps=$steps (say=$hasSay, comida=$hasComida, seq=$hasSecuencia, ordenIncludesComida=$ordenActionIncludesComida)")

        // Contar cuántos comandos vamos a encolar (incluyendo OpenApp)
        var commandCount = 1 // OpenApp siempre se ejecuta primero

        // SIEMPRE abrir la app primero
        Log.d(TAG, "Paso 0: Abriendo app Temi Deamon DB primero")
        enqueue(Command.OpenApp(context.applicationContext))

        for (step in steps) {
            when (step) {
                "say" -> {
                    val text = pedido.say?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        enqueue(Command.Say(text))
                        commandCount++
                    }
                }
                "comida" -> {
                    val comidaValue = pedido.comida?.trim()
                    val place = pedido.place?.trim()
                    // Si orden_action incluye "comida" pero comida está vacía, encolar de todas formas
                    // para forzar apertura de MainActivity
                    if (!comidaValue.isNullOrBlank()) {
                        enqueue(Command.Web(context.applicationContext, comidaValue, place))
                        commandCount++
                    } else if (ordenActionIncludesComida) {
                        Log.d(TAG, "orden_action incluye 'comida' pero campo comida vacío -> forzando apertura de app")
                        enqueue(Command.Web(context.applicationContext, null, place))
                        commandCount++
                    }
                }
                "secuencia" -> {
                    val id = pedido.secuencia?.trim().orEmpty()
                    if (id.isNotEmpty()) {
                        enqueue(Command.Sequence(id))
                        commandCount++
                    }
                }
            }
        }

        // Crear latch para esperar a que terminen todos los comandos
        val latch = CountDownLatch(commandCount)
        completionLatch = latch
        Log.d(TAG, "Creado latch para $commandCount comandos")

        // Iniciar procesamiento si no está activo
        processNext()

        // Esperar a que terminen todos los comandos (máximo MAX_WAIT_SECONDS)
        return try {
            val completed = latch.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS)
            if (completed) {
                Log.d(TAG, "Pedido id=${pedido.id} completado exitosamente")
            } else {
                Log.w(TAG, "Pedido id=${pedido.id} timeout después de ${MAX_WAIT_SECONDS}s")
            }
            completionLatch = null
            completed
        } catch (e: InterruptedException) {
            Log.e(TAG, "Pedido id=${pedido.id} interrumpido: ${e.message}")
            completionLatch = null
            false
        }
    }

    /**
     * Versión legacy que no espera (para compatibilidad)
     */
    fun enqueuePedido(context: Context, pedido: RobotPedido) {
        val hasSay = !pedido.say.isNullOrBlank()
        val hasComida = !pedido.comida.isNullOrBlank()
        val hasSecuencia = !pedido.secuencia.isNullOrBlank()

        val ordenActionIncludesComida = pedido.ordenAction?.lowercase()?.contains("comida") == true
        val steps = parseStepsWithData(pedido.ordenAction, hasSay, hasComida, hasSecuencia, ordenActionIncludesComida)
        Log.d(TAG, "[Legacy] Encolando pedido id=${pedido.id} con steps=$steps")

        // SIEMPRE abrir la app primero
        enqueue(Command.OpenApp(context.applicationContext))

        for (step in steps) {
            when (step) {
                "say" -> {
                    val text = pedido.say?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        enqueue(Command.Say(text))
                    }
                }
                "comida" -> {
                    val comidaValue = pedido.comida?.trim()
                    val place = pedido.place?.trim()
                    if (!comidaValue.isNullOrBlank()) {
                        enqueue(Command.Web(context.applicationContext, comidaValue, place))
                    } else if (ordenActionIncludesComida) {
                        enqueue(Command.Web(context.applicationContext, null, place))
                    }
                }
                "secuencia" -> {
                    val id = pedido.secuencia?.trim().orEmpty()
                    if (id.isNotEmpty()) {
                        enqueue(Command.Sequence(id))
                    }
                }
            }
        }
        processNext()
    }

    /**
     * Encola un comando individual.
     */
    fun enqueue(command: Command) {
        Log.d(TAG, "Encolando comando: $command")
        commandQueue.offer(command)
        processNext()
    }

    /**
     * Procesa el siguiente comando en la cola si no hay uno en proceso.
     */
    private fun processNext() {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "Ya hay un comando en proceso, esperando...")
            return
        }

        val command = commandQueue.poll()
        if (command == null) {
            Log.d(TAG, "Cola vacía, nada que procesar")
            isProcessing.set(false)
            return
        }

        Log.d(TAG, "Procesando comando: $command")

        handler.post {
            try {
                val delayAfter = executeCommand(command)
                scheduleNext(delayAfter)
            } catch (t: Throwable) {
                Log.e(TAG, "Error ejecutando comando: ${t.message}", t)
                scheduleNext(DELAY_DEFAULT)
            }
        }
    }

    /**
     * Ejecuta un comando y retorna el delay recomendado después de su ejecución.
     */
    private fun executeCommand(command: Command): Long = when (command) {
        is Command.OpenApp -> {
            Log.d(TAG, "Ejecutando OPEN_APP: Abriendo MainActivity")
            launchMainActivity(command.context)
            DELAY_OPEN_APP
        }

        is Command.Say -> {
            Log.d(TAG, "Ejecutando SAY: ${command.text}")
            TemiController.speak(command.text)
            // Estimar duración del speech: ~100ms por carácter + base
            val estimatedDuration = (command.text.length * 80L) + 1000L
            maxOf(DELAY_AFTER_SAY, estimatedDuration)
        }

        is Command.Web -> {
            Log.d(TAG, "Ejecutando WEB: ${command.url}, place=${command.place}")
            executeWebWithRetry(command.context, command.url, 0, command.place)
            DELAY_AFTER_WEB
        }

        is Command.Sequence -> {
            Log.d(TAG, "Ejecutando SEQUENCE: ${command.sequenceId}")
            executeSequence(command.sequenceId)
            DELAY_AFTER_SEQUENCE
        }
    }

    /**
     * Ejecuta apertura de web con reintentos para mayor confiabilidad.
     * Si url es "comida", abre MenuActivity en lugar de KioskWebActivity.
     */
    private fun executeWebWithRetry(context: Context, url: String?, attempt: Int, place: String? = null) {
        if (url.isNullOrBlank()) {
            Log.d(TAG, "URL vacía, volviendo a MainActivity")
            launchMainActivity(context)
            return
        }

        // Si url es "comida", abrir MenuActivity
        if (url.lowercase() == "comida") {
            Log.d(TAG, "URL es 'comida', abriendo MenuActivity")
            launchMenuActivity(context, place)
            return
        }

        try {
            Log.d(TAG, "Intentando abrir web (intento ${attempt + 1}/$WEB_MAX_RETRIES): $url")

            val intent = Intent(context, KioskWebActivity::class.java).apply {
                putExtra(KioskWebActivity.EXTRA_URL, url)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }
            context.startActivity(intent)
            Log.d(TAG, "Intent de KioskWebActivity enviado exitosamente")
        } catch (t: Throwable) {
            Log.e(TAG, "Error abriendo web (intento ${attempt + 1}): ${t.message}", t)

            if (attempt < WEB_MAX_RETRIES - 1) {
                handler.postDelayed({
                    executeWebWithRetry(context, url, attempt + 1, place)
                }, WEB_RETRY_DELAY)
            } else {
                Log.e(TAG, "Fallaron todos los intentos de abrir web, volviendo a MainActivity")
                launchMainActivity(context)
            }
        }
    }

    private fun launchMenuActivity(context: Context, place: String?) {
        try {
            val intent = Intent(context, MenuActivity::class.java).apply {
                putExtra("place", place ?: "")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            context.startActivity(intent)
            Log.d(TAG, "MenuActivity lanzada con place=$place")
        } catch (t: Throwable) {
            Log.e(TAG, "Error lanzando MenuActivity: ${t.message}", t)
            launchMainActivity(context)
        }
    }

    private fun launchMainActivity(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Error lanzando MainActivity: ${t.message}", t)
        }
    }

    private fun executeSequence(sequenceId: String) {
        if (!TemiController.isSequencePermissionGranted()) {
            Log.w(TAG, "Permiso de secuencias no concedido, solicitando...")
            val granted = TemiController.requestSequencePermission()
            if (!granted) {
                Log.w(TAG, "Permiso de secuencias no concedido; no se ejecutará la secuencia id=$sequenceId")
                return
            }
        }

        val ok = TemiController.playSequenceById(sequenceId)
        if (!ok) {
            Log.w(TAG, "playSequenceById failed for id=$sequenceId")
        } else {
            Log.d(TAG, "Secuencia $sequenceId iniciada correctamente")
        }
    }

    /**
     * Programa el procesamiento del siguiente comando después del delay especificado.
     */
    private fun scheduleNext(delayMs: Long) {
        Log.d(TAG, "Esperando ${delayMs}ms antes del siguiente comando...")

        // Notificar que un comando terminó
        completionLatch?.countDown()
        val remaining = completionLatch?.count ?: 0
        Log.d(TAG, "Comando completado. Comandos restantes en latch: $remaining")

        handler.postDelayed({
            isProcessing.set(false)
            processNext()
        }, delayMs)
    }

    /**
     * Determina el orden de ejecución basado en orden_action y los datos disponibles.
     * Si orden_action está vacío, genera automáticamente el orden basado en qué campos tienen datos.
     * Si orden_action tiene valores, usa ese orden pero solo para los campos que tienen datos.
     * EXCEPCIÓN: Si orden_action incluye "comida", siempre se incluye "comida" en los pasos
     * (para forzar apertura de app cuando comida está vacío).
     */
    private fun parseStepsWithData(
        ordenAction: String?,
        hasSay: Boolean,
        hasComida: Boolean,
        hasSecuencia: Boolean,
        forceComidaStep: Boolean = false,
    ): List<String> {
        val raw = ordenAction?.trim().orEmpty()

        // Si no hay orden_action definido, generar orden automático basado en datos disponibles
        if (raw.isEmpty()) {
            val autoSteps = mutableListOf<String>()
            // Orden por defecto: say -> comida -> secuencia
            if (hasSay) autoSteps.add("say")
            if (hasComida) autoSteps.add("comida")
            if (hasSecuencia) autoSteps.add("secuencia")
            Log.d(TAG, "orden_action vacío, generando orden automático: $autoSteps")
            return if (autoSteps.isEmpty()) listOf("secuencia") else autoSteps
        }

        // Si hay orden_action, parsear y filtrar solo los que tienen datos
        val normalized = raw.replace(";", ",").replace(".", ",")
        val requestedSteps = normalized.split(",").mapNotNull { part ->
            val s = part.trim().lowercase()
            if (s == "say" || s == "comida" || s == "secuencia") s else null
        }

        // Filtrar solo los pasos que tienen datos disponibles
        // EXCEPCIÓN: "comida" se incluye si forceComidaStep=true (orden_action lo pide aunque comida esté vacío)
        val stepsWithData = requestedSteps.filter { step ->
            when (step) {
                "say" -> hasSay
                "comida" -> hasComida || forceComidaStep
                "secuencia" -> hasSecuencia
                else -> false
            }
        }

        // Si después de filtrar no queda nada, añadir los que tienen datos en orden por defecto
        if (stepsWithData.isEmpty()) {
            val fallbackSteps = mutableListOf<String>()
            if (hasSay) fallbackSteps.add("say")
            if (hasComida || forceComidaStep) fallbackSteps.add("comida")
            if (hasSecuencia) fallbackSteps.add("secuencia")
            Log.d(TAG, "orden_action '$raw' no coincide con datos, usando fallback: $fallbackSteps")
            return if (fallbackSteps.isEmpty()) listOf("secuencia") else fallbackSteps
        }

        // Añadir comandos que tienen datos pero NO están en orden_action (al final)
        val missingSteps = mutableListOf<String>()
        if (hasSay && "say" !in stepsWithData) missingSteps.add("say")
        if ((hasComida || forceComidaStep) && "comida" !in stepsWithData) missingSteps.add("comida")
        if (hasSecuencia && "secuencia" !in stepsWithData) missingSteps.add("secuencia")

        val finalSteps = stepsWithData + missingSteps
        if (missingSteps.isNotEmpty()) {
            Log.d(TAG, "Añadiendo comandos faltantes al final: $missingSteps")
        }

        return finalSteps
    }

    /**
     * Limpia la cola de comandos pendientes.
     */
    fun clear() {
        Log.d(TAG, "Limpiando cola de comandos")
        commandQueue.clear()
    }

    /**
     * Retorna el número de comandos pendientes en la cola.
     */
    fun pendingCount(): Int = commandQueue.size

    /**
     * Verifica si hay comandos en proceso o pendientes.
     */
    fun isBusy(): Boolean = isProcessing.get() || commandQueue.isNotEmpty()
}
