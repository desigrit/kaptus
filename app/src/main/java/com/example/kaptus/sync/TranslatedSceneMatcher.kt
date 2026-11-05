package com.example.kaptus.sync

import com.example.kaptus.data.CaptionIndex
import com.example.kaptus.data.MatchResult
import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.RecognitionTask

/** Translation token times describe output text, not exact spoken-word timing. */
class TranslatedSceneMatcher(private val matcher: SceneMatcher = LocalSceneMatcher()) {
    fun match(segment: RecognizedSegment, index: CaptionIndex): MatchResult {
        if (segment.task != RecognitionTask.TRANSLATE || segment.words.isEmpty()) return MatchResult.NoMatch
        // All words in one translated utterance retain its interval. Do not distribute them
        // into invented, precise word timestamps. The median cue alignment gives a coarse anchor.
        val result = matcher.match(segment, index, expectedTimeMs = null)
        return result.copy(confident = MatchingEvidenceProfile.TranslatedEnglish.accepts(result))
    }
}
