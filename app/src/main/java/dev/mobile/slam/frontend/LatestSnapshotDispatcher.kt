package dev.mobile.slam.frontend

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Coalesces immutable analyzer snapshots on the receiving executor.
 *
 * At most one runnable and one pending snapshot are held. If the camera produces
 * faster than the UI consumes, older pending snapshots are intentionally replaced.
 * Closing cancels pending delivery and forbids callbacks after [close] completes.
 * No image buffers or native OpenCV objects may be passed as snapshots.
 */
internal class LatestSnapshotDispatcher<T : Any>(
    private val executor: Executor,
    private val onSnapshot: (T) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var pending: T? = null
    private var scheduled = false
    private var closed = false

    fun publish(snapshot: T) {
        val shouldSchedule = synchronized(lock) {
            if (closed) {
                false
            } else {
                pending = snapshot
                if (scheduled) {
                    false
                } else {
                    scheduled = true
                    true
                }
            }
        }
        if (shouldSchedule) {
            try {
                executor.execute { deliverLatest() }
            } catch (_: RejectedExecutionException) {
                synchronized(lock) {
                    pending = null
                    scheduled = false
                }
            }
        }
    }

    private fun deliverLatest() {
        val snapshot = synchronized(lock) {
            scheduled = false
            if (closed) {
                pending = null
                null
            } else {
                pending.also { pending = null }
            }
        }
        if (snapshot != null) {
            synchronized(lock) {
                if (!closed) onSnapshot(snapshot)
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            pending = null
        }
    }
}
