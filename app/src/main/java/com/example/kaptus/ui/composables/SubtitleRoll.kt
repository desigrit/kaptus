// app/src/main/java/com/example/kaptus/ui/composables/SubtitleRoll.kt

package com.example.kaptus.ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaptus.data.SubtitleEntry
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs

@Composable
fun SubtitleRoll(
    modifier: Modifier = Modifier,
    subtitles: List<SubtitleEntry>,
    currentSubtitle: SubtitleEntry?,
    isPlaying: Boolean,
    onSubtitleSelected: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    // When playback is active, scroll to the current subtitle.
    // This is the ONLY effect that should run when isPlaying = true.
    LaunchedEffect(currentSubtitle) {
        if (isPlaying) {
            currentSubtitle?.let {
                if (!listState.isScrollInProgress) {
                    val index = subtitles.indexOf(it)
                    if (index != -1) {
                        listState.animateScrollToItem(index)
                    }
                }
            }
        }
    }

    // When the user stops a MANUAL scroll, snap to the nearest item and report it.
    // This effect is now much simpler.
    LaunchedEffect(listState.isScrollInProgress) {
        // We only care about this logic when the user is NOT playing.
        if (!listState.isScrollInProgress && !isPlaying) {
            val viewportCenter = listState.layoutInfo.viewportSize.height / 2
            val centerItem = listState.layoutInfo.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - viewportCenter) }

            if (centerItem != null) {
                // Gently snap to the final position
                listState.animateScrollToItem(centerItem.index)
                // Report the selected time to the ViewModel
                onSubtitleSelected(subtitles[centerItem.index].startTimeMs)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        userScrollEnabled = !isPlaying // Disable scrolling when playing
    ) {
        itemsIndexed(subtitles) { index, subtitle ->
            val centerIndex by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val viewportCenter = layoutInfo.viewportSize.height / 2
                    val centerItem = layoutInfo.visibleItemsInfo
                        .minByOrNull { abs(it.offset + it.size / 2 - viewportCenter) }
                    centerItem?.index ?: -1
                }
            }

            // Determine the visual state of the item
            val scale: Float
            val alpha: Float
            val color: Color
            val fontSize: TextUnit

            when {
                index == centerIndex -> {
                    scale = 1f
                    alpha = 1f
                    color = MaterialTheme.colorScheme.onSurface
                    fontSize = 24.sp
                }
                abs(index - centerIndex) == 1 -> {
                    scale = 0.85f
                    alpha = 0.6f
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                    fontSize = 16.sp
                }
                else -> { // Hide other items
                    scale = 0.85f
                    alpha = 0f
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                    fontSize = 16.sp
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
    }
}