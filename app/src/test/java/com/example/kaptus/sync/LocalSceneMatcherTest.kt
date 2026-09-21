package com.example.kaptus.sync

import com.example.kaptus.data.CaptionIndex
import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.RecognizedWord
import com.example.kaptus.data.SubtitleEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSceneMatcherTest {
    private val matcher = LocalSceneMatcher()

    @Test
    fun normalizationRemovesLabelsMarkupAndSoundDescriptions() {
        val tokens = CaptionTextNormalizer.tokens(
            "<i>Louise:</i> [door closes] We can't stay here, can we?"
        )

        assertTrue(tokens.containsAll(listOf("we", "cant", "stay", "here", "can", "we")))
        assertFalse(tokens.contains("louise"))
        assertFalse(tokens.contains("door"))
    }

    @Test
    fun punctuationAndApostropheStylesProduceTheSameWords() {
        val punctuated = CaptionTextNormalizer.tokens(
            "Wait... \u201cDon\u2019t,\u201d re-enter! We\u2019re here."
        )
        val plain = CaptionTextNormalizer.tokens("wait dont re enter were here")

        assertTrue(punctuated == plain)
    }

    @Test
    fun punctuationDifferencesDoNotPreventSceneMatching() {
        val captions = listOf(
            SubtitleEntry(1, 40_000L, 44_000L, "Wait... don't move, we're inside!"),
            SubtitleEntry(2, 50_000L, 53_000L, "The road is empty")
        )
        val captionIndex = matcher.buildIndex(captions)
        val spoken = listOf("wait", "dont", "move", "were", "inside")
            .mapIndexed { position, word ->
                RecognizedWord(
                    word,
                    position * 300_000_000L,
                    position * 300_000_000L + 100_000_000L,
                    0.9f
                )
            }
        val segment = RecognizedSegment(spoken, 0L, 1_500_000_000L)

        assertTrue(matcher.match(segment, captionIndex, null).confident)
    }

    @Test
    fun matchesDialogueAcrossTwoCuesWithAsrOmissions() {
        val captions = uniqueCaptions(100_000L)
        val index = matcher.buildIndex(captions)
        val selected = index.words
            .filter { it.cueIndex <= 1 }
            .filterNot { it.value in setOf("the", "and") }
            .take(10)
        val segment = segmentFrom(selected, offsetMs = 92_000L)

        val result = matcher.match(segment, index, expectedTimeMs = null)

        assertTrue("Expected a confident match but got $result", result.confident)
        assertTrue(result.distinctCueMatches >= 2)
        assertTrue(result.contentWordMatches >= 6)
    }

    @Test
    fun matchesDistinctiveWordsInsideOneLongCue() {
        val captions = listOf(
            SubtitleEntry(
                1,
                80_000L,
                91_000L,
                "Remember the copper telescope beside the frozen garden tonight"
            ),
            SubtitleEntry(2, 96_000L, 100_000L, "We should keep moving")
        )
        val index = matcher.buildIndex(captions)
        val selected = index.words.filter { it.cueIndex == 0 }.take(7)

        val result = matcher.match(segmentFrom(selected, offsetMs = 72_000L), index, null)

        assertTrue("A unique phrase should match without crossing cue boundaries: $result", result.confident)
        assertTrue(result.distinctCueMatches == 1)
    }

    @Test
    fun matchesThreeDistinctiveWordsFromAShortLine() {
        val captions = listOf(
            SubtitleEntry(1, 20_000L, 23_000L, "Bring the silver compass"),
            SubtitleEntry(2, 30_000L, 33_000L, "Wait here until morning")
        )
        val index = matcher.buildIndex(captions)
        val selected = index.words
            .filter { it.cueIndex == 0 && it.value != "the" }
            .take(3)

        val result = matcher.match(segmentFrom(selected, offsetMs = 17_000L), index, null)

        assertTrue("Three unique content words should locate the scene: $result", result.confident)
    }

    @Test
    fun subtitleWordTimingDoesNotRejectAUniqueTextMatch() {
        val captions = uniqueCaptions(100_000L)
        val index = matcher.buildIndex(captions)
        val selected = index.words.filter { it.cueIndex <= 1 }.take(10)
        val recognized = selected.mapIndexed { position, word ->
            RecognizedWord(
                word.value,
                position * 2_500_000_000L,
                position * 2_500_000_000L + 100_000_000L,
                0.9f
            )
        }
        val segment = RecognizedSegment(recognized, 0L, 25_000_000_000L)

        val result = matcher.match(segment, index, null)

        assertTrue("Text order should decide acquisition even when SRT word timing is coarse: $result", result.confident)
    }

    @Test
    fun rejectsRepeatedDialogueWithNoRunnerUpMargin() {
        val first = uniqueCaptions(10_000L)
        val repeated = uniqueCaptions(100_000L).map { it.copy(index = it.index + 10) }
        val index = matcher.buildIndex(first + repeated)
        val segment = segmentFrom(index.words.filter { it.cueIndex <= 1 }.take(12), offsetMs = 2_000L)

        val result = matcher.match(segment, index, expectedTimeMs = null)

        assertFalse("Repeated dialogue must remain ambiguous: $result", result.confident)
        assertTrue(result.score - result.runnerUpScore < 0.12f)
    }

    @Test
    fun synchronizedSearchStaysNearExpectedSceneUntilCoordinatorReacquires() {
        val captions = uniqueCaptions(300_000L)
        val index = matcher.buildIndex(captions)
        val segment = segmentFrom(index.words.filter { it.cueIndex <= 1 }.take(12), offsetMs = 292_000L)

        val nearbyResult = matcher.match(segment, index, expectedTimeMs = 10_000L)
        val wholeTrackResult = matcher.match(segment, index, expectedTimeMs = null)

        assertFalse(nearbyResult.confident)
        assertTrue(wholeTrackResult.confident)
    }

    private fun uniqueCaptions(startMs: Long) = listOf(
        SubtitleEntry(
            1,
            startMs,
            startMs + 4_000L,
            "Beyond the silent valley our scattered friends are waiting"
        ),
        SubtitleEntry(
            2,
            startMs + 4_000L,
            startMs + 8_000L,
            "Follow every lantern until the northern morning arrives"
        ),
        SubtitleEntry(
            3,
            startMs + 9_000L,
            startMs + 12_000L,
            "Nothing else belongs in this scene"
        )
    )

    private fun segmentFrom(
        words: List<com.example.kaptus.data.IndexedCaptionWord>,
        offsetMs: Long
    ): RecognizedSegment {
        val recognized = words.map { word ->
            val startNanos = (word.movieTimeMs - offsetMs) * 1_000_000L
            RecognizedWord(word.value, startNanos, startNanos + 120_000_000L, 0.9f)
        }
        return RecognizedSegment(
            words = recognized,
            capturedStartNanos = recognized.first().startTimeNanos,
            capturedEndNanos = recognized.last().endTimeNanos
        )
    }
}
