// app/src/main/java/com/example/kaptus/ui/composables/SubtitleRoll.kt

package com.example.kaptus.ui.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaptus.data.SubtitleEntry
import kotlin.math.abs

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SubtitleRoll(
    modifier: Modifier = Modifier,
    previousSubtitle: SubtitleEntry?,
    currentSubtitle: SubtitleEntry?,
    nextSubtitle: SubtitleEntry?,
    lastActiveSubtitle: SubtitleEntry?,
    onSeek: (String) -> Unit
) {
    var dragY by remember { mutableStateOf(0f) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragY += dragAmount
                    },
                    onDragEnd = {
                        if (abs(dragY) > 50) {
                            if (dragY < 0) onSeek("up") else onSeek("down")
                        }
                        dragY = 0f
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            previousSubtitle?.let {
                Text(
                    text = it.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp).alpha(0.7f)
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentSubtitle ?: lastActiveSubtitle,
                transitionSpec = {
                    if ((targetState?.index ?: 0) > (initialState?.index ?: 0)) {
                        slideInVertically { h -> h } + fadeIn() with
                                slideOutVertically { h -> -h } + fadeOut()
                    } else {
                        slideInVertically { h -> -h } + fadeIn() with
                                slideOutVertically { h -> h } + fadeOut()
                    }.using(SizeTransform(clip = false))
                },
                label = "SubtitleText"
            ) { subtitle ->
                if (subtitle != null) {
                    val isCurrent = subtitle == currentSubtitle
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = subtitle.text,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            nextSubtitle?.let {
                Text(
                    text = it.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp).alpha(0.7f)
                )
            }
        }
    }
}