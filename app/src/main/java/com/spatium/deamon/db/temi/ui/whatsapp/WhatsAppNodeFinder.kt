package com.spatium.deamon.db.temi.ui.whatsapp

import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Motor de búsqueda de nodos con múltiples estrategias y fallback.
 * Implementa búsqueda recursiva optimizada y scoring de confianza.
 */
class WhatsAppNodeFinder {

    companion object {
        private const val TAG = "WhatsAppNodeFinder"
    }

    private val screenHeight = Resources.getSystem().displayMetrics.heightPixels
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels

    /**
     * Busca el botón de enviar usando todas las estrategias disponibles en orden de prioridad.
     */
    fun findSendButton(rootNode: AccessibilityNodeInfo): FoundNode? {
        val strategies = listOf(
            WhatsAppConstants.SearchStrategyPriority.VIEW_ID,
            WhatsAppConstants.SearchStrategyPriority.CONTENT_DESCRIPTION,
            WhatsAppConstants.SearchStrategyPriority.TEXT_MATCH,
            WhatsAppConstants.SearchStrategyPriority.CLASS_NAME_WITH_POSITION,
            WhatsAppConstants.SearchStrategyPriority.VISUAL_HEURISTIC
        )

        for (strategy in strategies) {
            WhatsAppAccessibilityLogger.logSearchStart(strategy)

            val result = when (strategy) {
                WhatsAppConstants.SearchStrategyPriority.VIEW_ID -> findByViewId(rootNode)
                WhatsAppConstants.SearchStrategyPriority.CONTENT_DESCRIPTION -> findByContentDescription(rootNode)
                WhatsAppConstants.SearchStrategyPriority.TEXT_MATCH -> findByTextMatching(rootNode)
                WhatsAppConstants.SearchStrategyPriority.CLASS_NAME_WITH_POSITION -> findByClassNameAndPosition(rootNode)
                WhatsAppConstants.SearchStrategyPriority.VISUAL_HEURISTIC -> findByVisualHeuristic(rootNode)
            }

            if (result != null) {
                WhatsAppAccessibilityLogger.logNodeFound(result)
                return result
            } else {
                WhatsAppAccessibilityLogger.logNodeNotFound(strategy)
            }
        }

        return null
    }

    /**
     * Estrategia 1: Búsqueda por ID de vista (más confiable).
     */
    private fun findByViewId(root: AccessibilityNodeInfo): FoundNode? {
        for (id in WhatsAppConstants.SEND_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (isValidSendButton(node)) {
                    return FoundNode(
                        node = node,
                        strategy = WhatsAppConstants.SearchStrategyPriority.VIEW_ID,
                        confidence = 0.95f,
                        metadata = NodeMetadata.fromNodeInfo(node, 0)
                    )
                }
            }
        }
        return null
    }

    /**
     * Estrategia 2: Búsqueda por contentDescription.
     */
    private fun findByContentDescription(root: AccessibilityNodeInfo): FoundNode? {
        val result = searchRecursive(root, maxDepth = WhatsAppConstants.MAX_SEARCH_DEPTH) { node ->
            val desc = node.contentDescription?.toString() ?: return@searchRecursive false
            WhatsAppConstants.SEND_BUTTON_CONTENT_DESCRIPTIONS.any {
                desc.contains(it, ignoreCase = true)
            } && isValidSendButton(node)
        }

        return result?.let { node ->
            FoundNode(
                node = node,
                strategy = WhatsAppConstants.SearchStrategyPriority.CONTENT_DESCRIPTION,
                confidence = 0.85f,
                metadata = NodeMetadata.fromNodeInfo(node, 0)
            )
        }
    }

    /**
     * Estrategia 3: Búsqueda por texto.
     */
    private fun findByTextMatching(root: AccessibilityNodeInfo): FoundNode? {
        val result = searchRecursive(root, maxDepth = WhatsAppConstants.MAX_SEARCH_DEPTH) { node ->
            val text = node.text?.toString() ?: return@searchRecursive false
            WhatsAppConstants.SEND_BUTTON_TEXTS.any {
                text.contains(it, ignoreCase = true)
            } && isValidSendButton(node)
        }

        return result?.let { node ->
            FoundNode(
                node = node,
                strategy = WhatsAppConstants.SearchStrategyPriority.TEXT_MATCH,
                confidence = 0.80f,
                metadata = NodeMetadata.fromNodeInfo(node, 0)
            )
        }
    }

    /**
     * Estrategia 4: Búsqueda por className y posición (botón en esquina inferior derecha).
     */
    private fun findByClassNameAndPosition(root: AccessibilityNodeInfo): FoundNode? {
        val result = searchRecursive(root, maxDepth = WhatsAppConstants.MAX_SEARCH_DEPTH) { node ->
            val className = node.className?.toString() ?: return@searchRecursive false

            // Verificar className válido
            val validClass = WhatsAppConstants.BUTTON_CLASS_NAMES.any { className.contains(it) }
            if (!validClass) return@searchRecursive false

            // Verificar posición (esquina inferior derecha)
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val isInBottomRight = bounds.bottom > screenHeight * 0.7f &&
                                  bounds.right > screenWidth * 0.7f

            isValidSendButton(node) && isInBottomRight
        }

        return result?.let { node ->
            FoundNode(
                node = node,
                strategy = WhatsAppConstants.SearchStrategyPriority.CLASS_NAME_WITH_POSITION,
                confidence = 0.65f,
                metadata = NodeMetadata.fromNodeInfo(node, 0)
            )
        }
    }

    /**
     * Estrategia 5: Heurística visual (último recurso).
     */
    private fun findByVisualHeuristic(root: AccessibilityNodeInfo): FoundNode? {
        val result = searchRecursive(root, maxDepth = WhatsAppConstants.MAX_SEARCH_DEPTH) { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val area = bounds.width() * bounds.height()
            val isInBottomRight = bounds.bottom > screenHeight * 0.6f &&
                                  bounds.right > screenWidth * 0.6f

            // Calcular confianza basada en múltiples factores
            val validArea = area >= WhatsAppConstants.MIN_CLICKABLE_AREA
            val validPosition = isInBottomRight
            val clickable = node.isClickable

            // Calcular score de confianza
            var confidence = 0.0f
            if (validArea) confidence += 0.3f
            if (validPosition) confidence += 0.4f
            if (clickable) confidence += 0.3f

            confidence >= 0.5f && clickable
        }

        return result?.let { node ->
            FoundNode(
                node = node,
                strategy = WhatsAppConstants.SearchStrategyPriority.VISUAL_HEURISTIC,
                confidence = 0.50f,
                metadata = NodeMetadata.fromNodeInfo(node, 0)
            )
        }
    }

    /**
     * Búsqueda recursiva optimizada en la jerarquía de nodos.
     */
    private fun searchRecursive(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        maxDepth: Int = WhatsAppConstants.MAX_SEARCH_DEPTH,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (depth > maxDepth) return null

        // Verificar nodo actual
        if (predicate(node)) {
            return node
        }

        // Buscar en hijos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = searchRecursive(child, depth + 1, maxDepth, predicate)
            if (result != null) {
                return result
            }
            child.recycle()
        }

        return null
    }

    /**
     * Valida si un nodo es un botón de enviar válido.
     */
    private fun isValidSendButton(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable) return false
        if (!node.isEnabled) return false
        if (!node.isVisibleToUser) return false

        // Verificar tamaño mínimo
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val area = bounds.width() * bounds.height()

        return area >= WhatsAppConstants.MIN_CLICKABLE_AREA
    }
}
