package com.spatium.deamon.db.temi.ui.whatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Ejecutor de clicks con múltiples estrategias de fallback.
 * Implementa diferentes métodos de click para máxima compatibilidad.
 */
class WhatsAppClickPerformer(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "WhatsAppClickPerformer"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Intenta hacer click en el nodo usando todas las estrategias disponibles.
     */
    fun performClick(node: AccessibilityNodeInfo): ClickResult {
        val methods = listOf(
            ClickResult.ClickMethod.STANDARD_ACTION_CLICK,
            ClickResult.ClickMethod.GESTURE_DISPATCH,
            ClickResult.ClickMethod.FOCUS_THEN_CLICK,
            ClickResult.ClickMethod.COORDINATE_TAP,
        )

        for (method in methods) {
            WhatsAppAccessibilityLogger.logClickAttempt(method)

            val result = when (method) {
                ClickResult.ClickMethod.STANDARD_ACTION_CLICK -> performStandardClick(node)
                ClickResult.ClickMethod.GESTURE_DISPATCH -> performGestureClick(node)
                ClickResult.ClickMethod.FOCUS_THEN_CLICK -> performFocusThenClick(node)
                ClickResult.ClickMethod.COORDINATE_TAP -> performCoordinateTap(node)
            }

            when (result) {
                is ClickResult.Success -> {
                    WhatsAppAccessibilityLogger.logClickSuccess(method)
                    return result
                }
                is ClickResult.Retryable -> {
                    WhatsAppAccessibilityLogger.logClickFailure(method, result.reason)
                    // Continuar con siguiente método
                }
                is ClickResult.Failure -> {
                    WhatsAppAccessibilityLogger.logClickFailure(method, result.reason)
                    // Continuar con siguiente método
                }
            }
        }

        return ClickResult.Failure("Todos los métodos de click fallaron", ClickResult.ClickMethod.COORDINATE_TAP)
    }

    /**
     * Método 1: ACTION_CLICK estándar (más confiable).
     */
    private fun performStandardClick(node: AccessibilityNodeInfo): ClickResult = try {
        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (success) {
            ClickResult.Success(ClickResult.ClickMethod.STANDARD_ACTION_CLICK)
        } else {
            ClickResult.Retryable("ACTION_CLICK retornó false", ClickResult.ClickMethod.GESTURE_DISPATCH)
        }
    } catch (e: Exception) {
        ClickResult.Retryable("Error en ACTION_CLICK: ${e.message}", ClickResult.ClickMethod.GESTURE_DISPATCH)
    }

    /**
     * Método 2: GestureDescription con Path.
     */
    private fun performGestureClick(node: AccessibilityNodeInfo): ClickResult = try {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val path = Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                WhatsAppAccessibilityLogger.debug("Gesture completado exitosamente")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                WhatsAppAccessibilityLogger.warning("Gesture fue cancelado")
            }
        }

        val success = service.dispatchGesture(gesture, callback, null)
        if (success) {
            ClickResult.Success(ClickResult.ClickMethod.GESTURE_DISPATCH)
        } else {
            ClickResult.Retryable("dispatchGesture retornó false", ClickResult.ClickMethod.FOCUS_THEN_CLICK)
        }
    } catch (e: Exception) {
        ClickResult.Retryable("Error en GestureDescription: ${e.message}", ClickResult.ClickMethod.FOCUS_THEN_CLICK)
    }

    /**
     * Método 3: ACTION_FOCUS + ACTION_CLICK.
     */
    private fun performFocusThenClick(node: AccessibilityNodeInfo): ClickResult {
        return try {
            val focusSuccess = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (!focusSuccess) {
                return ClickResult.Retryable("ACTION_FOCUS falló", ClickResult.ClickMethod.COORDINATE_TAP)
            }

            // Esperar un momento y luego hacer click
            var clickResult: ClickResult? = null
            mainHandler.postDelayed({
                val clickSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clickResult = if (clickSuccess) {
                    ClickResult.Success(ClickResult.ClickMethod.FOCUS_THEN_CLICK)
                } else {
                    ClickResult.Retryable("ACTION_CLICK falló después de focus", ClickResult.ClickMethod.COORDINATE_TAP)
                }
            }, 50)

            // Retornar resultado inmediato (focus funcionó, click es asíncrono)
            clickResult ?: ClickResult.Retryable("Click pendiente después de focus", ClickResult.ClickMethod.COORDINATE_TAP)
        } catch (e: Exception) {
            ClickResult.Retryable("Error en Focus+Click: ${e.message}", ClickResult.ClickMethod.COORDINATE_TAP)
        }
    }

    /**
     * Método 4: Click por coordenadas (último recurso).
     */
    private fun performCoordinateTap(node: AccessibilityNodeInfo): ClickResult = try {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                WhatsAppAccessibilityLogger.debug("Click por coordenadas completado")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                WhatsAppAccessibilityLogger.warning("Click por coordenadas cancelado")
            }
        }

        val success = service.dispatchGesture(gesture, callback, null)
        if (success) {
            ClickResult.Success(ClickResult.ClickMethod.COORDINATE_TAP)
        } else {
            ClickResult.Failure("dispatchGesture por coordenadas falló", ClickResult.ClickMethod.COORDINATE_TAP)
        }
    } catch (e: Exception) {
        ClickResult.Failure("Error en click por coordenadas: ${e.message}", ClickResult.ClickMethod.COORDINATE_TAP)
    }

    /**
     * Verifica si un nodo es visible en pantalla.
     */
    private fun isNodeVisible(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        return bounds.width() > 0 && bounds.height() > 0
    }

    /**
     * Obtiene las coordenadas del centro de un nodo.
     */
    private fun getNodeCenter(node: AccessibilityNodeInfo): Pair<Float, Float> {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return Pair(bounds.exactCenterX(), bounds.exactCenterY())
    }
}
