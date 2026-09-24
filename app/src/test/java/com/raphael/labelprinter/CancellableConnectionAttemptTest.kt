package com.raphael.labelprinter

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableConnectionAttemptTest {
    @Test
    fun cancelClosesSocketWithoutWaitingForBlockedConnect() {
        val socket = BlockingSocket()
        val attempt = CancellableConnectionAttempt<BlockingSocket> { it.close() }
        val worker = thread(start = true) {
            runCatching {
                attempt.connect(create = { socket }, connect = { it.connect() })
            }
        }

        assertTrue(socket.connectStarted.await(1, TimeUnit.SECONDS))
        attempt.cancel()
        worker.join(1_000)

        assertTrue(socket.closed)
        assertFalse(worker.isAlive)
    }

    private class BlockingSocket {
        val connectStarted = CountDownLatch(1)
        private val closedSignal = CountDownLatch(1)

        @Volatile
        var closed = false
            private set

        fun connect() {
            connectStarted.countDown()
            closedSignal.await()
            throw IOException("socket closed")
        }

        fun close() {
            closed = true
            closedSignal.countDown()
        }
    }
}
