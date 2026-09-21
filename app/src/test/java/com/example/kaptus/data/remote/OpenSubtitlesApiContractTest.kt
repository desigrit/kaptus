package com.example.kaptus.data.remote

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OpenSubtitlesApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OpenSubtitlesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    encodeDefaults = true
                }
                    .asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(OpenSubtitlesApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun searchesMovieFeaturesAndParsesYear() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {"data":[{"id":"feature-42","type":"feature","attributes":{
                  "title":"Arrival","year":2016,"feature_type":"Movie",
                  "imdb_id":2543164,"tmdb_id":329865}}]}
                """.trimIndent()
            )
        )

        val response = api.searchFeatures("Arrival 2016")
        val request = server.takeRequest()

        assertEquals("Arrival", response.data.single().attributes.title)
        assertEquals("2016", response.data.single().attributes.year?.toString())
        assertEquals("/api/v1/features?query=Arrival%202016", request.path)
    }

    @Test
    fun requestsCaptionsForASpecificTvEpisode() = runTest {
        server.enqueue(jsonResponse("""{"data":[]}"""))

        api.searchSubtitles(
            parentFeatureId = 9001,
            parentImdbId = 123456,
            seasonNumber = 2,
            episodeNumber = 4,
            type = "episode"
        )
        val path = server.takeRequest().requestUrl!!

        assertEquals("9001", path.queryParameter("parent_feature_id"))
        assertEquals("123456", path.queryParameter("parent_imdb_id"))
        assertEquals("2", path.queryParameter("season_number"))
        assertEquals("4", path.queryParameter("episode_number"))
        assertEquals("episode", path.queryParameter("type"))
        assertEquals("en", path.queryParameter("languages"))
    }

    @Test
    fun requestsSrtAndReadsRemainingQuota() = runTest {
        server.enqueue(jsonResponse("""{"link":"https://download.example/caption","remaining":17}"""))

        val response = api.download(DownloadRequest(998L))
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals(17, response.remaining)
        assertEquals("/api/v1/download", request.path)
        assertTrue(body, body.contains("\"sub_format\":\"srt\""))
    }

    @Test
    fun surfacesAuthenticationAndQuotaHttpFailures() = runTest {
        server.enqueue(jsonResponse("""{"message":"unauthorized"}""", 401))
        server.enqueue(jsonResponse("""{"message":"quota exhausted"}""", 429))

        assertEquals(401, httpCode { api.searchFeatures("Movie") })
        assertEquals(429, httpCode { api.download(DownloadRequest(1L)) })
    }

    private suspend fun httpCode(call: suspend () -> Any): Int = try {
        call()
        -1
    } catch (error: HttpException) {
        error.code()
    }

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
