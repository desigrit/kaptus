package com.example.kaptus.speech

import android.content.Context
import com.example.kaptus.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface SpeechModelStatus {
    data object NotDownloaded : SpeechModelStatus
    data class Downloading(val progress: Float) : SpeechModelStatus
    data object Ready : SpeechModelStatus
    data class Failed(val message: String) : SpeechModelStatus
}

/** Downloaded models live outside Android backup and never replace the bundled English model. */
class SpeechModelManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val store = SpeechModelDownloadStore(applicationContext.noBackupFilesDir.resolve("speech_models"))
    private val mutableState = MutableStateFlow<SpeechModelStatus>(SpeechModelStatus.NotDownloaded)
    val state: StateFlow<SpeechModelStatus> = mutableState.asStateFlow()
    @Volatile private var activeDownload: Deferred<Unit>? = null
    private var startupVerification: Job? = null
    @Volatile private var deleting = false

    val multilingualModelPath: String?
        get() = store.destination.absolutePath.takeIf { mutableState.value == SpeechModelStatus.Ready && store.destination.isFile }

    init {
        startupVerification = scope.launch {
            val verificationContext = currentCoroutineContext()
            mutex.withLock {
                val installed = store.isInstalled { verificationContext.ensureActive() }
                verificationContext.ensureActive()
                if (installed) mutableState.value = SpeechModelStatus.Ready
            }
        }
    }

    suspend fun download() {
        val deferred = synchronized(this) {
            if (deleting) return
            activeDownload?.takeIf { it.isActive } ?: scope.async {
                val downloadContext = currentCoroutineContext()
                mutex.withLock {
                    val installed = store.isInstalled { downloadContext.ensureActive() }
                    downloadContext.ensureActive()
                    if (installed) {
                        mutableState.value = SpeechModelStatus.Ready
                        return@withLock
                    }
                    mutableState.value = SpeechModelStatus.Downloading(0f)
                    try {
                        store.download { mutableState.value = SpeechModelStatus.Downloading(it) }
                        mutableState.value = SpeechModelStatus.Ready
                    } catch (error: CancellationException) {
                        mutableState.value = SpeechModelStatus.NotDownloaded
                        throw error
                    } catch (_: Exception) {
                        mutableState.value = SpeechModelStatus.Failed(applicationContext.getString(R.string.error_speech_model_download))
                    }
                }
            }.also { activeDownload = it }
        }
        try {
            deferred.await()
        } catch (error: CancellationException) {
            cancel()
            throw error
        }
    }

    fun cancel() {
        // Cancel the coroutine before checking the call reference. If the worker has not
        // published its call yet, its pre-execute cancellation check prevents the request.
        activeDownload?.cancel()
        store.cancel()
    }

    suspend fun delete() {
        val jobs = synchronized(this) {
            deleting = true
            listOfNotNull(startupVerification, activeDownload).also { pending ->
                pending.forEach { it.cancel() }
            }
        }
        store.cancel()
        try {
            jobs.forEach { it.cancelAndJoin() }
            mutex.withLock {
                store.delete()
                mutableState.value = SpeechModelStatus.NotDownloaded
            }
        } finally {
            deleting = false
        }
    }
}

/** Transport and verification separated from Android so bounded downloads can be tested. */
internal class SpeechModelDownloadStore(
    private val directory: File,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build(),
    private val url: String = MODEL_URL,
    private val expectedBytes: Long = MODEL_BYTES,
    private val expectedSha256: String = MODEL_SHA256,
    private val beforeCallAssigned: () -> Unit = {}
) {
    val destination = directory.resolve(MODEL_NAME)
    private val partial = directory.resolve("$MODEL_NAME.part")
    @Volatile private var currentCall: Call? = null

    fun isInstalled(checkCancellation: () -> Unit = {}): Boolean = destination.isFile && destination.length() == expectedBytes &&
        sha256(destination, checkCancellation).equals(expectedSha256, ignoreCase = true)

    fun cancel() { currentCall?.cancel() }

    fun delete() {
        destination.delete()
        partial.delete()
    }

    suspend fun download(onProgress: (Float) -> Unit) {
        val downloadContext = coroutineContext
        downloadContext.ensureActive()
        directory.mkdirs()
        require(directory.isDirectory)
        if (partial.length() >= expectedBytes) partial.delete()
        val offset = partial.takeIf { it.isFile }?.length() ?: 0L
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .apply { if (offset > 0L) header("Range", "bytes=$offset-") }.build()
        val call = client.newCall(request)
        beforeCallAssigned()
        currentCall = call
        try {
            downloadContext.ensureActive()
            call.execute().use { response ->
                if (response.code !in setOf(200, 206)) throw IOException("Model download failed (${response.code}).")
                val append = response.code == 206 && offset > 0L
                if (response.code == 206) {
                    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
                        .matchEntire(response.header("Content-Range").orEmpty())
                        ?: throw IOException("Invalid model range response.")
                    require(match.groupValues[1].toLong() == offset && match.groupValues[3].toLong() == expectedBytes)
                    require(match.groupValues[2].toLong() == expectedBytes - 1L)
                }
                val start = if (append) offset else 0L
                val body = response.body ?: throw IOException("Model download was empty.")
                if (body.contentLength() > expectedBytes - start) throw IOException("Model download exceeds its expected size.")
                var total = start
                onProgress(total.toFloat() / expectedBytes)
                body.byteStream().use { input ->
                    java.io.FileOutputStream(partial, append).buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > expectedBytes) {
                                partial.delete()
                                throw IOException("Model download exceeds its expected size.")
                            }
                            output.write(buffer, 0, count)
                            onProgress(total.toFloat() / expectedBytes)
                        }
                    }
                }
                coroutineContext.ensureActive()
                if (total != expectedBytes || !sha256(partial) { downloadContext.ensureActive() }.equals(expectedSha256, ignoreCase = true)) {
                    partial.delete()
                    throw IOException("Model integrity check failed.")
                }
                coroutineContext.ensureActive()
                Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: IOException) {
            coroutineContext.ensureActive()
            throw error
        } finally {
            currentCall = null
        }
    }

    private fun sha256(file: File, checkCancellation: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_NAME = "ggml-small-q5_1.bin"
        const val MODEL_BYTES = 190_085_487L
        const val MODEL_SHA256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"
        const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/98aa99a0a9db05ae2342309f5096248665f7cba3/ggml-small-q5_1.bin"
    }
}
