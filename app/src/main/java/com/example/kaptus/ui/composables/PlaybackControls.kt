// app/src/main/java/com/example/kaptus/ui/composables/PlaybackControls.kt

package com.example.kaptus.ui.composables

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaptus.utils.formatTime

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
    orientation: Int
) {
    val verticalPadding = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 8.dp else 16.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = verticalPadding)
        ) {
            Slider(
                value = sliderPosition,
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderChangeFinished,
                valueRange = 0f..totalDurationMs.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
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
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSeek(-5000) }, modifier = Modifier.size(48.dp)) {
                    SeekIcon(text = "-5")
                }
                IconButton(onClick = { onSeek(-1000) }, modifier = Modifier.size(60.dp)) {
                    SeekIcon(text = "-1")
                }
                val buttonShapeCornerRadius by animateDpAsState(
                    targetValue = if (isPlaying) 8.dp else 36.dp,
                    animationSpec = tween(durationMillis = 400),
                    label = "PlayPauseButtonShape"
                )
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(buttonShapeCornerRadius)
                        )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                IconButton(onClick = { onSeek(1000) }, modifier = Modifier.size(60.dp)) {
                    SeekIcon(text = "+1")
                }
                IconButton(onClick = { onSeek(5000) }, modifier = Modifier.size(48.dp)) {
                    SeekIcon(text = "+5")
                }
            }
        }
    }
}

@Composable
private fun SeekIcon(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp, // Increased font size for better visibility
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}