package com.example.kaptus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kaptus.R
import com.example.kaptus.data.CaptionLanguage
import java.util.Locale

fun captionLanguageName(code: String, languages: List<CaptionLanguage>): String =
    languages.firstOrNull { it.code == code }?.name
        ?: Locale.forLanguageTag(code).getDisplayName(Locale.getDefault()).takeIf { it.isNotBlank() }
        ?: code

@Composable
fun LanguageSelection(
    languages: List<CaptionLanguage>,
    captionLanguage: String,
    spokenLanguage: String,
    onCaptionLanguageChange: (String) -> Unit,
    onSpokenLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowUnknown: Boolean = false,
    enabled: Boolean = true
) {
    var choosingCaptions by remember { mutableStateOf<Boolean?>(null) }
    val unknown = stringResource(R.string.language_unknown)
    val mandarin = stringResource(R.string.language_mandarin)
    val cantonese = stringResource(R.string.language_cantonese)
    val captionOptions = remember(languages, allowUnknown, unknown) {
        (if (allowUnknown) listOf(CaptionLanguage("und", unknown)) else emptyList()) + languages
    }
    val spokenOptions = remember(languages, mandarin, cantonese) {
        (languages.filterNot { it.code.startsWith("zh") || it.code == "ze" || it.code == "yue" } +
            listOf(CaptionLanguage("zh", mandarin), CaptionLanguage("yue", cantonese)))
            .distinctBy { it.code }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LanguageRow(stringResource(R.string.caption_language),
            if (captionLanguage == "und") unknown else captionLanguageName(captionLanguage, languages),
            enabled, "caption_language") { choosingCaptions = true }
        LanguageRow(stringResource(R.string.spoken_language),
            captionLanguageName(spokenLanguage, spokenOptions), enabled, "spoken_language") { choosingCaptions = false }
    }
    choosingCaptions?.let { captions ->
        LanguagePicker(
            title = stringResource(if (captions) R.string.caption_language else R.string.spoken_language),
            languages = if (captions) captionOptions else spokenOptions,
            selected = if (captions) captionLanguage else spokenLanguage,
            onSelect = {
                if (captions) onCaptionLanguageChange(it) else onSpokenLanguageChange(it)
                choosingCaptions = null
            }, onDismiss = { choosingCaptions = null }
        )
    }
}

@Composable
private fun LanguageRow(label: String, value: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(tag).semantics { stateDescription = value },
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(title: String, languages: List<CaptionLanguage>, selected: String,
                           onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(languages, query) {
        languages.filter { it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).semantics { heading() })
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.language_search)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("language_search"))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(vertical = 12.dp)) {
            if (filtered.isEmpty()) item { Text(stringResource(R.string.language_empty), Modifier.padding(24.dp)) }
            items(filtered, key = { it.code }) { language ->
                ListItem(headlineContent = { Text(language.name) },
                    supportingContent = { Text(language.code) },
                    trailingContent = { if (language.code == selected) Icon(Icons.Outlined.Check, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(role = Role.RadioButton) { onSelect(language.code) }
                        .semantics { this.selected = language.code == selected })
            }
        }
    }
}
