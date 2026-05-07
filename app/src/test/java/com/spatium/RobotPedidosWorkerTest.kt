package com.spatium

import com.spatium.deamon.db.temi.core.RobotPedidosWorker
import org.junit.Assert.assertFalse
import org.junit.Test

class RobotPedidosWorkerTest {

    @Test
    fun `worker starts in non-processing state`() {
        // isProcessing is false before any work begins — no DB, no coroutine needed
        assertFalse(RobotPedidosWorker.isProcessingForTest())
    }

    @Test
    fun `worker can be stopped cleanly`() {
        // stop() must not throw when the worker has never been started,
        // and isProcessing must remain false afterward
        RobotPedidosWorker.stop()
        assertFalse(RobotPedidosWorker.isProcessingForTest())
    }
}
