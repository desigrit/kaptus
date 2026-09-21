package com.example.kaptus.sync

import kotlinx.coroutines.flow.StateFlow

interface PlaybackClock {
    val positionMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    fun setDuration(durationMs: Long)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun applyCorrection(positionMs: Long, confidence: Float)
    fun setRate(rate: Double)
    fun close()
}
