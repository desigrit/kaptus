package com.example.kaptus.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "movies")
data class MovieEntity(
    @androidx.room.PrimaryKey val featureId: String,
    val title: String,
    val year: Int?,
    val imdbId: Long?,
    val tmdbId: Long?,
    val preparedAtEpochMs: Long
)

@Entity(
    tableName = "caption_tracks",
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
    @androidx.room.PrimaryKey val fileId: Long,
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
    val downloadedAtEpochMs: Long
)

data class PreparedMovieRow(
    val featureId: String,
    val title: String,
    val year: Int?,
    val preparedAtEpochMs: Long,
    val trackCount: Int
)

@Dao
interface CaptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovie(movie: MovieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: CaptionTrackEntity)

    @Query("SELECT * FROM movies WHERE featureId = :featureId")
    suspend fun movie(featureId: String): MovieEntity?

    @Query("SELECT * FROM caption_tracks WHERE featureId = :featureId ORDER BY rankScore DESC, downloadCount DESC")
    suspend fun tracks(featureId: String): List<CaptionTrackEntity>

    @Query(
        """
        SELECT movies.featureId, movies.title, movies.year, movies.preparedAtEpochMs,
               COUNT(caption_tracks.fileId) AS trackCount
        FROM movies
        INNER JOIN caption_tracks ON movies.featureId = caption_tracks.featureId
        GROUP BY movies.featureId
        ORDER BY movies.preparedAtEpochMs DESC
        LIMIT :limit
        """
    )
    fun observePreparedMovies(limit: Int = 12): Flow<List<PreparedMovieRow>>
}

@Database(
    entities = [MovieEntity::class, CaptionTrackEntity::class],
    version = 1,
    exportSchema = true
)
abstract class KaptusDatabase : RoomDatabase() {
    abstract fun captionDao(): CaptionDao
}
