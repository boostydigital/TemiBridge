package com.spatium.deamon.db.temi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GuiaStateMachineTest {

    private lateinit var fake: FakeRobotGateway
    private lateinit var machine: GuiaStateMachine

    private val samplePayload = GuiaPayload(
        id = "test-id-001",
        nombreEvento = "Expo Test",
        descripcion = null,
        waypointInicial = "entrada",
        waypointFinal = "sala_b",
        horaInicio = "2026-04-27T18:00:00Z",
        imagenFondoUrl = null,
        videoLoopUrl = null,
        bienvenidaTts = "Bienvenido al tour",
        llegadaTts = "Hemos llegado",
        etiquetaBoton = "Iniciar",
        expiresAt = "2026-04-27T22:00:00Z",
    )

    @Before
    fun setUp() {
        fake = FakeRobotGateway()
        machine = GuiaStateMachine()
    }

    @Test
    fun `initial state is Idle`() {
        assertTrue(machine.currentState() is GuiaState.Idle)
    }

    @Test
    fun `Idle to Waiting is valid transition`() {
        val snapshot = RobotStateSnapshot(
            volume = 7,
            speedLevel = TemiController.SpeedLevel.MEDIUM,
            kioskOn = false,
            navBillboardHidden = false,
        )
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))

        val state = machine.currentState()
        assertTrue(state is GuiaState.Waiting)
        assertEquals(samplePayload, (state as GuiaState.Waiting).guia)
    }

    @Test
    fun `Waiting to Guiding is valid transition`() {
        val snapshot = RobotStateSnapshot(
            volume = 7,
            speedLevel = TemiController.SpeedLevel.MEDIUM,
            kioskOn = false,
            navBillboardHidden = false,
        )
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Guiding(samplePayload, snapshot))

        val state = machine.currentState()
        assertTrue(state is GuiaState.Guiding)
        assertEquals(samplePayload, (state as GuiaState.Guiding).guia)
    }

    @Test
    fun `Guiding to Finishing completada is valid`() {
        val snapshot = RobotStateSnapshot(
            volume = 7,
            speedLevel = TemiController.SpeedLevel.MEDIUM,
            kioskOn = false,
            navBillboardHidden = false,
        )
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Guiding(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Finishing(samplePayload, "completada"))

        val state = machine.currentState()
        assertTrue(state is GuiaState.Finishing)
        assertEquals("completada", (state as GuiaState.Finishing).reason)
    }

    @Test
    fun `Guiding to Finishing expirada is valid`() {
        val snapshot = RobotStateSnapshot(
            volume = 5,
            speedLevel = TemiController.SpeedLevel.SLOW,
            kioskOn = true,
            navBillboardHidden = true,
        )
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Guiding(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Finishing(samplePayload, "expirada"))

        val state = machine.currentState()
        assertTrue(state is GuiaState.Finishing)
        assertEquals("expirada", (state as GuiaState.Finishing).reason)
    }

    @Test
    fun `Waiting to Finishing expirada is valid - covers expiration from waiting`() {
        val snapshot = RobotStateSnapshot(
            volume = 5,
            speedLevel = TemiController.SpeedLevel.SLOW,
            kioskOn = true,
            navBillboardHidden = true,
        )
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Finishing(samplePayload, "expirada"))

        val state = machine.currentState()
        assertTrue(state is GuiaState.Finishing)
        assertEquals("expirada", (state as GuiaState.Finishing).reason)
    }

    @Test
    fun `Guiding to Finishing cancelada is valid`() {
        val snapshot = RobotStateSnapshot(
            volume = 5,
            speedLevel = TemiController.SpeedLevel.SLOW,
            kioskOn = true,
            navBillboardHidden = false,
        )
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Guiding(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Finishing(samplePayload, "cancelada"))

        val state = machine.currentState()
        assertTrue(state is GuiaState.Finishing)
        assertEquals("cancelada", (state as GuiaState.Finishing).reason)
    }

    @Test
    fun `Guiding to Waiting is valid - covers loop-back-to-inicial case`() {
        val snapshot = RobotStateSnapshot(
            volume = 7,
            speedLevel = TemiController.SpeedLevel.MEDIUM,
            kioskOn = false,
            navBillboardHidden = false,
        )
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Guiding(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))

        val state = machine.currentState()
        assertTrue(state is GuiaState.Waiting)
        assertEquals(samplePayload, (state as GuiaState.Waiting).guia)
    }

    @Test
    fun `Finishing to Idle is valid after cleanup`() {
        val snapshot = RobotStateSnapshot(
            volume = 5,
            speedLevel = TemiController.SpeedLevel.MEDIUM,
            kioskOn = false,
            navBillboardHidden = false,
        )
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Guiding(samplePayload, snapshot))
        machine.transitionTo(GuiaState.Finishing(samplePayload, "completada"))
        machine.transitionTo(GuiaState.Idle)

        assertTrue(machine.currentState() is GuiaState.Idle)
    }

    @Test
    fun `Idle to Guiding directly is rejected - invalid transition`() {
        val snapshot = RobotStateSnapshot(
            volume = 5,
            speedLevel = TemiController.SpeedLevel.MEDIUM,
            kioskOn = false,
            navBillboardHidden = false,
        )
        val result = machine.tryTransitionTo(GuiaState.Guiding(samplePayload, snapshot))

        assertFalse("Idle to Guiding must be rejected", result)
        assertTrue(machine.currentState() is GuiaState.Idle)
    }

    @Test
    fun `Idle to Finishing is rejected - invalid transition`() {
        val result = machine.tryTransitionTo(GuiaState.Finishing(samplePayload, "cancelada"))

        assertFalse("Idle to Finishing must be rejected", result)
        assertTrue(machine.currentState() is GuiaState.Idle)
    }

    @Test
    fun `double-acquire Waiting to Waiting is rejected`() {
        val snapshot = RobotStateSnapshot(
            volume = 5,
            speedLevel = TemiController.SpeedLevel.MEDIUM,
            kioskOn = false,
            navBillboardHidden = false,
        )
        machine.transitionTo(GuiaState.Waiting(samplePayload, snapshot))
        val result = machine.tryTransitionTo(GuiaState.Waiting(samplePayload, snapshot))

        assertFalse("Waiting to Waiting must be rejected", result)
        assertTrue(machine.currentState() is GuiaState.Waiting)
    }
}
