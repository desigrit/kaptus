package com.example.kaptus.data

import kotlinx.coroutines.flow.Flow

interface SubtitleRepository {
    suspend fun searchMovies(query: String): Result<List<MovieCandidate>>
    suspend fun getLanguages(): Result<List<CaptionLanguage>>
    suspend fun findTracks(movie: MovieCandidate, language: String = "en"): Result<List<CaptionTrack>>
    suspend fun download(track: CaptionTrack): Result<LocalCaptionTrack>
    suspend fun loadPrepared(
        featureId: String,
        language: String = "en",
        role: CaptionTrackRole = CaptionTrackRole.DISPLAY
    ): Result<List<LocalCaptionTrack>>
    fun observePreparedMovies(): Flow<List<PreparedMovie>>
    fun resetSession()
}

class ProviderConfigurationException(message: String) : IllegalStateException(message)
class CaptionDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
