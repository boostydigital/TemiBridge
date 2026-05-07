package com.spatium.deamon.db.temi.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ExclusiveModeArbiterTest {

    @Before
    fun setUp() {
        // Reset arbiter state before each test
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_GUIA)
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_RATING)
    }

    @After
    fun tearDown() {
        // Clean up after each test
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_GUIA)
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_RATING)
    }

    @Test
    fun `tryAcquire when idle returns true and sets current mode`() {
        val result = runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA) }

        assertTrue(result)
        assertEquals(ExclusiveModeArbiter.MODE_GUIA, ExclusiveModeArbiter.currentMode())
    }

    @Test
    fun `tryAcquire same mode when already holding returns true - idempotent`() {
        runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA)
            val secondResult = ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA)

            assertTrue("Re-acquire of same mode must be idempotent", secondResult)
            assertEquals(ExclusiveModeArbiter.MODE_GUIA, ExclusiveModeArbiter.currentMode())
        }
    }

    @Test
    fun `tryAcquire different mode while occupied returns false`() {
        runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
            val guiaResult = ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA)

            assertFalse("GUIA must be denied when ANNOUNCEMENT holds", guiaResult)
            assertEquals(ExclusiveModeArbiter.MODE_ANNOUNCEMENT, ExclusiveModeArbiter.currentMode())
        }
    }

    @Test
    fun `release matching mode clears current mode`() {
        runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA) }

        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_GUIA)

        assertNull(ExclusiveModeArbiter.currentMode())
    }

    @Test
    fun `after release a different mode can acquire`() {
        runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA) }
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_GUIA)

        val announcementResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        }

        assertTrue(announcementResult)
        assertEquals(ExclusiveModeArbiter.MODE_ANNOUNCEMENT, ExclusiveModeArbiter.currentMode())
    }

    @Test
    fun `release mismatched mode does not change state`() {
        runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA) }

        // Release with wrong mode - should be ignored
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)

        assertEquals(
            "State must remain GUIA after mismatched release",
            ExclusiveModeArbiter.MODE_GUIA,
            ExclusiveModeArbiter.currentMode(),
        )
    }

    @Test
    fun `currentMode returns null when no mode holds the lock`() {
        assertNull(ExclusiveModeArbiter.currentMode())
    }

    @Test
    fun `concurrency - 100 coroutines competing, exactly one MODE wins the initial slot`() {
        // Each coroutine tries a UNIQUE mode string so we can count distinct first-acquires.
        // With the Mutex, only one coroutine can set the owner from null → exactly 1 unique winner.
        val total = 100

        val results: List<Pair<String, Boolean>> = runBlocking {
            (1..total).map { i ->
                async(kotlinx.coroutines.Dispatchers.Default) {
                    // Each coroutine uses a unique mode name so idempotent re-acquire cannot inflate the count
                    val mode = "MODE_$i"
                    mode to ExclusiveModeArbiter.tryAcquire(mode)
                }
            }.awaitAll()
        }

        val winners = results.filter { it.second }
        assertEquals(
            "Exactly one unique mode must win the concurrent acquisition race",
            1,
            winners.size,
        )

        val winningMode = winners.first().first
        assertEquals(
            "currentMode() must reflect the winning mode",
            winningMode,
            ExclusiveModeArbiter.currentMode(),
        )

        // Clean up the dynamic mode
        ExclusiveModeArbiter.release(winningMode)
    }
}
