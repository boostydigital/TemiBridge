package com.spatium

import com.spatium.deamon.db.temi.net.OkHttpSupabaseGateway
import org.junit.Assert.assertNotNull
import org.junit.Test

class UrlConstantTest {
    @Test
    fun `OkHttpSupabaseGateway accepts injected base URL`() {
        val gateway = OkHttpSupabaseGateway(
            baseUrl = "https://test.supabase.co/functions/v1",
            anonKey = "test-key",
        )
        assertNotNull(gateway)
    }

    @Test
    fun `FakeSupabaseGateway records calls`() {
        val fake = FakeSupabaseGateway()
        assertNotNull(fake)
        assert(fake.calls.isEmpty())
    }
}
