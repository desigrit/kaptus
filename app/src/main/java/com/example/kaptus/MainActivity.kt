package com.example.kaptus

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kaptus.ui.theme.KaptusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

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
    var isPlaying by remember { mutableStateOf(false) }
    var totalDurationMs by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto-scroll to current subtitle
    LaunchedEffect(currentTimeMs, subtitles) {
        if (subtitles.isNotEmpty()) {
            val currentSubtitleIndex = subtitles.indexOfFirst { subtitle ->
                currentTimeMs >= subtitle.startTimeMs && currentTimeMs <= subtitle.endTimeMs
            }
            if (currentSubtitleIndex >= 0) {
                listState.animateScrollToItem(currentSubtitleIndex)
            }
        }
    }

    // Playback timer
    LaunchedEffect(isPlaying) {
        while (isPlaying && currentTimeMs < totalDurationMs) {
            delay(100) // Update every 100ms
            currentTimeMs += 100
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
                    isPlaying = false
                } catch (e: Exception) {
                    errorMessage = "Error parsing SRT file: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Kaptus - Subtitle Sync",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Button(
                onClick = {
                    filePickerLauncher.launch("*/*")
                },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("Select SRT File")
            }

            fileName?.let {
                Text(
                    text = "File: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (subtitles.isNotEmpty()) {
                // Timeline controls
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Timeline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatTime(currentTimeMs)} / ${formatTime(totalDurationMs)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        isPlaying = !isPlaying
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Timeline slider
                        Slider(
                            value = currentTimeMs.toFloat(),
                            onValueChange = { newTime ->
                                currentTimeMs = newTime.toLong()
                                isPlaying = false // Pause when manually seeking
                            },
                            valueRange = 0f..totalDurationMs.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Current subtitle display
                        val currentSubtitle = subtitles.find { subtitle ->
                            currentTimeMs >= subtitle.startTimeMs && currentTimeMs <= subtitle.endTimeMs
                        }

                        if (currentSubtitle != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "Now Playing:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = currentSubtitle.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            errorMessage?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (subtitles.isNotEmpty()) {
                Text(
                    text = "All Subtitles",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    state = listState
                ) {
                    items(subtitles) { subtitle ->
                        SubtitleCard(
                            subtitle = subtitle,
                            isActive = currentTimeMs >= subtitle.startTimeMs && currentTimeMs <= subtitle.endTimeMs,
                            onClick = {
                                currentTimeMs = subtitle.startTimeMs
                                isPlaying = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleCard(
    subtitle: SubtitleEntry,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#${subtitle.index}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = "${subtitle.startTime} → ${subtitle.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
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
    val millis = timeMs % 1000

    return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
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