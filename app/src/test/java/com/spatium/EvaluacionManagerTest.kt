package com.spatium

import com.spatium.temibridge.core.RatingManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EvaluacionManagerTest {

    private lateinit var fake: FakeSupabaseGateway
    private lateinit var manager: RatingManager

    @Before
    fun setUp() {
        fake = FakeSupabaseGateway()
        manager = RatingManager(
            context = null,
            gateway = fake,
        )
        // Avoid TemiController / Android side effects in unit tests
        manager.navigationDispatch = { /* no-op */ }
    }

    @Test
    fun `claim evaluation - happy path`() = runTest {
        fake.enqueue(
            buildJsonObject {
                put("pendiente", true)
                putJsonObject("evaluacion") {
                    put("id", "1")
                    put("salon", "Sala Duarte")
                    put("waypoint", "salonduarte")
                    put("hora_llegada", "2026-05-06T10:00:00Z")
                    put("nombre_reserva", "Test")
                }
            },
        )

        manager.checkForPendingEvaluation()

        assertEquals(1, fake.calls.size)
        assertEquals("GET", fake.calls[0].method)
        assertEquals("evaluacion-pendiente", fake.calls[0].path)
    }

    @Test
    fun `cancel evaluation calls programar-evaluacion`() = runTest {
        // Seed currentEvaluacion so updateEvaluationStatus doesn't early-return
        manager.currentEvaluacion = JSONObject().apply { put("id", "42") }

        fake.enqueue(buildJsonObject { put("ok", true) })

        manager.updateEvaluationStatus("cancelada")

        assertEquals(1, fake.calls.size)
        assertEquals("POST", fake.calls[0].method)
        assertEquals("programar-evaluacion", fake.calls[0].path)
        assertEquals("42", fake.calls[0].body?.get("id")?.toString()?.trim('"'))
        assertEquals("cancelada", fake.calls[0].body?.get("estado")?.toString()?.trim('"'))
    }

    @Test
    fun `no pending evaluation - manager stays idle`() = runTest {
        fake.enqueue(buildJsonObject { put("pendiente", false) })

        manager.checkForPendingEvaluation()

        assertEquals(1, fake.calls.size)
        assertEquals("evaluacion-pendiente", fake.calls[0].path)
        // navigationDispatch was never called — currentEvaluacion remains null
        assertNull(manager.currentEvaluacion)
    }
}
