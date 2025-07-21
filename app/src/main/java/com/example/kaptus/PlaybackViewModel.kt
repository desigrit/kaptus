package com.example.kaptus

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaptus.data.SubtitleEntry
import com.example.kaptus.utils.getFileName
import com.example.kaptus.utils.parseSrtFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackViewModel : ViewModel() {

    //================================================================================
    // State - The single source of truth for the UI
    //================================================================================
    var uiState by mutableStateOf(UiState())
        private set

    private var playbackJob: Job? = null

    //================================================================================
    // Public Events - Called by the UI
    //================================================================================

    fun loadSubtitles(context: Context, uri: Uri) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            try {
                val parsedSubtitles = withContext(Dispatchers.IO) { parseSrtFile(context, uri) }
                val duration = if (parsedSubtitles.isNotEmpty()) parsedSubtitles.last().endTimeMs else 0L

                uiState = uiState.copy(
                    subtitles = parsedSubtitles,
                    fileName = getFileName(context, uri),
                    totalDurationMs = duration,
                    isLoading = false
                )
                // Reset state for the new file
                resetPlayback()

            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Error parsing SRT file: ${e.message}"
                )
            }
        }
    }

    fun playPause() {
        val isPlaying = !uiState.isPlaying
        uiState = uiState.copy(isPlaying = isPlaying)

        if (isPlaying) {
            startPlaybackLoop()
        } else {
            stopPlaybackLoop()
        }
    }

    fun seek(offset: Long) {
        val newTime = (uiState.currentTimeMs + offset).coerceIn(0L, uiState.totalDurationMs)
        updateTime(newTime)
    }

    fun onSliderChange(newPosition: Float) {
        // Stop the automatic playback loop while the user is scrubbing
        stopPlaybackLoop()
        uiState = uiState.copy(isDraggingSlider = true)
        updateTime(newPosition.toLong())
    }

    fun onSliderChangeFinished() {
        uiState = uiState.copy(isDraggingSlider = false)
        // If the user was playing before, resume playback from the new position
        if (uiState.isPlaying) {
            startPlaybackLoop()
        }
    }

    fun onSubtitleSelected(timeMs: Long) {
        // This is called when the user manually scrolls the SubtitleRoll
        // We only honor this if playback is paused
        if (!uiState.isPlaying) {
            updateTime(timeMs)
        }
    }

    //================================================================================
    // Private Logic
    //================================================================================

    private fun startPlaybackLoop() {
        stopPlaybackLoop() // Ensure only one loop is running
        playbackJob = viewModelScope.launch {
            val startTimeMs = uiState.currentTimeMs
            val startRealTime = System.nanoTime()

            while (true) { // Loop will be controlled by the Job's lifecycle
                delay(16) // Aim for ~60fps updates
                val elapsedRealTime = (System.nanoTime() - startRealTime) / 1_000_000
                val newTime = (startTimeMs + elapsedRealTime).coerceIn(0L, uiState.totalDurationMs)
                updateTime(newTime)

                if (newTime >= uiState.totalDurationMs) {
                    uiState = uiState.copy(isPlaying = false)
                    stopPlaybackLoop()
                }
            }
        }
    }

    private fun stopPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun updateTime(newTime: Long) {
        val currentSubtitle = uiState.subtitles.find { it.isActiveAt(newTime) }
        uiState = uiState.copy(
            currentTimeMs = newTime,
            sliderPosition = newTime.toFloat(),
            currentSubtitle = currentSubtitle
        )
    }

    private fun resetPlayback() {
        stopPlaybackLoop()
        uiState = uiState.copy(
            currentTimeMs = 0L,
            sliderPosition = 0f,
            isPlaying = false,
            isDraggingSlider = false,
            currentSubtitle = uiState.subtitles.firstOrNull()
        )
    }
}

// Data class to hold all UI state, making it easy to manage
data class UiState(
    val subtitles: List<SubtitleEntry> = emptyList(),
    val fileName: String? = null,
    val currentTimeMs: Long = 0L,
    val sliderPosition: Float = 0f,
    val isPlaying: Boolean = false,
    val totalDurationMs: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isDraggingSlider: Boolean = false,
    val currentSubtitle: SubtitleEntry? = null,
    // Add Search State here if needed
)