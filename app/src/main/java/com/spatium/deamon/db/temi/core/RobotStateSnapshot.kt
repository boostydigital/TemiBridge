package com.spatium.deamon.db.temi.core

/**
 * Immutable snapshot of robot settings captured before a guia session starts.
 * Call [capture] to read current state, [restore] to reapply it after the session ends.
 *
 * Design rule (from design §3):
 * - If original [kioskOn] was false → restore calls setKioskModeOn(false)
 * - If original [kioskOn] was true  → kiosk was already on, leave it on (no call)
 * - Navigation billboard is always restored to visible (disabled=false)
 */
data class RobotStateSnapshot(
    val volume: Int?,
    val speedLevel: TemiController.SpeedLevel?,
    val kioskOn: Boolean,
    val navBillboardHidden: Boolean,
) {

    companion object {
        /**
         * Reads the current robot state and returns a snapshot.
         * Safe to call when Robot SDK is unavailable — null values are preserved.
         */
        fun capture(robot: RobotGateway): RobotStateSnapshot = RobotStateSnapshot(
            volume = robot.getVolume(),
            speedLevel = robot.getGoToSpeed(),
            kioskOn = robot.isKioskModeOn(),
            navBillboardHidden = false, // we never read the billboard state; always restore to shown
        )
    }

    /**
     * Applies the saved values back to [robot].
     * Idempotent — safe to call multiple times.
     */
    fun restore(robot: RobotGateway) {
        volume?.let { robot.setVolume(it) }
        speedLevel?.let { robot.setGoToSpeed(it) }

        // Always show the navigation billboard (GuiaManager hid it, restore shows it)
        robot.toggleNavigationBillboard(false)

        // Only turn off kiosk if it was originally off (GuiaManager may have turned it on)
        if (!kioskOn) {
            robot.setKioskModeOn(false)
        }
        // If kioskOn=true, kiosk was already on before the guia → leave it on, no call needed
    }
}
