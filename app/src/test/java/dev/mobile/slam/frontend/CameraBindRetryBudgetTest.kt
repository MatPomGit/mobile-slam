package dev.mobile.slam.frontend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraBindRetryBudgetTest {
    @Test
    fun retriesStopAtBudget() {
        val budget = CameraBindRetryBudget(3)
        repeat(3) { assertTrue(budget.acquire(disposed = false)) }
        assertFalse(budget.acquire(disposed = false))
    }

    @Test
    fun successRestoresBudgetForLaterRebind() {
        val budget = CameraBindRetryBudget(2)
        assertTrue(budget.acquire(disposed = false))
        assertTrue(budget.acquire(disposed = false))
        assertFalse(budget.acquire(disposed = false))
        budget.reset()
        assertTrue(budget.acquire(disposed = false))
    }

    @Test
    fun disposalBlocksRetriesWithoutSpendingBudget() {
        val budget = CameraBindRetryBudget(1)
        assertFalse(budget.acquire(disposed = true))
        assertTrue(budget.acquire(disposed = false))
        assertFalse(budget.acquire(disposed = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroBudgetIsInvalid() {
        CameraBindRetryBudget(0)
    }
}
