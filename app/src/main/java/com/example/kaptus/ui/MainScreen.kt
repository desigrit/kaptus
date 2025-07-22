// app/src/main/java/com/example/kaptus/ui/MainScreen.kt

package com.example.kaptus.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kaptus.PlaybackViewModel
import com.example.kaptus.ui.composables.*

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel = viewModel()
) {
    val uiState by rememberUpdatedState(viewModel.uiState)
    val context = LocalContext.current
    val orientation = LocalConfiguration.current.orientation
    var controlsVisible by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        onSnap = { snappedIndex ->
            val subtitleIndex = snappedIndex - 1
            if (subtitleIndex in uiState.subtitles.indices) {
                viewModel.onSubtitleSelected(uiState.subtitles[subtitleIndex].startTimeMs)
            }
        }
    )

    // This effect handles programmatic scrolling ONLY during playback.
    LaunchedEffect(uiState.visibleSubtitle, uiState.isPlaying) {
        if (uiState.isPlaying) {
            uiState.visibleSubtitle?.let {
                val index = uiState.subtitles.indexOf(it)
                if (index != -1) {
                    listState.animateScrollToItem(index + 1)
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.loadSubtitles(context, it) } }

    Scaffold(
        modifier = modifier,
        topBar = {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 200))
            ) {
                KaptusTopAppBar(
                    fileName = uiState.fileName,
                    showActions = uiState.subtitles.isNotEmpty(),
                    orientation = orientation,
                    isSearchActive = uiState.isSearchActive,
                    searchQuery = uiState.searchQuery,
                    searchResults = Pair(uiState.currentSearchResultIndex, uiState.searchResults.size),
                    onSearchActiveChange = viewModel::onSearchActiveChange,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onNextResult = viewModel::goToNextResult,
                    onPreviousResult = viewModel::goToPreviousResult,
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
        },
    ) { innerPadding ->
        // The root Box for our layout
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // LAYER 1: The Tappable Background
            // This Box fills the entire screen and ONLY listens for taps.
            // It will ignore drag gestures, allowing them to pass through to content on top.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    }
            )

            // LAYER 2: The UI Content, placed on TOP of the tappable background
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.subtitles.isNotEmpty()) {
                val rollHeight = 600.dp

                // The SubtitleRoll is now free to receive all scroll gestures.
                SubtitleRoll(
                    modifier = Modifier
                        .height(rollHeight)
                        .align(Alignment.Center),
                    listState = listState,
                    subtitles = uiState.subtitles,
                    visibleSubtitle = uiState.visibleSubtitle,
                    height = rollHeight,
                    flingBehavior = flingBehavior,
                    isPlaying = uiState.isPlaying
                )

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 200))
                ) {
                    PlaybackControls(
                        isPlaying = uiState.isPlaying,
                        currentTimeMs = uiState.currentTimeMs,
                        totalDurationMs = uiState.totalDurationMs,
                        sliderPosition = uiState.sliderPosition,
                        onPlayPauseClick = viewModel::playPause,
                        onSliderChange = viewModel::onSliderChange,
                        onSliderChangeFinished = viewModel::onSliderChangeFinished,
                        onSeek = viewModel::seek,
                        orientation = orientation
                    )
                }
            } else {
                // The initial "Select File" screen
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Select an SRT file to begin",
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