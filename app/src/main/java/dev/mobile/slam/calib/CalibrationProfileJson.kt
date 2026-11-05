package dev.mobile.slam.calib

import org.json.JSONArray
import org.json.JSONObject

/** Portable schema for one measured camera profile; no photos or device identifier. */
internal object CalibrationProfileJson {
    private const val VERSION = 1

    fun encode(profile: MeasuredCalibration): String {
        return JSONObject().apply {
            put("schema_version", VERSION)
            put("source", "measured_chessboard_or_verified_import")
            put("camera_id", profile.cameraId)
            put("width_px", profile.referenceWidth)
            put("height_px", profile.referenceHeight)
            put(
                "reference_crop",
                JSONArray(
                    listOf(
                        profile.referenceCrop.left,
                        profile.referenceCrop.top,
                        profile.referenceCrop.right,
                        profile.referenceCrop.bottom,
                    ),
                ),
            )
            put("fx", profile.fx)
            put("fy", profile.fy)
            put("cx", profile.cx)
            put("cy", profile.cy)
            put("distortion_model", profile.distortionModel.name)
            put("distortion_k1_k2_p1_p2_k3", JSONArray(profile.distortion))
            put("rms_reprojection_px", profile.rmsReprojectionPx)
            put("provenance", profile.provenance.name)
            if (profile.focusDistanceDiopters != null) {
                put("focus_distance_diopters", profile.focusDistanceDiopters)
            }
        }.toString(2)
    }

    /** Reject malformed, incompatible or unversioned profiles; never fall back to demo K. */
    fun decode(json: String): MeasuredCalibration {
        require(json.length <= 65536) { "Calibration file exceeds size limit" }
        val root = JSONObject(json)
        require(root.getInt("schema_version") == VERSION)
        val crop = root.getJSONArray("reference_crop")
        require(crop.length() == 4)
        val distortion = root.getJSONArray("distortion_k1_k2_p1_p2_k3")
        require(distortion.length() == 5)
        val provenance = CalibrationProvenance.valueOf(root.getString("provenance"))
        val model = DistortionModel.valueOf(root.getString("distortion_model"))
        return MeasuredCalibration(
            cameraId = root.getString("camera_id"),
            referenceWidth = root.getInt("width_px"),
            referenceHeight = root.getInt("height_px"),
            referenceCrop = CalibrationCrop(crop.getInt(0), crop.getInt(1), crop.getInt(2), crop.getInt(3)),
            fx = root.getDouble("fx"),
            fy = root.getDouble("fy"),
            cx = root.getDouble("cx"),
            cy = root.getDouble("cy"),
            distortion = (0 until distortion.length()).map { distortion.getDouble(it) },
            rmsReprojectionPx = root.getDouble("rms_reprojection_px"),
            provenance = provenance,
            distortionModel = model,
            focusDistanceDiopters = if (root.has("focus_distance_diopters")) {
                root.getDouble("focus_distance_diopters")
            } else {
                null
            },
        )
    }
}
