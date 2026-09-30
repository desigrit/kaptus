package com.example.kaptus.sync

import com.example.kaptus.data.MatchResult

/** Starting profiles for device evaluation. These do not enable a shipping language. */
data class MatchingEvidenceProfile(val minimumScore: Float, val minimumContentTokens: Int,
                                   val minimumMargin: Float, val minimumCues: Int,
                                   val maximumTimingDeviationMs: Long) {
    fun accepts(result: MatchResult): Boolean = result.confident && result.anchor != null &&
        result.score >= minimumScore && result.contentWordMatches >= minimumContentTokens &&
        result.score - result.runnerUpScore >= minimumMargin && result.distinctCueMatches >= minimumCues &&
        result.timingDeviationMs <= maximumTimingDeviationMs

    companion object {
        val TranslatedEnglish = MatchingEvidenceProfile(.60f, 4, .10f, 2, 2_500L)
        val SourceWords = MatchingEvidenceProfile(.60f, 4, .10f, 2, 1_750L)
        val SourceCharacterGrams = MatchingEvidenceProfile(.65f, 5, .12f, 2, 1_750L)
        fun source(language: String) = if (language in setOf("zh", "ja")) SourceCharacterGrams else SourceWords
    }
}
