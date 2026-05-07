package com.spatium.deamon.db.temi.ui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Servicio de accesibilidad mejorado para automatizar el envío de fotos por WhatsApp.
 * Basado en el enfoque de 24x7AiWhatsappAgent con GestureDescription y múltiples estrategias.
 */
class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhatsAppA11y"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        // IDs actualizados del botón de enviar (basados en 24x7AiWhatsappAgent)
        private val SEND_BUTTON_IDS = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp:id/send_button",
            "com.whatsapp:id/fab_send",
            "com.whatsapp:id/confirmation_send",
            "com.whatsapp.w4b:id/send",
            "com.whatsapp.w4b:id/send_button",
            "com.whatsapp.w4b:id/fab_send",
        )

        // Textos del botón en múltiples idiomas
        private val SEND_BUTTON_TEXTS = listOf(
            "Enviar", "Send", "ENVIAR", "SEND",
            "➤", "▶", "✓", "send", "enviar",
        )

        // Configuración de timing
        private const val INITIAL_DELAY_MS = 3000L // Esperar 3s después de abrir WhatsApp (WhatsApp tarda en cargar)
        private const val RETRY_DELAY_MS = 600L // 600ms entre reintentos
        private const val MAX_RETRIES = 12 // Más reintentos
        private const val GESTURE_DURATION_MS = 150L // Duración del gesto de click
        private const val CLICK_DELAY_MS = 200L // Delay entre intentos de click
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var currentRetryCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "✓ Servicio de accesibilidad conectado")
        Log.d(TAG, "✓ Paquetes monitoreados: $WHATSAPP_PACKAGE, $WHATSAPP_BUSINESS_PACKAGE")
        Log.d(TAG, "✓ Esperando eventos de accesibilidad...")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Configurar el servicio programáticamente como fallback
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                packageNames = arrayOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)
                notificationTimeout = 100
            }
            setServiceInfo(info)
            Log.d(TAG, "✓ Configuración del servicio aplicada programáticamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando servicio: ${e.message}", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString()
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) return

        // Solo procesar si estamos en modo de envío
        if (!SharedData.isSending()) {
            return
        }

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "Evento recibido: ${getEventTypeName(event.eventType)}")
        Log.d(TAG, "Estado actual: ${SharedData.sendState}")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        when (SharedData.sendState) {
            SharedData.SendState.WAITING_FOR_CHAT_OPEN -> {
                Log.d(TAG, "⏳ WhatsApp detectado, esperando ${INITIAL_DELAY_MS}ms...")
                mainHandler.postDelayed({
                    if (SharedData.isSending()) {
                        SharedData.setState(SharedData.SendState.WAITING_FOR_SEND_BUTTON)
                        currentRetryCount = 0
                        startSearchLoop()
                    }
                }, INITIAL_DELAY_MS)
            }
            SharedData.SendState.WAITING_FOR_SEND_BUTTON,
            SharedData.SendState.CLICK_FAILED_RETRYING,
            -> {
                // Si la búsqueda aún no se ha iniciado, iniciarla
                if (searchRunnable == null) {
                    Log.d(TAG, "🔁 Iniciando búsqueda de botón (evento recibido)...")
                    currentRetryCount = 0
                    startSearchLoop()
                }
                // La búsqueda ya está en progreso
            }
            else -> {
                Log.d(TAG, "ℹ️ Estado: ${SharedData.sendState}")
            }
        }
    }

    private fun startSearchLoop() {
        // Cancelar búsqueda anterior si existe
        searchRunnable?.let { mainHandler.removeCallbacks(it) }

        searchRunnable = object : Runnable {
            override fun run() {
                if (!SharedData.isSending()) {
                    Log.d(TAG, "⚠️ Búsqueda cancelada: ya no se está enviando")
                    return
                }

                when (SharedData.sendState) {
                    SharedData.SendState.WAITING_FOR_SEND_BUTTON,
                    SharedData.SendState.CLICK_FAILED_RETRYING,
                    -> {
                        if (currentRetryCount >= MAX_RETRIES) {
                            Log.e(TAG, "❌ Máximo de reintentos alcanzado: $MAX_RETRIES")
                            SharedData.setError("No se encontró el botón después de $MAX_RETRIES intentos", SharedData.SendState.ERROR_MAX_RETRIES)
                            return
                        }

                        currentRetryCount++
                        Log.d(TAG, "🔍 Buscando botón... (intento $currentRetryCount/$MAX_RETRIES)")

                        if (tryFindAndClickSendButton()) {
                            Log.d(TAG, "✅ Click realizado exitosamente!")
                            SharedData.setCompleted()
                            searchRunnable = null
                        } else {
                            Log.d(TAG, "⚠️ Botón no encontrado, reintentando en ${RETRY_DELAY_MS}ms...")
                            SharedData.setState(SharedData.SendState.CLICK_FAILED_RETRYING)
                            mainHandler.postDelayed(this, RETRY_DELAY_MS)
                        }
                    }
                    else -> {
                        Log.d(TAG, "ℹ️ Estado cambió, deteniendo búsqueda")
                    }
                }
            }
        }

        mainHandler.postDelayed(searchRunnable!!, 100)
    }

    private fun tryFindAndClickSendButton(): Boolean {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "❌ No se pudo obtener rootInActiveWindow")
            return false
        }

        try {
            // Dump de la jerarquía para debugging (solo primeros 2 intentos)
            if (currentRetryCount <= 2) {
                dumpNodeHierarchy(rootNode, maxDepth = 12)
            }

            // Estrategia 1: Búsqueda por ID (más confiable)
            Log.d(TAG, "📌 Estrategia 1: Buscando por ID...")
            for (id in SEND_BUTTON_IDS) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                for (node in nodes) {
                    if (isValidSendButton(node)) {
                        Log.d(TAG, "✅ Botón encontrado por ID: $id")
                        return performClickOnNode(node, "ID:$id")
                    }
                }
            }

            // Estrategia 2: Búsqueda por texto
            Log.d(TAG, "📝 Estrategia 2: Buscando por texto...")
            val textNode = searchByTextNode(rootNode, SEND_BUTTON_TEXTS)
            if (textNode != null) {
                Log.d(TAG, "✅ Botón encontrado por texto: ${textNode.text}")
                return performClickOnNode(textNode, "TEXT:${textNode.text}")
            }

            // Estrategia 3: Búsqueda por ContentDescription
            Log.d(TAG, "📝 Estrategia 3: Buscando por contentDescription...")
            val descNode = searchByContentDescription(rootNode, SEND_BUTTON_TEXTS)
            if (descNode != null) {
                Log.d(TAG, "✅ Botón encontrado por contentDescription: ${descNode.contentDescription}")
                return performClickOnNode(descNode, "DESC:${descNode.contentDescription}")
            }

            // Estrategia 4: Búsqueda por posición (esquina inferior derecha)
            Log.d(TAG, "📍 Estrategia 4: Buscando por posición...")
            val positionNode = searchByPosition(rootNode)
            if (positionNode != null) {
                Log.d(TAG, "✅ Botón encontrado por posición")
                return performClickOnNode(positionNode, "POSITION")
            }

            Log.w(TAG, "❌ No se encontró el botón con ninguna estrategia")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en tryFindAndClickSendButton: ${e.message}", e)
            return false
        } finally {
            rootNode.recycle()
        }
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo, source: String): Boolean {
        Log.d(TAG, "🖱️ Intentando click en nodo encontrado por: $source")

        // Logging detallado de propiedades del nodo
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        Log.d(TAG, "📋 Propiedades del nodo:")
        Log.d(TAG, "   - Clickeable: ${node.isClickable}")
        Log.d(TAG, "   - Habilitado: ${node.isEnabled}")
        Log.d(TAG, "   - Visible: ${node.isVisibleToUser}")
        Log.d(TAG, "   - Clase: ${node.className}")
        Log.d(TAG, "   - Texto: ${node.text}")
        Log.d(TAG, "   - ContentDesc: ${node.contentDescription}")
        Log.d(TAG, "   - Bounds: [${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom}]")
        Log.d(TAG, "   - Tamaño: ${bounds.width()}x${bounds.height()}")

        // Método 1: ACTION_CLICK estándar
        Log.d(TAG, "🔵 Método 1: ACTION_CLICK estándar...")
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "✅ ACTION_CLICK exitoso")
            return true
        }
        Log.d(TAG, "❌ ACTION_CLICK falló")

        // Método 2: GestureDescription (más confiable en Android moderno)
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        Log.d(TAG, "� Método 2: GestureDescription en ($centerX, $centerY)...")

        if (performGestureClick(centerX, centerY)) {
            Log.d(TAG, "✅ GestureClick exitoso")
            return true
        }
        Log.d(TAG, "❌ GestureClick falló")

        // Método 3: Focus + Click
        Log.d(TAG, "🔵 Método 3: FOCUS + CLICK...")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        Thread.sleep(CLICK_DELAY_MS)
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "✅ FOCUS+CLICK exitoso")
            return true
        }
        Log.d(TAG, "❌ FOCUS+CLICK falló")

        return false
    }

    private fun performGestureClick(x: Float, y: Float): Boolean {
        return try {
            Log.d(TAG, "🎯 Iniciando GestureClick en ($x, $y)")
            val path = Path().apply {
                moveTo(x, y)
            }

            val stroke = GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION_MS)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            var gestureCompleted = false
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    gestureCompleted = true
                    Log.d(TAG, "✅ Gesture completado exitosamente")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "⚠️ Gesture fue cancelado por el sistema")
                }
            }

            val result = dispatchGesture(gesture, callback, null)
            Log.d(TAG, "🎯 dispatchGesture retornó: $result")

            // Esperar a que el callback se ejecute
            Thread.sleep(GESTURE_DURATION_MS + 100)

            if (gestureCompleted) {
                Log.d(TAG, "✅ Gesture se completó correctamente")
                return true
            } else {
                Log.d(TAG, "⚠️ Gesture no se completó dentro del timeout")
                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en performGestureClick: ${e.message}", e)
            false
        }
    }

    private fun searchByTextNode(
        node: AccessibilityNodeInfo,
        texts: List<String>,
        maxDepth: Int = 15,
        currentDepth: Int = 0,
    ): AccessibilityNodeInfo? {
        if (currentDepth > maxDepth) return null

        val nodeText = node.text?.toString() ?: ""
        if (texts.any { nodeText.equals(it, ignoreCase = true) } && isValidSendButton(node)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = searchByTextNode(child, texts, maxDepth, currentDepth + 1)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }

        return null
    }

    private fun searchByContentDescription(
        node: AccessibilityNodeInfo,
        texts: List<String>,
        maxDepth: Int = 15,
        currentDepth: Int = 0,
    ): AccessibilityNodeInfo? {
        if (currentDepth > maxDepth) return null

        val nodeDesc = node.contentDescription?.toString() ?: ""
        if (texts.any { nodeDesc.equals(it, ignoreCase = true) } && isValidSendButton(node)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = searchByContentDescription(child, texts, maxDepth, currentDepth + 1)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }

        return null
    }

    private fun searchByPosition(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels

        return searchRecursive(rootNode, maxDepth = 15) { node ->
            if (!isValidSendButton(node)) return@searchRecursive false

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // Buscar en la esquina inferior derecha (donde suele estar el botón de enviar)
            val isInBottomRight = bounds.bottom > screenHeight * 0.75f &&
                bounds.right > screenWidth * 0.6f

            // Tamaño mínimo razonable para un botón
            val hasValidSize = bounds.width() > 50 && bounds.height() > 50

            isInBottomRight && hasValidSize
        }
    }

    private fun searchRecursive(
        node: AccessibilityNodeInfo,
        maxDepth: Int = 15,
        currentDepth: Int = 0,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (currentDepth > maxDepth) return null

        if (predicate(node)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = searchRecursive(child, maxDepth, currentDepth + 1, predicate)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }

        return null
    }

    private fun isValidSendButton(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val hasValidSize = bounds.width() > 30 && bounds.height() > 30
        val isClickable = node.isClickable || node.isEnabled
        val isVisible = node.isVisibleToUser

        val result = hasValidSize && isClickable && isVisible
        if (!result) {
            Log.v(TAG, "⚠️ Nodo inválido: size=$hasValidSize, clickable=$isClickable, visible=$isVisible")
        }

        return result
    }

    private fun dumpNodeHierarchy(
        node: AccessibilityNodeInfo,
        maxDepth: Int = 15,
        currentDepth: Int = 0,
        prefix: String = "",
    ) {
        if (currentDepth > maxDepth) return

        val indent = "│  ".repeat(currentDepth)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val className = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val viewId = node.viewIdResourceName?.substringAfterLast('/') ?: "null"
        val text = node.text?.toString()?.take(20) ?: "null"
        val desc = node.contentDescription?.toString()?.take(20) ?: "null"
        val clickable = if (node.isClickable) "✓" else "✗"

        Log.d(TAG, "$indent├─ [$className] id=$viewId text=$text desc=$desc click=$clickable bounds=[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNodeHierarchy(child, maxDepth, currentDepth + 1, prefix)
            child.recycle()
        }
    }

    private fun getEventTypeName(eventType: Int): String = when (eventType) {
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
        else -> "UNKNOWN($eventType)"
    }

    override fun onInterrupt() {
        Log.d(TAG, "⚠️ Servicio interrumpido")
        searchRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "❌ Servicio destruido")
        searchRunnable?.let { mainHandler.removeCallbacks(it) }
    }
}
