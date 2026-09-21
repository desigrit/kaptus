package com.example.kaptus.ui

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kaptus.PlayerUiState
import com.example.kaptus.data.MovieCandidate
import com.example.kaptus.data.PreparedMovie
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.settings.AppSettings
import com.example.kaptus.sync.SyncState
import com.example.kaptus.ui.screens.HomeScreen
import com.example.kaptus.ui.screens.MovieSearchScreen
import com.example.kaptus.ui.screens.PlayerScreen
import com.example.kaptus.ui.theme.KaptusTheme
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CinemaUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun grantMicrophonePermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.RECORD_AUDIO
        )
    }

    @Test
    fun homeShowsSetupAndOfflineReadyMovie() {
        compose.setContent {
            KaptusTheme {
                HomeScreen(
                    providerConfigured = false,
                    preparedMovies = listOf(PreparedMovie("42", "Arrival", 2016, 3, 0L)),
                    onFindMovie = {},
                    onOpenSrt = {},
                    onOpenSettings = {},
                    onOpenPreparedMovie = {}
                )
            }
        }

        compose.onNodeWithText("Connect OpenSubtitles").assertIsDisplayed()
        compose.onNodeWithText("Find a movie").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Arrival · 2016"))
        compose.onNodeWithText("Arrival · 2016").assertIsDisplayed()
        compose.onNodeWithText("3 caption tracks offline").assertIsDisplayed()
    }

    @Test
    fun searchEmbedsLoadingResultAndRetryStates() {
        compose.setContent {
            KaptusTheme {
                MovieSearchScreen(
                    query = "Arrival",
                    results = listOf(MovieCandidate("42", "Arrival", 2016, 2543164, 329865)),
                    isSearching = false,
                    error = "A temporary provider problem occurred.",
                    providerConfigured = true,
                    onBack = {},
                    onOpenSettings = {},
                    onQueryChange = {},
                    onSelectMovie = {},
                    onPrepareForTheater = {}
                )
            }
        }

        compose.onNodeWithText("A temporary provider problem occurred.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("2016"))
        compose.onNodeWithText("2016").assertIsDisplayed()
        compose.onNodeWithTag("movie_result_42").performScrollTo().performClick()
        compose.onNodeWithText("Prepare for theater").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun playerExplainsMicrophoneUseWhenPermissionIsDenied() {
        compose.setContent {
            KaptusTheme {
                PlayerScreen(
                    player = playerState().copy(isPlaying = false, syncState = SyncState.Idle),
                    settings = AppSettings(),
                    onBack = {},
                    onPlayPause = {},
                    onSliderChange = {},
                    onSliderChangeFinished = {},
                    onAdjustCaption = {},
                    onStartSynchronization = {},
                    onResync = {},
                    onMicrophoneDenied = {},
                    onStopForBackground = {},
                    onResumeAfterBackground = {},
                    onCaptionSizeChange = {},
                    onBrightnessChange = {},
                    microphonePermissionOverride = false
                )
            }
        }

        compose.onNodeWithText("Microphone access").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Needs attention").assertIsDisplayed()
    }

    @Test
    fun primaryHomeActionsRemainVisibleAtTwoHundredPercentFontScale() {
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 2f)
            ) {
                KaptusTheme {
                    HomeScreen(
                        providerConfigured = true,
                        preparedMovies = emptyList(),
                        onFindMovie = {},
                        onOpenSrt = {},
                        onOpenSettings = {},
                        onOpenPreparedMovie = {}
                    )
                }
            }
        }

        compose.onNodeWithText("Find a movie").assertIsDisplayed()
        compose.onNodeWithText("Open SRT file").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("History").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun modelBecomingReadyInBackgroundDoesNotStartMicrophone() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        val ready = mutableStateOf(false)
        var starts = 0
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                KaptusTheme {
                    PlayerScreen(
                        player = playerState().copy(modelReady = ready.value), settings = AppSettings(),
                        onBack = {}, onPlayPause = {}, onSliderChange = {},
                        onSliderChangeFinished = {}, onAdjustCaption = {},
                        onStartSynchronization = { starts++ }, onResync = {}, onMicrophoneDenied = {},
                        onStopForBackground = {}, onResumeAfterBackground = {},
                        onCaptionSizeChange = {}, onBrightnessChange = {}, microphonePermissionOverride = true
                    )
                }
            }
        }
        compose.runOnIdle { ready.value = true }
        compose.runOnIdle { assertEquals(0, starts) }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test
    fun playerShowsOneCaptionAndAutoHidesThenRevealsControls() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KaptusTheme {
                PlayerScreen(
                    player = playerState(),
                    settings = AppSettings(),
                    onBack = {},
                    onPlayPause = {},
                    onSliderChange = {},
                    onSliderChangeFinished = {},
                    onAdjustCaption = {},
                    onStartSynchronization = {},
                    onResync = {},
                    onMicrophoneDenied = {},
                    onStopForBackground = {},
                    onResumeAfterBackground = {},
                    onCaptionSizeChange = {},
                    onBrightnessChange = {}
                )
            }
        }

        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("There are days that define your story.").assertIsDisplayed()
        compose.onNodeWithText("Synced").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(4_500)
        compose.onAllNodesWithText("Synced").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Pause").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Settings").assertCountEquals(0)

        compose.onNodeWithTag("player_surface", useUnmergedTree = true)
            .performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun playerShowsCompactTimingControlsAndPersistentOffset() {
        compose.setContent {
            KaptusTheme {
                PlayerScreen(
                    player = playerState().copy(captionDelayMs = 5_500L),
                    settings = AppSettings(),
                    onBack = {},
                    onPlayPause = {},
                    onSliderChange = {},
                    onSliderChangeFinished = {},
                    onAdjustCaption = {},
                    onStartSynchronization = {},
                    onResync = {},
                    onMicrophoneDenied = {},
                    onStopForBackground = {},
                    onResumeAfterBackground = {},
                    onCaptionSizeChange = {},
                    onBrightnessChange = {},
                    autoRequestMicrophone = false,
                    microphonePermissionOverride = true
                )
            }
        }

        compose.onNodeWithText("−0.5s").assertIsDisplayed()
        compose.onNodeWithText("+0.5s").assertIsDisplayed()
        compose.onNodeWithText("+5.5s").assertIsDisplayed()
        compose.onNodeWithContentDescription("Caption timing offset +5.5s").assertIsDisplayed()
    }

    private fun playerState() = PlayerUiState(
        title = "Arrival",
        sourceName = "Arrival.2016.srt",
        subtitles = listOf(SubtitleEntry(1, 0, 4_000, "There are days that define your story.")),
        currentCaption = SubtitleEntry(1, 0, 4_000, "There are days that define your story."),
        currentTimeMs = 1_000,
        totalDurationMs = 7_000_000,
        isPlaying = true,
        syncState = SyncState.Synchronized(0.9f, 0L),
        theaterPrepared = true,
        modelReady = true
    )
}
