package com.spatium.deamon.db.temi.core

/**
 * Production implementation of [RobotGateway] that delegates every call
 * to the corresponding [TemiController] method.
 * Used as the default in GuiaManager constructor.
 */
object DefaultRobotGateway : RobotGateway {

    override fun setVolume(level: Int): Boolean =
        TemiController.setVolume(level)

    override fun getVolume(): Int? =
        TemiController.getVolume()

    override fun setGoToSpeed(level: TemiController.SpeedLevel): Boolean =
        TemiController.setGoToSpeed(level)

    override fun getGoToSpeed(): TemiController.SpeedLevel? =
        TemiController.getGoToSpeed()

    override fun setKioskModeOn(on: Boolean): Boolean =
        TemiController.setKioskModeOn(on)

    override fun isKioskModeOn(): Boolean =
        TemiController.isKioskModeOn()

    override fun toggleNavigationBillboard(disabled: Boolean): Boolean =
        TemiController.toggleNavigationBillboard(disabled)

    override fun enableFaceTracking(): Boolean =
        TemiController.enableFaceTracking()

    override fun disableFaceTracking(): Boolean =
        TemiController.disableFaceTracking()

    override fun goTo(place: String) =
        TemiController.goTo(place)

    override fun goToBackwards(place: String) =
        TemiController.goToBackwards(place)

    override fun stopMovement(): Boolean =
        TemiController.stopMovement()

    override fun speak(text: String) =
        TemiController.speak(text)

    override fun setArrivalCallbackOnce(cb: () -> Unit) =
        TemiController.setArrivalCallbackOnce(cb)

    override fun setAbortCallback(cb: () -> Unit) =
        TemiController.setAbortCallback(cb)

    override fun clearArrivalCallback() =
        TemiController.clearArrivalCallback()

    override fun hasSettingsPermission(): Boolean =
        TemiController.hasSettingsPermission()
}
