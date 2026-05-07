package com.spatium.deamon.db.temi.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide singleton that serializes exclusive operating modes.
 *
 * At most ONE mode may hold the lock at any time.
 * GuiaManager, AnnouncementManager, and RatingManager all consult this
 * before starting their respective sessions.
 *
 * Design rationale:
 * - [AtomicReference] for non-suspending [currentMode] reads (no coroutine context needed).
 * - [Mutex] for the test-and-set in [tryAcquire] to prevent races between concurrent callers.
 * - No queueing: denied callers retry on the next polling cycle (≤ 30 s). Simpler than fairness.
 */
object ExclusiveModeArbiter {

    const val MODE_GUIA = "GUIA"
    const val MODE_ANNOUNCEMENT = "ANNOUNCEMENT"
    const val MODE_RATING = "RATING"

    private val mutex = Mutex()
    private val currentOwner = AtomicReference<String?>(null)

    /**
     * Tries to acquire the lock for [mode].
     * Returns true if:
     *   - No mode currently holds the lock (acquires it), OR
     *   - [mode] already holds the lock (idempotent re-acquire).
     * Returns false if a different mode holds the lock.
     */
    suspend fun tryAcquire(mode: String): Boolean = mutex.withLock {
        val current = currentOwner.get()
        when {
            current == null -> {
                currentOwner.set(mode)
                true
            }
            current == mode -> true // idempotent re-acquire
            else -> false
        }
    }

    /**
     * Releases the lock if [mode] currently holds it.
     * If [mode] does NOT hold the lock, the call is silently ignored (no state change).
     */
    fun release(mode: String) {
        currentOwner.compareAndSet(mode, null)
        // Mismatched release is a no-op (compareAndSet returns false but we ignore it silently)
    }

    /**
     * Returns the name of the mode currently holding the lock, or null if idle.
     * Non-suspending — safe to call from any context.
     */
    fun currentMode(): String? = currentOwner.get()
}
