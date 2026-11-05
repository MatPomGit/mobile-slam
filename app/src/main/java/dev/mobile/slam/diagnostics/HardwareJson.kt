package dev.mobile.slam.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/** Schema-v1 JSON export. Missing values are explicit and no unique device IDs are collected. */
internal object HardwareJson {
    const val SCHEMA_VERSION = 1

    fun encode(report: HardwareReport, appVersion: String): String {
        val sections = JSONArray()
        for (section in report.sections) {
            val fields = JSONArray()
            for (field in section.fields) {
                fields.put(
                    JSONObject()
                        .put("name", field.name)
                        .put("value", field.value ?: JSONObject.NULL)
                        .put("unit", field.unit ?: JSONObject.NULL)
                        .put("provenance", field.source.name.lowercase())
                        .put("reason", field.reason ?: JSONObject.NULL),
                )
            }
            sections.put(JSONObject().put("title", section.name).put("fields", fields))
        }
        val checklist = JSONArray()
        for (test in listOf(
            "Uprawnienie kamery: przyznanie/odmowa/cofnięcie",
            "Kadrowanie i FAST: orientacje 0/90/180/270°",
            "Przejścia do tła, wznowienie, zwolnienie CameraX i IMU",
            "Stabilność 5–10 minut oraz temperatura baterii",
            "Sprawdzenie próbkowania IMU i kolejności timestampów",
            "Weryfikacja synchronizacji Camera–IMU na urządzeniu",
        )) {
            checklist.put(
                JSONObject()
                    .put("test", test)
                    .put("status", "NOT_TESTED")
                    .put("method", "physical_device")
                    .put("notes", JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("captured_at_utc", report.collectedAtUtc)
            .put("app_version", appVersion)
            .put("camera_timestamp_source", report.cameraClock.name)
            .put("camera_imu_clock_comparable", ClockDomainSafety.cameraImuComparable(report.cameraClock))
            .put("clock_alignment_verified", false)
            .put("provenance_values", JSONArray(listOf("reported", "requested", "observed", "unavailable")))
            .put("privacy", "local_export_no_serial_android_id_fingerprint_location_or_raw_media")
            .put("sections", sections)
            .put("validation_cases", checklist)
            .toString(2)
    }
}
