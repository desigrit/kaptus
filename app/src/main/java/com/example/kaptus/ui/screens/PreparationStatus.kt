package com.example.kaptus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kaptus.PreparationState
import com.example.kaptus.R
import com.example.kaptus.ui.theme.KaptusTheme

/** Progress stays in context and always offers a way back to browsing. */
@Composable
fun PreparationStatus(
    state: PreparationState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state is PreparationState.Idle) return
    Box(
        modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()).padding(20.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state) {
                    is PreparationState.Working -> {
                        Text(state.message, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.cancel_preparation))
                            }
                        }
                    }
                    is PreparationState.Failed -> {
                        Text(
                            stringResource(R.string.preparation_failed_title),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Separate rows keep actions accessible at large font scales.
                        FilledTonalButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.retry))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.close))
                            }
                            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.settings))
                            }
                        }
                    }
                    PreparationState.Idle -> Unit
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreparationPreview() {
    KaptusTheme {
        PreparationStatus(
            PreparationState.Working(stringResource(R.string.preparation_finding_captions), 0, 3),
            {}, {}, {}
        )
    }
}
