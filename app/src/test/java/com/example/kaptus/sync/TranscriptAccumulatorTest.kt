package com.example.kaptus.sync

import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.RecognizedWord
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptAccumulatorTest {
    @Test
    fun combinesOnlyTheNewWordsFromOverlappingWindows() {
        val accumulator = TranscriptAccumulator()

        accumulator.add(
            segment(
                0,
                8_000,
                word("follow", 1_000),
                word("lantern", 6_000),
                word("north", 7_800)
            )
        )
        val combined = accumulator.add(
            segment(
                4_000,
                12_000,
                word("lantern", 6_050),
                word("north", 7_900),
                word("until", 9_000),
                word("morning", 11_000)
            )
        )

        assertEquals(
            listOf("follow", "lantern", "north", "until", "morning"),
            combined.words.map { it.text }
        )
    }

    @Test
    fun clearStartsTheNextAttemptWithFreshEvidence() {
        val accumulator = TranscriptAccumulator()
        accumulator.add(segment(0, 8_000, word("old", 7_000)))

        accumulator.clear()
        val combined = accumulator.add(segment(20_000, 28_000, word("fresh", 27_000)))

        assertEquals(listOf("fresh"), combined.words.map { it.text })
    }

    private fun segment(startMs: Long, endMs: Long, vararg words: RecognizedWord) =
        RecognizedSegment(
            words = words.toList(),
            capturedStartNanos = startMs * 1_000_000L,
            capturedEndNanos = endMs * 1_000_000L
        )

    private fun word(text: String, startMs: Long) = RecognizedWord(
        text = text,
        startTimeNanos = startMs * 1_000_000L,
        endTimeNanos = (startMs + 200L) * 1_000_000L,
        confidence = 0.9f
    )
}
