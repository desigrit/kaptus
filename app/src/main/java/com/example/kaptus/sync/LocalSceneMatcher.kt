package com.example.kaptus.sync

import com.example.kaptus.data.CaptionIndex
import com.example.kaptus.data.IndexedCaptionWord
import com.example.kaptus.data.MatchResult
import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.SyncAnchor
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class LocalSceneMatcher(
    private val minimumScore: Float = SyncTuning.INITIAL_MATCH_SCORE,
    private val trackingScore: Float = SyncTuning.TRACKING_MATCH_SCORE,
    private val minimumContentWords: Int = SyncTuning.MINIMUM_CONTENT_WORDS,
    private val minimumRunnerUpMargin: Float = SyncTuning.MINIMUM_RUNNER_UP_MARGIN
) : SceneMatcher {

    override fun buildIndex(captions: List<SubtitleEntry>): CaptionIndex {
        val cueTokens = captions.map { cue -> CaptionTextNormalizer.tokens(cue.text) }
        val frequencies = cueTokens.flatten().groupingBy { it }.eachCount()
        val words = buildList {
            captions.forEachIndexed { cueIndex, cue ->
                val tokens = cueTokens[cueIndex]
                tokens.forEachIndexed { wordIndex, token ->
                    val fraction = (wordIndex + 0.5) / tokens.size.coerceAtLeast(1)
                    val movieTime = cue.startTimeMs + ((cue.endTimeMs - cue.startTimeMs) * fraction).toLong()
                    add(
                        IndexedCaptionWord(
                            value = token,
                            movieTimeMs = movieTime,
                            cueIndex = cueIndex,
                            weight = tokenWeight(token, frequencies[token] ?: 0)
                        )
                    )
                }
            }
        }
        val positions = words.indices
            .groupBy { words[it].value }
            .mapValues { (_, values) -> values.toIntArray() }
        return CaptionIndex(words, positions)
    }

    override fun match(
        segment: RecognizedSegment,
        index: CaptionIndex,
        expectedTimeMs: Long?
    ): MatchResult {
        if (segment.words.isEmpty() || index.words.isEmpty()) return MatchResult.NoMatch

        val recognized = segment.words.mapIndexedNotNull { sourceIndex, word ->
            CaptionTextNormalizer.token(word.text)?.let { token ->
                NormalizedRecognition(
                    value = token,
                    sourceIndex = sourceIndex,
                    weight = tokenWeight(token, index.positions[token]?.size ?: 0),
                    isContent = token !in STOP_WORDS
                )
            }
        }
        if (recognized.count(NormalizedRecognition::isContent) < minimumContentWords) {
            return MatchResult.NoMatch
        }

        val votes = mutableMapOf<Int, Float>()
        recognized.forEachIndexed { queryIndex, word ->
            index.positions[word.value]?.forEach { captionPosition ->
                val captionWord = index.words[captionPosition]
                if (expectedTimeMs == null || abs(captionWord.movieTimeMs - expectedTimeMs) <= NEARBY_WINDOW_MS) {
                    votes[captionPosition - queryIndex] =
                        votes.getOrDefault(captionPosition - queryIndex, 0f) + word.weight
                }
            }
        }
        if (votes.isEmpty()) return MatchResult.NoMatch

        val candidates = votes.entries
            .sortedByDescending { it.value }
            .take(MAX_CANDIDATES)
            .map { scoreCandidate(recognized, segment, index, it.key) }
            .sortedByDescending { it.score }

        val best = candidates.firstOrNull() ?: return MatchResult.NoMatch
        val runnerUp = candidates.drop(1)
            .firstOrNull { other ->
                best.anchor == null || other.anchor == null ||
                    abs(other.anchor.movieTimeMs - best.anchor.movieTimeMs) > DISTINCT_RESULT_MS
            }
            ?.score ?: 0f
        val margin = best.score - runnerUp
        val acquiring = expectedTimeMs == null
        return best.copy(
            runnerUpScore = runnerUp,
            confident = best.score >= (if (acquiring) minimumScore else trackingScore) &&
                best.contentWordMatches >= (if (acquiring) minimumContentWords else TRACKING_CONTENT_WORDS) &&
                margin >= (if (acquiring) minimumRunnerUpMargin else TRACKING_RUNNER_UP_MARGIN)
        )
    }

    private fun scoreCandidate(
        recognized: List<NormalizedRecognition>,
        segment: RecognizedSegment,
        index: CaptionIndex,
        estimatedStart: Int
    ): MatchResult {
        val windowStart = (estimatedStart - WINDOW_PADDING).coerceAtLeast(0)
        val windowEnd = (estimatedStart + recognized.size + WINDOW_PADDING)
            .coerceAtMost(index.words.size)
        val captionWindow = index.words.subList(windowStart, windowEnd)
        if (captionWindow.isEmpty()) return MatchResult.NoMatch

        val rows = recognized.size + 1
        val columns = captionWindow.size + 1
        val scores = Array(rows) { FloatArray(columns) }
        for (row in 1 until rows) {
            for (column in 1 until columns) {
                val similarity = tokenSimilarity(recognized[row - 1].value, captionWindow[column - 1].value)
                val matched = scores[row - 1][column - 1] + similarity * captionWindow[column - 1].weight
                scores[row][column] = max(matched, max(scores[row - 1][column], scores[row][column - 1]))
            }
        }

        var row = recognized.size
        var column = captionWindow.size
        val pairs = mutableListOf<Pair<Int, Int>>()
        while (row > 0 && column > 0) {
            val similarity = tokenSimilarity(recognized[row - 1].value, captionWindow[column - 1].value)
            val diagonal = scores[row - 1][column - 1] + similarity * captionWindow[column - 1].weight
            when {
                similarity > 0f && abs(scores[row][column] - diagonal) < 0.001f -> {
                    pairs += (row - 1) to (column - 1)
                    row--
                    column--
                }
                scores[row - 1][column] >= scores[row][column - 1] -> row--
                else -> column--
            }
        }
        if (pairs.isEmpty()) return MatchResult.NoMatch

        val totalWeight = recognized.sumOf { word ->
            word.weight.toDouble()
        }.toFloat().coerceAtLeast(1f)
        val matchedWeight = pairs.sumOf { (recognizedIndex, captionIndex) ->
            val similarity = tokenSimilarity(
                recognized[recognizedIndex].value,
                captionWindow[captionIndex].value
            )
            (recognized[recognizedIndex].weight * similarity).toDouble()
        }.toFloat()
        val contentMatches = pairs.count { (recognizedIndex, _) ->
            recognized[recognizedIndex].isContent
        }
        val distinctCues = pairs.map { (_, captionIndex) -> captionWindow[captionIndex].cueIndex }.distinct().size

        val offsets = pairs.map { (recognizedIndex, captionIndex) ->
            val sourceWord = segment.words[recognized[recognizedIndex].sourceIndex]
            val captureMs = ((sourceWord.startTimeNanos + sourceWord.endTimeNanos) / 2L) / 1_000_000L
            captionWindow[captionIndex].movieTimeMs - captureMs
        }.sorted()
        val medianOffset = offsets[offsets.size / 2]
        val deviations = offsets.map { abs(it - medianOffset) }.sorted()
        val deviation = deviations[deviations.size / 2]
        val captureTime = segment.capturedEndNanos
        val movieTime = captureTime / 1_000_000L + medianOffset

        return MatchResult(
            confident = false,
            score = (matchedWeight / totalWeight).coerceIn(0f, 1f),
            runnerUpScore = 0f,
            contentWordMatches = contentMatches,
            distinctCueMatches = distinctCues,
            timingDeviationMs = deviation,
            anchor = SyncAnchor(captureTime, movieTime, matchedWeight / totalWeight)
        )
    }

    private fun tokenSimilarity(left: String, right: String): Float {
        if (left == right) return 1f
        if (left.length < 5 || right.length < 5 || abs(left.length - right.length) > 1) return 0f
        var leftIndex = 0
        var rightIndex = 0
        var edits = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            if (left[leftIndex] == right[rightIndex]) {
                leftIndex++
                rightIndex++
                continue
            }
            if (++edits > 1) return 0f
            when {
                left.length > right.length -> leftIndex++
                right.length > left.length -> rightIndex++
                else -> {
                    leftIndex++
                    rightIndex++
                }
            }
        }
        if (leftIndex < left.length || rightIndex < right.length) edits++
        return if (edits <= 1) 0.65f else 0f
    }

    private data class NormalizedRecognition(
        val value: String,
        val sourceIndex: Int,
        val weight: Float,
        val isContent: Boolean
    )

    private companion object {
        fun tokenWeight(token: String, occurrences: Int): Float = when {
            token in STOP_WORDS -> 0.12f
            occurrences <= 1 -> 1f
            occurrences <= 3 -> 0.9f
            occurrences <= 12 -> 0.75f
            else -> 0.55f
        }

        const val MAX_CANDIDATES = 12
        const val WINDOW_PADDING = 8
        const val NEARBY_WINDOW_MS = 120_000L
        const val DISTINCT_RESULT_MS = 30_000L
        const val TRACKING_CONTENT_WORDS = 3
        const val TRACKING_RUNNER_UP_MARGIN = 0.04f
        val STOP_WORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from",
            "he", "her", "him", "his", "i", "in", "is", "it", "me", "my", "of", "on",
            "or", "she", "so", "that", "the", "their", "them", "they", "this", "to",
            "was", "we", "were", "what", "with", "you", "your"
        )
    }
}

object CaptionTextNormalizer {
    private val bracketedCue = Regex("\\[[^]]*]|\\([^)]*\\)")
    private val htmlTag = Regex("<[^>]+>")
    private val speakerPrefix = Regex("(?m)^\\s*(?:[-–—]\\s*)?[A-Z][A-Za-z0-9 .'-]{1,24}:\\s*")
    private val apostrophe = Regex("['‘’`´]")
    private val combiningMark = Regex("\\p{M}+")
    private val tokenPattern = Regex("[a-z0-9]+")

    fun tokens(text: String): List<String> = tokenPattern
        .findAll(
            Normalizer.normalize(
                text.replace(htmlTag, " ")
                .replace(bracketedCue, " ")
                .replace(speakerPrefix, " ")
                .replace(apostrophe, ""),
                Normalizer.Form.NFKD
            )
                .replace(combiningMark, "")
                .lowercase(Locale.ROOT)
        )
        .map { it.value }
        .toList()

    fun token(text: String): String? = tokens(text).firstOrNull()
}
