package com.example.kaptus.sync

import com.example.kaptus.data.CaptionIndex
import com.example.kaptus.data.MatchResult
import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.RecognitionTask
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.SyncAnchor
import com.example.kaptus.speech.RecognitionCadence
import com.example.kaptus.speech.RecognitionActivity
import com.example.kaptus.speech.SpeechRecognitionEngine
import com.example.kaptus.speech.SpeechRecognitionConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private var restartJob: Job? = null
    private var stopJob: Job? = null
    private var captions: List<SubtitleEntry> = emptyList()
    private var pendingLargeCorrection: SyncAnchor? = null
    private val timelineEstimator = SyncTimelineEstimator()
    private val transcriptAccumulator = TranscriptAccumulator()
    private var consecutiveMisses = 0
    private var dialogueStartedAtNanos: Long? = null
    private var playbackEnabled = true
    private var spokenLanguage = "en"
    private var captionLanguage = "en"
    private var modelPath: String? = null
    private var helperIndex: CaptionIndex? = null
    private var displayHash = ""
    private var helperHash = ""
    private var pathPolicy: AutoSeekPathPolicy = SupportedAutoSeekPaths
    private var mappingCallback: ((ValidatedCaptionMapping) -> Unit)? = null
    private var mapping: ValidatedCaptionMapping? = null
    private var sessionId = 0L
    private val translationAccumulator = TranscriptAccumulator()
    private val translatedMatcher = TranslatedSceneMatcher(matcher)
    private val sourceMatches = LinkedHashMap<Pair<Long, Long>, MatchResult>()
    private var pendingForeignEvidence: ForeignEvidence? = null

    val capability: AutoSeekCapability
        get() = AutoSeekCapabilities.evaluate(spokenLanguage, captionLanguage,
            modelReady = if (spokenLanguage == "en") engine.isReady else modelPath != null,
            pathPolicy = pathPolicy)

    fun configureLanguages(
        spokenLanguage: String,
        captionLanguage: String,
        modelPath: String? = null,
        helperCaptions: List<SubtitleEntry> = emptyList(),
        displayHash: String = "",
        helperHash: String = "",
        pathPolicy: AutoSeekPathPolicy = SupportedAutoSeekPaths,
        onValidatedMapping: ((ValidatedCaptionMapping) -> Unit)? = null,
        savedMapping: ValidatedCaptionMapping? = null
    ) {
        haltListening()
        sessionId++
        this.spokenLanguage = spokenLanguage
        this.captionLanguage = captionLanguage
        this.modelPath = if (spokenLanguage == "en") null else modelPath?.takeIf { it.isNotBlank() }
        this.displayHash = displayHash
        this.helperHash = helperHash
        this.pathPolicy = pathPolicy
        mappingCallback = onValidatedMapping
        mapping = savedMapping?.takeIf { it.displayHash == displayHash && it.helperHash == helperHash }
        helperIndex = helperCaptions.takeIf { it.isNotEmpty() }?.let(matcher::buildIndex)
        resetTracking()
        stopEngineAsync()
    }

    /** Refreshes an installed model without opening captions again or starting capture. */
    fun updateModelPath(modelPath: String?) {
        if (spokenLanguage == "en") return
        val nextPath = modelPath?.takeIf { it.isNotBlank() }
        if (this.modelPath == nextPath) return
        this.modelPath = nextPath
        val finding = collectionJob?.isActive == true || restartJob?.isActive == true ||
            mutableState.value is SyncState.Listening || mutableState.value is SyncState.Acquiring ||
            mutableState.value is SyncState.Reacquiring
        if (finding) {
            haltListening()
            resetTracking()
            mutableState.value = SyncState.Idle
            stopEngineAsync()
        }
    }

    fun load(captions: List<SubtitleEntry>) {
        haltListening()
        sessionId++
        this.captions = captions
        index = matcher.buildIndex(captions)
        resetTracking()
        mutableState.value = SyncState.Idle
        playbackEnabled = true
        stopEngineAsync()
    }

    fun setPlaybackEnabled(enabled: Boolean) {
        playbackEnabled = enabled
    }

    fun start() {
        if (!capability.canListen) return
        if (index == null || collectionJob?.isActive == true) return
        if (mutableState.value is SyncState.Synchronized) return
        val reacquisitionReason = (mutableState.value as? SyncState.Reacquiring)?.reason
        val attemptSession = ++sessionId
        val pendingStop = stopJob
        collectionJob = scope.launch {
            try {
                pendingStop?.join()
                currentCoroutineContext().ensureActive()
                if (sessionId != attemptSession) return@launch
                engine.setCadence(RecognitionCadence.ACQUIRING)
                mutableState.value = reacquisitionReason?.let { SyncState.Reacquiring(it) }
                    ?: SyncState.Listening
                // The deadline includes native model initialization and all recognition passes.
                launchTimeout()
                engine.configure(SpeechRecognitionConfiguration(
                    sourceLanguage = spokenLanguage,
                    task = if (spokenLanguage != "en" && (helperIndex == null || MatchingPath.MATCHING_HELPER !in capability.paths))
                        RecognitionTask.TRANSLATE else RecognitionTask.TRANSCRIBE,
                    modelPath = modelPath,
                    sessionId = attemptSession,
                    includeTranslation = spokenLanguage != "en" && helperIndex != null && MatchingPath.MATCHING_HELPER in capability.paths
                ))
                activityJob?.cancel()
                activityJob = scope.launch {
                    engine.activity.collect(::handleRecognitionActivity)
                }
                engine.start()
                currentCoroutineContext().ensureActive()
                if (sessionId != attemptSession) return@launch
                mutableState.value = reacquisitionReason?.let { SyncState.Reacquiring(it) }
                    ?: SyncState.Acquiring()
                engine.results.collect(::handleSegment)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                activityJob?.cancel()
                if (sessionId == attemptSession) mutableState.value = SyncState.Error(SyncErrorKind.RECOGNIZER)
            }
        }
    }

    fun resync(reason: ReacquisitionReason = ReacquisitionReason.USER_REQUEST) {
        if (!capability.canListen) return
        haltListening()
        val intent = ++sessionId
        resetTracking()
        clock.setRate(1.0)
        mutableState.value = SyncState.Reacquiring(reason)
        restartJob = scope.launch {
            engine.stop()
            currentCoroutineContext().ensureActive()
            if (sessionId == intent) start()
        }
    }

    fun stop(backgrounded: Boolean = false) {
        val synchronized = mutableState.value as? SyncState.Synchronized
        haltListening()
        sessionId++
        if (backgrounded && synchronized != null) {
            pendingLargeCorrection = null
            mutableState.value = synchronized.copy(
                checking = false,
                pendingLargeCorrection = false
            )
        }
        if (!backgrounded || synchronized == null) {
            resetTracking()
            if (!backgrounded) clock.pause()
            mutableState.value = if (backgrounded) {
                mutableState.value as? SyncState.Error ?: SyncState.Error(SyncErrorKind.NO_MATCH)
            } else SyncState.Idle
        }
        stopEngineAsync()
    }

    suspend fun close() {
        sessionId++
        restartJob?.cancel()
        stopJob?.cancel()
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
                val finding = mutableState.value is SyncState.Listening ||
                    mutableState.value is SyncState.Acquiring ||
                    mutableState.value is SyncState.Reacquiring
                if (!finding) return@launch
                if (elapsed >= SyncTuning.ACQUISITION_TIMEOUT_SECONDS) {
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
        if (segment.sessionId != sessionId || segment.sourceLanguage != spokenLanguage || !capability.canListen) return
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
        if (spokenLanguage != "en") {
            handleForeignSegment(segment)
            return
        }
        if (segment.task != RecognitionTask.TRANSCRIBE) return
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

    private fun handleForeignSegment(segment: RecognizedSegment) {
        showComparingCaptions()
        val displayIndex = index ?: return
        val key = segment.capturedStartNanos to segment.capturedEndNanos
        if (segment.task == RecognitionTask.TRANSCRIBE) {
            transcriptAccumulator.add(segment)
            if (MatchingPath.MATCHING_HELPER in capability.paths) {
                helperIndex?.let {
                    val result = matcher.match(segment, it, null)
                    sourceMatches[key] = result.copy(confident = MatchingEvidenceProfile.source(spokenLanguage).accepts(result))
                }
                while (sourceMatches.size > 2) sourceMatches.remove(sourceMatches.keys.first())
            }
            return
        }
        translationAccumulator.add(segment)
        // Cross-check the entire display track, even when a helper or saved mapping suggests a place.
        val translated = translatedMatcher.match(segment, displayIndex)
        if (!translated.confident || translated.anchor == null) return
        val source = sourceMatches.remove(key)?.takeIf { it.confident && it.anchor != null }
        val useHelper = source != null && MatchingPath.MATCHING_HELPER in capability.paths
        if (!useHelper && MatchingPath.DIRECT_TRANSLATION !in capability.paths) return
        if (useHelper) {
            val helperTime = source?.anchor?.movieTimeMs ?: return
            val cachedDisplayTime = mapping?.displayTimeAt(helperTime, displayHash, helperHash)
            if (cachedDisplayTime != null && abs(cachedDisplayTime - translated.anchor.movieTimeMs) >
                SyncTuning.MAXIMUM_ANCHOR_DISAGREEMENT_MS) return
        }
        val evidence = ForeignEvidence(segment.capturedStartNanos, segment.capturedEndNanos,
            translated.anchor, if (useHelper) source?.anchor else null, translated.score)
        val pending = pendingForeignEvidence
        if (pending == null) {
            pendingForeignEvidence = evidence
            return
        }
        // Overlapping windows and two passes over the same audio are one piece of evidence.
        if (evidence.startNanos < pending.endNanos) return
        if (!anchorsAgree(pending.displayAnchor, evidence.displayAnchor) ||
            (pending.helperAnchor != null && evidence.helperAnchor != null &&
                !anchorsAgree(pending.helperAnchor, evidence.helperAnchor))) {
            pendingForeignEvidence = evidence
            return
        }
        if (pending.helperAnchor != null && evidence.helperAnchor != null) {
            val firstOffset = pending.displayAnchor.movieTimeMs - pending.helperAnchor.movieTimeMs
            val secondOffset = evidence.displayAnchor.movieTimeMs - evidence.helperAnchor.movieTimeMs
            if (abs(firstOffset - secondOffset) > SyncTuning.MAXIMUM_ANCHOR_DISAGREEMENT_MS) {
                pendingForeignEvidence = evidence
                return
            }
            if (displayHash.isNotBlank() && helperHash.isNotBlank()) {
                val validated = ValidatedCaptionMapping(displayHash, helperHash,
                    (pending.helperAnchor.movieTimeMs - (pending.endNanos - pending.startNanos) / 1_000_000L).coerceAtLeast(0L),
                    evidence.helperAnchor.movieTimeMs,
                    (firstOffset + secondOffset) / 2L, minOf(pending.confidence, evidence.confidence))
                mapping = validated
                mappingCallback?.invoke(validated)
            }
        }
        acceptAnchor(evidence.displayAnchor, minOf(pending.confidence, evidence.confidence), initial = true)
    }

    private data class ForeignEvidence(
        val startNanos: Long,
        val endNanos: Long,
        val displayAnchor: SyncAnchor,
        val helperAnchor: SyncAnchor?,
        val confidence: Float
    )

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
        translationAccumulator.clear()
        sourceMatches.clear()
        pendingForeignEvidence = null
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
        translationAccumulator.clear()
        sourceMatches.clear()
        pendingForeignEvidence = null
        dialogueStartedAtNanos = null
        consecutiveMisses = 0
    }

    private fun haltListening() {
        restartJob?.cancel()
        sessionId++
        timeoutJob?.cancel()
        correctionJob?.cancel()
        comparisonStatusJob?.cancel()
        activityJob?.cancel()
        collectionJob?.cancel()
        activityJob = null
        collectionJob = null
    }

    private fun stopEngineAsync() {
        stopJob?.cancel()
        stopJob = scope.launch { engine.stop() }
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
