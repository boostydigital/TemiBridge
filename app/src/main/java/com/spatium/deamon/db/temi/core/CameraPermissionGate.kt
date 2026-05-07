package com.spatium.deamon.db.temi.core

/**
 * Pure decision object — zero Android imports, zero side effects.
 *
 * Given the current runtime state of the CAMERA permission, returns the
 * appropriate [Decision] so that callers (Activities) can react without
 * embedding permission logic themselves.
 */
object CameraPermissionGate {

    sealed class Decision {
        object Allow : Decision()
        object Request : Decision()
        object ShowSettings : Decision()
        object Deny : Decision()
    }

    /**
     * Determines the correct action for the camera permission request.
     *
     * @param granted              `true` if CAMERA permission is already granted.
     * @param shouldShowRationale  `true` if the system recommends showing a rationale UI
     *                             (the user previously denied but didn't choose "Don't ask again").
     * @param previouslyAsked      `true` if the app already asked for the permission at least once
     *                             (stored in SharedPreferences by the caller).
     *
     * Decision table:
     * - granted=true                                                → Allow
     * - granted=false, shouldShowRationale=true                    → Request  (user denied once, show rationale + re-ask)
     * - granted=false, shouldShowRationale=false, !previouslyAsked → Request  (very first ask)
     * - granted=false, shouldShowRationale=false, previouslyAsked  → ShowSettings  (permanently denied)
     */
    fun decide(granted: Boolean, shouldShowRationale: Boolean, previouslyAsked: Boolean): Decision {
        if (granted) return Decision.Allow
        if (shouldShowRationale) return Decision.Request
        return if (!previouslyAsked) Decision.Request else Decision.ShowSettings
    }
}
