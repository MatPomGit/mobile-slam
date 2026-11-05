package dev.mobile.slam.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Android to verify versioned export, missing-data provenance and clock safety. */
@RunWith(AndroidJUnit4::class)
class HardwareJsonTest {
    @Test
    fun unavailableCameraClockCannotBeExportedAsSynchronized() {
        val report = HardwareReport(
            collectedAtUtc = "2026-11-05T12:00:00Z",
            sections = listOf(
                DiagnosticSection(
                    "Test",
                    listOf(
                        DiagnosticField("brak", null, source = EvidenceSource.UNAVAILABLE, reason = "nie zmierzono"),
                        DiagnosticField("ms", "20", "ms", EvidenceSource.OBSERVED),
                    ),
                ),
            ),
            cameraClock = CameraClockSource.UNKNOWN,
        )
        val json = JSONObject(HardwareJson.encode(report, "0.1.0"))
        assertEquals(HardwareJson.SCHEMA_VERSION, json.getInt("schema_version"))
        assertFalse(json.getBoolean("camera_imu_clock_comparable"))
        assertFalse(json.getBoolean("clock_alignment_verified"))
        assertEquals("UNKNOWN", json.getString("camera_timestamp_source"))
        val sections = json.getJSONArray("sections")
        val rows = sections.getJSONObject(0).getJSONArray("fields")
        assertTrue(rows.getJSONObject(0).isNull("value"))
        assertEquals("nie zmierzono", rows.getJSONObject(0).getString("reason"))
        assertEquals("observed", rows.getJSONObject(1).getString("provenance"))
        assertEquals("ms", rows.getJSONObject(1).getString("unit"))
        assertEquals("NOT_TESTED", json.getJSONArray("validation_cases").getJSONObject(0).getString("status"))
        assertFalse(json.has("serial"))
        assertFalse(json.has("android_id"))
        assertFalse(json.has("fingerprint"))
    }
}
