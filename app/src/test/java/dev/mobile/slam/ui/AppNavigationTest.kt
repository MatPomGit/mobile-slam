package dev.mobile.slam.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun mainMenuContainsAllEightPlannedModesInStableOrder() {
        assertEquals(8, AppDestination.modes.size)
        assertEquals(AppDestination.LIVE, AppDestination.modes.first())
        assertEquals(AppDestination.ARCORE, AppDestination.modes.last())
        assertEquals(AppDestination.modes.size, AppDestination.modes.distinct().size)
    }

    @Test
    fun onlyImplementedDestinationsAreEnabled() {
        val enabled = AppDestination.entries.filter { it.isAvailable }.toSet()
        assertEquals(
            setOf(
                AppDestination.MENU,
                AppDestination.LIVE,
                AppDestination.SETTINGS,
                AppDestination.ABOUT,
            ),
            enabled,
        )
        AppDestination.modes.drop(1).forEach {
            assertFalse(it.isAvailable)
            assertTrue(it.releaseNote != null)
        }
    }

    @Test
    fun disabledRoutesCannotBeEnteredEvenByProgrammaticNavigation() {
        val current = AppDestination.SETTINGS
        AppDestination.modes.filterNot { it.isAvailable }.forEach { requested ->
            assertEquals(current, AppNavigation.navigate(current, requested))
        }
        assertEquals(AppDestination.LIVE, AppNavigation.navigate(current, AppDestination.LIVE))
        assertEquals(AppDestination.MENU, AppNavigation.navigate(current, AppDestination.MENU))
    }
}
