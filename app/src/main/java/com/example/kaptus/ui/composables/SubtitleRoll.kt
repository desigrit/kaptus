// app/src/main/java/com/example/kaptus/ui/composables/SubtitleRoll.kt

package com.example.kaptus.ui.composables

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaptus.data.SubtitleEntry

@Composable
fun SubtitleRoll(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    subtitles: List<SubtitleEntry>,
    visibleSubtitle: SubtitleEntry?,
    height: Dp,
    flingBehavior: FlingBehavior,
    isPlaying: Boolean
) {
    val currentSubtitleIndex = subtitles.indexOf(visibleSubtitle)
    val spacerHeight = height / 2

    LazyColumn(
        state = listState,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        flingBehavior = flingBehavior,
        userScrollEnabled = !isPlaying
    ) {
        item { Spacer(modifier = Modifier.height(spacerHeight)) }

        itemsIndexed(subtitles) { index, subtitle ->
            val scale: Float
            val alpha: Float
            val color: Color
            val fontSize: TextUnit

            when {
                index == currentSubtitleIndex -> {
                    scale = 1f; alpha = 1f; color = MaterialTheme.colorScheme.onSurface; fontSize = 24.sp
                }
                kotlin.math.abs(index - currentSubtitleIndex) == 1 -> {
                    scale = 0.85f; alpha = 0.6f; color = MaterialTheme.colorScheme.onSurfaceVariant; fontSize = 16.sp
                }
                else -> {
                    scale = 0.85f; alpha = 0f; color = MaterialTheme.colorScheme.onSurfaceVariant; fontSize = 16.sp
                }
            }

            Text(
                text = subtitle.text,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .scale(scale)
                    .alpha(alpha),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = fontSize,
                color = color
            )
        }
        item { Spacer(modifier = Modifier.height(spacerHeight)) }
    }
}