package com.example.kaptus.data

/**
 * Represents a single subtitle entry with timing and text
 */
data class SubtitleEntry(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
) {
    /**
     * Check if this subtitle should be displayed at the given time
     */
    fun isActiveAt(timeMs: Long): Boolean {
        return timeMs >= startTimeMs && timeMs <= endTimeMs
    }

    /**
     * Get duration of this subtitle in milliseconds
     */
    val durationMs: Long
        get() = endTimeMs - startTimeMs

    /**
     * Format start time as readable string (for debugging)
     */
    fun formatStartTime(): String {
        val totalSeconds = startTimeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val milliseconds = startTimeMs % 1000

        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, milliseconds)
    }
}