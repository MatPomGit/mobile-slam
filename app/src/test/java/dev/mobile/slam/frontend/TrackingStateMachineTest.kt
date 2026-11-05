package dev.mobile.slam.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingStateMachineTest {
    @Test
    fun cannotClaimTrackingFromIndependentFastDetections() {
        val state = TrackingStateMachine()
        state.onDetections()
        assertEquals(TrackingPhase.DETECTING, state.phase)
        assertFalse(state.initializationSucceeded())
        assertEquals(TrackingPhase.DETECTING, state.phase)
        assertTrue(state.beginInitialization())
        assertEquals(TrackingPhase.INITIALIZING, state.phase)
        assertTrue(state.initializationSucceeded())
        assertEquals(TrackingPhase.TRACKING, state.phase)
    }

    @Test
    fun failedInitializationReturnsToDetectionsAndLossRequiresReinitialization() {
        val state = TrackingStateMachine()
        state.onDetections()
        assertTrue(state.beginInitialization())
        state.initializationFailed()
        assertEquals(TrackingPhase.DETECTING, state.phase)
        state.beginInitialization()
        state.initializationSucceeded()
        state.trackingLost()
        assertEquals(TrackingPhase.LOST, state.phase)
        assertFalse(state.initializationSucceeded())
        state.onDetections()
        assertEquals(TrackingPhase.DETECTING, state.phase)
        state.reset()
        assertEquals(TrackingPhase.WAITING_FOR_FRAME, state.phase)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTrackId() {
        FeatureTrack(0, ImageFeature(1f, 2f), 1, FeatureStatus.TRACKED)
    }
}
