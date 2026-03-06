package com.spatium.deamon.db.temi.ui

import android.net.Uri
import java.util.concurrent.atomic.AtomicInteger

/**
 * Estado compartido entre PhotoPreviewActivity y WhatsAppAccessibilityService.
 */
object SharedData {
    enum class SendState {
        IDLE,
        WAITING_FOR_CHAT_OPEN,      // Esperando a que se abra el chat directo
        WAITING_FOR_SEND_BUTTON,    // Esperando botón de enviar
        SEND_BUTTON_FOUND,
        ATTEMPTING_CLICK,
        CLICK_SUCCESSFUL,
        CLICK_FAILED_RETRYING,
        COMPLETED,
        ERROR_MAX_RETRIES,
        ERROR_TIMEOUT,
        ERROR_NO_SEND_BUTTON,
        ERROR_GENERAL
    }

    private const val MAX_RETRIES = 5
    private const val MAX_TIMEOUT_MS = 10000L

    var photoPath: String? = null
    var photoUri: Uri? = null
    var contactName: String? = null  // Nombre del contacto a buscar en WhatsApp
    var sendState: SendState = SendState.IDLE
    var errorMessage: String? = null
    var shouldAutoSend: Boolean = false

    private val retryCount = AtomicInteger(0)
    private var startTimeMs: Long = 0
    private var lastActivityTimeMs: Long = 0

    fun reset() {
        photoPath = null
        photoUri = null
        contactName = null
        sendState = SendState.IDLE
        errorMessage = null
        shouldAutoSend = false
        retryCount.set(0)
        startTimeMs = 0
        lastActivityTimeMs = 0
    }

    fun setSending(path: String, uri: Uri) {
        photoPath = path
        photoUri = uri
        contactName = null  // No necesitamos nombre de contacto
        sendState = SendState.WAITING_FOR_CHAT_OPEN
        shouldAutoSend = true
        errorMessage = null
        retryCount.set(0)
        startTimeMs = System.currentTimeMillis()
        lastActivityTimeMs = startTimeMs
    }

    fun setSendingWithContact(path: String, uri: Uri, contact: String) {
        photoPath = path
        photoUri = uri
        contactName = contact
        sendState = SendState.WAITING_FOR_CHAT_OPEN
        shouldAutoSend = true
        errorMessage = null
        retryCount.set(0)
        startTimeMs = System.currentTimeMillis()
        lastActivityTimeMs = startTimeMs
    }

    fun setState(newState: SendState) {
        val oldState = sendState
        sendState = newState
        lastActivityTimeMs = System.currentTimeMillis()
        android.util.Log.d("SharedData", "Estado: $oldState → $newState (intento ${retryCount.get()})")
    }

    fun setCompleted() {
        sendState = SendState.COMPLETED
        shouldAutoSend = false
        lastActivityTimeMs = System.currentTimeMillis()
    }

    fun setError(message: String, errorType: SendState = SendState.ERROR_GENERAL) {
        sendState = errorType
        errorMessage = message
        shouldAutoSend = false
        lastActivityTimeMs = System.currentTimeMillis()
    }

    fun isSending(): Boolean = shouldAutoSend && sendState != SendState.COMPLETED &&
                              sendState != SendState.ERROR_MAX_RETRIES &&
                              sendState != SendState.ERROR_TIMEOUT &&
                              sendState != SendState.ERROR_NO_SEND_BUTTON &&
                              sendState != SendState.ERROR_GENERAL

    fun incrementRetry(): Int = retryCount.incrementAndGet()

    fun getRetryCount(): Int = retryCount.get()

    fun getElapsedTimeMs(): Long = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0

    fun getTimeSinceLastActivityMs(): Long = if (lastActivityTimeMs > 0) System.currentTimeMillis() - lastActivityTimeMs else 0

    fun hasExceededMaxRetries(): Boolean = retryCount.get() >= MAX_RETRIES

    fun hasExceededTimeout(): Boolean = getElapsedTimeMs() > MAX_TIMEOUT_MS
}
