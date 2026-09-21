package com.example.kaptus.sync

import com.example.kaptus.data.SyncAnchor

class SyncTimelineEstimator(private val maximumAnchors: Int = 8) {
    private val anchors = ArrayDeque<SyncAnchor>()

    fun clear() = anchors.clear()

    fun add(anchor: SyncAnchor) {
        anchors.addLast(anchor)
        while (anchors.size > maximumAnchors) anchors.removeFirst()
    }

    fun estimatedRate(): Double? {
        if (anchors.size < 2) return null
        val ordered = anchors.sortedBy { it.captureTimeNanos }
        val totalSpanMs = (ordered.last().captureTimeNanos - ordered.first().captureTimeNanos) /
            1_000_000.0
        if (totalSpanMs < SyncTuning.RATE_ESTIMATION_SPAN_MS) return null
        val slopes = buildList {
            ordered.forEachIndexed { firstIndex, first ->
                for (secondIndex in firstIndex + 1 until ordered.size) {
                    val second = ordered[secondIndex]
                    val captureSpanMs = (second.captureTimeNanos - first.captureTimeNanos) / 1_000_000.0
                    val movieSpanMs = (second.movieTimeMs - first.movieTimeMs).toDouble()
                    if (captureSpanMs >= MINIMUM_PAIR_SPAN_MS && movieSpanMs > 0.0) {
                        add(movieSpanMs / captureSpanMs)
                    }
                }
            }
        }.sorted()
        if (slopes.isEmpty()) return null
        return slopes[slopes.size / 2].coerceIn(
            SyncTuning.MINIMUM_PLAYBACK_RATE,
            SyncTuning.MAXIMUM_PLAYBACK_RATE
        )
    }

    fun movieTimeAt(captureTimeNanos: Long): Long? {
        if (anchors.isEmpty()) return null
        val rate = estimatedRate() ?: 1.0
        val predictions = anchors.map { anchor ->
            val elapsedMs = (captureTimeNanos - anchor.captureTimeNanos) / 1_000_000.0
            anchor.movieTimeMs + elapsedMs * rate
        }.sorted()
        return predictions[predictions.size / 2].toLong()
    }

    private companion object {
        const val MINIMUM_PAIR_SPAN_MS = 10_000.0
    }
}
