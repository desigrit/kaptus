package com.example.kaptus.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class MonotonicPlaybackClock(
    private val scope: CoroutineScope,
    private val nowNanos: () -> Long = System::nanoTime
) : PlaybackClock {
    private val mutablePosition = MutableStateFlow(0L)
    private val mutablePlaying = MutableStateFlow(false)
    override val positionMs: StateFlow<Long> = mutablePosition.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = mutablePlaying.asStateFlow()

    private var durationMs = 0L
    private var anchorPositionMs = 0L
    private var anchorRealtimeNanos = nowNanos()
    private var playbackRate = 1.0
    private var ticker: Job? = null
    private var slewAmountMs = 0.0
    private var slewStartNanos = 0L

    @Synchronized
    override fun setDuration(durationMs: Long) {
        this.durationMs = durationMs.coerceAtLeast(0L)
        seekTo(mutablePosition.value)
    }

    @Synchronized
    override fun play() {
        if (mutablePlaying.value || durationMs <= 0L) return
        anchorPositionMs = mutablePosition.value
        anchorRealtimeNanos = nowNanos()
        mutablePlaying.value = true
        ticker = scope.launch {
            while (isActive && mutablePlaying.value) {
                updateFromAnchor()
                if (mutablePosition.value >= durationMs) {
                    pause()
                    break
                }
                delay(33L)
            }
        }
    }

    @Synchronized
    override fun pause() {
        if (mutablePlaying.value) updateFromAnchor()
        mutablePlaying.value = false
        ticker?.cancel()
        ticker = null
        anchorPositionMs = mutablePosition.value
        anchorRealtimeNanos = nowNanos()
        clearSlew()
    }

    @Synchronized
    override fun seekTo(positionMs: Long) {
        val bounded = positionMs.coerceIn(0L, durationMs)
        mutablePosition.value = bounded
        anchorPositionMs = bounded
        anchorRealtimeNanos = nowNanos()
        clearSlew()
    }

    @Synchronized
    override fun applyCorrection(positionMs: Long, confidence: Float) {
        updateFromAnchor()
        val target = positionMs.coerceIn(0L, durationMs)
        val difference = target - mutablePosition.value
        if (abs(difference) >= SyncTuning.SMALL_CORRECTION_MS || confidence < SyncTuning.INITIAL_MATCH_SCORE) {
            if (confidence >= SyncTuning.INITIAL_MATCH_SCORE) seekTo(target)
            return
        }
        if (!mutablePlaying.value) {
            seekTo(target)
            return
        }
        anchorPositionMs = mutablePosition.value
        anchorRealtimeNanos = nowNanos()
        slewAmountMs = difference.toDouble()
        slewStartNanos = anchorRealtimeNanos
    }

    @Synchronized
    override fun setRate(rate: Double) {
        updateFromAnchor()
        playbackRate = rate.coerceIn(
            SyncTuning.MINIMUM_PLAYBACK_RATE,
            SyncTuning.MAXIMUM_PLAYBACK_RATE
        )
        anchorPositionMs = mutablePosition.value
        anchorRealtimeNanos = nowNanos()
    }

    @Synchronized
    private fun updateFromAnchor() {
        if (!mutablePlaying.value) return
        val now = nowNanos()
        val elapsedMs = (now - anchorRealtimeNanos) / 1_000_000.0 * playbackRate
        val slewFraction = if (slewAmountMs == 0.0) 0.0 else {
            ((now - slewStartNanos).toDouble() / SLEW_DURATION_NANOS).coerceIn(0.0, 1.0)
        }
        val position = (anchorPositionMs + elapsedMs + slewAmountMs * slewFraction)
            .toLong()
            .coerceIn(0L, durationMs)
        mutablePosition.value = position
        if (slewAmountMs != 0.0 && slewFraction >= 1.0) {
            anchorPositionMs = position
            anchorRealtimeNanos = now
            slewAmountMs = 0.0
            slewStartNanos = 0L
        }
    }

    override fun close() {
        pause()
    }

    private fun clearSlew() {
        slewAmountMs = 0.0
        slewStartNanos = 0L
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        val SLEW_DURATION_NANOS =
            SyncTuning.CORRECTION_SLEW_DURATION_MS * NANOS_PER_MILLISECOND
    }
}
