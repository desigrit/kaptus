// app/src/main/java/com/example/kaptus/ui/MainScreen.kt

package com.example.kaptus.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.ui.composables.PlaybackControls
import com.example.kaptus.ui.composables.SubtitleRoll
import com.example.kaptus.utils.getFileName
import com.example.kaptus.utils.parseSrtFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var subtitles by rememberSaveable { mutableStateOf<List<SubtitleEntry>>(emptyList()) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var currentTimeMs by rememberSaveable { mutableStateOf(0L) }
    var sliderPosition by rememberSaveable { mutableStateOf(0f) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var totalDurationMs by rememberSaveable { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var lastActiveSubtitleDisplayed by rememberSaveable { mutableStateOf<SubtitleEntry?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentTimeMs, isDragging) {
        if (!isDragging) {
            sliderPosition = currentTimeMs.toFloat()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && currentTimeMs < totalDurationMs) {
            delay(50)
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
                    val parsedSubtitles = withContext(Dispatchers.IO) { parseSrtFile(context, uri) }
                    subtitles = parsedSubtitles
                    fileName = getFileName(context, uri)
                    totalDurationMs = if (parsedSubtitles.isNotEmpty()) parsedSubtitles.last().endTimeMs else 0L
                    currentTimeMs = 0L
                    sliderPosition = 0f
                    isPlaying = false
                    lastActiveSubtitleDisplayed = null
                } catch (e: Exception) {
                    errorMessage = "Error parsing SRT file: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val currentSubtitleIndex = subtitles.indexOfFirst { it.isActiveAt(currentTimeMs) }
    val currentSubtitle = if (currentSubtitleIndex != -1) subtitles[currentSubtitleIndex] else null
    if (currentSubtitle != null) {
        lastActiveSubtitleDisplayed = currentSubtitle
    }
    val referenceIndex = currentSubtitleIndex.takeIf { it != -1 } ?: lastActiveSubtitleDisplayed?.let { subtitles.indexOf(it) } ?: -1
    val previousSubtitle = if (referenceIndex > 0) subtitles[referenceIndex - 1] else null
    val nextSubtitle = if (referenceIndex != -1 && referenceIndex < subtitles.size - 1) subtitles[referenceIndex + 1] else null

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kaptus", style = MaterialTheme.typography.titleLarge)
                        fileName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (subtitles.isNotEmpty()) {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Change SRT File") },
                                onClick = {
                                    showMenu = false
                                    filePickerLauncher.launch("*/*")
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (subtitles.isNotEmpty()) {
                SubtitleRoll(
                    modifier = Modifier.fillMaxSize(),
                    previousSubtitle = previousSubtitle,
                    currentSubtitle = currentSubtitle,
                    nextSubtitle = nextSubtitle,
                    lastActiveSubtitle = lastActiveSubtitleDisplayed,
                    onSeek = { direction ->
                        val newIndex = when (direction) {
                            "up" -> (referenceIndex + 1).coerceAtMost(subtitles.lastIndex)
                            "down" -> (referenceIndex - 1).coerceAtLeast(0)
                            else -> referenceIndex
                        }
                        if (newIndex != referenceIndex) {
                            currentTimeMs = subtitles[newIndex].startTimeMs
                        }
                    }
                )

                PlaybackControls(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isPlaying = isPlaying,
                    currentTimeMs = currentTimeMs,
                    totalDurationMs = totalDurationMs,
                    sliderPosition = sliderPosition,
                    onPlayPauseClick = { isPlaying = !isPlaying },
                    onSliderChange = { newValue ->
                        sliderPosition = newValue
                        if (!isDragging) isDragging = true
                        currentTimeMs = newValue.toLong()
                    },
                    onSliderChangeFinished = {
                        isDragging = false
                        currentTimeMs = sliderPosition.toLong()
                    },
                    onSeek = { offset ->
                        currentTimeMs = (currentTimeMs + offset).coerceIn(0L, totalDurationMs)
                    }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMessage ?: "Select an SRT file to begin",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { filePickerLauncher.launch("*/*") }) {
                        Text("Select File")
                    }
                }
            }
        }
    }
}