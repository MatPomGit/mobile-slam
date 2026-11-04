package dev.mobile.slam.viz

import dev.mobile.slam.sensors.FeaturePoint
import dev.mobile.slam.sensors.FrameCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewCoordinatesTest {
    private val crop = FrameCrop(left = 10, top = 20, right = 110, bottom = 220)

    @Test
    fun projectsCropCornersForAllQuarterTurns() {
        val origin = FeaturePoint(10f, 20f)
        val expected =
            mapOf(
                0 to FeaturePoint(0f, 0f),
                90 to FeaturePoint(300f, 0f),
                180 to FeaturePoint(300f, 400f),
                    270 to FeaturePoint(0f, 400f),
            )
        expected.forEach { (rotation, target) ->
            val actual = PreviewCoordinates.project(origin, crop, rotation, 300, 400)!!
            assertEquals("rotation $rotation x", target.x, actual.x, 0.001f)
            assertEquals("rotation $rotation y", target.y, actual.y, 0.001f)
        }
    }

    @Test
    fun projectsCropCentreIndependentOfRotation() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val actual = PreviewCoordinates.project(FeaturePoint(60f, 120f), crop, rotation, 300, 400)!!
            assertEquals(150f, actual.x, 0.001f)
            assertEquals(200f, actual.y, 0.001f)
        }
    }

    @Test
    fun rejectsPointsOutsideVisibleCrop() {
        assertNull(PreviewCoordinates.project(FeaturePoint(9f, 20f), crop, 0, 300, 400))
        assertNull(PreviewCoordinates.project(FeaturePoint(10f, 221f), crop, 90, 300, 400))
    }

    @Test
    fun rejectsInvalidDimensionsAndRotations() {
        assertNull(PreviewCoordinates.project(FeaturePoint(20f, 30f), crop, 45, 300, 400))
        assertNull(PreviewCoordinates.project(FeaturePoint(20f, 30f), crop, 0, 0, 400))
        assertNull(
            PreviewCoordinates.project(
                FeaturePoint(20f, 30f),
                FrameCrop(0, 0, 0, 100),
                0,
                300,
                400,
            ),
        )
    }
}
