package dev.mobile.slam.geometry

import dev.mobile.slam.calib.CalibrationCrop
import dev.mobile.slam.calib.MeasuredCalibration

/**
 * Single verified entry point from a KLT-selected pair into two-view geometry.
 * It derives buffer dimensions/crop from the actual stored frame metadata,
 * never from an assumed preview size or demo intrinsics.
 */
internal object KltTwoViewBridge {
    fun initialize(
        pair: TwoViewFramePair,
        measuredProfile: MeasuredCalibration?,
        cameraId: String?,
        opticalPathVerified: Boolean,
        config: TwoViewConfig = TwoViewConfig(),
    ): TwoViewResult {
        val shape = pair.first.geometry
        return TwoViewInitializer.initialize(
            calibration = measuredProfile,
            opticalPathVerified = opticalPathVerified,
            cameraId = cameraId,
            fullWidth = shape.widthPx,
            fullHeight = shape.heightPx,
            crop = CalibrationCrop(
                shape.crop.left,
                shape.crop.top,
                shape.crop.right,
                shape.crop.bottom,
            ),
            matches = pair.matches,
            config = config,
        )
    }
}
