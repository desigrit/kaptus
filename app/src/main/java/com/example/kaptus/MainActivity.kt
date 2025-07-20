package com.example.kaptus

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaptus.ui.theme.KaptusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class SubtitleEntry(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val startTime: String,
    val endTime: String,
    val text: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaptusTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var subtitles by remember { mutableStateOf<List<SubtitleEntry>>(emptyList()) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var currentTimeMs by remember { mutableStateOf(0L) }
    var sliderPosition by remember { mutableStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var totalDurationMs by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var lastActiveSubtitleDisplayed by remember { mutableStateOf<SubtitleEntry?>(null) } // State to remember last active subtitle

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Update slider position smoothly when not dragging
    LaunchedEffect(currentTimeMs, isDragging) {
        if (!isDragging) {
            sliderPosition = currentTimeMs.toFloat()
        }
    }

    // Playback timer with better performance
    LaunchedEffect(isPlaying) {
        while (isPlaying && currentTimeMs < totalDurationMs) {
            delay(50) // Update every 50ms for smoother playback
            if (!isDragging) {
                currentTimeMs += 50
            }
        }
        if (currentTimeMs >= totalDurationMs) {
            isPlaying = false
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                errorMessage = null
                try {
                    val parsedSubtitles = withContext(Dispatchers.IO) {
                        parseSrtFile(context, uri)
                    }
                    subtitles = parsedSubtitles
                    fileName = getFileName(context, uri)
                    totalDurationMs = if (parsedSubtitles.isNotEmpty()) {
                        parsedSubtitles.last().endTimeMs
                    } else 0L
                    currentTimeMs = 0L
                    sliderPosition = 0f
                    isPlaying = false
                    lastActiveSubtitleDisplayed = null // Reset on new file load
                } catch (e: Exception) {
                    errorMessage = "Error parsing SRT file: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Find current, previous, and next subtitles
    val currentSubtitleIndex = subtitles.indexOfFirst { subtitle ->
        currentTimeMs >= subtitle.startTimeMs && currentTimeMs <= subtitle.endTimeMs
    }

    val currentSubtitle = if (currentSubtitleIndex >= 0) subtitles[currentSubtitleIndex] else null

    // If a subtitle is currently active, update lastActiveSubtitleDisplayed
    if (currentSubtitle != null) {
        lastActiveSubtitleDisplayed = currentSubtitle
    }

    // Determine the reference index for C and D based on current or last active subtitle
    val referenceIndex = if (currentSubtitleIndex != -1) { // If A is active
        currentSubtitleIndex
    } else { // If A is not active (State B)
        lastActiveSubtitleDisplayed?.let { subtitles.indexOf(it) } ?: -1
    }

    val previousSubtitle = if (referenceIndex > 0) subtitles[referenceIndex - 1] else null
    val nextSubtitle: SubtitleEntry? = if (referenceIndex != -1 && referenceIndex < subtitles.size - 1) {
        subtitles[referenceIndex + 1]
    } else null


    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Kaptus",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )

                if (fileName == null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        }
                    ) {
                        Text("Select SRT File")
                    }
                } else {
                    Text(
                        text = fileName!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (subtitles.isNotEmpty()) {
                // Main subtitle display area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp), // Added vertical padding here
                    contentAlignment = Alignment.Center
                ) {
                    SubtitleRoll(
                        previousSubtitle = previousSubtitle,
                        currentSubtitle = currentSubtitle,
                        nextSubtitle = nextSubtitle,
                        lastActiveSubtitle = lastActiveSubtitleDisplayed
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Bottom scrubber controls
        if (subtitles.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Time display
                    Text(
                        text = "${formatTime(currentTimeMs)} / ${formatTime(totalDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // High-performance timeline slider
                    Slider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            sliderPosition = newValue
                            if (!isDragging) {
                                isDragging = true
                            }
                            currentTimeMs = newValue.toLong()
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            currentTimeMs = sliderPosition.toLong()
                        },
                        valueRange = 0f..totalDurationMs.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Playback controls: -5s, -1s, Play/Pause, +1s, +5s
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { currentTimeMs = (currentTimeMs - 5000).coerceAtLeast(0L) }) {
                            Text("-5s")
                        }
                        Button(onClick = { currentTimeMs = (currentTimeMs - 1000).coerceAtLeast(0L) }) {
                            Text("-1s")
                        }
                        IconButton(
                            onClick = {
                                isPlaying = !isPlaying
                            },
                            modifier = Modifier
                                .size(64.dp) // Make it larger
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), // A subtle background
                                    shape = CircleShape // Round container
                                )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp) // Make the icon itself larger
                            )
                        }
                        Button(onClick = { currentTimeMs = (currentTimeMs + 1000).coerceAtMost(totalDurationMs) }) {
                            Text("+1s")
                        }
                        Button(onClick = { currentTimeMs = (currentTimeMs + 5000).coerceAtMost(totalDurationMs) }) {
                            Text("+5s")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change File")
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleRoll(
    previousSubtitle: SubtitleEntry?,
    currentSubtitle: SubtitleEntry?,
    nextSubtitle: SubtitleEntry?,
    lastActiveSubtitle: SubtitleEntry? // New parameter
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly, // Changed to SpaceEvenly
        modifier = Modifier.fillMaxSize()
    ) {
        // Previous subtitle (above, smaller, greyed out)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomCenter
        ) {
            previousSubtitle?.let { subtitle ->
                Text(
                    text = subtitle.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), // Reduced font size
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .alpha(0.7f)
                )
            }
        }

        // Current subtitle (center, large, prominent) or last active subtitle (greyed out)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            currentSubtitle?.let { subtitle ->
                // State A: Currently playing
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = subtitle.text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            } ?: run {
                // currentSubtitle is null (State B or initial state)
                lastActiveSubtitle?.let { subtitle ->
                    // State B: Display last active subtitle, but greyed out
                    Text(
                        text = subtitle.text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), // Greyed out
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp) // Maintain padding for consistent sizing
                    )
                } ?: run {
                    // Initial state or no last active subtitle ever
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }
        }

        // Next subtitle (below, smaller, greyed out)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            nextSubtitle?.let { subtitle ->
                Text(
                    text = subtitle.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), // Reduced font size
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .alpha(0.7f)
                )
            }
        }
    }
}

private fun parseSrtFile(context: android.content.Context, uri: Uri): List<SubtitleEntry> {
    val subtitles = mutableListOf<SubtitleEntry>()
    val inputStream = context.contentResolver.openInputStream(uri)
    val reader = BufferedReader(InputStreamReader(inputStream))

    var currentIndex = 0
    var currentTimeRange = ""
    var currentText = StringBuilder()

    reader.useLines { lines ->
        for (line in lines) {
            val trimmedLine = line.trim()

            when {
                trimmedLine.isEmpty() -> {
                    // End of subtitle entry
                    if (currentIndex > 0 && currentTimeRange.isNotEmpty() && currentText.isNotEmpty()) {
                        val timeRangeParts = currentTimeRange.split(" --> ")
                        if (timeRangeParts.size == 2) {
                            val startTimeMs = parseTimeToMs(timeRangeParts[0])
                            val endTimeMs = parseTimeToMs(timeRangeParts[1])
                            subtitles.add(
                                SubtitleEntry(
                                    index = currentIndex,
                                    startTimeMs = startTimeMs,
                                    endTimeMs = endTimeMs,
                                    startTime = timeRangeParts[0],
                                    endTime = timeRangeParts[1],
                                    text = currentText.toString().trim()
                                )
                            )
                        }
                    }
                    // Reset for next entry
                    currentIndex = 0
                    currentTimeRange = ""
                    currentText.clear()
                }
                trimmedLine.matches(Regex("\\d+")) -> {
                    // Subtitle index
                    currentIndex = trimmedLine.toInt()
                }
                trimmedLine.contains(" --> ") -> {
                    // Time range
                    currentTimeRange = trimmedLine
                }
                else -> {
                    // Subtitle text
                    if (currentText.isNotEmpty()) {
                        currentText.append("\n")
                    }
                    currentText.append(trimmedLine)
                }
            }
        }

        // Handle last subtitle if file doesn't end with empty line
        if (currentIndex > 0 && currentTimeRange.isNotEmpty() && currentText.isNotEmpty()) {
            val timeRangeParts = currentTimeRange.split(" --> ")
            if (timeRangeParts.size == 2) {
                val startTimeMs = parseTimeToMs(timeRangeParts[0])
                val endTimeMs = parseTimeToMs(timeRangeParts[1])
                subtitles.add(
                    SubtitleEntry(
                        index = currentIndex,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        startTime = timeRangeParts[0],
                        endTime = timeRangeParts[1],
                        text = currentText.toString().trim()
                    )
                )
            }
        }
    }

    return subtitles
}

private fun parseTimeToMs(timeString: String): Long {
    try {
        // Parse SRT time format: HH:MM:SS,mmm
        val parts = timeString.split(":")
        if (parts.size != 3) return 0L

        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val secondsAndMillis = parts[2].split(",")
        val seconds = secondsAndMillis[0].toInt()
        val millis = secondsAndMillis[1].toInt()

        return (hours * 3600 + minutes * 60 + seconds) * 1000L + millis
    } catch (e: Exception) {
        return 0L
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var result = "Unknown"
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0) {
                    result = it.getString(columnIndex) ?: "Unknown"
                }
            }
        }
    }
    if (result == "Unknown") {
        result = uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        } ?: "Unknown"
    }
    return result
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KaptusTheme {
        MainScreen()
    }
}