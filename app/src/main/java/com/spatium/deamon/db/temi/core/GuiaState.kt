package com.spatium.deamon.db.temi.core

/**
 * State machine for GuiaManager.
 *
 * Legal transitions:
 *   Idle        → Waiting    (on successful guia-pendiente claim + arbiter acquire)
 *   Waiting     → Guiding    (on INTENT_GUIA_USER_TAPPED_START broadcast)
 *   Waiting     → Finishing  (on expiration or external cancellation)
 *   Guiding     → Finishing  (on arrival at waypoint_final, expiration, or cancellation)
 *   Finishing   → Idle       (after cleanup completes)
 *
 * Any other transition is illegal and must be rejected (logged + ignored).
 */
sealed class GuiaState {

    /** No active guia session. Polling continues normally. */
    object Idle : GuiaState()

    /**
     * Guia acquired; robot is en route to or parked at [guia.waypointInicial].
     * Waiting for the user to tap the CTA button.
     * [originalState] holds pre-guia robot settings for restoration.
     */
    data class Waiting(
        val guia: GuiaPayload,
        val originalState: RobotStateSnapshot,
    ) : GuiaState()

    /**
     * User tapped the button; robot is navigating to [guia.waypointFinal].
     * Video playback is active in GuiaActivity.
     */
    data class Guiding(
        val guia: GuiaPayload,
        val originalState: RobotStateSnapshot,
    ) : GuiaState()

    /**
     * Terminal cleanup in progress.
     * [reason] is one of: "completada", "expirada", "cancelada".
     * Transitions to [Idle] after cleanup completes.
     */
    data class Finishing(
        val guia: GuiaPayload,
        val reason: String,
    ) : GuiaState()
}

/**
 * Validates whether a transition from [from] to [to] is legal.
 *
 * Encapsulates the transition rules so both [GuiaStateMachine] and tests can use it
 * without coupling to any mutable state.
 *
 * Guiding → Waiting covers the loop case: after arriving at waypoint_final (not yet
 * expired), the robot navigates back to waypoint_inicial and re-enters waiting mode
 * so a new visitor can start the tour.
 */
fun isLegalTransition(from: GuiaState, to: GuiaState): Boolean = when {
    from is GuiaState.Idle && to is GuiaState.Waiting -> true
    from is GuiaState.Waiting && to is GuiaState.Guiding -> true
    from is GuiaState.Waiting && to is GuiaState.Finishing -> true
    from is GuiaState.Guiding && to is GuiaState.Waiting -> true // loop: arrived → return to inicial
    from is GuiaState.Guiding && to is GuiaState.Finishing -> true
    from is GuiaState.Finishing && to is GuiaState.Idle -> true
    else -> false
}
