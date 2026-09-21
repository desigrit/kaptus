package com.example.kaptus.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizedWordAssemblerTest {
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
