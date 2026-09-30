package com.example.kaptus.speech

import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.RecognitionTask
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

data class SpeechRecognitionConfiguration(
    val sourceLanguage: String = "en",
    val task: RecognitionTask = RecognitionTask.TRANSCRIBE,
    val modelPath: String? = null,
    val sessionId: Long = 0,
    val includeTranslation: Boolean = false
)

interface SpeechRecognitionEngine {
    val results: Flow<RecognizedSegment>
    val activity: StateFlow<RecognitionActivity>
    val isReady: Boolean
    fun setCadence(cadence: RecognitionCadence)
    suspend fun configure(configuration: SpeechRecognitionConfiguration) = Unit
    suspend fun prepare()
    suspend fun start()
    suspend fun stop()
    suspend fun close()
}
