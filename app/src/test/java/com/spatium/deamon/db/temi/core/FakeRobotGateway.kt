package com.spatium.deamon.db.temi.core

/**
 * Test helper: in-memory fake implementation of RobotGateway.
 * Records every call in [calls] so tests can assert what happened.
 * NOT a test class — no @Test annotations.
 *
 * State fields use `fakeVolume` / `fakeSpeed` / `fakeKioskOn` names to avoid
 * JVM getter name clashes with the RobotGateway interface methods
 * (`getVolume()`, `getGoToSpeed()`, `isKioskModeOn()`).
 */
class FakeRobotGateway : RobotGateway {

    val calls = mutableListOf<String>()

    // State — named to avoid clash with interface methods
    var fakeVolume: Int? = 5
    var fakeSpeed: TemiController.SpeedLevel? = TemiController.SpeedLevel.MEDIUM
    var fakeKioskOn: Boolean = false
    var fakeBillboardHidden: Boolean = false

    var arrivalCb: (() -> Unit)? = null
    var abortCb: (() -> Unit)? = null

    // ----- RobotGateway implementation -----

    override fun setVolume(level: Int): Boolean {
        calls += "setVolume($level)"
        fakeVolume = level
        return true
    }

    override fun getVolume(): Int? = fakeVolume

    override fun setGoToSpeed(level: TemiController.SpeedLevel): Boolean {
        calls += "setGoToSpeed(${level.name})"
        fakeSpeed = level
        return true
    }

    override fun getGoToSpeed(): TemiController.SpeedLevel? = fakeSpeed

    override fun setKioskModeOn(on: Boolean): Boolean {
        calls += "setKioskModeOn($on)"
        fakeKioskOn = on
        return true
    }

    override fun isKioskModeOn(): Boolean = fakeKioskOn

    override fun toggleNavigationBillboard(disabled: Boolean): Boolean {
        calls += "toggleNavigationBillboard($disabled)"
        fakeBillboardHidden = disabled
        return true
    }

    override fun enableFaceTracking(): Boolean {
        calls += "enableFaceTracking()"
        return true
    }

    override fun disableFaceTracking(): Boolean {
        calls += "disableFaceTracking()"
        return true
    }

    override fun goTo(place: String) {
        calls += "goTo($place)"
    }

    override fun goToBackwards(place: String) {
        calls += "goToBackwards($place)"
    }

    override fun stopMovement(): Boolean {
        calls += "stopMovement()"
        return true
    }

    override fun speak(text: String) {
        calls += "speak($text)"
    }

    override fun setArrivalCallbackOnce(cb: () -> Unit) {
        calls += "setArrivalCallbackOnce()"
        arrivalCb = cb
    }

    override fun setAbortCallback(cb: () -> Unit) {
        calls += "setAbortCallback()"
        abortCb = cb
    }

    override fun clearArrivalCallback() {
        calls += "clearArrivalCallback()"
        arrivalCb = null
        abortCb = null
    }

    override fun hasSettingsPermission(): Boolean {
        calls += "hasSettingsPermission()"
        return true
    }

    // ----- Test helpers -----

    fun simulateArrival() {
        arrivalCb?.invoke()
    }

    fun simulateAbort() {
        abortCb?.invoke()
    }

    fun clearCalls() {
        calls.clear()
    }
}
