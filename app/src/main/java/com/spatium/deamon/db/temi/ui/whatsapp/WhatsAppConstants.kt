package com.spatium.deamon.db.temi.ui.whatsapp

/**
 * Constantes centralizadas para la integración con WhatsApp.
 * Actualizadas para 2025 con IDs de WhatsApp y WhatsApp Business.
 */
object WhatsAppConstants {

    // Paquetes
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    // IDs de recursos para botón de envío (2025)
    val SEND_BUTTON_IDS = listOf(
        "com.whatsapp:id/send",                    // ID principal
        "com.whatsapp:id/send_button",             // Alternativo común
        "com.whatsapp:id/action_button",           // Botón de acción genérico
        "com.whatsapp:id/confirmation_send",       // Pantalla de confirmación
        "com.whatsapp.w4b:id/send",                // WhatsApp Business
        "com.whatsapp.w4b:id/send_button",         // WhatsApp Business alt
        "com.whatsapp.w4b:id/action_button"        // WhatsApp Business action
    )

    // Textos para búsqueda (multilenguaje)
    val SEND_BUTTON_TEXTS = listOf(
        "Enviar",           // Español
        "Send",             // Inglés
        "Enviar mensaje",   // Español completo
        "Send message",     // Inglés completo
        "➤",                // Símbolo de enviar
        "▶",                // Símbolo alternativo
        "✓"                 // Check mark
    )

    // Content descriptions
    val SEND_BUTTON_CONTENT_DESCRIPTIONS = listOf(
        "Send",
        "Enviar",
        "Send message",
        "Enviar mensaje"
    )

    // ClassNames de botones
    val BUTTON_CLASS_NAMES = listOf(
        "android.widget.Button",
        "android.widget.ImageButton",
        "android.widget.TextView",  // Algunos botones son TextViews
        "android.support.v7.widget.AppCompatImageButton",
        "androidx.appcompat.widget.AppCompatImageButton"
    )

    // Configuración de timing
    const val EVENT_DEBOUNCE_MS = 300L              // Reducido de 500ms
    const val INITIAL_SEARCH_DELAY_MS = 500L        // Delay inicial después de abrir WhatsApp
    const val RETRY_DELAY_MS = 200L                 // Delay entre reintentos
    const val MAX_SEARCH_RETRIES = 5                // Máximo de reintentos
    const val MAX_TOTAL_ATTEMPTS_MS = 5000L         // Timeout total de búsqueda (5 seg)

    // Configuración de búsqueda
    const val MAX_SEARCH_DEPTH = 15                 // Profundidad máxima de recursión
    const val MIN_CLICKABLE_AREA = 10000            // Área mínima en pixeles (100x100)

    // Prioridades de estrategia
    enum class SearchStrategyPriority(val priority: Int) {
        VIEW_ID(100),              // Prioridad más alta
        CONTENT_DESCRIPTION(90),
        TEXT_MATCH(80),
        CLASS_NAME_WITH_POSITION(70),
        VISUAL_HEURISTIC(60)       // Último recurso
    }

    // Estados detallados
    enum class SendState {
        IDLE,
        WAITING_FOR_WHATSAPP_OPEN,
        WAITING_FOR_SEND_BUTTON,
        SEND_BUTTON_FOUND,
        ATTEMPTING_CLICK,
        CLICK_SUCCESSFUL,
        CLICK_FAILED_RETRYING,
        ERROR_MAX_RETRIES,
        ERROR_TIMEOUT,
        ERROR_NO_SEND_BUTTON,
        COMPLETED
    }

    // Resultados de búsqueda
    data class SearchResult(
        val nodeInfo: NodeInfo,
        val strategy: SearchStrategyPriority,
        val confidence: Float  // 0.0 a 1.0
    )

    data class NodeInfo(
        val viewId: String?,
        val text: CharSequence?,
        val contentDescription: CharSequence?,
        val className: String?,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val isVisibleToUser: Boolean,
        val bounds: android.graphics.Rect,
        val depth: Int
    )
}
