package com.example.kaptus.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.first
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

    @Test
    fun storesCaptionLanguagesAndHelpersSeparatelyWithoutDeletingEarlierTracks() = runBlocking {
        val dao = database.captionDao()
        dao.upsertMovie(MovieEntity("movie", "Movie", 2025, 1, 2, 100))
        dao.upsertTrack(track(1, 20.0))
        dao.upsertMovie(MovieEntity("movie", "Movie", 2025, 1, 2, 200, spokenLanguage = "ja"))
        dao.upsertTrack(track(2, 20.0).copy(language = "pt-br"))
        dao.upsertTrack(track(3, 20.0).copy(language = "ja", role = "MATCHING_HELPER"))

        assertEquals(listOf(1L), dao.tracks("movie", "en").map { it.fileId })
        assertEquals(listOf(2L), dao.tracks("movie", "pt-br").map { it.fileId })
        assertEquals(emptyList<Long>(), dao.tracks("movie", "ja").map { it.fileId })
        assertEquals(listOf(3L), dao.tracks("movie", "ja", "MATCHING_HELPER").map { it.fileId })
        assertEquals(setOf("en", "pt-br"), dao.observePreparedMovies().first().map { it.captionLanguage }.toSet())
        assertEquals("ja", dao.movie("movie")?.spokenLanguage)
    }

    @Test
    fun episodeIdentityAndContentHashSurviveReload() = runBlocking {
        val dao = database.captionDao()
        dao.upsertMovie(MovieEntity("movie", "Show S02E04", 2025, null, null, 100,
            mediaType = "Episode", parentFeatureId = 700, parentImdbId = 800,
            parentTmdbId = 900, seasonNumber = 2, episodeNumber = 4, spokenLanguage = "ta"))
        dao.upsertTrack(track(1, 20.0).copy(contentHash = "known-content-hash"))
        val movie = dao.movie("movie")!!
        assertEquals("Episode", movie.mediaType)
        assertEquals(700L, movie.parentFeatureId)
        assertEquals(800L, movie.parentImdbId)
        assertEquals(900L, movie.parentTmdbId)
        assertEquals(2, movie.seasonNumber)
        assertEquals(4, movie.episodeNumber)
        assertEquals("known-content-hash", dao.tracks("movie").single().contentHash)
    }

    @Test
    fun oneDownloadedFileCanKeepBothDisplayAndHelperRoles() = runBlocking {
        val dao = database.captionDao()
        dao.upsertMovie(MovieEntity("movie", "Movie", 2025, 1, 2, 100))
        val saved = track(1, 20.0).copy(language = "ja", contentHash = "same-file-hash")
        dao.upsertTrack(saved.copy(role = "MATCHING_HELPER"))
        dao.upsertTrack(saved.copy(role = "DISPLAY"))
        assertEquals(1L, dao.tracks("movie", "ja", "MATCHING_HELPER").single().fileId)
        assertEquals(1L, dao.tracks("movie", "ja", "DISPLAY").single().fileId)
        assertEquals(1, dao.observePreparedMovies().first().single().trackCount)
        assertEquals("same-file-hash", dao.trackFile(1)?.contentHash)
    }

    @Test
    fun upgradesLegacyEnglishHistoryWithoutLosingFilesOrMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "kaptus-language-migration-test.db"
        context.deleteDatabase(name)
        var upgraded: KaptusDatabase? = null
        try {
            val path = context.getDatabasePath(name)
            path.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
                legacy.execSQL("CREATE TABLE movies (featureId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, year INTEGER, imdbId INTEGER, tmdbId INTEGER, preparedAtEpochMs INTEGER NOT NULL)")
                legacy.execSQL("CREATE TABLE caption_tracks (fileId INTEGER NOT NULL PRIMARY KEY, subtitleId TEXT NOT NULL, featureId TEXT NOT NULL, fileName TEXT NOT NULL, language TEXT NOT NULL, hearingImpaired INTEGER NOT NULL, trusted INTEGER NOT NULL, foreignPartsOnly INTEGER NOT NULL, aiTranslated INTEGER NOT NULL, machineTranslated INTEGER NOT NULL, ratings REAL NOT NULL, downloadCount INTEGER NOT NULL, release TEXT NOT NULL, rankScore REAL NOT NULL, localPath TEXT NOT NULL, downloadedAtEpochMs INTEGER NOT NULL, FOREIGN KEY(featureId) REFERENCES movies(featureId) ON DELETE CASCADE)")
                legacy.execSQL("CREATE INDEX index_caption_tracks_featureId ON caption_tracks(featureId)")
                legacy.execSQL("INSERT INTO movies VALUES ('movie','Saved movie',2025,1,2,100)")
                legacy.execSQL("INSERT INTO movies VALUES ('900-s2e4','Saved episode',2025,NULL,NULL,100)")
                legacy.execSQL("INSERT INTO caption_tracks VALUES (1,'subtitle-1','movie','1.srt','en',1,1,0,0,0,8,100,'Movie',20,'/private/1.srt',100)")
                legacy.version = 1
            }
            upgraded = Room.databaseBuilder(context, KaptusDatabase::class.java, name)
                .addMigrations(KaptusDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
            val movie = upgraded.captionDao().movie("movie")!!
            val caption = upgraded.captionDao().tracks("movie").single()
            assertEquals("Saved movie", movie.title)
            assertEquals("en", movie.spokenLanguage)
            assertEquals("Movie", movie.mediaType)
            assertEquals("/private/1.srt", caption.localPath)
            assertEquals("DISPLAY", caption.role)
            val episode = upgraded.captionDao().movie("900-s2e4")!!
            assertEquals("Episode", episode.mediaType)
            assertEquals(900L, episode.parentFeatureId)
            assertEquals(2, episode.seasonNumber)
            assertEquals(4, episode.episodeNumber)
        } finally {
            upgraded?.close()
            context.deleteDatabase(name)
        }
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
