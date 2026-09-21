package com.example.kaptus.sync

import com.example.kaptus.data.CaptionIndex
import com.example.kaptus.data.MatchResult
import com.example.kaptus.data.RecognizedSegment
import com.example.kaptus.data.SubtitleEntry

interface SceneMatcher {
    fun buildIndex(captions: List<SubtitleEntry>): CaptionIndex
    fun match(
        segment: RecognizedSegment,
        index: CaptionIndex,
        expectedTimeMs: Long? = null
    ): MatchResult
}
