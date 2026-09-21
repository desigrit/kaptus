package com.example.kaptus.speech

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.kaptus.R
import com.example.kaptus.data.RecognizedSegment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max

class WhisperSpeechRecognitionEngine(
    context: Context,
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default
) : SpeechRecognitionEngine {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + inferenceDispatcher)
    private val lifecycleMutex = Mutex()
    private val audioWindows = Channel<AudioWindow>(Channel.CONFLATED)
    private val mutableResults = MutableSharedFlow<RecognizedSegment>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val results: Flow<RecognizedSegment> = mutableResults.asSharedFlow()
    private val mutableActivity = MutableStateFlow(RecognitionActivity.IDLE)
    override val activity: StateFlow<RecognitionActivity> = mutableActivity.asStateFlow()

    @Volatile private var cadence = RecognitionCadence.ACQUIRING
    private var whisperPointer = 0L
    private var vadPointer = 0L
    private var captureJob: Job? = null
    private var inferenceJob: Job? = null

    override val isReady: Boolean
        get() = runCatching {
            applicationContext.assets.open(MODEL_PATH).close()
            true
        }.getOrDefault(false)

    override fun setCadence(cadence: RecognitionCadence) {
        this.cadence = cadence
    }

    override suspend fun prepare() = lifecycleMutex.withLock {
        ensureNativeContexts()
    }

    override suspend fun start() = lifecycleMutex.withLock {
        if (captureJob?.isActive == true) return
        check(ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            applicationContext.getString(R.string.error_microphone_required)
        }
        ensureNativeContexts()
        while (audioWindows.tryReceive().isSuccess) Unit
        mutableActivity.value = RecognitionActivity.LISTENING
        inferenceJob = scope.launch { runInferenceLoop() }
        captureJob = scope.launch(Dispatchers.IO) { runCaptureLoop() }
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        captureJob?.cancelAndJoin()
        inferenceJob?.cancelAndJoin()
        captureJob = null
        inferenceJob = null
        while (audioWindows.tryReceive().isSuccess) Unit
        mutableActivity.value = RecognitionActivity.IDLE
    }

    override suspend fun close() = lifecycleMutex.withLock {
        captureJob?.cancelAndJoin()
        inferenceJob?.cancelAndJoin()
        captureJob = null
        inferenceJob = null
        mutableActivity.value = RecognitionActivity.IDLE
        withContext(inferenceDispatcher) {
            if (whisperPointer != 0L) WhisperNative.freeWhisper(whisperPointer)
            if (vadPointer != 0L) WhisperNative.freeVad(vadPointer)
            whisperPointer = 0L
            vadPointer = 0L
        }
    }

    private suspend fun ensureNativeContexts() = withContext(inferenceDispatcher) {
        if (whisperPointer == 0L) {
            whisperPointer = WhisperNative.initWhisper(applicationContext.assets, MODEL_PATH)
            check(whisperPointer != 0L) {
                applicationContext.getString(R.string.error_whisper_model_load)
            }
        }
        if (vadPointer == 0L) {
            vadPointer = WhisperNative.initVad(applicationContext.assets, VAD_PATH)
            check(vadPointer != 0L) {
                applicationContext.getString(R.string.error_vad_model_load)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runCaptureLoop() {
        val minimumBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minimumBytes > 0) {
            applicationContext.getString(R.string.error_microphone_16khz)
        }
        val recorder = createAudioRecord(max(minimumBytes, READ_SAMPLES * 2))
        val ring = FloatArray(WINDOW_SAMPLES)
        val readBuffer = ShortArray(READ_SAMPLES)
        var writeIndex = 0
        var bufferedSamples = 0
        var samplesSinceWindow = 0
        try {
            recorder.startRecording()
            while (scope.isActive && captureJob?.isActive == true) {
                val read = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue
                for (index in 0 until read) {
                    ring[writeIndex] = readBuffer[index] / 32768f
                    writeIndex = (writeIndex + 1) % ring.size
                }
                bufferedSamples = (bufferedSamples + read).coerceAtMost(ring.size)
                samplesSinceWindow += read
                val requiredStep = (cadence.stepMs * SAMPLE_RATE / 1_000L).toInt()
                if (bufferedSamples == ring.size && samplesSinceWindow >= requiredStep) {
                    samplesSinceWindow = 0
                    val snapshot = FloatArray(ring.size)
                    val tail = ring.size - writeIndex
                    ring.copyInto(snapshot, 0, writeIndex, ring.size)
                    ring.copyInto(snapshot, tail, 0, writeIndex)
                    val endNanos = System.nanoTime()
                    audioWindows.trySend(
                        AudioWindow(
                            samples = snapshot,
                            startNanos = endNanos - WINDOW_MS * 1_000_000L,
                            endNanos = endNanos
                        )
                    )
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(bufferBytes: Int): AudioRecord {
        fun build(source: Int) = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes * 2)
            .build()

        val unprocessed = runCatching { build(MediaRecorder.AudioSource.UNPROCESSED) }.getOrNull()
        if (unprocessed?.state == AudioRecord.STATE_INITIALIZED) return unprocessed
        unprocessed?.release()
        return build(MediaRecorder.AudioSource.VOICE_RECOGNITION).also {
            check(it.state == AudioRecord.STATE_INITIALIZED) {
                applicationContext.getString(R.string.error_microphone_initialize)
            }
        }
    }

    private suspend fun runInferenceLoop() {
        for (window in audioWindows) {
            try {
                val segment = transcribe(window)
                mutableResults.emit(segment)
            } finally {
                if (captureJob?.isActive == true) {
                    mutableActivity.value = RecognitionActivity.LISTENING
                }
            }
        }
    }

    private fun transcribe(window: AudioWindow): RecognizedSegment {
        if (!WhisperNative.hasSpeech(vadPointer, window.samples)) {
            return RecognizedSegment(
                emptyList(),
                window.startNanos,
                window.endNanos,
                speechDetected = false
            )
        }
        mutableActivity.value = RecognitionActivity.TRANSCRIBING
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val startedAtNanos = System.nanoTime()
        val result = WhisperNative.transcribe(whisperPointer, window.samples, threads)
        val durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
        Log.i(LOG_TAG, "Whisper decoded ${WINDOW_MS}ms of audio in ${durationMs}ms")
        if (result != 0) {
            return RecognizedSegment(
                emptyList(),
                window.startNanos,
                window.endNanos,
                speechDetected = true
            )
        }
        val words = buildList {
            for (segmentIndex in 0 until WhisperNative.segmentCount(whisperPointer)) {
                val tokens = buildList {
                    for (tokenIndex in 0 until WhisperNative.tokenCount(whisperPointer, segmentIndex)) {
                        val text = WhisperNative.tokenText(whisperPointer, segmentIndex, tokenIndex)
                        val startCs = WhisperNative.tokenStart(whisperPointer, segmentIndex, tokenIndex)
                        val endCs = WhisperNative.tokenEnd(whisperPointer, segmentIndex, tokenIndex)
                        add(
                            DecodedSpeechToken(
                                text = text,
                                startCentiseconds = startCs,
                                endCentiseconds = endCs,
                                probability = WhisperNative.tokenProbability(
                                    whisperPointer,
                                    segmentIndex,
                                    tokenIndex
                                )
                            )
                        )
                    }
                }
                addAll(RecognizedWordAssembler.assemble(tokens, window.startNanos))
            }
        }
        return RecognizedSegment(
            words,
            window.startNanos,
            window.endNanos,
            speechDetected = true
        )
    }

    private data class AudioWindow(
        val samples: FloatArray,
        val startNanos: Long,
        val endNanos: Long
    )

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val WINDOW_MS = 6_000L
        const val WINDOW_SAMPLES = SAMPLE_RATE * 6
        const val READ_SAMPLES = 2_048
        const val MODEL_PATH = "models/ggml-base.en-q5_1.bin"
        const val VAD_PATH = "models/ggml-silero-v6.2.0.bin"
        const val LOG_TAG = "KaptusSpeech"
    }
}
