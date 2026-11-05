package com.example.kaptus

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.kaptus.data.OpenSubtitlesRepository
import com.example.kaptus.data.SubtitleRepository
import com.example.kaptus.data.local.KaptusDatabase
import com.example.kaptus.data.remote.OpenSubtitlesService
import com.example.kaptus.data.settings.SecureCredentialStore
import com.example.kaptus.data.settings.SettingsRepository
import com.example.kaptus.speech.SpeechModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KaptusApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val credentials = SecureCredentialStore(applicationContext)
    val settings = SettingsRepository(applicationContext)
    val speechModels = SpeechModelManager(applicationContext)
    private val database = Room.databaseBuilder(
        applicationContext,
        KaptusDatabase::class.java,
        "kaptus.db"
    ).addMigrations(KaptusDatabase.MIGRATION_1_2).build()
    val subtitles: SubtitleRepository = OpenSubtitlesRepository(
        context = applicationContext,
        service = OpenSubtitlesService.create(credentials),
        credentials = credentials,
        captionDao = database.captionDao()
    )
}
