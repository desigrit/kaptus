package com.example.kaptus.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MonotonicPlaybackClockTest {
    @Test
    fun remainsAnchoredAcrossTwoHours() = runTest {
        var nowNanos = 0L
        val clock = MonotonicPlaybackClock(this) { nowNanos }
        clock.setDuration(8 * 60 * 60 * 1_000L)
        clock.play()

        nowNanos = 2 * 60 * 60 * 1_000_000_000L
        runCurrent()

        assertTrue(clock.positionMs.value in 7_199_950L..7_200_050L)
        clock.close()
    }

    @Test
    fun seekPersistsWhilePlaying() = runTest {
        val clock = MonotonicPlaybackClock(this) { testScheduler.currentTime * 1_000_000L }
        clock.setDuration(180_000L)
        clock.play()
        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue(clock.positionMs.value in 9_950L..10_050L)

        clock.seekTo(50_000L)
        advanceTimeBy(5_000L)
        runCurrent()

        assertTrue(clock.positionMs.value in 54_950L..55_050L)
        clock.close()
    }

    @Test
    fun smallCorrectionIsAddedWithoutFreezingPlayback() = runTest {
        val clock = MonotonicPlaybackClock(this) { testScheduler.currentTime * 1_000_000L }
        clock.setDuration(180_000L)
        clock.seekTo(30_000L)
        clock.play()
        clock.applyCorrection(30_500L, confidence = 0.9f)

        advanceTimeBy(2_100L)
        runCurrent()

        assertTrue(
            "Expected playback plus the correction, got ${clock.positionMs.value}",
            clock.positionMs.value in 32_500L..32_700L
        )
        clock.close()
    }

    @Test
    fun playbackRateIsConstrainedToCutDifferenceRange() = runTest {
        val clock = MonotonicPlaybackClock(this) { testScheduler.currentTime * 1_000_000L }
        clock.setDuration(180_000L)
        clock.setRate(2.0)
        clock.play()

        advanceTimeBy(60_000L)
        runCurrent()

        assertTrue(clock.positionMs.value in 62_950L..63_050L)
        clock.close()
    }
}
