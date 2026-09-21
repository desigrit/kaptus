package com.example.kaptus.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.kaptus.data.settings.AppSettings
import com.example.kaptus.data.settings.ProviderCredentials
import com.example.kaptus.ui.screens.SettingsScreen
import com.example.kaptus.ui.theme.KaptusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun testingAnEditedFormSavesCurrentValuesBeforeTesting() {
        val events = mutableListOf<String>()
        var submitted: ProviderCredentials? = null
        compose.setContent {
            KaptusTheme {
                SettingsScreen(
                    credentials = ProviderCredentials("old-key", "old-user", "old-password"),
                    settings = AppSettings(),
                    isTesting = false,
                    onBack = {},
                    onSaveCredentials = { key, username, password ->
                        events += "save"
                        submitted = ProviderCredentials(key, username, password)
                    },
                    onTestConnection = { events += "test" },
                    onCaptionSizeChange = {},
                    onBrightnessChange = {}
                )
            }
        }

        replaceField("settings_api_key", "current-key")
        replaceField("settings_username", "current-user")
        replaceField("settings_password", "current-password")
        compose.onNodeWithTag("settings_password").performImeAction()
        scrollTo("settings_test_connection")
        compose.onNodeWithTag("settings_test_connection").assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals(listOf("save", "test"), events)
            assertEquals(
                ProviderCredentials("current-key", "current-user", "current-password"),
                submitted
            )
        }
    }

    @Test
    fun unchangedFormCannotSaveAndConnectionTestingDisablesEditsAndActions() {
        val isTesting = mutableStateOf(false)
        val events = mutableListOf<String>()
        compose.setContent {
            KaptusTheme {
                SettingsScreen(
                    credentials = ProviderCredentials("saved-key", "saved-user", "saved-password"),
                    settings = AppSettings(),
                    isTesting = isTesting.value,
                    onBack = {},
                    onSaveCredentials = { _, _, _ -> events += "save" },
                    onTestConnection = { events += "test" },
                    onCaptionSizeChange = {},
                    onBrightnessChange = {}
                )
            }
        }

        scrollTo("settings_save")
        compose.onNodeWithTag("settings_save").assertIsNotEnabled()
        scrollTo("settings_test_connection")
        compose.onNodeWithTag("settings_test_connection").assertIsEnabled()

        replaceField("settings_api_key", "edited-key")
        // Finish the form's IME session so focus cannot scroll the API field back
        // into view while the test moves to the action buttons.
        scrollTo("settings_password")
        compose.onNodeWithTag("settings_password").performClick().performImeAction()
        scrollTo("settings_save")
        compose.onNodeWithTag("settings_save").assertIsEnabled()
        compose.runOnIdle { isTesting.value = true }

        listOf("settings_api_key", "settings_username", "settings_password").forEach { tag ->
            scrollTo(tag)
            compose.onNodeWithTag(tag).assertIsNotEnabled()
        }
        scrollTo("settings_api_key")
        compose.onNodeWithContentDescription("Show API key").assertIsNotEnabled()
        scrollTo("settings_password")
        compose.onNodeWithContentDescription("Show password").assertIsNotEnabled()
        scrollTo("settings_save")
        compose.onNodeWithTag("settings_save").assertIsNotEnabled()
        scrollTo("settings_test_connection")
        compose.onNodeWithTag("settings_test_connection").assertIsNotEnabled()

        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }

    private fun replaceField(tag: String, value: String) {
        scrollTo(tag)
        compose.onNodeWithTag(tag).performTextReplacement(value)
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
    }
}
