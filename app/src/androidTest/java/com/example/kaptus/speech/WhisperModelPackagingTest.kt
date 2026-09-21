package com.example.kaptus.speech

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperModelPackagingTest {
    @Test
    fun bundledModelsLoadThroughNativeRuntime() = runBlocking {
        val engine = WhisperSpeechRecognitionEngine(ApplicationProvider.getApplicationContext())

        assertTrue(engine.isReady)
        try {
            withTimeout(60_000L) { engine.prepare() }
        } finally {
            engine.close()
        }
    }
}
