package com.spatium.deamon.db.temi.core

/**
 * Seam interface that abstracts all Temi SDK calls needed by GuiaManager.
 * Production code uses [DefaultRobotGateway]; tests inject [FakeRobotGateway].
 * No new SDK methods were needed — everything maps to existing TemiController calls.
 */
interface RobotGateway {

    // Volume
    fun setVolume(level: Int): Boolean
    fun getVolume(): Int?

    // Navigation speed
    fun setGoToSpeed(level: TemiController.SpeedLevel): Boolean
    fun getGoToSpeed(): TemiController.SpeedLevel?

    // Kiosk mode
    fun setKioskModeOn(on: Boolean): Boolean
    fun isKioskModeOn(): Boolean

    // Navigation billboard ("Yendo a...")
    fun toggleNavigationBillboard(disabled: Boolean): Boolean

    // Face tracking
    fun enableFaceTracking(): Boolean
    fun disableFaceTracking(): Boolean

    // Movement
    fun goTo(place: String)

    /** Navigate backwards so the screen faces the following visitor. Falls back to goTo if unsupported by SDK. */
    fun goToBackwards(place: String)
    fun stopMovement(): Boolean

    // Speech
    fun speak(text: String)

    // Arrival / abort callbacks
    fun setArrivalCallbackOnce(cb: () -> Unit)
    fun setAbortCallback(cb: () -> Unit)
    fun clearArrivalCallback()

    // Settings permission check
    fun hasSettingsPermission(): Boolean
}
