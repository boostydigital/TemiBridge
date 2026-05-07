package com.spatium.deamon.db.temi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPermissionGateTest {

    @Test
    fun `decide when granted returns Allow`() {
        val decision = CameraPermissionGate.decide(
            granted = true,
            shouldShowRationale = false,
            previouslyAsked = false,
        )
        assertEquals(CameraPermissionGate.Decision.Allow, decision)
    }

    @Test
    fun `decide when not granted and shouldShowRationale is true returns Request`() {
        val decision = CameraPermissionGate.decide(
            granted = false,
            shouldShowRationale = true,
            previouslyAsked = true,
        )
        assertEquals(CameraPermissionGate.Decision.Request, decision)
    }

    @Test
    fun `decide when not granted shouldShowRationale false and not previouslyAsked returns Request (first ask)`() {
        val decision = CameraPermissionGate.decide(
            granted = false,
            shouldShowRationale = false,
            previouslyAsked = false,
        )
        assertEquals(CameraPermissionGate.Decision.Request, decision)
    }

    @Test
    fun `decide when not granted shouldShowRationale false and previouslyAsked returns ShowSettings (permanently denied)`() {
        val decision = CameraPermissionGate.decide(
            granted = false,
            shouldShowRationale = false,
            previouslyAsked = true,
        )
        assertEquals(CameraPermissionGate.Decision.ShowSettings, decision)
    }
}
