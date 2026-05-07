package com.spatium

import com.spatium.deamon.db.temi.core.FakeRobotGateway
import com.spatium.deamon.db.temi.core.GuiaManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiaManagerTest {

    @Test
    fun `guia-pendiente returns empty — manager backs off`() = runTest {
        val fake = FakeSupabaseGateway()
        fake.enqueue(buildJsonObject { put("pendiente", false) })
        val manager = GuiaManager(gateway = fake, robot = FakeRobotGateway())
        manager.checkForPendingGuia()
        assertEquals(1, fake.calls.size)
        assertEquals("guia-pendiente", fake.calls[0].path)
    }

    @Test
    fun `finalizar-guia records completion`() = runTest {
        val fake = FakeSupabaseGateway()
        fake.enqueue(buildJsonObject { put("success", true) })
        val manager = GuiaManager(gateway = fake, robot = FakeRobotGateway())
        manager.finalizarGuia("1", "completada")
        assertTrue(fake.calls.any { it.path == "finalizar-guia" })
    }

    @Test
    fun `sweep stale guias calls robot-sweep-guias`() = runTest {
        val fake = FakeSupabaseGateway()
        fake.enqueue(buildJsonObject { put("swept", 0) })
        val manager = GuiaManager(gateway = fake, robot = FakeRobotGateway())
        manager.sweepStaleGuias()
        assertTrue(fake.calls.any { it.path == "robot-sweep-guias" })
    }
}
