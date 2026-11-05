package com.example.kaptus.data.local

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "movies")
data class MovieEntity(
    @androidx.room.PrimaryKey val featureId: String,
    val title: String,
    val year: Int?,
    val imdbId: Long?,
    val tmdbId: Long?,
    val preparedAtEpochMs: Long,
    @ColumnInfo(defaultValue = "'Movie'") val mediaType: String = "Movie",
    val parentFeatureId: Long? = null,
    val parentImdbId: Long? = null,
    val parentTmdbId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    @ColumnInfo(defaultValue = "'en'") val spokenLanguage: String = "en"
)

@Entity(
    tableName = "caption_tracks",
    primaryKeys = ["fileId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["featureId"],
            childColumns = ["featureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("featureId")]
)
data class CaptionTrackEntity(
    val fileId: Long,
    val subtitleId: String,
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
    val release: String,
    val rankScore: Double,
    val localPath: String,
    val downloadedAtEpochMs: Long,
    @ColumnInfo(defaultValue = "'DISPLAY'") val role: String = "DISPLAY",
    @ColumnInfo(defaultValue = "''") val contentHash: String = ""
)

data class PreparedMovieRow(
    val featureId: String,
    val title: String,
    val year: Int?,
    val preparedAtEpochMs: Long,
    val trackCount: Int,
    val captionLanguage: String,
    val mediaType: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val spokenLanguage: String
)

@Dao
interface CaptionDao {
    // REPLACE would delete the parent and cascade-delete previously saved languages.
    @Upsert
    suspend fun upsertMovie(movie: MovieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: CaptionTrackEntity)

    @Query("SELECT * FROM movies WHERE featureId = :featureId")
    suspend fun movie(featureId: String): MovieEntity?

    @Query("SELECT * FROM caption_tracks WHERE featureId = :featureId AND language = :language AND role = :role ORDER BY rankScore DESC, downloadCount DESC")
    suspend fun tracks(featureId: String, language: String = "en", role: String = "DISPLAY"): List<CaptionTrackEntity>

    @Query("SELECT * FROM caption_tracks WHERE fileId = :fileId ORDER BY role ASC LIMIT 1")
    suspend fun trackFile(fileId: Long): CaptionTrackEntity?

    @Query(
        """
        SELECT movies.featureId, movies.title, movies.year, movies.preparedAtEpochMs,
               COUNT(caption_tracks.fileId) AS trackCount, caption_tracks.language AS captionLanguage,
               movies.mediaType, movies.seasonNumber, movies.episodeNumber, movies.spokenLanguage
        FROM movies
        INNER JOIN caption_tracks ON movies.featureId = caption_tracks.featureId
        WHERE caption_tracks.role = 'DISPLAY'
        GROUP BY movies.featureId, caption_tracks.language
        ORDER BY movies.preparedAtEpochMs DESC
        LIMIT :limit
        """
    )
    fun observePreparedMovies(limit: Int = 12): Flow<List<PreparedMovieRow>>
}

@Database(
    entities = [MovieEntity::class, CaptionTrackEntity::class],
    version = 2,
    exportSchema = true
)
abstract class KaptusDatabase : RoomDatabase() {
    abstract fun captionDao(): CaptionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE movies ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'Movie'")
                database.execSQL("ALTER TABLE movies ADD COLUMN spokenLanguage TEXT NOT NULL DEFAULT 'en'")
                listOf("parentFeatureId", "parentImdbId", "parentTmdbId", "seasonNumber", "episodeNumber")
                    .forEach { database.execSQL("ALTER TABLE movies ADD COLUMN $it INTEGER") }
                database.execSQL("ALTER TABLE caption_tracks ADD COLUMN role TEXT NOT NULL DEFAULT 'DISPLAY'")
                database.execSQL("ALTER TABLE caption_tracks ADD COLUMN contentHash TEXT NOT NULL DEFAULT ''")
                database.execSQL("""
                    CREATE TABLE caption_tracks_new (
                        fileId INTEGER NOT NULL, subtitleId TEXT NOT NULL, featureId TEXT NOT NULL,
                        fileName TEXT NOT NULL, language TEXT NOT NULL, hearingImpaired INTEGER NOT NULL,
                        trusted INTEGER NOT NULL, foreignPartsOnly INTEGER NOT NULL,
                        aiTranslated INTEGER NOT NULL, machineTranslated INTEGER NOT NULL,
                        ratings REAL NOT NULL, downloadCount INTEGER NOT NULL, release TEXT NOT NULL,
                        rankScore REAL NOT NULL, localPath TEXT NOT NULL, downloadedAtEpochMs INTEGER NOT NULL,
                        role TEXT NOT NULL DEFAULT 'DISPLAY', contentHash TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(fileId, role), FOREIGN KEY(featureId) REFERENCES movies(featureId) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO caption_tracks_new
                    SELECT fileId, subtitleId, featureId, fileName, language, hearingImpaired, trusted,
                           foreignPartsOnly, aiTranslated, machineTranslated, ratings, downloadCount,
                           release, rankScore, localPath, downloadedAtEpochMs, role, contentHash
                    FROM caption_tracks
                """.trimIndent())
                database.execSQL("DROP TABLE caption_tracks")
                database.execSQL("ALTER TABLE caption_tracks_new RENAME TO caption_tracks")
                database.execSQL("CREATE INDEX index_caption_tracks_featureId ON caption_tracks(featureId)")
                // Legacy episode IDs retain enough information to recover their provider identity.
                val episodeId = Regex("^(.+)-s(\\d+)e(\\d+)$")
                database.query("SELECT featureId FROM movies").use { rows ->
                    while (rows.moveToNext()) {
                        val id = rows.getString(0)
                        val parts = episodeId.matchEntire(id) ?: continue
                        val season = parts.groupValues[2].toIntOrNull() ?: continue
                        val episode = parts.groupValues[3].toIntOrNull() ?: continue
                        database.execSQL(
                            "UPDATE movies SET mediaType = 'Episode', parentFeatureId = ?, seasonNumber = ?, episodeNumber = ? WHERE featureId = ?",
                            arrayOf(parts.groupValues[1].toLongOrNull(), season, episode, id)
                        )
                    }
                }
            }
        }
    }
}
