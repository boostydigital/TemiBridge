package com.spatium

import com.spatium.deamon.db.temi.core.AnnouncementManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AnnouncementManagerTest {

    private lateinit var fake: FakeSupabaseGateway
    private lateinit var manager: AnnouncementManager

    @Before
    fun setUp() {
        fake = FakeSupabaseGateway()
        manager = AnnouncementManager(
            context = null,
            gateway = fake,
        )
        manager.patrolDispatch = { /* no-op: avoids TemiController in unit tests */ }
    }

    @Test
    fun `active announcement triggers patrol`() = runTest {
        fake.enqueue(
            buildJsonObject {
                put("activo", true)
                putJsonObject("anuncio") {
                    put("id", "1")
                    put("texto", "test")
                    put("imagen_url", "")
                    put("duracion_minutos", 5)
                    putJsonArray("waypoints") {
                        add(kotlinx.serialization.json.JsonPrimitive("wp1"))
                        add(kotlinx.serialization.json.JsonPrimitive("wp2"))
                        add(kotlinx.serialization.json.JsonPrimitive("wp3"))
                    }
                }
            },
        )

        manager.checkForActiveAnnouncement()

        assertEquals(1, fake.calls.size)
        assertEquals("GET", fake.calls[0].method)
        assertEquals("anuncio-activo", fake.calls[0].path)
    }

    @Test
    fun `no announcement returns idle state`() = runTest {
        fake.enqueue(buildJsonObject { put("activo", false) })

        manager.checkForActiveAnnouncement()

        assertEquals(1, fake.calls.size)
        assertEquals("anuncio-activo", fake.calls[0].path)
        assertNull(manager.currentAnuncioId)
    }

    @Test
    fun `calls list records expected paths`() = runTest {
        fake.enqueue(buildJsonObject { put("activo", false) })
        fake.enqueue(buildJsonObject { put("activo", false) })

        manager.checkForActiveAnnouncement()
        manager.checkForActiveAnnouncement()

        assertEquals(2, fake.calls.size)
        fake.calls.forEach { call ->
            assertEquals("GET", call.method)
            assertEquals("anuncio-activo", call.path)
        }
    }
}
