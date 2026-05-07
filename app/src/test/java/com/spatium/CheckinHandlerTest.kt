package com.spatium

import com.spatium.deamon.db.temi.core.CheckinHandler
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinHandlerTest {

    @Test
    fun `first check-in returns guest and contact names`() = runTest {
        val fake = FakeSupabaseGateway()
        fake.enqueue(
            buildJsonObject {
                put("guest_name", "Juan Pérez")
                put("contact_name", "María López")
                put("message_to_speak", "Bienvenido/a Juan Pérez. Le hemos notificado a María López.")
                put("already_checked_in", false)
            },
        )
        val handler = CheckinHandler(fake)
        val result = handler.handleById("abc-123")!!
        assertEquals("Juan Pérez", result.guestName)
        assertEquals("María López", result.contactName)
        assertFalse(result.alreadyCheckedIn)
        assertEquals(1, fake.calls.size)
        assertEquals("robot-invitado-checkin", fake.calls[0].path)
    }

    @Test
    fun `duplicate scan returns already checked in without re-notifying`() = runTest {
        val fake = FakeSupabaseGateway()
        fake.enqueue(
            buildJsonObject {
                put("guest_name", "Juan Pérez")
                put("contact_name", "María López")
                put("message_to_speak", "Juan Pérez ya fue registrado.")
                put("already_checked_in", true)
            },
        )
        val handler = CheckinHandler(fake)
        val result = handler.handleById("abc-123")!!
        assertTrue(result.alreadyCheckedIn)
        assertEquals(1, fake.calls.size)
    }

    @Test
    fun `missing guest id returns null`() = runTest {
        val fake = FakeSupabaseGateway()
        val handler = CheckinHandler(fake)
        val result = handler.handleById("")
        assertNull(result)
        assertTrue(fake.calls.isEmpty())
    }
}
