package com.example.kaptus.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OpenSubtitlesApi {
    @GET("api/v1/features")
    suspend fun searchFeatures(
        @Query("query") query: String,
        @Query("type") type: String = "movie"
    ): FeatureResponse

    @GET("api/v1/subtitles")
    suspend fun searchSubtitles(
        @Query("imdb_id") imdbId: Long? = null,
        @Query("languages") languages: String = "en",
        @Query("query") query: String? = null,
        @Query("type") type: String = "movie"
    ): SubtitleResponse

    @POST("api/v1/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/v1/download")
    suspend fun download(@Body request: DownloadRequest): DownloadResponse
}

@Serializable
data class FeatureResponse(val data: List<FeatureDto> = emptyList())

@Serializable
data class FeatureDto(
    val id: JsonElement,
    val type: String = "",
    val attributes: FeatureAttributesDto = FeatureAttributesDto()
)

@Serializable
data class FeatureAttributesDto(
    val title: String = "",
    val year: JsonElement? = null,
    @SerialName("feature_type") val featureType: String = "",
    @SerialName("imdb_id") val imdbId: Long? = null,
    @SerialName("tmdb_id") val tmdbId: Long? = null
)

@Serializable
data class SubtitleResponse(val data: List<SubtitleDto> = emptyList())

@Serializable
data class SubtitleDto(
    val id: JsonElement,
    val attributes: SubtitleAttributesDto = SubtitleAttributesDto()
)

@Serializable
data class SubtitleAttributesDto(
    @SerialName("subtitle_id") val subtitleId: JsonElement? = null,
    val language: String = "",
    @SerialName("download_count") val downloadCount: Long = 0,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerialName("from_trusted") val trusted: Boolean = false,
    @SerialName("foreign_parts_only") val foreignPartsOnly: Boolean = false,
    @SerialName("ai_translated") val aiTranslated: Boolean = false,
    @SerialName("machine_translated") val machineTranslated: Boolean = false,
    val ratings: Float = 0f,
    val release: String = "",
    val files: List<SubtitleFileDto> = emptyList()
)

@Serializable
data class SubtitleFileDto(
    @SerialName("file_id") val fileId: Long,
    @SerialName("file_name") val fileName: String = "caption.srt"
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val token: String = "")

@Serializable
data class DownloadRequest(
    @SerialName("file_id") val fileId: Long,
    @SerialName("sub_format") val subtitleFormat: String = "srt"
)

@Serializable
data class DownloadResponse(
    val link: String = "",
    val remaining: Int? = null,
    val message: String? = null
)
