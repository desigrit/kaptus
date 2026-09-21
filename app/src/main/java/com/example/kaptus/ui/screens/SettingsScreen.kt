package com.example.kaptus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaptus.R
import com.example.kaptus.data.settings.AppSettings
import com.example.kaptus.data.settings.ProviderCredentials
import com.example.kaptus.ui.theme.KaptusTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    credentials: ProviderCredentials,
    settings: AppSettings,
    isTesting: Boolean,
    onBack: () -> Unit,
    onSaveCredentials: (String, String, String) -> Unit,
    onTestConnection: () -> Unit,
    onCaptionSizeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Secrets intentionally stay out of saved instance state.
    var apiKey by remember(credentials.apiKey) { mutableStateOf(credentials.apiKey) }
    var username by remember(credentials.username) { mutableStateOf(credentials.username) }
    var password by remember(credentials.password) { mutableStateOf(credentials.password) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var captionSize by remember(settings.captionSizeSp) { mutableFloatStateOf(settings.captionSizeSp) }
    var brightness by remember(settings.theaterBrightness) { mutableFloatStateOf(settings.theaterBrightness) }
    val focusManager = LocalFocusManager.current
    val hasChanges = apiKey.trim() != credentials.apiKey ||
        username.trim() != credentials.username || password != credentials.password
    val testing = isTesting
    // Keep the entry order stable while credentials are edited or saved.
    val providerFirst = remember { !credentials.isConfigured }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            fun LazyListScope.appearanceItems() {
                item(key = "appearance_heading") {
                    SettingsSectionHeading(
                        title = stringResource(R.string.caption_appearance),
                        description = stringResource(R.string.settings_appearance_description)
                    )
                }
                item(key = "caption_preview") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text(
                                stringResource(R.string.settings_caption_preview),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFB8B8B8)
                            )
                            Text(
                                text = stringResource(R.string.settings_caption_preview_text),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = captionSize.sp,
                                    lineHeight = (captionSize * 1.25f).sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                item(key = "text_size") {
                    SettingsSlider(
                        label = stringResource(R.string.text_size),
                        valueLabel = stringResource(R.string.caption_size_short, captionSize.roundToInt()),
                        description = stringResource(R.string.settings_size_description),
                        value = captionSize,
                        onValueChange = { captionSize = it },
                        onValueChangeFinished = { onCaptionSizeChange(captionSize) },
                        valueRange = 24f..44f,
                        steps = 19,
                        tag = "settings_caption_size"
                    )
                }
                item(key = "brightness") {
                    SettingsSlider(
                        label = stringResource(R.string.settings_brightness_label),
                        valueLabel = stringResource(R.string.brightness_short, (brightness * 100).roundToInt()),
                        description = stringResource(R.string.settings_brightness_description),
                        value = brightness,
                        onValueChange = { brightness = it },
                        onValueChangeFinished = { onBrightnessChange(brightness) },
                        valueRange = 0.01f..1f,
                        tag = "settings_brightness"
                    )
                }
            }

            fun LazyListScope.providerItems() {
                item(key = "provider_heading") {
                    SettingsSectionHeading(
                        title = stringResource(R.string.settings_provider_title),
                        description = stringResource(R.string.settings_provider_description)
                    )
                }
                item(key = "credentials") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth().testTag("settings_api_key"),
                            enabled = !testing,
                            label = { Text(stringResource(R.string.api_key)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = CredentialKeyboardOptions,
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            trailingIcon = {
                                CredentialVisibilityButton(
                                    visible = apiKeyVisible,
                                    enabled = !testing,
                                    onClick = { apiKeyVisible = !apiKeyVisible },
                                    contentDescription = stringResource(
                                        if (apiKeyVisible) R.string.settings_hide_api_key else R.string.settings_show_api_key
                                    )
                                )
                            }
                        )
                        Text(
                            stringResource(R.string.settings_optional_account),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth().testTag("settings_username"),
                            enabled = !testing,
                            label = { Text(stringResource(R.string.username_optional)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = CredentialKeyboardOptions.copy(keyboardType = KeyboardType.Ascii),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth().testTag("settings_password"),
                            enabled = !testing,
                            label = { Text(stringResource(R.string.password_optional)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = CredentialKeyboardOptions.copy(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                CredentialVisibilityButton(
                                    visible = passwordVisible,
                                    enabled = !testing,
                                    onClick = { passwordVisible = !passwordVisible },
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.hide_password else R.string.show_password
                                    )
                                )
                            }
                        )
                    }
                }
                item(key = "credential_actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!hasChanges && credentials.isConfigured) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                stringResource(
                                    when {
                                        hasChanges -> R.string.settings_unsaved_changes
                                        credentials.isConfigured -> R.string.settings_credentials_saved
                                        else -> R.string.settings_provider_not_added
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSaveCredentials(apiKey, username, password)
                            },
                            enabled = hasChanges && !testing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("settings_save")
                        ) {
                            Text(
                                stringResource(
                                    if (credentials.isConfigured && apiKey.isBlank()) {
                                        R.string.settings_remove_credentials
                                    } else {
                                        R.string.settings_save_changes
                                    }
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                onSaveCredentials(apiKey, username, password)
                                onTestConnection()
                            },
                            enabled = apiKey.isNotBlank() && !testing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("settings_test_connection")
                        ) {
                            if (testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 12.dp).size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                stringResource(
                                    when {
                                        testing -> R.string.testing_connection
                                        hasChanges -> R.string.settings_save_and_test
                                        else -> R.string.test_connection
                                    }
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                item(key = "credential_privacy") {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.settings_credentials_privacy),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().testTag("settings_list"),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (providerFirst) providerItems() else appearanceItems()
                item(key = "section_divider") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (providerFirst) appearanceItems() else providerItems()
            }
        }
    }
}

private val CredentialKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Password,
    imeAction = ImeAction.Next
)

@Composable
private fun SettingsSectionHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    valueLabel: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    tag: String,
    steps: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).semantics {
                contentDescription = label
                stateDescription = valueLabel
            }
        )
    }
}

@Composable
private fun CredentialVisibilityButton(
    visible: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = contentDescription
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF111112)
@Composable
private fun SettingsPreview() {
    SettingsPreviewContent(ProviderCredentials("preview-key", "caption-fan", "preview-password"))
}

@Preview(showBackground = true, backgroundColor = 0xFF111112, fontScale = 2f)
@Composable
private fun SettingsLargeTextPreview() {
    SettingsPreviewContent(ProviderCredentials())
}

@Composable
private fun SettingsPreviewContent(credentials: ProviderCredentials) {
    KaptusTheme {
        SettingsScreen(
            credentials = credentials,
            settings = AppSettings(),
            isTesting = false,
            onBack = {},
            onSaveCredentials = { _, _, _ -> },
            onTestConnection = {},
            onCaptionSizeChange = {},
            onBrightnessChange = {}
        )
    }
}
