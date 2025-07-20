// app/src/main/java/com/example/kaptus/PlaybackControls.kt

package com.example.kaptus

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlaybackControls(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    currentTimeMs: Long,
    totalDurationMs: Long,
    sliderPosition: Float,
    onPlayPauseClick: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Slider and Time Row
            Box(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPosition,
                    onValueChange = onSliderChange,
                    onValueChangeFinished = onSliderChangeFinished,
                    valueRange = 0f..totalDurationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentTimeMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "-${formatTime(totalDurationMs - currentTimeMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -5s
                Button(onClick = { onSeek(-5000) }, modifier = Modifier.size(48.dp)) {
                    Text("-5")
                }
                // -1s
                Button(onClick = { onSeek(-1000) }, modifier = Modifier.size(56.dp)) {
                    Text("-1")
                }
                // Play/Pause
                IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }
                // +1s
                Button(onClick = { onSeek(1000) }, modifier = Modifier.size(56.dp)) {
                    Text("+1")
                }
                // +5s
                Button(onClick = { onSeek(5000) }, modifier = Modifier.size(48.dp)) {
                    Text("+5")
                }
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        // Remove leading zero from hours
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}