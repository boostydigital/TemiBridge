package com.spatium.deamon.db.temi.core

/**
 * Testable state machine wrapper for [GuiaState] transitions.
 *
 * GuiaManager embeds one of these to enforce legal transition rules.
 * Having it as a standalone class keeps the transition logic unit-testable
 * without instantiating the full GuiaManager (which requires Android context).
 *
 * Intentionally avoids android.util.Log so it can run in JVM unit tests.
 */
class GuiaStateMachine {

    @Volatile
    private var state: GuiaState = GuiaState.Idle

    /** Returns the current state. Thread-safe via @Volatile. */
    fun currentState(): GuiaState = state

    /**
     * Transitions to [next] if the transition is legal; otherwise logs a warning
     * and leaves state unchanged. Use [tryTransitionTo] if you need the boolean result.
     */
    fun transitionTo(next: GuiaState) {
        val previous = state
        if (isLegalTransition(previous, next)) {
            state = next
        }
        // Illegal transitions are silently dropped to stay testable without Android Log
    }

    /**
     * Attempts to transition to [next].
     * Returns true if the transition was legal and applied; false if rejected.
     */
    fun tryTransitionTo(next: GuiaState): Boolean {
        val previous = state
        return if (isLegalTransition(previous, next)) {
            state = next
            true
        } else {
            false
        }
    }

    /** Resets to [GuiaState.Idle]. Used in cleanup paths. */
    fun reset() {
        state = GuiaState.Idle
    }
}
