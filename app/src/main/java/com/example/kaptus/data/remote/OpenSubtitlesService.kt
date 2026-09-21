package com.example.kaptus.data.remote

import com.example.kaptus.data.settings.SecureCredentialStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class OpenSubtitlesSession {
    @Volatile var bearerToken: String? = null
}

class OpenSubtitlesHeadersInterceptor(
    private val credentials: SecureCredentialStore,
    private val session: OpenSubtitlesSession
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val stored = credentials.load()
        val builder = chain.request().newBuilder()
            .header("User-Agent", "Kaptus v1.0")
            .header("Accept", "application/json")
        if (stored.apiKey.isNotBlank()) builder.header("Api-Key", stored.apiKey)
        session.bearerToken?.let { builder.header("Authorization", "Bearer $it") }
        return chain.proceed(builder.build())
    }
}

data class OpenSubtitlesService(
    val api: OpenSubtitlesApi,
    val downloadClient: OkHttpClient,
    val session: OpenSubtitlesSession
) {
    companion object {
        fun create(credentials: SecureCredentialStore): OpenSubtitlesService {
            val session = OpenSubtitlesSession()
            val apiClient = OkHttpClient.Builder()
                .addInterceptor(OpenSubtitlesHeadersInterceptor(credentials, session))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                encodeDefaults = true
            }
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.opensubtitles.com/")
                .client(apiClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return OpenSubtitlesService(
                api = retrofit.create(OpenSubtitlesApi::class.java),
                downloadClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build(),
                session = session
            )
        }
    }
}
