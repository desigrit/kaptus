package com.example.kaptus.sync

import com.example.kaptus.data.CaptionIndex
import com.example.kaptus.data.MatchResult
import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.RecognizedWord
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.SyncAnchor
import com.example.kaptus.speech.RecognitionCadence
import com.example.kaptus.speech.RecognitionActivity
import com.example.kaptus.speech.SpeechRecognitionEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {
    @Test
    fun silenceKeepsListeningWithoutAdvancingOrFailingTheClock() = runTest {
        val engine = FakeSpeechEngine()
        val clock = FakeClock()
        val coordinator = SyncCoordinator(
            this,
            engine,
            QueueMatcher(),
            clock
        ) { testScheduler.currentTime * 1_000_000L }
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()

        engine.emit(speechDetected = false)
        advanceTimeBy(65_000L)
        runCurrent()

        assertTrue(engine.started)
        assertTrue(coordinator.state.value is SyncState.Acquiring)
        assertFalse(clock.playing.value)
        coordinator.close()
    }

    @Test
    fun oneUniqueWindowCanSynchronizeWithoutASecondConfirmation() = runTest {
        var nowNanos = 10_000_000_000L
        val engine = FakeSpeechEngine()
        val clock = FakeClock()
        val coordinator = SyncCoordinator(
            this,
            engine,
            QueueMatcher(result(anchor(captureSeconds = 4, movieMs = 14_000))),
            clock
        ) { nowNanos }
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()

        engine.emit()
        runCurrent()

        assertEquals(20_000L, clock.seeks.single())
        assertTrue(coordinator.state.value is SyncState.Synchronized)
        assertFalse(engine.started)
        coordinator.close()
    }

    @Test
    fun reportsTranscriptionAndCaptionComparisonStages() = runTest {
        val engine = FakeSpeechEngine()
        val coordinator = SyncCoordinator(
            this,
            engine,
            QueueMatcher(MatchResult.NoMatch),
            FakeClock()
        ) { testScheduler.currentTime * 1_000_000L }
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()

        engine.setActivity(RecognitionActivity.TRANSCRIBING)
        runCurrent()
        assertEquals(
            AcquisitionPhase.TRANSCRIBING,
            (coordinator.state.value as SyncState.Acquiring).phase
        )

        engine.emit()
        runCurrent()
        assertEquals(
            AcquisitionPhase.COMPARING_CAPTIONS,
            (coordinator.state.value as SyncState.Acquiring).phase
        )

        advanceTimeBy(651L)
        runCurrent()
        assertEquals(
            AcquisitionPhase.HEARING_DIALOGUE,
            (coordinator.state.value as SyncState.Acquiring).phase
        )
        coordinator.close()
    }

    @Test
    fun keepsASynchronizedClockRunningAcrossBackgroundAndResume() = runTest {
        var nowNanos = 8_000_000_000L
        val engine = FakeSpeechEngine()
        val clock = FakeClock()
        val matcher = QueueMatcher(
            MatchResult.NoMatch,
            result(anchor(captureSeconds = 4, movieMs = 14_000))
        )
        val coordinator = SyncCoordinator(this, engine, matcher, clock) { nowNanos }
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()

        engine.emit()
        runCurrent()
        assertTrue(clock.seeks.isEmpty())

        nowNanos = 12_000_000_000L
        engine.emit()
        runCurrent()

        assertEquals(22_000L, clock.seeks.single())
        assertTrue(clock.playing.value)
        assertTrue(coordinator.state.value is SyncState.Synchronized)

        coordinator.stop(backgrounded = true)
        runCurrent()

        assertFalse(engine.started)
        assertTrue(clock.playing.value)
        assertTrue(coordinator.state.value is SyncState.Synchronized)

        coordinator.start()
        runCurrent()

        assertFalse(engine.started)
        assertEquals(RecognitionCadence.ACQUIRING, engine.currentCadence)
        assertTrue(coordinator.state.value is SyncState.Synchronized)
        coordinator.close()
    }

    private fun result(anchor: SyncAnchor) = MatchResult(
        confident = true,
        score = 0.9f,
        runnerUpScore = 0.2f,
        contentWordMatches = 8,
        distinctCueMatches = 2,
        timingDeviationMs = 100,
        anchor = anchor
    )

    private fun anchor(captureSeconds: Long, movieMs: Long) =
        SyncAnchor(captureSeconds * 1_000_000_000L, movieMs, 0.9f)

    private fun captions(durationMs: Long = 40_000L) = listOf(
        SubtitleEntry(1, 0, durationMs / 2, "one two three four five six"),
        SubtitleEntry(2, durationMs / 2, durationMs, "seven eight nine ten eleven twelve")
    )

    private class QueueMatcher(vararg results: MatchResult) : SceneMatcher {
        private val queued = ArrayDeque(results.toList())

        override fun buildIndex(captions: List<SubtitleEntry>) = CaptionIndex(emptyList(), emptyMap())

        override fun match(
            segment: RecognizedSegment,
            index: CaptionIndex,
            expectedTimeMs: Long?
        ): MatchResult = queued.removeFirst()
    }

    private class FakeSpeechEngine : SpeechRecognitionEngine {
        private val mutableResults = MutableSharedFlow<RecognizedSegment>(extraBufferCapacity = 4)
        override val results: Flow<RecognizedSegment> = mutableResults
        private val mutableActivity = MutableStateFlow(RecognitionActivity.IDLE)
        override val activity: StateFlow<RecognitionActivity> = mutableActivity
        override val isReady = true
        var started = false
        var currentCadence = RecognitionCadence.ACQUIRING

        override suspend fun prepare() = Unit

        override suspend fun start() {
            started = true
            mutableActivity.value = RecognitionActivity.LISTENING
        }

        override suspend fun stop() {
            started = false
            mutableActivity.value = RecognitionActivity.IDLE
        }

        override suspend fun close() {
            started = false
            mutableActivity.value = RecognitionActivity.IDLE
        }

        override fun setCadence(cadence: RecognitionCadence) {
            currentCadence = cadence
        }

        fun setActivity(activity: RecognitionActivity) {
            mutableActivity.value = activity
        }

        suspend fun emit(speechDetected: Boolean = true) {
            val words = if (speechDetected) {
                listOf(RecognizedWord("dialogue", 0, 100_000_000L, 0.9f))
            } else {
                emptyList()
            }
            mutableResults.emit(RecognizedSegment(words, 0, 0, speechDetected = speechDetected))
        }
    }

    private class FakeClock : PlaybackClock {
        val position = MutableStateFlow(0L)
        val playing = MutableStateFlow(false)
        val seeks = mutableListOf<Long>()
        var lastRate = 1.0
        override val positionMs: StateFlow<Long> = position
        override val isPlaying: StateFlow<Boolean> = playing

        override fun setDuration(durationMs: Long) = Unit
        override fun play() { playing.value = true }
        override fun pause() { playing.value = false }
        override fun seekTo(positionMs: Long) {
            position.value = positionMs
            seeks += positionMs
        }
        override fun applyCorrection(positionMs: Long, confidence: Float) {
            position.value = positionMs
        }
        override fun setRate(rate: Double) { lastRate = rate }
        override fun close() = Unit
    }
}
