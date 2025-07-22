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
    // Search Events - Called by the UI
    //================================================================================

    fun onSearchActiveChange(isActive: Boolean) {
        uiState = uiState.copy(isSearchActive = isActive)
        // Clear search query when the search bar is closed
        if (!isActive) {
            onSearchQueryChange("")
        }
    }

    fun onSearchQueryChange(query: String) {
        val results = if (query.isNotBlank()) {
            uiState.subtitles.mapIndexedNotNull { index, subtitle ->
                if (subtitle.text.contains(query, ignoreCase = true)) index else null
            }
        } else {
            emptyList()
        }
        val newIndex = if (results.isNotEmpty()) 0 else -1

        uiState = uiState.copy(
            searchQuery = query,
            searchResults = results,
            currentSearchResultIndex = newIndex
        )

        // If there are results, jump to the first one
        if (newIndex != -1) {
            val jumpTime = uiState.subtitles[results[newIndex]].startTimeMs
            updateTime(jumpTime)
        }
    }

    fun goToNextResult() {
        if (uiState.searchResults.isEmpty()) return
        val nextIndex = (uiState.currentSearchResultIndex + 1) % uiState.searchResults.size
        val jumpTime = uiState.subtitles[uiState.searchResults[nextIndex]].startTimeMs
        updateTime(jumpTime)
        uiState = uiState.copy(currentSearchResultIndex = nextIndex)
    }

    fun goToPreviousResult() {
        if (uiState.searchResults.isEmpty()) return
        val prevIndex = (uiState.currentSearchResultIndex - 1 + uiState.searchResults.size) % uiState.searchResults.size
        val jumpTime = uiState.subtitles[uiState.searchResults[prevIndex]].startTimeMs
        updateTime(jumpTime)
        uiState = uiState.copy(currentSearchResultIndex = prevIndex)
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
        val activeSubtitle = uiState.subtitles.find { it.isActiveAt(newTime) }

        // This is the key change:
        // If there's an active subtitle, it's the one we show.
        // If not, we keep showing the *previous* one instead of going blank.
        val subtitleToShow = activeSubtitle ?: uiState.visibleSubtitle

        uiState = uiState.copy(
            currentTimeMs = newTime,
            sliderPosition = newTime.toFloat(),
            currentSubtitle = activeSubtitle,
            visibleSubtitle = subtitleToShow // Update the new state property
        )
    }

    private fun resetPlayback() {
        stopPlaybackLoop()
        val firstSubtitle = uiState.subtitles.firstOrNull()
        uiState = uiState.copy(
            currentTimeMs = 0L,
            sliderPosition = 0f,
            isPlaying = false,
            isDraggingSlider = false,
            currentSubtitle = firstSubtitle,
            visibleSubtitle = firstSubtitle // Ensure the first subtitle is visible on load
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
    val visibleSubtitle: SubtitleEntry? = null,
    // --- Add all the search state here ---
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Int> = emptyList(),
    val currentSearchResultIndex: Int = -1
)