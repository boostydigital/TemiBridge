package com.spatium.deamon.db.temi.ui.whatsapp

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Resultados sellados para operaciones de WhatsApp.
 * Proporciona type-safety y exhaustividad en el manejo de resultados.
 */
sealed class WhatsAppSendResult {
    data class Success(
        val strategyUsed: WhatsAppConstants.SearchStrategyPriority,
        val nodeName: String,
        val timeElapsedMs: Long,
        val retryCount: Int,
    ) : WhatsAppSendResult()

    data class RetryableFailure(
        val reason: String,
        val attemptNumber: Int,
        val maxRetries: Int,
        val nextRetryInMs: Long,
    ) : WhatsAppSendResult()

    data class FatalFailure(
        val reason: String,
        val errorDetails: String,
        val possibleSolutions: List<String>,
    ) : WhatsAppSendResult()

    data class Timeout(
        val stage: WhatsAppConstants.SendState,
        val timeElapsedMs: Long,
        val lastKnownState: String,
    ) : WhatsAppSendResult()

    object MaxRetriesExceeded : WhatsAppSendResult() {
        val message = "Se excedió el número máximo de reintentos"
        val possibleSolutions = listOf(
            "Verificar que WhatsApp esté actualizado",
            "Revisar permisos de accesibilidad",
            "Verificar conexión a internet",
            "Reiniciar el servicio de accesibilidad",
        )
    }
}

/**
 * Información detallada de un nodo encontrado durante la búsqueda.
 */
data class FoundNode(
    val node: AccessibilityNodeInfo,
    val strategy: WhatsAppConstants.SearchStrategyPriority,
    val confidence: Float,
    val metadata: NodeMetadata,
)

data class NodeMetadata(
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: String,
    val depth: Int,
    val isClickable: Boolean,
    val isEnabled: Boolean,
    val isVisibleToUser: Boolean,
) {
    companion object {
        fun fromNodeInfo(node: AccessibilityNodeInfo, depth: Int): NodeMetadata {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)

            return NodeMetadata(
                viewId = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                className = node.className?.toString(),
                bounds = "Rect(${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom})",
                depth = depth,
                isClickable = node.isClickable,
                isEnabled = node.isEnabled,
                isVisibleToUser = node.isVisibleToUser,
            )
        }
    }
}

/**
 * Estrategias de click con sus resultados.
 */
sealed class ClickResult {
    data class Success(val method: ClickMethod) : ClickResult()
    data class Failure(val reason: String, val method: ClickMethod) : ClickResult()
    data class Retryable(val reason: String, val nextMethod: ClickMethod) : ClickResult()

    enum class ClickMethod {
        STANDARD_ACTION_CLICK,
        GESTURE_DISPATCH,
        FOCUS_THEN_CLICK,
        COORDINATE_TAP,
    }
}
