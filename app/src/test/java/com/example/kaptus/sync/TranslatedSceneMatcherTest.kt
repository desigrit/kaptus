package com.example.kaptus.sync

import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.RecognizedWord
import com.example.kaptus.data.RecognitionTask
import com.example.kaptus.data.SubtitleEntry
import org.junit.Assert.*
import org.junit.Test

class TranslatedSceneMatcherTest {
    private val matcher = LocalSceneMatcher()
    private val translated = TranslatedSceneMatcher(matcher)

    @Test fun aTranslatedUtteranceNeedsOrderedEvidenceAcrossCaptionGroups() {
        val cues = listOf(
            SubtitleEntry(1, 60_000, 62_000, "Bring the silver compass"),
            SubtitleEntry(2, 62_000, 64_000, "Follow the northern lantern")
        )
        val index = matcher.buildIndex(cues)
        val words = "bring silver compass follow northern lantern".split(" ").map {
            RecognizedWord(it, 1_000_000_000, 5_000_000_000, 0.9f)
        }
        val result = translated.match(RecognizedSegment(words, 0, 6_000_000_000,
            sourceLanguage = "es", task = RecognitionTask.TRANSLATE), index)
        assertTrue(result.confident)
        assertEquals(2, result.distinctCueMatches)
        assertTrue(result.anchor!!.movieTimeMs in 63_000..66_000)
    }

    @Test fun aRepeatedTranslatedPhraseAndOneShortCueRemainInsufficient() {
        val phrase = "bring the silver compass"
        val words = phrase.split(" ").map { RecognizedWord(it, 0, 2_000_000_000, 0.9f) }
        val segment = RecognizedSegment(words, 0, 2_000_000_000, sourceLanguage = "es", task = RecognitionTask.TRANSLATE)
        val single = matcher.buildIndex(listOf(SubtitleEntry(1, 10_000, 12_000, phrase)))
        val repeated = matcher.buildIndex(listOf(SubtitleEntry(1, 10_000, 12_000, phrase),
            SubtitleEntry(2, 100_000, 102_000, phrase)))
        assertFalse(translated.match(segment, single).confident)
        assertFalse(translated.match(segment, repeated).confident)
        assertFalse(translated.match(segment.copy(task = RecognitionTask.TRANSCRIBE), single).confident)
    }
}
