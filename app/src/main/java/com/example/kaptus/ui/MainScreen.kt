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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kaptus.PlaybackViewModel
import com.example.kaptus.ui.composables.KaptusTopAppBar
import com.example.kaptus.ui.composables.PlaybackControls
import com.example.kaptus.ui.composables.SubtitleRoll

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel = viewModel()
) {
    val uiState by rememberUpdatedState(viewModel.uiState)
    val context = LocalContext.current
    val orientation = LocalConfiguration.current.orientation
    var controlsVisible by remember { mutableStateOf(true) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadSubtitles(context, it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                KaptusTopAppBar(
                    fileName = uiState.fileName,
                    showActions = uiState.subtitles.isNotEmpty(),
                    orientation = orientation,
                    isSearchActive = false, // TODO: Re-wire search logic
                    searchQuery = "",
                    searchResults = Pair(0, 0),
                    onSearchQueryChange = { /* TODO */ },
                    onSearchActiveChange = { /* TODO */ },
                    onNextResult = { /* TODO */ },
                    onPreviousResult = { /* TODO */ },
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
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.subtitles.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { controlsVisible = !controlsVisible }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    SubtitleRoll(
                        modifier = Modifier.height(600.dp),
                        subtitles = uiState.subtitles,
                        currentSubtitle = uiState.currentSubtitle,
                        isPlaying = uiState.isPlaying,
                        // This now calls the ViewModel, which handles the logic
                        onSubtitleSelected = { newTime -> viewModel.onSubtitleSelected(newTime) }
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    PlaybackControls(
                        isPlaying = uiState.isPlaying,
                        currentTimeMs = uiState.currentTimeMs,
                        totalDurationMs = uiState.totalDurationMs,
                        sliderPosition = uiState.sliderPosition,
                        onPlayPauseClick = { viewModel.playPause() },
                        onSliderChange = { newPosition -> viewModel.onSliderChange(newPosition) },
                        onSliderChangeFinished = { viewModel.onSliderChangeFinished() },
                        onSeek = { offset -> viewModel.seek(offset) },
                        orientation = orientation
                    )
                }
            } else {
                // Initial State / Error State
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