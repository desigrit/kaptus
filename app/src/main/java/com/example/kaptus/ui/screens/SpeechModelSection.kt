package com.example.kaptus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kaptus.R
import com.example.kaptus.speech.SpeechModelStatus
import kotlin.math.roundToInt

@Composable
fun SpeechModelSection(status: SpeechModelStatus, onDownload: () -> Unit,
                       onCancel: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.speech_download_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.speech_download_description), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (status) {
            is SpeechModelStatus.Downloading -> {
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.speech_download_progress, (status.progress * 100).roundToInt()),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.speech_download_cancel))
                }
            }
            SpeechModelStatus.Ready -> {
                Text(stringResource(R.string.speech_download_ready), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.speech_download_delete))
                }
            }
            else -> {
                if (status is SpeechModelStatus.Failed) Text(stringResource(R.string.speech_download_failed),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onDownload, modifier = Modifier.heightIn(min = 48.dp).testTag("download_multilingual_model")) {
                    Text(stringResource(if (status is SpeechModelStatus.Failed) R.string.speech_download_retry else R.string.speech_download))
                }
            }
        }
    }
}
