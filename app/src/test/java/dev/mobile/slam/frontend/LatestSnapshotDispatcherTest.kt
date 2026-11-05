package dev.mobile.slam.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class LatestSnapshotDispatcherTest {
    private class QueuedExecutor : Executor {
        private val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            queue.addLast(command)
        }

        fun queuedCount(): Int = queue.size

        fun drain() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    @Test
    fun keepsOnlyMostRecentPendingFrameWhenUiIsSlow() {
        val executor = QueuedExecutor()
        val received = mutableListOf<Int>()
        val dispatcher = LatestSnapshotDispatcher<Int>(executor) { received.add(it) }
        for (frame in 1..100) dispatcher.publish(frame)
        assertEquals(1, executor.queuedCount())
        executor.drain()
        assertEquals(listOf(100), received)
    }

    @Test
    fun deliversNewSnapshotAfterPreviousDrain() {
        val executor = QueuedExecutor()
        val received = mutableListOf<Int>()
        val dispatcher = LatestSnapshotDispatcher<Int>(executor) { received.add(it) }
        dispatcher.publish(1)
        executor.drain()
        dispatcher.publish(2)
        executor.drain()
        assertEquals(listOf(1, 2), received)
    }

    @Test
    fun closingDropsPendingSnapshotAndFutureWrites() {
        val executor = QueuedExecutor()
        val received = mutableListOf<Int>()
        val dispatcher = LatestSnapshotDispatcher<Int>(executor) { received.add(it) }
        dispatcher.publish(1)
        dispatcher.publish(2)
        dispatcher.close()
        dispatcher.publish(3)
        executor.drain()
        assertTrue(received.isEmpty())
    }

    @Test
    fun callbackCanPublishFollowupWithoutUnboundedQueue() {
        val executor = QueuedExecutor()
        val received = mutableListOf<Int>()
        lateinit var dispatcher: LatestSnapshotDispatcher<Int>
        dispatcher = LatestSnapshotDispatcher<Int>(executor) {
            received.add(it)
            if (it == 1) dispatcher.publish(2)
        }
        dispatcher.publish(1)
        executor.drain()
        assertEquals(listOf(1, 2), received)
        assertEquals(0, executor.queuedCount())
    }
}
