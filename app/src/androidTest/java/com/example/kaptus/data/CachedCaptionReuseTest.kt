package com.example.kaptus.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kaptus.data.local.CaptionTrackEntity
import com.example.kaptus.data.local.KaptusDatabase
import com.example.kaptus.data.local.MovieEntity
import com.example.kaptus.data.remote.OpenSubtitlesApi
import com.example.kaptus.data.remote.OpenSubtitlesService
import com.example.kaptus.data.remote.OpenSubtitlesSession
import com.example.kaptus.data.settings.SecureCredentialStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class CachedCaptionReuseTest {
    @Test fun aVerifiedHelperCanBecomeDisplayWithoutProviderAccessOrLosingEitherRole() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, KaptusDatabase::class.java).build()
        val file = File.createTempFile("kaptus-reuse-", ".srt", context.cacheDir)
        try {
            val contents = "1\n00:00:01,000 --> 00:00:03,000\n明日は東京へ行こう\n".toByteArray(Charsets.UTF_8)
            file.writeBytes(contents)
            val hash = MessageDigest.getInstance("SHA-256").digest(contents)
                .joinToString("") { "%02x".format(it) }
            val dao = database.captionDao()
            dao.upsertMovie(MovieEntity("reuse-movie", "Synthetic scene", 2026, 1, 2, 100))
            dao.upsertTrack(CaptionTrackEntity(7, "subtitle-7", "reuse-movie", "7.srt", "ja",
                true, true, false, false, false, 8f, 100, "Synthetic scene", 20.0,
                file.absolutePath, 100, "MATCHING_HELPER", hash))
            val calls = AtomicInteger()
            val api = Proxy.newProxyInstance(OpenSubtitlesApi::class.java.classLoader,
                arrayOf(OpenSubtitlesApi::class.java)) { _, method, _ ->
                calls.incrementAndGet()
                error("Unexpected provider request: ${method.name}")
            } as OpenSubtitlesApi
            val repository = OpenSubtitlesRepository(context,
                OpenSubtitlesService(api, OkHttpClient(), OpenSubtitlesSession()), SecureCredentialStore(context), dao)
            val display = CaptionTrack("subtitle-7", 7, "reuse-movie", "7.srt", "ja",
                true, true, false, false, false, 8f, 100, "Synthetic scene")

            val local = repository.download(display).getOrThrow()

            assertEquals(0, calls.get())
            assertEquals(hash, local.contentHash)
            assertEquals("明日は東京へ行こう", local.entries.single().text)
            assertEquals(CaptionTrackRole.DISPLAY, local.track.role)
            assertEquals(7L, dao.tracks("reuse-movie", "ja", "DISPLAY").single().fileId)
            assertEquals(7L, dao.tracks("reuse-movie", "ja", "MATCHING_HELPER").single().fileId)
        } finally {
            database.close()
            file.delete()
        }
    }
}
