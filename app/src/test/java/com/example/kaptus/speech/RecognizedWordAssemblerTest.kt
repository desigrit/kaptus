package com.example.kaptus.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizedWordAssemblerTest {
    @Test fun joinsSplitUtf8BeforeDecodingAndPreservesIndicMarks() {
        val original = " नमस्ते தமிழ் తెలుగు"
        val bytes = original.toByteArray(Charsets.UTF_8)
        val tokens = bytes.mapIndexed { index, byte -> DecodedSpeechBytes(byteArrayOf(byte), index.toLong(), index + 1L, 0.9f) }
        val words = RecognizedWordAssembler.assembleBytes(tokens, 0)
        assertEquals(listOf("नमस्ते", "தமிழ்", "తెలుగు"), words.map { it.text })
        assertTrue(words.none { '\uFFFD' in it.text })
    }

    @Test fun translatedTokensRetainUtteranceIntervalsInsteadOfInventedWordTimes() {
        val words = RecognizedWordAssembler.assembleBytes(listOf(
            DecodedSpeechBytes(" Bring the silver compass".toByteArray(), 10, 200, 0.9f)
        ), 1_000_000_000, "es", translated = true)
        assertTrue(words.all { it.startTimeNanos == 1_100_000_000L && it.endTimeNanos == 3_000_000_000L })
    }

    @Test fun japaneseAndMandarinUseCharacterPairsWithoutWhitespace() {
        val words = RecognizedWordAssembler.assembleBytes(listOf(
            DecodedSpeechBytes("明日は東京へ行こう".toByteArray(), 0, 300, 0.9f)
        ), 0, "ja")
        assertTrue(words.size > 3)
        assertEquals("明日", words.first().text)
    }
    @Test
    fun joinsSubwordPiecesAndKeepsTimedWordBoundaries() {
        val words = RecognizedWordAssembler.assemble(
            listOf(
                DecodedSpeechToken(" Capt", 10, 18, 0.9f),
                DecodedSpeechToken("ions", 18, 26, 0.8f),
                DecodedSpeechToken(" synchronize", 27, 40, 0.95f),
                DecodedSpeechToken(" perfectly", 41, 54, 0.92f),
                DecodedSpeechToken(".", 54, 55, 0.9f)
            ),
            captureStartNanos = 2_000_000_000L
        )

        assertEquals(listOf("captions", "synchronize", "perfectly"), words.map { it.text })
        assertEquals(2_100_000_000L, words.first().startTimeNanos)
        assertEquals(2_260_000_000L, words.first().endTimeNanos)
        assertTrue(words.zipWithNext().all { (first, second) -> first.endTimeNanos <= second.startTimeNanos })
    }

    @Test
    fun dropsSpecialTokens() {
        val words = RecognizedWordAssembler.assemble(
            listOf(
                DecodedSpeechToken("<|startoftranscript|>", 0, 0, 1f),
                DecodedSpeechToken(" [MUSIC]", 1, 2, 0.8f),
                DecodedSpeechToken(" Hello", 3, 8, 0.9f)
            ),
            captureStartNanos = 0L
        )

        assertEquals(listOf("hello"), words.map { it.text })
    }
}
