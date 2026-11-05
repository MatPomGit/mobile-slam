package dev.mobile.slam.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun defaultsAreCompatibleWithPreviousRelease() {
        assertEquals(Settings(20, 100L), SettingsCodec.decode(null, null, null).settings)
        assertEquals(Settings(), SettingsCodec.decode(1, null, null).settings)
    }

    @Test
    fun validValuesSurviveDecodingAndParsing() {
        val expected = Settings(84, 400L)
        assertEquals(expected, SettingsCodec.decode(1, 84, 400L).settings)
        assertEquals(expected, SettingsCodec.parse(" 84 ", "400"))
        assertEquals(Settings(1, 100L), SettingsCodec.parse("1", "100"))
        assertEquals(Settings(255, 1000L), SettingsCodec.parse("255", "1000"))
    }

    @Test
    fun invalidValuesAreRejectedRatherThanClamped() {
        for (fast in listOf("", "0", "256", "-1", "1.0", "text", "999999999999")) {
            assertNull(SettingsCodec.parse(fast, "100"))
        }
        for (imu in listOf("", "0", "99", "1001", "-1", "NaN", "1.5")) {
            assertNull(SettingsCodec.parse("20", imu))
        }
        assertThrows(IllegalArgumentException::class.java) { Settings(0, 100) }
        assertThrows(IllegalArgumentException::class.java) { Settings(20, 99) }
    }

    @Test
    fun malformedOrFutureSchemaFallsBackWithVisibleWarning() {
        for (snapshot in listOf(
            SettingsCodec.decode(2, 20, 100),
            SettingsCodec.decode(1, 0, 100),
            SettingsCodec.decode(1, 20, 5000),
        )) {
            assertEquals(Settings(), snapshot.settings)
            assertNotNull(snapshot.warning)
        }
    }
}
