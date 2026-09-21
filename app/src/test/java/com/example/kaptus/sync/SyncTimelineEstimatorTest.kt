package com.example.kaptus.sync

import com.example.kaptus.data.SyncAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncTimelineEstimatorTest {
    @Test
    fun estimatesOffsetBeforeRateAndWaitsForSixtySeconds() {
        val estimator = SyncTimelineEstimator()
        estimator.add(anchor(10, 42_000))
        estimator.add(anchor(40, 72_000))

        assertNull(estimator.estimatedRate())
        assertEquals(82_000L, estimator.movieTimeAt(seconds(50)))
    }

    @Test
    fun medianPairwiseRateResistsOneBadAnchor() {
        val estimator = SyncTimelineEstimator()
        estimator.add(anchor(0, 10_000))
        estimator.add(anchor(60, 70_000))
        estimator.add(anchor(90, 200_000))
        estimator.add(anchor(120, 130_000))

        assertEquals(1.0, estimator.estimatedRate()!!, 0.0001)
        assertEquals(140_000L, estimator.movieTimeAt(seconds(130)))
    }

    @Test
    fun constrainsFrameRateDifference() {
        val estimator = SyncTimelineEstimator()
        estimator.add(anchor(0, 0))
        estimator.add(anchor(60, 72_000))

        assertEquals(1.05, estimator.estimatedRate()!!, 0.0001)
    }

    private fun anchor(captureSeconds: Long, movieMs: Long) =
        SyncAnchor(seconds(captureSeconds), movieMs, 0.9f)

    private fun seconds(value: Long) = value * 1_000_000_000L
}
