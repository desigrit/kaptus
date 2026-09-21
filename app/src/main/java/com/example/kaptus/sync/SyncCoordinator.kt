package com.example.kaptus.sync

import com.example.kaptus.data.CaptionIndex
import com.example.kaptus.data.MatchResult
import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.SyncAnchor
import com.example.kaptus.speech.RecognitionCadence
import com.example.kaptus.speech.RecognitionActivity
import com.example.kaptus.speech.SpeechRecognitionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class SyncCoordinator(
    private val scope: CoroutineScope,
    private val engine: SpeechRecognitionEngine,
    private val matcher: SceneMatcher,
    private val clock: PlaybackClock,
    private val nowNanos: () -> Long = System::nanoTime
) {
    private val mutableState = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = mutableState.asStateFlow()

    private var index: CaptionIndex? = null
    private var collectionJob: Job? = null
    private var activityJob: Job? = null
    private var timeoutJob: Job? = null
    private var correctionJob: Job? = null
    private var comparisonStatusJob: Job? = null
    private var captions: List<SubtitleEntry> = emptyList()
    private var pendingLargeCorrection: SyncAnchor? = null
    private val timelineEstimator = SyncTimelineEstimator()
    private val transcriptAccumulator = TranscriptAccumulator()
    private var consecutiveMisses = 0
    private var dialogueStartedAtNanos: Long? = null
    private var playbackEnabled = true

    fun load(captions: List<SubtitleEntry>) {
        haltListening()
        this.captions = captions
        index = matcher.buildIndex(captions)
        resetTracking()
        mutableState.value = SyncState.Idle
        playbackEnabled = true
        scope.launch { engine.stop() }
    }

    fun setPlaybackEnabled(enabled: Boolean) {
        playbackEnabled = enabled
    }

    fun start() {
        if (index == null || collectionJob?.isActive == true) return
        if (mutableState.value is SyncState.Synchronized) return
        val reacquisitionReason = (mutableState.value as? SyncState.Reacquiring)?.reason
        collectionJob = scope.launch {
            try {
                engine.setCadence(RecognitionCadence.ACQUIRING)
                mutableState.value = reacquisitionReason?.let { SyncState.Reacquiring(it) }
                    ?: SyncState.Listening
                activityJob?.cancel()
                activityJob = scope.launch {
                    engine.activity.collect(::handleRecognitionActivity)
                }
                engine.start()
                mutableState.value = reacquisitionReason?.let { SyncState.Reacquiring(it) }
                    ?: SyncState.Acquiring()
                launchTimeout()
                engine.results.collect(::handleSegment)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                activityJob?.cancel()
                mutableState.value = SyncState.Error(SyncErrorKind.RECOGNIZER)
            }
        }
    }

    fun resync(reason: ReacquisitionReason = ReacquisitionReason.USER_REQUEST) {
        haltListening()
        resetTracking()
        clock.setRate(1.0)
        mutableState.value = SyncState.Reacquiring(reason)
        scope.launch {
            engine.stop()
            start()
        }
    }

    fun stop(backgrounded: Boolean = false) {
        val synchronized = mutableState.value as? SyncState.Synchronized
        haltListening()
        if (backgrounded && synchronized != null) {
            pendingLargeCorrection = null
            mutableState.value = synchronized.copy(
                checking = false,
                pendingLargeCorrection = false
            )
        }
        if (!backgrounded || synchronized == null) {
            resetTracking()
            clock.pause()
            mutableState.value = if (backgrounded) {
                SyncState.Reacquiring(ReacquisitionReason.BACKGROUNDED)
            } else SyncState.Idle
        }
        scope.launch {
            engine.stop()
        }
    }

    suspend fun close() {
        timeoutJob?.cancel()
        correctionJob?.cancel()
        comparisonStatusJob?.cancel()
        activityJob?.cancel()
        collectionJob?.cancel()
        engine.close()
    }

    private fun launchTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            var elapsed = 0
            while (isActive) {
                delay(1_000L)
                elapsed++
                val current = mutableState.value
                if (current is SyncState.Acquiring) {
                    mutableState.value = current.copy(secondsListening = elapsed)
                }
                val finding = mutableState.value is SyncState.Acquiring ||
                    mutableState.value is SyncState.Reacquiring
                if (!finding) return@launch
                val dialogueStart = dialogueStartedAtNanos ?: continue
                val dialogueElapsedSeconds = (nowNanos() - dialogueStart) / 1_000_000_000L
                if (dialogueElapsedSeconds >= SyncTuning.ACQUISITION_TIMEOUT_SECONDS) {
                    mutableState.value = SyncState.Error(SyncErrorKind.NO_MATCH)
                    comparisonStatusJob?.cancel()
                    activityJob?.cancel()
                    collectionJob?.cancel()
                    collectionJob = null
                    engine.stop()
                    return@launch
                }
            }
        }
    }

    private fun handleSegment(segment: RecognizedSegment) {
        if (correctionJob?.isActive == true) return
        val finding = mutableState.value is SyncState.Acquiring ||
            mutableState.value is SyncState.Reacquiring
        if (finding && segment.speechDetected && dialogueStartedAtNanos == null) {
            dialogueStartedAtNanos = nowNanos()
        }
        if (!segment.speechDetected || segment.words.isEmpty()) {
            showHearingDialogue()
            return
        }
        val captionIndex = index ?: return
        val currentlySynchronized = mutableState.value as? SyncState.Synchronized
        if (currentlySynchronized != null) {
            mutableState.value = currentlySynchronized.copy(checking = true)
        }
        val matchingSegment = if (currentlySynchronized == null) {
            transcriptAccumulator.add(segment)
        } else {
            segment
        }
        if (currentlySynchronized == null) showComparingCaptions()
        val expected = currentlySynchronized?.let { clock.positionMs.value }
        val result = matcher.match(matchingSegment, captionIndex, expected)
        if (!result.confident || result.anchor == null) {
            handleMiss(segment)
            return
        }
        consecutiveMisses = 0
        if (currentlySynchronized == null) confirmAcquisition(result) else confirmTracking(result)
    }

    private fun confirmAcquisition(result: MatchResult) {
        val anchor = result.anchor ?: return
        acceptAnchor(anchor, result.score, initial = true)
    }

    private fun confirmTracking(result: MatchResult) {
        val anchor = result.anchor ?: return
        val currentAtCapture = clock.positionMs.value -
            ((nowNanos() - anchor.captureTimeNanos) / 1_000_000L)
        val difference = anchor.movieTimeMs - currentAtCapture
        if (abs(difference) < SyncTuning.SMALL_CORRECTION_MS) {
            pendingLargeCorrection = null
            acceptAnchor(anchor, result.score, initial = false)
            return
        }
        val pending = pendingLargeCorrection
        if (pending != null && anchorsAgree(pending, anchor)) {
            pendingLargeCorrection = null
            scheduleLargeCorrection(anchor, result.score)
        } else {
            pendingLargeCorrection = anchor
            mutableState.value = SyncState.Synchronized(
                confidence = result.score,
                lastConfirmedRealtimeMs = nowNanos() / 1_000_000L,
                checking = true
            )
        }
    }

    private fun scheduleLargeCorrection(anchor: SyncAnchor, confidence: Float) {
        mutableState.value = SyncState.Synchronized(
            confidence = confidence,
            lastConfirmedRealtimeMs = nowNanos() / 1_000_000L,
            checking = true,
            pendingLargeCorrection = true
        )
        correctionJob = scope.launch {
            val position = clock.positionMs.value
            val boundary = captions.firstOrNull { position >= it.startTimeMs && position < it.endTimeMs }
                ?.endTimeMs
                ?: captions.firstOrNull { it.startTimeMs > position }?.startTimeMs
                ?: position
            val delayUntilBoundary = if (clock.isPlaying.value) {
                (boundary - position).coerceAtLeast(0L)
            } else {
                0L
            }
            delay(delayUntilBoundary)
            acceptAnchor(anchor, confidence, initial = false)
            correctionJob = null
        }
    }

    private fun acceptAnchor(anchor: SyncAnchor, confidence: Float, initial: Boolean) {
        timeoutJob?.cancel()
        comparisonStatusJob?.cancel()
        transcriptAccumulator.clear()
        val wasPlaying = clock.isPlaying.value
        timelineEstimator.add(anchor)
        updatePlaybackRate()
        val target = timelineEstimator.movieTimeAt(nowNanos()) ?: anchor.movieTimeMs
        if (initial) clock.seekTo(target) else clock.applyCorrection(target, confidence)
        if (playbackEnabled && (initial || wasPlaying)) clock.play()
        mutableState.value = SyncState.Synchronized(
            confidence = confidence,
            lastConfirmedRealtimeMs = nowNanos() / 1_000_000L
        )
        haltListening()
        scope.launch { engine.stop() }
    }

    private fun updatePlaybackRate() {
        timelineEstimator.estimatedRate()?.let(clock::setRate)
    }

    private fun handleMiss(segment: RecognizedSegment) {
        consecutiveMisses++
        val synchronized = mutableState.value as? SyncState.Synchronized
        if (synchronized != null && consecutiveMisses < 3) {
            mutableState.value = synchronized.copy(checking = false)
            return
        }
        if (consecutiveMisses >= 3 && synchronized != null) {
            engine.setCadence(RecognitionCadence.ACQUIRING)
            transcriptAccumulator.clear()
            transcriptAccumulator.add(segment)
            dialogueStartedAtNanos = nowNanos()
            mutableState.value = SyncState.Reacquiring(
                ReacquisitionReason.DIALOGUE_MISMATCH,
                AcquisitionPhase.COMPARING_CAPTIONS
            )
            launchTimeout()
        }
    }

    private fun anchorsAgree(first: SyncAnchor, second: SyncAnchor): Boolean {
        val elapsedMs = (second.captureTimeNanos - first.captureTimeNanos) / 1_000_000L
        val predicted = first.movieTimeMs + elapsedMs
        return abs(second.movieTimeMs - predicted) <= SyncTuning.MAXIMUM_ANCHOR_DISAGREEMENT_MS
    }

    private fun resetTracking() {
        timeoutJob?.cancel()
        correctionJob?.cancel()
        comparisonStatusJob?.cancel()
        pendingLargeCorrection = null
        timelineEstimator.clear()
        transcriptAccumulator.clear()
        dialogueStartedAtNanos = null
        consecutiveMisses = 0
    }

    private fun haltListening() {
        timeoutJob?.cancel()
        correctionJob?.cancel()
        comparisonStatusJob?.cancel()
        activityJob?.cancel()
        collectionJob?.cancel()
        activityJob = null
        collectionJob = null
    }

    private fun handleRecognitionActivity(activity: RecognitionActivity) {
        when (activity) {
            RecognitionActivity.IDLE -> Unit
            RecognitionActivity.LISTENING -> {
                if (comparisonStatusJob?.isActive != true) {
                    updateAcquisitionPhase(AcquisitionPhase.HEARING_DIALOGUE)
                }
            }
            RecognitionActivity.TRANSCRIBING -> {
                val finding = mutableState.value is SyncState.Acquiring ||
                    mutableState.value is SyncState.Reacquiring
                if (finding && dialogueStartedAtNanos == null) {
                    dialogueStartedAtNanos = nowNanos()
                }
                comparisonStatusJob?.cancel()
                updateAcquisitionPhase(AcquisitionPhase.TRANSCRIBING)
            }
        }
    }

    private fun showComparingCaptions() {
        comparisonStatusJob?.cancel()
        updateAcquisitionPhase(AcquisitionPhase.COMPARING_CAPTIONS)
        comparisonStatusJob = scope.launch {
            delay(COMPARISON_STATUS_DURATION_MS)
            updateAcquisitionPhase(AcquisitionPhase.HEARING_DIALOGUE)
        }
    }

    private fun showHearingDialogue() {
        comparisonStatusJob?.cancel()
        updateAcquisitionPhase(AcquisitionPhase.HEARING_DIALOGUE)
    }

    private fun updateAcquisitionPhase(phase: AcquisitionPhase) {
        mutableState.value = when (val current = mutableState.value) {
            is SyncState.Acquiring -> current.copy(phase = phase)
            is SyncState.Reacquiring -> current.copy(phase = phase)
            else -> current
        }
    }

    private companion object {
        const val COMPARISON_STATUS_DURATION_MS = 650L
    }

}
