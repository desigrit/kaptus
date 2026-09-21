package com.example.kaptus.sync

import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.RecognizedWord

/**
 * Keeps the new portion of overlapping recognition windows so scene acquisition can use a
 * short phrase rather than requiring every recognition window to match on its own.
 */
internal class TranscriptAccumulator(
    private val retentionNanos: Long = DEFAULT_RETENTION_NANOS,
    private val overlapSlackNanos: Long = DEFAULT_OVERLAP_SLACK_NANOS
) {
    private val words = mutableListOf<RecognizedWord>()
    private var latestWindowEndNanos: Long? = null

    fun add(segment: RecognizedSegment): RecognizedSegment {
        val previousWindowEnd = latestWindowEndNanos
        val newWords = if (previousWindowEnd == null) {
            segment.words
        } else {
            val boundary = previousWindowEnd - overlapSlackNanos
            segment.words.filter { word -> midpoint(word) >= boundary }
        }

        newWords.sortedBy(RecognizedWord::startTimeNanos).forEach { word ->
            val duplicate = words.lastOrNull { existing ->
                existing.text.equals(word.text, ignoreCase = true) &&
                    kotlin.math.abs(midpoint(existing) - midpoint(word)) <= DUPLICATE_TOLERANCE_NANOS
            }
            if (duplicate == null) words += word
        }

        val windowEnd = maxOf(latestWindowEndNanos ?: Long.MIN_VALUE, segment.capturedEndNanos)
        latestWindowEndNanos = windowEnd
        val oldestAllowed = windowEnd - retentionNanos
        words.removeAll { it.endTimeNanos < oldestAllowed }
        while (words.size > MAX_WORDS) words.removeAt(0)

        return RecognizedSegment(
            words = words.toList(),
            capturedStartNanos = words.firstOrNull()?.startTimeNanos ?: segment.capturedStartNanos,
            capturedEndNanos = windowEnd,
            speechDetected = segment.speechDetected
        )
    }

    fun clear() {
        words.clear()
        latestWindowEndNanos = null
    }

    private fun midpoint(word: RecognizedWord): Long =
        word.startTimeNanos + (word.endTimeNanos - word.startTimeNanos) / 2L

    private companion object {
        const val DEFAULT_RETENTION_NANOS = 20_000_000_000L
        const val DEFAULT_OVERLAP_SLACK_NANOS = 400_000_000L
        const val DUPLICATE_TOLERANCE_NANOS = 750_000_000L
        const val MAX_WORDS = 96
    }
}
