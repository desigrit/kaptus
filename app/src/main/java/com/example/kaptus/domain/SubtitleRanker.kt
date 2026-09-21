package com.example.kaptus.domain

import com.example.kaptus.data.CaptionTrack
import com.example.kaptus.data.MovieCandidate

object SubtitleRanker {
    fun rank(
        tracks: List<CaptionTrack>,
        movie: MovieCandidate? = null
    ): List<CaptionTrack> = tracks
        .asSequence()
        .filter { it.language.equals("en", ignoreCase = true) }
        .filterNot { it.foreignPartsOnly }
        .sortedWith(
            compareByDescending<CaptionTrack> { score(it, movie) }
                .thenByDescending { it.downloadCount }
                .thenBy { it.fileId }
        )
        .toList()

    internal fun score(track: CaptionTrack, movie: MovieCandidate? = null): Double =
        (if (track.hearingImpaired) 100.0 else 0.0) +
            (if (track.trusted) 35.0 else 0.0) +
            (if (!track.machineTranslated) 20.0 else -40.0) +
            (if (!track.aiTranslated) 10.0 else -15.0) +
            track.ratings.coerceIn(0f, 10f) * 2.0 +
            kotlin.math.ln1p(track.downloadCount.toDouble()).coerceAtMost(20.0) +
            releaseMatchScore(track, movie)

    private fun releaseMatchScore(track: CaptionTrack, movie: MovieCandidate?): Double {
        movie ?: return 0.0
        val release = "${track.release} ${track.fileName}".lowercase()
        val titleWords = movie.title.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 }
        val titleMatches = titleWords.count(release::contains)
        val titleScore = if (titleWords.isEmpty()) 0.0 else 12.0 * titleMatches / titleWords.size
        val yearScore = if (movie.year?.toString()?.let(release::contains) == true) 8.0 else 0.0
        return titleScore + yearScore
    }
}
