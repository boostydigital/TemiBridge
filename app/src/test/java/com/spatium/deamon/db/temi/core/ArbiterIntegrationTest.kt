package com.spatium.deamon.db.temi.core

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration test for ExclusiveModeArbiter using the named mode constants
 * (MODE_ANNOUNCEMENT, MODE_GUIA, MODE_RATING).
 *
 * ExclusiveModeArbiterTest covers general concurrency and API contracts.
 * This file covers the concrete cross-mode exclusion combinations that
 * AnnouncementManager, RatingManager, and GuiaManager rely on at runtime.
 */
class ArbiterIntegrationTest {

    @Before
    fun resetArbiter() {
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_GUIA)
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_RATING)
    }

    @After
    fun cleanUpArbiter() {
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_GUIA)
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_RATING)
    }

    // ─── ANNOUNCEMENT blocks others ──────────────────────────────────────────

    @Test
    fun `ANNOUNCEMENT acquired first - GUIA is denied`() {
        val announcementResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        }
        val guiaResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA)
        }

        assertTrue("ANNOUNCEMENT must acquire when idle", announcementResult)
        assertFalse("GUIA must be denied while ANNOUNCEMENT holds", guiaResult)
        assertEquals(ExclusiveModeArbiter.MODE_ANNOUNCEMENT, ExclusiveModeArbiter.currentMode())
    }

    @Test
    fun `ANNOUNCEMENT acquired first - RATING is denied`() {
        val announcementResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        }
        val ratingResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_RATING)
        }

        assertTrue("ANNOUNCEMENT must acquire when idle", announcementResult)
        assertFalse("RATING must be denied while ANNOUNCEMENT holds", ratingResult)
    }

    // ─── GUIA blocks others ───────────────────────────────────────────────────

    @Test
    fun `GUIA acquired first - ANNOUNCEMENT is denied`() {
        val guiaResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA)
        }
        val announcementResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        }

        assertTrue("GUIA must acquire when idle", guiaResult)
        assertFalse("ANNOUNCEMENT must be denied while GUIA holds", announcementResult)
        assertEquals(ExclusiveModeArbiter.MODE_GUIA, ExclusiveModeArbiter.currentMode())
    }

    @Test
    fun `GUIA acquired first - RATING is denied`() {
        val guiaResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA)
        }
        val ratingResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_RATING)
        }

        assertTrue("GUIA must acquire when idle", guiaResult)
        assertFalse("RATING must be denied while GUIA holds", ratingResult)
    }

    // ─── RATING blocks others ─────────────────────────────────────────────────

    @Test
    fun `RATING acquired first - ANNOUNCEMENT is denied`() {
        val ratingResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_RATING)
        }
        val announcementResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        }

        assertTrue("RATING must acquire when idle", ratingResult)
        assertFalse("ANNOUNCEMENT must be denied while RATING holds", announcementResult)
    }

    @Test
    fun `RATING acquired first - GUIA is denied`() {
        val ratingResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_RATING)
        }
        val guiaResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA)
        }

        assertTrue("RATING must acquire when idle", ratingResult)
        assertFalse("GUIA must be denied while RATING holds", guiaResult)
        assertEquals(ExclusiveModeArbiter.MODE_RATING, ExclusiveModeArbiter.currentMode())
    }

    // ─── Release + re-acquire cycle ───────────────────────────────────────────

    @Test
    fun `ANNOUNCEMENT releases - GUIA can acquire next`() {
        runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT) }
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)

        val guiaResult = runBlocking {
            ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_GUIA)
        }

        assertTrue("GUIA must acquire after ANNOUNCEMENT releases", guiaResult)
        assertEquals(ExclusiveModeArbiter.MODE_GUIA, ExclusiveModeArbiter.currentMode())
    }

    @Test
    fun `full ANNOUNCEMENT cycle - acquire then release leaves arbiter idle`() {
        runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT) }
        assertEquals(ExclusiveModeArbiter.MODE_ANNOUNCEMENT, ExclusiveModeArbiter.currentMode())

        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_ANNOUNCEMENT)
        assertNull("Arbiter must be idle after full ANNOUNCEMENT cycle", ExclusiveModeArbiter.currentMode())
    }

    @Test
    fun `full RATING cycle - acquire then release leaves arbiter idle`() {
        runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_RATING) }
        assertEquals(ExclusiveModeArbiter.MODE_RATING, ExclusiveModeArbiter.currentMode())

        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_RATING)
        assertNull("Arbiter must be idle after full RATING cycle", ExclusiveModeArbiter.currentMode())
    }

    // ─── Release safety (no-op on mismatch) ──────────────────────────────────

    @Test
    fun `release wrong mode while ANNOUNCEMENT holds - state unchanged`() {
        runBlocking { ExclusiveModeArbiter.tryAcquire(ExclusiveModeArbiter.MODE_ANNOUNCEMENT) }

        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_GUIA) // wrong mode
        ExclusiveModeArbiter.release(ExclusiveModeArbiter.MODE_RATING) // wrong mode

        assertEquals(
            "ANNOUNCEMENT must still hold after mismatched releases",
            ExclusiveModeArbiter.MODE_ANNOUNCEMENT,
            ExclusiveModeArbiter.currentMode(),
        )
    }
}
