// app/src/main/java/com/example/kaptus/ui/MainScreen.kt

package com.example.kaptus.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.ui.composables.KaptusTopAppBar
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
    // Core State
    var subtitles by rememberSaveable { mutableStateOf<List<SubtitleEntry>>(emptyList()) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var currentTimeMs by rememberSaveable { mutableStateOf(0L) }
    var sliderPosition by rememberSaveable { mutableStateOf(0f) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var totalDurationMs by rememberSaveable { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    // Search State
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentSearchResultIndex by rememberSaveable { mutableStateOf(-1) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val orientation = LocalConfiguration.current.orientation

    // --- Search Logic ---
    LaunchedEffect(searchQuery, subtitles) {
        if (searchQuery.isNotBlank()) {
            searchResults = subtitles.mapIndexedNotNull { index, subtitle ->
                if (subtitle.text.contains(searchQuery, ignoreCase = true)) index else null
            }
            currentSearchResultIndex = if (searchResults.isNotEmpty()) 0 else -1
        } else {
            searchResults = emptyList()
            currentSearchResultIndex = -1
        }
    }

// --- State Synchronization ---
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect

        // Get the time from the system clock when playback starts.
        var startRealTime = System.nanoTime()
        // Get the position in the media that we are starting from.
        var startMediaTime = currentTimeMs

        while (isPlaying && currentTimeMs < totalDurationMs) {
            // If the user starts dragging the slider, don't advance the time here.
            // Instead, reset our time references for when they let go.
            if (isDraggingSlider) {
                // When dragging, the user is controlling the time.
                // We update our media time reference to match the slider's position.
                startMediaTime = currentTimeMs
                // And we reset the "real time" reference to now.
                startRealTime = System.nanoTime()
                delay(16) // A short delay to prevent a busy-wait loop
                continue
            }

            // This is how much "real time" has passed since playback started or was last reset.
            val elapsedRealTime = System.nanoTime() - startRealTime

            // The new media time is the time we started at, plus the elapsed real time.
            val newTime = startMediaTime + (elapsedRealTime / 1_000_000)

            // Only update the state if the time has actually changed to avoid unnecessary work.
            if (newTime != currentTimeMs) {
                currentTimeMs = newTime.coerceIn(0L, totalDurationMs)
            }

            // Update at a rate of roughly 60 frames per second.
            delay(16)
        }

        if (currentTimeMs >= totalDurationMs) {
            isPlaying = false
        }
    }

    LaunchedEffect(currentTimeMs) {
        if (!isDraggingSlider) sliderPosition = currentTimeMs.toFloat()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                try {
                    val parsedSubtitles = withContext(Dispatchers.IO) { parseSrtFile(context, uri) }
                    fileName = getFileName(context, uri)
                    totalDurationMs = if (parsedSubtitles.isNotEmpty()) parsedSubtitles.last().endTimeMs else 0L
                    currentTimeMs = 0L
                    sliderPosition = 0f
                    isPlaying = false
                    subtitles = parsedSubtitles
                } catch (e: Exception) {
                    errorMessage = "Error parsing SRT file: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val currentSubtitle = subtitles.find { it.isActiveAt(currentTimeMs) }

    // --- UI Layout ---
    Scaffold(
        modifier = modifier,
        topBar = {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                KaptusTopAppBar(
                    fileName = fileName,
                    showActions = subtitles.isNotEmpty(),
                    orientation = orientation,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    searchResults = Pair(currentSearchResultIndex, searchResults.size),
                    onSearchQueryChange = { searchQuery = it },
                    onSearchActiveChange = { isSearchActive = it },
                    onNextResult = {
                        if (searchResults.isNotEmpty()) {
                            val nextIndex = (currentSearchResultIndex + 1) % searchResults.size
                            currentSearchResultIndex = nextIndex
                            currentTimeMs = subtitles[searchResults[nextIndex]].startTimeMs
                        }
                    },
                    onPreviousResult = {
                        if (searchResults.isNotEmpty()) {
                            val prevIndex = (currentSearchResultIndex - 1 + searchResults.size) % searchResults.size
                            currentSearchResultIndex = prevIndex
                            currentTimeMs = subtitles[searchResults[prevIndex]].startTimeMs
                        }
                    },
                    onFlipOrientationClick = {
                        val activity = context as? Activity
                        activity?.requestedOrientation = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                )
            }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            controlsVisible = !controlsVisible
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SubtitleRoll(
                        modifier = Modifier.height(600.dp),
                        subtitles = subtitles,
                        currentSubtitle = currentSubtitle,
                        isPlaying = isPlaying,
                        onSubtitleSelected = { newTime ->
                            currentTimeMs = newTime
                        }
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    PlaybackControls(
                        isPlaying = isPlaying,
                        currentTimeMs = currentTimeMs,
                        totalDurationMs = totalDurationMs,
                        sliderPosition = sliderPosition,
                        onPlayPauseClick = { isPlaying = !isPlaying },
                        onSliderChange = { newValue ->
                            isDraggingSlider = true
                            sliderPosition = newValue
                            currentTimeMs = newValue.toLong()
                        },
                        onSliderChangeFinished = { isDraggingSlider = false },
                        onSeek = { offset ->
                            currentTimeMs = (currentTimeMs + offset).coerceIn(0L, totalDurationMs)
                        },
                        orientation = orientation
                    )
                }
            } else {
                // Initial State
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
