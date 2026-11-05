package dev.mobile.slam.frontend

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Device/emulator test of persisted Settings across repository instances. */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @Test
    fun settingsPersistAndResetThroughDataStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = SettingsRepository(context)
        try {
            first.save(Settings(fastThreshold = 62, imuHudIntervalMs = 350L))
            val reopened = SettingsRepository(context)
            assertEquals(Settings(62, 350L), reopened.snapshots.first().settings)
            reopened.reset()
            assertEquals(Settings(), SettingsRepository(context).snapshots.first().settings)
        } finally {
            first.reset()
        }
    }
}
