package com.spatium.deamon.db.temi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RobotStateSnapshotTest {

    private lateinit var fake: FakeRobotGateway

    @Before
    fun setUp() {
        fake = FakeRobotGateway()
    }

    @Test
    fun `capture returns snapshot with current values from gateway`() {
        fake.fakeVolume = 8
        fake.fakeSpeed = TemiController.SpeedLevel.SLOW
        fake.fakeKioskOn = true

        val snapshot = RobotStateSnapshot.capture(fake)

        assertEquals(8, snapshot.volume)
        assertEquals(TemiController.SpeedLevel.SLOW, snapshot.speedLevel)
        assertTrue(snapshot.kioskOn)
    }

    @Test
    fun `restore applies volume and speed to target gateway`() {
        val snapshot = RobotStateSnapshot(
            volume = 6,
            speedLevel = TemiController.SpeedLevel.HIGH,
            kioskOn = false,
            navBillboardHidden = true,
        )
        val restoreTarget = FakeRobotGateway()

        snapshot.restore(restoreTarget)

        assertTrue(restoreTarget.calls.contains("setVolume(6)"))
        assertTrue(restoreTarget.calls.contains("setGoToSpeed(HIGH)"))
        // navBillboardHidden=true means billboard was hidden; restore must show it (disabled=false)
        assertTrue(restoreTarget.calls.contains("toggleNavigationBillboard(false)"))
    }

    @Test
    fun `restore calls setKioskModeOn(false) when original kioskOn was false`() {
        val snapshot = RobotStateSnapshot(
            volume = 5,
            speedLevel = TemiController.SpeedLevel.MEDIUM,
            kioskOn = false,
            navBillboardHidden = false,
        )
        val restoreTarget = FakeRobotGateway()

        snapshot.restore(restoreTarget)

        // Design: if original was false, GuiaManager turned it on → we turn it off on restore
        assertTrue(
            "setKioskModeOn(false) must be called when original was false",
            restoreTarget.calls.contains("setKioskModeOn(false)"),
        )
    }

    @Test
    fun `restore does NOT call setKioskModeOn when original kioskOn was true`() {
        val snapshot = RobotStateSnapshot(
            volume = 5,
            speedLevel = TemiController.SpeedLevel.MEDIUM,
            kioskOn = true,
            navBillboardHidden = false,
        )
        val restoreTarget = FakeRobotGateway()

        snapshot.restore(restoreTarget)

        // When original was true → kiosk was already on, leave it, no setKioskModeOn call
        val kioskCallCount = restoreTarget.calls.count { it.startsWith("setKioskModeOn") }
        assertEquals("setKioskModeOn must NOT be called when original was true", 0, kioskCallCount)
    }

    @Test
    fun `round trip - capture then modify state then restore returns to original values`() {
        fake.fakeVolume = 5
        fake.fakeSpeed = TemiController.SpeedLevel.MEDIUM
        fake.fakeKioskOn = false

        val snapshot = RobotStateSnapshot.capture(fake)

        // Simulate guia modifying the robot state
        fake.fakeVolume = 7
        fake.fakeSpeed = TemiController.SpeedLevel.SLOW
        fake.fakeKioskOn = true

        // Restore
        snapshot.restore(fake)

        assertEquals("Volume must be restored to 5", 5, fake.fakeVolume)
        assertEquals("Speed must be restored to MEDIUM", TemiController.SpeedLevel.MEDIUM, fake.fakeSpeed)
    }
}
