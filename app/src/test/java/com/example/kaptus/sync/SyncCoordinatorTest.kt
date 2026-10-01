package com.example.kaptus.sync

import com.example.kaptus.data.CaptionIndex
import com.example.kaptus.data.MatchResult
import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.RecognizedWord
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.SyncAnchor
import com.example.kaptus.data.RecognitionTask
import com.example.kaptus.speech.RecognitionCadence
import com.example.kaptus.speech.RecognitionActivity
import com.example.kaptus.speech.SpeechRecognitionEngine
import com.example.kaptus.speech.SpeechRecognitionConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
    @Test fun aDelayedResyncCannotStartCaptureAfterBackgrounding() = runTest {
        val engine = FakeSpeechEngine()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(), FakeClock())
        coordinator.load(captions())
        runCurrent()
        engine.stopDelayMs = 5_000L
        coordinator.resync()
        runCurrent()
        coordinator.stop(backgrounded = true)
        advanceTimeBy(6_000L)
        runCurrent()
        assertFalse(engine.started)
        coordinator.close()
    }

    @Test fun manuallyPlayingAfterATimeoutContinuesAcrossBackgrounding() = runTest {
        val engine = FakeSpeechEngine()
        val clock = FakeClock()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(), clock)
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        advanceTimeBy(36_000L)
        runCurrent()
        assertTrue(coordinator.state.value is SyncState.Error)
        clock.play()
        coordinator.stop(backgrounded = true)
        runCurrent()
        assertTrue(clock.playing.value)
        assertFalse(engine.started)
        coordinator.close()
    }

    @Test fun directTranslationDoesNotRunAnUnneededSourcePass() = runTest {
        val engine = FakeSpeechEngine()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(), FakeClock())
        coordinator.configureLanguages("es", "en", "verified-model", pathPolicy = AutoSeekPathPolicy {
            language, path -> language == "es" && path == MatchingPath.DIRECT_TRANSLATION
        })
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()
        assertEquals(RecognitionTask.TRANSLATE, engine.configuration.task)
        assertFalse(engine.configuration.includeTranslation)
        coordinator.close()
    }

    @Test
    fun silenceEndsAtTheTotalAttemptDeadlineWithoutAdvancingTheClock() = runTest {
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

        assertFalse(engine.started)
        assertTrue(coordinator.state.value is SyncState.Error)
        assertFalse(clock.playing.value)
        coordinator.close()
    }

    @Test
    fun unsupportedAndMissingModelPairsNeverStartTheMicrophone() = runTest {
        val engine = FakeSpeechEngine()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(), FakeClock())
        coordinator.configureLanguages("es", "en")
        coordinator.load(captions())
        coordinator.start()
        coordinator.resync()
        runCurrent()
        assertFalse(engine.started)
        assertEquals(AutoSeekReason.MODEL_MISSING, coordinator.capability.reason)
        coordinator.configureLanguages("en", "fr")
        coordinator.start()
        runCurrent()
        assertFalse(engine.started)
        assertEquals(AutoSeekReason.UNSUPPORTED_PAIR, coordinator.capability.reason)
        coordinator.close()
    }

    @Test
    fun translatedPassesRequireNonOverlappingEvidenceAndRejectStaleSessions() = runTest {
        val engine = FakeSpeechEngine()
        val clock = FakeClock()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(
            result(anchor(6, 16_000)), result(anchor(9, 19_000)), result(anchor(12, 22_000))
        ), clock) { 15_000_000_000L }
        coordinator.configureLanguages("es", "en", "verified-model", pathPolicy = AutoSeekPathPolicy {
            language, path -> language == "es" && path == MatchingPath.DIRECT_TRANSLATION
        })
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()
        engine.emitSegment(0, 6, RecognitionTask.TRANSLATE, stale = true)
        engine.emitSegment(0, 6, RecognitionTask.TRANSLATE)
        runCurrent()
        assertTrue(clock.seeks.isEmpty())
        engine.emitSegment(3, 9, RecognitionTask.TRANSLATE)
        runCurrent()
        assertTrue("An overlapping window is not independent confirmation", clock.seeks.isEmpty())
        engine.emitSegment(6, 12, RecognitionTask.TRANSLATE)
        runCurrent()
        assertEquals(25_000L, clock.seeks.single())
        assertFalse(engine.started)
        coordinator.close()
    }

    @Test
    fun helperMappingIsAcceptedOnlyAfterIndependentEnglishCrossChecks() = runTest {
        val engine = FakeSpeechEngine()
        val mappings = mutableListOf<ValidatedCaptionMapping>()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(
            result(anchor(6, 36_000)), result(anchor(6, 16_000)),
            result(anchor(12, 42_000)), result(anchor(12, 22_000))
        ), FakeClock()) { 12_000_000_000L }
        coordinator.configureLanguages("ja", "en", "verified-model", captions(), "display-digest", "helper-digest",
            AutoSeekPathPolicy { language, path -> language == "ja" && path == MatchingPath.MATCHING_HELPER },
            onValidatedMapping = mappings::add)
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()
        engine.emitSegment(0, 6, RecognitionTask.TRANSCRIBE)
        engine.emitSegment(0, 6, RecognitionTask.TRANSLATE)
        runCurrent()
        assertTrue(mappings.isEmpty())
        engine.emitSegment(6, 12, RecognitionTask.TRANSCRIBE)
        engine.emitSegment(6, 12, RecognitionTask.TRANSLATE)
        runCurrent()
        assertEquals(-20_000L, mappings.single().displayOffsetMs)
        assertEquals(18_000L, mappings.single().displayTimeAt(38_000, "display-digest", "helper-digest"))
        assertEquals(null, mappings.single().displayTimeAt(80_000, "display-digest", "helper-digest"))
        assertEquals(null, mappings.single().displayTimeAt(38_000, "different-digest", "helper-digest"))
        coordinator.close()
    }

    @Test
    fun allRequestedLanguagesUseTranslationWhenNoHelperIsSaved() = runTest {
        for (language in AutoSeekCapabilities.targetSpokenLanguages) {
            val engine = FakeSpeechEngine()
            val coordinator = SyncCoordinator(this, engine, QueueMatcher(), FakeClock())
            coordinator.configureLanguages(language, "en", "verified-model")
            coordinator.load(captions())
            runCurrent()
            coordinator.start()
            runCurrent()
            assertTrue("$language should start with the shared model", engine.started)
            assertEquals(language, engine.configuration.sourceLanguage)
            assertEquals("verified-model", engine.configuration.modelPath)
            assertEquals(RecognitionTask.TRANSLATE, engine.configuration.task)
            assertFalse(engine.configuration.includeTranslation)
            coordinator.close()
        }
    }

    @Test
    fun aSavedHindiHelperUsesSourceRecognitionAndAnEnglishCrossCheckByDefault() = runTest {
        val engine = FakeSpeechEngine()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(), FakeClock())
        coordinator.configureLanguages("hi", "en", "verified-model", helperCaptions = captions())
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()
        assertEquals(RecognitionTask.TRANSCRIBE, engine.configuration.task)
        assertTrue(engine.configuration.includeTranslation)
        assertEquals("hi", engine.configuration.sourceLanguage)
        coordinator.close()
    }

    @Test
    fun anUnsuitableHelperFallsBackToIndependentEnglishTranslationEvidence() = runTest {
        val engine = FakeSpeechEngine()
        val clock = FakeClock()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(
            MatchResult.NoMatch, result(anchor(6, 16_000)),
            MatchResult.NoMatch, result(anchor(12, 22_000))
        ), clock) { 15_000_000_000L }
        coordinator.configureLanguages("hi", "en", "verified-model", helperCaptions = captions())
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()
        engine.emitSegment(0, 6, RecognitionTask.TRANSCRIBE)
        engine.emitSegment(0, 6, RecognitionTask.TRANSLATE)
        runCurrent()
        assertTrue(clock.seeks.isEmpty())
        engine.emitSegment(6, 12, RecognitionTask.TRANSCRIBE)
        engine.emitSegment(6, 12, RecognitionTask.TRANSLATE)
        runCurrent()
        assertEquals(25_000L, clock.seeks.single())
        assertTrue(coordinator.state.value is SyncState.Synchronized)
        assertFalse(engine.started)
        coordinator.close()
    }

    @Test
    fun englishKeepsTheBundledModelAndDoesNotTranscribeAnUnrelatedHelper() = runTest {
        val engine = FakeSpeechEngine()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(), FakeClock())
        coordinator.configureLanguages("en", "en", modelPath = "multilingual-model", helperCaptions = captions())
        coordinator.load(captions())
        coordinator.updateModelPath("multilingual-model")
        runCurrent()
        coordinator.start()
        runCurrent()
        assertEquals("en", engine.configuration.sourceLanguage)
        assertEquals(null, engine.configuration.modelPath)
        assertEquals(RecognitionTask.TRANSCRIBE, engine.configuration.task)
        assertFalse(engine.configuration.includeTranslation)
        coordinator.close()
    }

    @Test
    fun modelBecomingReadyEnablesAnOpenHindiTrackWithoutResettingClockOrStartingCapture() = runTest {
        val engine = FakeSpeechEngine()
        val clock = FakeClock()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(), clock)
        coordinator.configureLanguages("hi", "en")
        coordinator.load(captions())
        runCurrent()
        clock.seekTo(12_345)
        clock.play()
        coordinator.start()
        coordinator.resync()
        runCurrent()
        assertFalse(engine.started)
        assertEquals(AutoSeekMode.MODEL_REQUIRED, coordinator.capability.mode)

        coordinator.updateModelPath("verified-model")
        runCurrent()
        assertTrue(coordinator.capability.canListen)
        assertFalse("Model readiness alone must not start the microphone", engine.started)
        assertEquals(12_345L, clock.position.value)
        assertTrue(clock.playing.value)
        assertTrue(coordinator.state.value is SyncState.Idle)

        coordinator.resync()
        runCurrent()
        assertTrue(engine.started)
        assertEquals("hi", engine.configuration.sourceLanguage)
        assertEquals("verified-model", engine.configuration.modelPath)
        coordinator.close()
    }

    @Test
    fun removingTheModelDuringAcquisitionStopsCaptureAndPreservesManualPlayback() = runTest {
        val engine = FakeSpeechEngine()
        val clock = FakeClock()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(), clock)
        coordinator.configureLanguages("hi", "en", "verified-model")
        coordinator.load(captions())
        runCurrent()
        clock.seekTo(9_000)
        clock.play()
        coordinator.start()
        runCurrent()
        assertTrue(engine.started)

        coordinator.updateModelPath(null)
        runCurrent()
        assertFalse(engine.started)
        assertEquals(AutoSeekMode.MODEL_REQUIRED, coordinator.capability.mode)
        assertEquals(9_000L, clock.position.value)
        assertTrue(clock.playing.value)
        coordinator.resync()
        runCurrent()
        assertFalse(engine.started)
        coordinator.close()
    }

    @Test
    fun removingAndRestoringTheModelRetainsSynchronizedPlaybackUntilExplicitResync() = runTest {
        val engine = FakeSpeechEngine()
        val clock = FakeClock()
        val coordinator = SyncCoordinator(this, engine, QueueMatcher(
            result(anchor(6, 16_000)), result(anchor(12, 22_000))
        ), clock) { 15_000_000_000L }
        coordinator.configureLanguages("hi", "en", "verified-model")
        coordinator.load(captions())
        runCurrent()
        coordinator.start()
        runCurrent()
        engine.emitSegment(0, 6, RecognitionTask.TRANSLATE)
        runCurrent()
        engine.emitSegment(6, 12, RecognitionTask.TRANSLATE)
        runCurrent()
        assertTrue(coordinator.state.value is SyncState.Synchronized)
        assertEquals(25_000L, clock.position.value)

        coordinator.updateModelPath(null)
        coordinator.updateModelPath("restored-model")
        coordinator.start()
        runCurrent()
        assertTrue(coordinator.state.value is SyncState.Synchronized)
        assertFalse(engine.started)
        assertTrue(clock.playing.value)
        assertEquals(25_000L, clock.position.value)

        coordinator.resync()
        runCurrent()
        assertTrue(engine.started)
        assertEquals("restored-model", engine.configuration.modelPath)
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
        var configuration = SpeechRecognitionConfiguration()
        var stopDelayMs = 0L

        override suspend fun configure(configuration: SpeechRecognitionConfiguration) {
            this.configuration = configuration
        }

        override suspend fun prepare() = Unit

        override suspend fun start() {
            started = true
            mutableActivity.value = RecognitionActivity.LISTENING
        }

        override suspend fun stop() {
            delay(stopDelayMs)
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
            mutableResults.emit(RecognizedSegment(words, 0, 0, speechDetected = speechDetected,
                sessionId = configuration.sessionId, sourceLanguage = configuration.sourceLanguage))
        }

        suspend fun emitSegment(startSeconds: Long, endSeconds: Long, task: RecognitionTask, stale: Boolean = false) {
            val start = startSeconds * 1_000_000_000L
            val end = endSeconds * 1_000_000_000L
            mutableResults.emit(RecognizedSegment(listOf(RecognizedWord("dialogue", start, end, 0.9f)), start, end,
                sessionId = configuration.sessionId - if (stale) 1 else 0,
                sourceLanguage = configuration.sourceLanguage, task = task))
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
