package com.example.kaptus.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KaptusDatabaseTest {
    private lateinit var database: KaptusDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KaptusDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun cachedTracksReloadInProviderRankOrder() = runBlocking {
        val dao = database.captionDao()
        dao.upsertMovie(MovieEntity("movie", "Movie", 2025, 1, 2, 100))
        dao.upsertTrack(track(fileId = 1, rankScore = 20.0))
        dao.upsertTrack(track(fileId = 2, rankScore = 140.0))
        dao.upsertTrack(track(fileId = 3, rankScore = 80.0))

        assertEquals(listOf(2L, 3L, 1L), dao.tracks("movie").map { it.fileId })
    }

    private fun track(fileId: Long, rankScore: Double) = CaptionTrackEntity(
        fileId = fileId,
        subtitleId = "subtitle-$fileId",
        featureId = "movie",
        fileName = "$fileId.srt",
        language = "en",
        hearingImpaired = true,
        trusted = true,
        foreignPartsOnly = false,
        aiTranslated = false,
        machineTranslated = false,
        ratings = 8f,
        downloadCount = 100,
        release = "Movie.2025",
        rankScore = rankScore,
        localPath = "/private/$fileId.srt",
        downloadedAtEpochMs = 100
    )
}
