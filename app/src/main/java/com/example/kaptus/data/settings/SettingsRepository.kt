package com.example.kaptus.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kaptusSettings by preferencesDataStore(name = "kaptus_settings")

data class AppSettings(
    val captionSizeSp: Float = 30f,
    val theaterBrightness: Float = 0.06f,
    val captionLanguage: String = "en",
    val spokenLanguage: String = "en"
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.kaptusSettings.data.map { preferences ->
        AppSettings(
            captionSizeSp = preferences[CAPTION_SIZE].orDefault(30f).coerceIn(24f, 44f),
            theaterBrightness = preferences[THEATER_BRIGHTNESS].orDefault(0.06f).coerceIn(0.01f, 1f),
            captionLanguage = preferences[CAPTION_LANGUAGE] ?: "en",
            spokenLanguage = preferences[SPOKEN_LANGUAGE] ?: "en"
        )
    }

    suspend fun setCaptionSize(sizeSp: Float) {
        context.kaptusSettings.edit { it[CAPTION_SIZE] = sizeSp.coerceIn(24f, 44f) }
    }

    suspend fun setTheaterBrightness(brightness: Float) {
        context.kaptusSettings.edit {
            it[THEATER_BRIGHTNESS] = brightness.coerceIn(0.01f, 1f)
        }
    }

    suspend fun setCaptionLanguage(language: String) {
        require(language.matches(Regex("[a-zA-Z]{2,3}(?:-[a-zA-Z]{2,8})?")) || language == "unknown")
        context.kaptusSettings.edit { it[CAPTION_LANGUAGE] = language.lowercase() }
    }

    suspend fun setSpokenLanguage(language: String) {
        require(language.matches(Regex("[a-zA-Z]{2,3}(?:-[a-zA-Z]{2,8})?")) || language == "unknown")
        context.kaptusSettings.edit { it[SPOKEN_LANGUAGE] = language.lowercase() }
    }

    suspend fun setLanguages(captionLanguage: String, spokenLanguage: String) {
        val code = Regex("[a-zA-Z]{2,3}(?:-[a-zA-Z]{2,8})?")
        require(captionLanguage.matches(code) || captionLanguage == "unknown")
        require(spokenLanguage.matches(code) || spokenLanguage == "unknown")
        context.kaptusSettings.edit {
            it[CAPTION_LANGUAGE] = captionLanguage.lowercase()
            it[SPOKEN_LANGUAGE] = spokenLanguage.lowercase()
        }
    }

    private fun Float?.orDefault(default: Float): Float = this ?: default

    private companion object {
        val CAPTION_SIZE = floatPreferencesKey("caption_size_sp")
        val THEATER_BRIGHTNESS = floatPreferencesKey("theater_brightness")
        val CAPTION_LANGUAGE = stringPreferencesKey("caption_language")
        val SPOKEN_LANGUAGE = stringPreferencesKey("spoken_language")
    }
}
