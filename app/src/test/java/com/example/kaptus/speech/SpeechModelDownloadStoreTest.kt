package com.example.kaptus.speech

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SpeechModelDownloadStoreTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private val bytes = "synthetic model artifact".toByteArray()
    private val checksum = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    @Before fun setup() {
        server = MockWebServer().also { it.start() }
        directory = Files.createTempDirectory("kaptus-model-test").toFile()
    }

    @After fun cleanup() {
        server.shutdown()
        directory.listFiles()?.forEach { it.delete() }
        directory.delete()
    }

    private fun store() = SpeechModelDownloadStore(directory, url = server.url("/model").toString(),
        expectedBytes = bytes.size.toLong(), expectedSha256 = checksum)

    @Test fun verifiesBeforeInstallingAndReportsCompletion() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val store = store()
        val progress = mutableListOf<Float>()
        store.download { progress.add(it) }
        assertTrue(store.isInstalled())
        assertArrayEquals(bytes, store.destination.readBytes())
        assertEquals(1f, progress.last())
        assertFalse(directory.resolve("${SpeechModelDownloadStore.MODEL_NAME}.part").exists())
    }

    @Test fun resumesWithValidatedRangeAndExactTotal() = runTest {
        directory.resolve("${SpeechModelDownloadStore.MODEL_NAME}.part").writeBytes(bytes.copyOfRange(0, 5))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 5-${bytes.size - 1}/${bytes.size}")
            .setBody(Buffer().write(bytes.copyOfRange(5, bytes.size))))
        val store = store()
        store.download {}
        assertEquals("bytes=5-", server.takeRequest().getHeader("Range"))
        assertTrue(store.isInstalled())
    }

    @Test fun restartsIfServerIgnoresRange() = runTest {
        directory.resolve("${SpeechModelDownloadStore.MODEL_NAME}.part").writeBytes(bytes.copyOfRange(0, 5))
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val store = store()
        store.download {}
        assertTrue(store.isInstalled())
    }

    @Test fun rejectsChecksumMismatchAndNeverInstallsIt() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(bytes.size))))
        val store = store()
        assertTrue(runCatching { store.download {} }.isFailure)
        assertFalse(store.destination.exists())
        assertFalse(directory.resolve("${SpeechModelDownloadStore.MODEL_NAME}.part").exists())
    }

    @Test fun rejectsWrongRangeStart() = runTest {
        directory.resolve("${SpeechModelDownloadStore.MODEL_NAME}.part").writeBytes(bytes.copyOfRange(0, 5))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-${bytes.size - 1}/${bytes.size}")
            .setBody(Buffer().write(bytes)))
        val store = store()
        assertTrue(runCatching { store.download {} }.isFailure)
        assertFalse(store.destination.exists())
    }

    @Test fun rejectsOversizedArtifact() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes + byteArrayOf(1))))
        val store = store()
        assertTrue(runCatching { store.download {} }.isFailure)
        assertFalse(store.destination.exists())
    }

    @Test fun deletesInstalledAndPartialArtifacts() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val store = store()
        store.download {}
        directory.resolve("${SpeechModelDownloadStore.MODEL_NAME}.part").writeBytes(byteArrayOf(1))
        store.delete()
        assertFalse(store.destination.exists())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun cancellingBeforeCallPublicationNeverStartsTheRequest() = runBlocking {
        val reachedBoundary = CountDownLatch(1)
        val releaseBoundary = CountDownLatch(1)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val store = SpeechModelDownloadStore(directory, url = server.url("/model").toString(),
            expectedBytes = bytes.size.toLong(), expectedSha256 = checksum,
            beforeCallAssigned = {
                reachedBoundary.countDown()
                check(releaseBoundary.await(5, TimeUnit.SECONDS))
            })
        val worker = launch(Dispatchers.IO) { store.download {} }
        try {
            assertTrue(reachedBoundary.await(5, TimeUnit.SECONDS))
            worker.cancel()
            store.cancel() // There is no published call yet, exactly the previous race boundary.
            releaseBoundary.countDown()
            withTimeout(5_000) { worker.join() }
            assertTrue(worker.isCancelled)
            assertEquals(0, server.requestCount)
            assertFalse(store.destination.exists())
        } finally {
            releaseBoundary.countDown()
            worker.cancel()
            store.cancel()
        }
    }

    @Test fun cancellingAnActiveReadInterruptsTransportAndPreservesABoundedPrefix() = runBlocking {
        val startedBody = CountDownLatch(1)
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).throttleBody(1, 1, TimeUnit.SECONDS))
        val store = store()
        val worker = launch(Dispatchers.IO) {
            store.download { if (it > 0f) startedBody.countDown() }
        }
        try {
            assertTrue(startedBody.await(5, TimeUnit.SECONDS))
            worker.cancel()
            store.cancel()
            withTimeout(5_000) { worker.join() }
            assertTrue(worker.isCancelled)
            assertEquals(1, server.requestCount)
            assertFalse(store.destination.exists())
            val partial = directory.resolve("${SpeechModelDownloadStore.MODEL_NAME}.part").readBytes()
            assertTrue(partial.isNotEmpty() && partial.size < bytes.size)
            assertArrayEquals(bytes.copyOfRange(0, partial.size), partial)
        } finally {
            worker.cancel()
            store.cancel()
        }
    }

    @Test fun installedModelVerificationHonorsCancellation() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val store = store()
        store.download {}
        var cancelled = false
        try {
            store.isInstalled { throw CancellationException("Cancelled verification") }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(store.isInstalled())
    }
}
