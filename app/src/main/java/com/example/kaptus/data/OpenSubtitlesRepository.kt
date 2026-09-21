package com.example.kaptus.data

import android.content.Context
import com.example.kaptus.R
import com.example.kaptus.data.local.CaptionDao
import com.example.kaptus.data.local.CaptionTrackEntity
import com.example.kaptus.data.local.MovieEntity
import com.example.kaptus.data.remote.DownloadRequest
import com.example.kaptus.data.remote.LoginRequest
import com.example.kaptus.data.remote.OpenSubtitlesService
import com.example.kaptus.data.settings.SecureCredentialStore
import com.example.kaptus.domain.SubtitleRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import retrofit2.HttpException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

class OpenSubtitlesRepository(
    private val context: Context,
    private val service: OpenSubtitlesService,
    private val credentials: SecureCredentialStore,
    private val captionDao: CaptionDao
) : SubtitleRepository {
    private val movies = ConcurrentHashMap<String, MovieCandidate>()

    override suspend fun searchMovies(query: String): Result<List<MovieCandidate>> = runProviderCall {
        requireConfigured()
        if (query.isBlank()) return@runProviderCall emptyList()
        providerRequest { service.api.searchFeatures(query.trim()) }.data
            .filter { it.attributes.title.isNotBlank() }
            .filter {
                it.attributes.featureType.isBlank() || it.attributes.featureType.lowercase() in
                    setOf("movie", "tvshow", "tv show", "series")
            }
            .map { feature ->
                val featureType = feature.attributes.featureType.lowercase()
                MovieCandidate(
                    id = feature.id.jsonPrimitive.content,
                    title = feature.attributes.title,
                    year = feature.attributes.year?.jsonPrimitive?.content?.toIntOrNull(),
                    imdbId = feature.attributes.imdbId,
                    tmdbId = feature.attributes.tmdbId,
                    mediaType = if (featureType in setOf("tvshow", "tv show", "series")) {
                        MediaType.TvShow
                    } else {
                        MediaType.Movie
                    }
                )
            }
            .distinctBy { it.id }
    }

    override suspend fun findTracks(movie: MovieCandidate): Result<List<CaptionTrack>> = runProviderCall {
        requireConfigured()
        movies.clear()
        movies[movie.id] = movie
        val query = if (movie.imdbId == null && movie.mediaType == MediaType.Movie) {
            listOfNotNull(movie.title, movie.year?.toString()).joinToString(" ")
        } else null
        val tracks = providerRequest {
            service.api.searchSubtitles(
                imdbId = movie.imdbId,
                tmdbId = movie.tmdbId,
                parentFeatureId = movie.parentFeatureId,
                parentImdbId = movie.parentImdbId,
                parentTmdbId = movie.parentTmdbId,
                seasonNumber = movie.seasonNumber,
                episodeNumber = movie.episodeNumber,
                query = query,
                type = if (movie.mediaType == MediaType.Episode) "episode" else "movie"
            )
        }.data.mapNotNull { subtitle ->
            val file = subtitle.attributes.files.firstOrNull() ?: return@mapNotNull null
            CaptionTrack(
                subtitleId = subtitle.attributes.subtitleId?.jsonPrimitive?.content
                    ?: subtitle.id.jsonPrimitive.content,
                fileId = file.fileId,
                featureId = movie.id,
                fileName = file.fileName,
                language = subtitle.attributes.language,
                hearingImpaired = subtitle.attributes.hearingImpaired,
                trusted = subtitle.attributes.trusted,
                foreignPartsOnly = subtitle.attributes.foreignPartsOnly,
                aiTranslated = subtitle.attributes.aiTranslated,
                machineTranslated = subtitle.attributes.machineTranslated,
                ratings = subtitle.attributes.ratings,
                downloadCount = subtitle.attributes.downloadCount,
                release = subtitle.attributes.release
            )
        }
        SubtitleRanker.rank(tracks, movie)
    }

    override suspend fun download(track: CaptionTrack): Result<LocalCaptionTrack> = runProviderCall {
        requireConfigured()
        val movie = movies[track.featureId]
            ?: captionDao.movie(track.featureId)?.toModel()
            ?: throw CaptionDownloadException(text(R.string.error_movie_metadata_unavailable))
        val response = providerRequest(authenticated = true) {
            service.api.download(DownloadRequest(track.fileId))
        }
        if (response.link.isBlank()) {
            throw CaptionDownloadException(
                response.message ?: text(R.string.error_provider_missing_download_link)
            )
        }
        val request = Request.Builder().url(response.link).build()
        val bytes = withContext(Dispatchers.IO) {
            var lastCode = 0
            repeat(MAX_PROVIDER_ATTEMPTS) { attempt ->
                service.downloadClient.newCall(request).execute().use { networkResponse ->
                    if (networkResponse.isSuccessful) {
                        return@withContext networkResponse.body?.bytes()
                            ?: throw CaptionDownloadException(text(R.string.error_download_empty))
                    }
                    lastCode = networkResponse.code
                }
                if (lastCode !in RETRYABLE_CODES || attempt == MAX_PROVIDER_ATTEMPTS - 1) {
                    throw CaptionDownloadException(text(R.string.error_download_http, lastCode))
                }
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
            throw CaptionDownloadException(text(R.string.error_download_http, lastCode))
        }.ungzipIfNeeded()

        val entries = SrtParser().parse(ByteArrayInputStream(bytes))
        if (entries.isEmpty()) {
            throw CaptionDownloadException(text(R.string.error_download_unreadable))
        }

        val directory = context.filesDir.resolve("captions/${safePath(movie.id)}").apply { mkdirs() }
        val destination = directory.resolve("${track.fileId}.srt")
        withContext(Dispatchers.IO) {
            val temporary = directory.resolve(".${track.fileId}.part")
            temporary.writeBytes(bytes)
            runCatching {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }
        val now = System.currentTimeMillis()
        captionDao.upsertMovie(movie.toEntity(now))
        captionDao.upsertTrack(
            track.toEntity(
                path = destination.absolutePath,
                now = now,
                rankScore = SubtitleRanker.score(track, movie)
            )
        )
        LocalCaptionTrack(track, movie, destination.absolutePath, entries)
    }

    override suspend fun loadPrepared(featureId: String): Result<List<LocalCaptionTrack>> = runCatching {
        val movieEntity = captionDao.movie(featureId)
            ?: throw CaptionDownloadException(text(R.string.error_prepared_movie_unavailable))
        val movie = movieEntity.toModel()
        captionDao.tracks(featureId).mapNotNull { entity ->
            val file = java.io.File(entity.localPath)
            if (!file.isFile) return@mapNotNull null
            val entries = withContext(Dispatchers.IO) { file.inputStream().use(SrtParser()::parse) }
            if (entries.isEmpty()) null else LocalCaptionTrack(entity.toModel(), movie, entity.localPath, entries)
        }
    }

    override fun observePreparedMovies(): Flow<List<PreparedMovie>> =
        captionDao.observePreparedMovies().map { rows ->
            rows.map { row ->
                PreparedMovie(
                    featureId = row.featureId,
                    title = row.title,
                    year = row.year,
                    trackCount = row.trackCount,
                    preparedAtEpochMs = row.preparedAtEpochMs
                )
            }
        }

    override fun resetSession() {
        service.session.bearerToken = null
    }

    private fun requireConfigured() {
        if (!credentials.load().isConfigured) {
            throw ProviderConfigurationException(text(R.string.error_provider_not_configured))
        }
    }

    private suspend fun ensureLogin() {
        if (!service.session.bearerToken.isNullOrBlank()) return
        val stored = credentials.load()
        if (stored.username.isBlank() || stored.password.isBlank()) return
        val token = retryTransient {
            service.api.login(LoginRequest(stored.username, stored.password))
        }.token
        if (token.isBlank()) {
            throw CaptionDownloadException(text(R.string.error_provider_missing_token))
        }
        service.session.bearerToken = token
    }

    private suspend fun <T> providerRequest(
        authenticated: Boolean = false,
        block: suspend () -> T
    ): T {
        if (authenticated) ensureLogin()
        var authenticationRetried = false
        var transientAttempts = 0
        while (true) {
            try {
                return block()
            } catch (error: HttpException) {
                if (
                    error.code() == 401 &&
                    !service.session.bearerToken.isNullOrBlank() &&
                    !authenticationRetried
                ) {
                    service.session.bearerToken = null
                    authenticationRetried = true
                    if (authenticated) ensureLogin()
                    continue
                }
                if (error.code() in RETRYABLE_CODES && transientAttempts < MAX_PROVIDER_ATTEMPTS - 1) {
                    transientAttempts++
                    delay(RETRY_DELAY_MS * transientAttempts)
                    continue
                }
                throw error
            }
        }
    }

    private suspend fun <T> retryTransient(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (error: HttpException) {
                if (error.code() !in RETRYABLE_CODES || attempt >= MAX_PROVIDER_ATTEMPTS - 1) throw error
                attempt++
                delay(RETRY_DELAY_MS * attempt)
            }
        }
    }

    private suspend fun <T> runProviderCall(block: suspend () -> T): Result<T> = runCatching { block() }
        .recoverCatching { error ->
            if (error is ProviderConfigurationException || error is CaptionDownloadException) throw error
            if (error is IOException) {
                throw CaptionDownloadException(text(R.string.error_provider_offline), error)
            }
            if (error is HttpException) {
                val message = when (error.code()) {
                    401, 403 -> text(R.string.error_provider_authentication)
                    406 -> text(R.string.error_provider_access)
                    429 -> text(R.string.error_provider_quota)
                    503 -> text(R.string.error_provider_unavailable)
                    else -> text(R.string.error_provider_http, error.code())
                }
                throw CaptionDownloadException(message, error)
            }
            throw CaptionDownloadException(
                error.message ?: text(R.string.error_provider_generic),
                error
            )
        }

    private fun text(resourceId: Int, vararg arguments: Any): String =
        context.getString(resourceId, *arguments)

    private fun ByteArray.ungzipIfNeeded(): ByteArray =
        if (size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(this)).use { it.readBytes() }
        } else this

    private fun safePath(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun MovieCandidate.toEntity(now: Long) = MovieEntity(id, title, year, imdbId, tmdbId, now)

    private fun MovieEntity.toModel() = MovieCandidate(featureId, title, year, imdbId, tmdbId)

    private fun CaptionTrack.toEntity(path: String, now: Long, rankScore: Double) = CaptionTrackEntity(
        fileId, subtitleId, featureId, fileName, language, hearingImpaired, trusted,
        foreignPartsOnly, aiTranslated, machineTranslated, ratings, downloadCount,
        release, rankScore, path, now
    )

    private fun CaptionTrackEntity.toModel() = CaptionTrack(
        subtitleId, fileId, featureId, fileName, language, hearingImpaired, trusted,
        foreignPartsOnly, aiTranslated, machineTranslated, ratings, downloadCount, release
    )

    private companion object {
        const val MAX_PROVIDER_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 500L
        val RETRYABLE_CODES = setOf(429, 502, 503, 504)
    }
}
