package com.example.kaptus.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SrtParserPlatformTest {
    @Test
    fun stripsSubtitleOverridesWithAndroidRegexEngine() {
        val source = """
            1
            00:00:01,000 --> 00:00:03,000
            {\an8}{\i1}The movie is starting.{\i0}
        """.trimIndent()

        val entry = SrtParser().parse(source.byteInputStream()).single()

        assertEquals("The movie is starting.", entry.text)
    }
}
