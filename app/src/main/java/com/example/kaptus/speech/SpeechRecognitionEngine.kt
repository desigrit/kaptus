package com.example.kaptus.speech

import com.example.kaptus.data.RecognizedSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class RecognitionCadence(val stepMs: Long) {
    ACQUIRING(3_000L)
}

enum class RecognitionActivity {
    IDLE,
    LISTENING,
    TRANSCRIBING
}

interface SpeechRecognitionEngine {
    val results: Flow<RecognizedSegment>
    val activity: StateFlow<RecognitionActivity>
    val isReady: Boolean
    fun setCadence(cadence: RecognitionCadence)
    suspend fun prepare()
    suspend fun start()
    suspend fun stop()
    suspend fun close()
}
