package com.example.kaptus.domain

import com.example.kaptus.data.CaptionTrack
import com.example.kaptus.data.MovieCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleRankerTest {
    private val movie = MovieCandidate("feature-1", "Quiet Signals", 2025, 123L, 456L)

    @Test
    fun prefersEnglishFullFilmSdhHumanAndTrustedTracks() {
        val ordinary = track(fileId = 1, downloads = 50_000)
        val sdh = track(
            fileId = 2,
            hearingImpaired = true,
            trusted = true,
            release = "Quiet.Signals.2025.1080p.BluRay"
        )
        val machineSdh = track(
            fileId = 3,
            hearingImpaired = true,
            trusted = true,
            machineTranslated = true
        )
        val foreignOnly = track(fileId = 4, hearingImpaired = true, foreignPartsOnly = true)
        val wrongLanguage = track(fileId = 5, language = "fr", hearingImpaired = true)

        val ranked = SubtitleRanker.rank(
            listOf(ordinary, machineSdh, foreignOnly, wrongLanguage, sdh),
            movie
        )

        assertEquals(listOf(2L, 3L, 1L), ranked.map { it.fileId })
        assertTrue(ranked.none { it.foreignPartsOnly || it.language != "en" })
    }

    private fun track(
        fileId: Long,
        language: String = "en",
        hearingImpaired: Boolean = false,
        trusted: Boolean = false,
        foreignPartsOnly: Boolean = false,
        machineTranslated: Boolean = false,
        downloads: Long = 100,
        release: String = "Other.Release"
    ) = CaptionTrack(
        subtitleId = "subtitle-$fileId",
        fileId = fileId,
        featureId = movie.id,
        fileName = "$release.srt",
        language = language,
        hearingImpaired = hearingImpaired,
        trusted = trusted,
        foreignPartsOnly = foreignPartsOnly,
        aiTranslated = false,
        machineTranslated = machineTranslated,
        ratings = 8f,
        downloadCount = downloads,
        release = release
    )
}
