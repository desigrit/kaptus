package com.example.kaptus.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.example.kaptus.PlayerUiState
import com.example.kaptus.data.CaptionLanguage
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.data.settings.AppSettings
import com.example.kaptus.ui.screens.LanguageSelection
import com.example.kaptus.ui.screens.PlayerScreen
import com.example.kaptus.ui.theme.KaptusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MultilingualUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unsupportedPairKeepsCaptionsAndNeverRequestsRecognition() {
        var starts = 0
        val cue = SubtitleEntry(1, 0, 10_000, "مرحبا بكم")
        compose.setContent {
            KaptusTheme {
                PlayerScreen(
                    player = PlayerUiState("Original dialogue", "captions.srt", listOf(cue),
                        currentCaption = cue, totalDurationMs = 10_000, theaterPrepared = true,
                        modelReady = false, captionLanguage = "ar", spokenLanguage = "es", autoSeekAvailable = false),
                    settings = AppSettings(), onBack = {}, onPlayPause = {}, onSliderChange = {},
                    onSliderChangeFinished = {}, onAdjustCaption = {}, onStartSynchronization = { starts++ },
                    onResync = { starts++ }, onMicrophoneDenied = {}, onStopForBackground = {},
                    onResumeAfterBackground = {}, onCaptionSizeChange = {}, onBrightnessChange = {},
                    microphonePermissionOverride = false
                )
            }
        }
        compose.onNodeWithText("Manual timing").assertIsDisplayed()
        compose.onNodeWithText("مرحبا بكم").assertIsDisplayed()
        compose.onNodeWithText("Start sync").assertDoesNotExist()
        compose.onNodeWithText("Allow microphone").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, starts) }
    }

    @Test fun languageControlsRespectTwoHundredPercentFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                KaptusTheme {
                    LanguageSelection(listOf(CaptionLanguage("en", "English"), CaptionLanguage("ta", "Tamil")),
                        "ta", "en", {}, {})
                }
            }
        }
        compose.onNodeWithText("Caption language").assertIsDisplayed()
        compose.onNodeWithText("Tamil").assertIsDisplayed()
        compose.onNodeWithText("Spoken language").assertIsDisplayed()
    }
}
