package com.spatium.deamon.db.temi.ui.whatsapp

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logger estructurado para debugging de AccessibilityService.
 * Proporciona logs detallados con contexto, timestamps y formato consistente.
 */
object WhatsAppAccessibilityLogger {

    private const val TAG = "WhatsAppA11y"
    private const val ENABLE_VERBOSE_LOGGING = true  // Desactivar en producción si es necesario
    private const val ENABLE_NODE_DUMPING = true      // Para debugging avanzado

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    // Contadores para métricas
    private var searchCount = 0
    private var clickAttempts = 0
    private var successfulClicks = 0
    private var failedClicks = 0

    /**
     * Log de información general con timestamp.
     */
    fun info(message: String) {
        Log.i(TAG, "[${getTimestamp()}] $message")
    }

    /**
     * Log de debug detallado.
     */
    fun debug(message: String) {
        if (ENABLE_VERBOSE_LOGGING) {
            Log.d(TAG, "[${getTimestamp()}] [DEBUG] $message")
        }
    }

    /**
     * Log de advertencia.
     */
    fun warning(message: String) {
        Log.w(TAG, "[${getTimestamp()}] [WARNING] $message")
    }

    /**
     * Log de error con excepción opcional.
     */
    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "[${getTimestamp()}] [ERROR] $message", throwable)
        } else {
            Log.e(TAG, "[${getTimestamp()}] [ERROR] $message")
        }
    }

    /**
     * Log cuando se inicia una búsqueda.
     */
    fun logSearchStart(strategy: WhatsAppConstants.SearchStrategyPriority) {
        searchCount++
        debug("Iniciando búsqueda #$searchCount con estrategia: $strategy")
    }

    /**
     * Log cuando se encuentra un nodo candidato.
     */
    fun logNodeFound(node: FoundNode) {
        info("✓ Nodo encontrado: ${node.metadata.className} " +
             "(estrategia: ${node.strategy}, confianza: ${(node.confidence * 100).toInt()}%)")
        debug("  - ID: ${node.metadata.viewId}")
        debug("  - Texto: ${node.metadata.text}")
        debug("  - ContentDescription: ${node.metadata.contentDescription}")
        debug("  - Bounds: ${node.metadata.bounds}")
        debug("  - Clickable: ${node.metadata.isClickable}, Enabled: ${node.metadata.isEnabled}, Visible: ${node.metadata.isVisibleToUser}")
    }

    /**
     * Log cuando NO se encuentra ningún nodo.
     */
    fun logNodeNotFound(strategy: WhatsAppConstants.SearchStrategyPriority) {
        debug("✗ No se encontró nodo con estrategia: $strategy")
    }

    /**
     * Log de intento de click.
     */
    fun logClickAttempt(method: ClickResult.ClickMethod) {
        clickAttempts++
        info("Intentando click #$clickAttempts con método: $method")
    }

    /**
     * Log de click exitoso.
     */
    fun logClickSuccess(method: ClickResult.ClickMethod) {
        successfulClicks++
        info("✓ Click exitoso #$successfulClicks con método: $method")
    }

    /**
     * Log de click fallido.
     */
    fun logClickFailure(method: ClickResult.ClickMethod, reason: String) {
        failedClicks++
        error("✗ Click fallido con método: $method. Razón: $reason")
    }

    /**
     * Log de cambio de estado.
     */
    fun logStateChange(oldState: WhatsAppConstants.SendState, newState: WhatsAppConstants.SendState) {
        info("Estado cambiado: $oldState → $newState")
    }

    /**
     * Log de error recuperable.
     */
    fun logRetryableError(error: String, attempt: Int, maxRetries: Int, retryInMs: Long) {
        warning("Error recuperable (intento $attempt/$maxRetries): $error. Reintentando en ${retryInMs}ms")
    }

    /**
     * Dump completo de la jerarquía de nodos para debugging avanzado.
     */
    fun dumpNodeHierarchy(rootNode: AccessibilityNodeInfo, maxDepth: Int = 10) {
        if (!ENABLE_NODE_DUMPING) return

        debug("=== INICIO DUMP DE JERARQUÍA ===")
        dumpNodeRecursive(rootNode, 0, maxDepth)
        debug("=== FIN DUMP DE JERARQUÍA ===")
    }

    private fun dumpNodeRecursive(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return

        val indent = "  ".repeat(depth)
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        debug("$indent├─ [Depth $depth] " +
              "ID: ${node.viewIdResourceName ?: "null"}, " +
              "Class: ${node.className?.takeLast(20) ?: "null"}, " +
              "Text: ${node.text?.take(20) ?: "null"}, " +
              "Clickable: ${node.isClickable}, " +
              "Bounds: [$bounds]")

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                dumpNodeRecursive(child, depth + 1, maxDepth)
                child.recycle()
            }
        }
    }

    /**
     * Log de métricas acumuladas.
     */
    fun logMetrics() {
        info("=== MÉTRICAS ===")
        info("Búsquedas: $searchCount")
        info("Intentos de click: $clickAttempts")
        info("Clicks exitosos: $successfulClicks")
        info("Clicks fallidos: $failedClicks")
        info("Tasa de éxito: ${if (clickAttempts > 0) (successfulClicks * 100 / clickAttempts) else 0}%")
    }

    /**
     * Reset de contadores (llamar al inicio de una nueva operación).
     */
    fun resetMetrics() {
        searchCount = 0
        clickAttempts = 0
        successfulClicks = 0
        failedClicks = 0
        debug("Métricas reseteadas")
    }

    private fun getTimestamp(): String {
        return dateFormat.format(Date())
    }

    /**
     * Log de información de pantalla para debugging.
     */
    fun logScreenInfo(packageName: String, eventType: Int, eventClassName: CharSequence?) {
        debug("=== INFO DE PANTALLA ===")
        debug("Package: $packageName")
        debug("Event Type: $eventType")
        debug("Event Class: $eventClassName")
    }
}
