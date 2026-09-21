package com.example.kaptus.data

data class MovieCandidate(
    val id: String,
    val title: String,
    val year: Int?,
    val imdbId: Long?,
    val tmdbId: Long?,
    val mediaType: MediaType = MediaType.Movie,
    val parentFeatureId: Long? = null,
    val parentImdbId: Long? = null,
    val parentTmdbId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
) {
    fun episode(season: Int, episode: Int): MovieCandidate = copy(
        id = "$id-s${season}e$episode",
        title = "$title · S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}",
        imdbId = null,
        tmdbId = null,
        mediaType = MediaType.Episode,
        parentFeatureId = id.toLongOrNull(),
        parentImdbId = imdbId,
        parentTmdbId = tmdbId,
        seasonNumber = season,
        episodeNumber = episode
    )
}

enum class MediaType {
    Movie,
    TvShow,
    Episode
}

data class CaptionTrack(
    val subtitleId: String,
    val fileId: Long,
    val featureId: String,
    val fileName: String,
    val language: String,
    val hearingImpaired: Boolean,
    val trusted: Boolean,
    val foreignPartsOnly: Boolean,
    val aiTranslated: Boolean,
    val machineTranslated: Boolean,
    val ratings: Float,
    val downloadCount: Long,
    val release: String
)

data class LocalCaptionTrack(
    val track: CaptionTrack,
    val movie: MovieCandidate,
    val localPath: String,
    val entries: List<SubtitleEntry>
)

data class PreparedMovie(
    val featureId: String,
    val title: String,
    val year: Int?,
    val trackCount: Int,
    val preparedAtEpochMs: Long
)

data class RecognizedWord(
    val text: String,
    val startTimeNanos: Long,
    val endTimeNanos: Long,
    val confidence: Float
)

data class RecognizedSegment(
    val words: List<RecognizedWord>,
    val capturedStartNanos: Long,
    val capturedEndNanos: Long,
    val speechDetected: Boolean = words.isNotEmpty()
) {
    val text: String = words.joinToString(" ") { it.text }
}

data class SyncAnchor(
    val captureTimeNanos: Long,
    val movieTimeMs: Long,
    val confidence: Float
)

data class MatchResult(
    val confident: Boolean,
    val score: Float,
    val runnerUpScore: Float,
    val contentWordMatches: Int,
    val distinctCueMatches: Int,
    val timingDeviationMs: Long,
    val anchor: SyncAnchor?
) {
    companion object {
        val NoMatch = MatchResult(false, 0f, 0f, 0, 0, Long.MAX_VALUE, null)
    }
}

data class IndexedCaptionWord(
    val value: String,
    val movieTimeMs: Long,
    val cueIndex: Int,
    val weight: Float
)

data class CaptionIndex(
    val words: List<IndexedCaptionWord>,
    val positions: Map<String, IntArray>
)
