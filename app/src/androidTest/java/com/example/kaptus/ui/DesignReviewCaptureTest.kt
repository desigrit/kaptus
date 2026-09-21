package com.example.kaptus.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kaptus.PlayerUiState
import com.example.kaptus.data.MovieCandidate
import com.example.kaptus.data.PreparedMovie
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.settings.AppSettings
import com.example.kaptus.data.settings.ProviderCredentials
import com.example.kaptus.sync.SyncState
import com.example.kaptus.ui.screens.HomeScreen
import com.example.kaptus.ui.screens.MovieSearchScreen
import com.example.kaptus.ui.screens.PlayerScreen
import com.example.kaptus.ui.screens.SettingsScreen
import com.example.kaptus.ui.theme.KaptusTheme
import com.example.kaptus.ui.theme.KaptusVisualStyle
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.io.File

/** Native review fixtures. Movie metadata here is illustrative, with no provider calls. */
class DesignReviewCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun useRequestedViewport() {
        val landscape = InstrumentationRegistry.getArguments().getString("reviewViewport") == "landscape"
        val orientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        compose.runOnUiThread { compose.activity.requestedOrientation = orientation }
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
    }

    @Test
    fun homeDirections() {
        val style = mutableStateOf(KaptusVisualStyle.CinemaAmber)
        compose.setContent {
            KaptusTheme(style.value) {
                HomeScreen(
                    providerConfigured = true,
                    preparedMovies = listOf(
                        PreparedMovie("1", "Arrival", 2016, 3, 0),
                        PreparedMovie("2", "The Grand Budapest Hotel", 2014, 3, 0),
                        PreparedMovie("3", "Interstellar", 2014, 1, 0)
                    ),
                    onFindMovie = {}, onOpenSrt = {}, onOpenSettings = {},
                    onOpenPreparedMovie = {}, visualStyle = style.value
                )
            }
        }
        KaptusVisualStyle.entries.forEach { direction ->
            compose.runOnIdle { style.value = direction }
            compose.waitForIdle()
            capture("home_${direction.name}")
        }
    }

    @Test
    fun sharedScreens() {
        val page = mutableStateOf("search")
        compose.setContent {
            KaptusTheme {
                when (page.value) {
                    "search" -> MovieSearchScreen(
                        query = "Arrival", results = listOf(
                            MovieCandidate("1", "Arrival", 2016, null, null),
                            MovieCandidate("2", "The Arrival", 1996, null, null)
                        ), isSearching = false, error = null, providerConfigured = true,
                        onBack = {}, onOpenSettings = {}, onQueryChange = {},
                        onSelectMovie = {}, onPrepareForTheater = {}
                    )
                    "settings" -> SettingsScreen(
                        credentials = ProviderCredentials("preview-key", "", ""), settings = AppSettings(),
                        isTesting = false, onBack = {}, onSaveCredentials = { _, _, _ -> },
                        onTestConnection = {}, onCaptionSizeChange = {}, onBrightnessChange = {}
                    )
                    else -> PlayerScreen(
                        player = samplePlayer(), settings = AppSettings(theaterBrightness = 0.5f),
                        onBack = {}, onPlayPause = {}, onSliderChange = {},
                        onSliderChangeFinished = {}, onAdjustCaption = {}, onStartSynchronization = {},
                        onResync = {}, onMicrophoneDenied = {}, onStopForBackground = {},
                        onResumeAfterBackground = {}, onCaptionSizeChange = {}, onBrightnessChange = {},
                        autoRequestMicrophone = false, microphonePermissionOverride = true
                    )
                }
            }
        }
        compose.onNodeWithTag("movie_result_1").performClick()
        capture("search_selected")
        compose.runOnIdle { page.value = "settings" }
        capture("settings")
        compose.runOnIdle { page.value = "player" }
        capture("player_controls")
        compose.onNodeWithContentDescription("Settings").performClick()
        capture("player_viewing_options")
    }

    @Test
    fun enlargedText() {
        val showPlayer = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                KaptusTheme {
                    if (!showPlayer.value) HomeScreen(
                        providerConfigured = false, preparedMovies = emptyList(),
                        onFindMovie = {}, onOpenSrt = {}, onOpenSettings = {}, onOpenPreparedMovie = {}
                    )
                    else PlayerScreen(
                        player = samplePlayer(), settings = AppSettings(theaterBrightness = 0.5f),
                        onBack = {}, onPlayPause = {}, onSliderChange = {},
                        onSliderChangeFinished = {}, onAdjustCaption = {}, onStartSynchronization = {},
                        onResync = {}, onMicrophoneDenied = {}, onStopForBackground = {},
                        onResumeAfterBackground = {}, onCaptionSizeChange = {}, onBrightnessChange = {},
                        autoRequestMicrophone = false, microphonePermissionOverride = true
                    )
                }
            }
        }
        compose.onNodeWithText("Find a movie or TV show").assertIsDisplayed()
        compose.onNodeWithText("Open SRT file").performScrollTo().assertIsDisplayed()
        capture("home_large_text")
        compose.runOnIdle { showPlayer.value = true }
        compose.onNodeWithText("The desert carries every whispered promise.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        capture("player_large_text")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Compose idleness precedes the system compositor and native window animations.
        SystemClock.sleep(700)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val variant = InstrumentationRegistry.getArguments().getString("reviewViewport") ?: "phone"
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${variant}_$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kaptus")
        }
        val uri = instrumentation.targetContext.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Could not create screenshot")
        instrumentation.targetContext.contentResolver.openOutputStream(uri)!!.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun samplePlayer(): PlayerUiState {
        val caption = SubtitleEntry(1, 0, 4_000, "The desert carries every whispered promise.")
        return PlayerUiState(
            title = "Dune: Part Two", sourceName = "Dune.Part.Two.2024.BluRay.en.srt",
            subtitles = listOf(caption), currentCaption = caption, currentTimeMs = 1_240_000,
            totalDurationMs = 6_960_000, isPlaying = false,
            syncState = SyncState.Synchronized(0.92f, 0), theaterPrepared = true, modelReady = true
        )
    }
}
