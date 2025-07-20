package com.example.kaptus.data

import java.io.InputStream
import java.util.regex.Pattern

/**
 * Parser for SRT (SubRip) subtitle files
 *
 * SRT format:
 * 1
 * 00:00:20,000 --> 00:00:24,400
 * Subtitle text here
 *
 * 2
 * 00:00:30,500 --> 00:00:35,000
 * Another subtitle
 */
class SrtParser {

    companion object {
        // Regex pattern to match SRT timestamp format: HH:MM:SS,mmm --> HH:MM:SS,mmm
        private val TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})"
        )
    }

    /**
     * Parse SRT content from string
     */
    fun parse(srtContent: String): List<SubtitleEntry> {
        val subtitles = mutableListOf<SubtitleEntry>()

        // Split into blocks (separated by double newlines)
        val blocks = srtContent.trim().split("\\r?\\n\\s*\\r?\\n".toRegex())

        for (block in blocks) {
            if (block.isBlank()) continue

            val lines = block.trim().split("\\r?\\n".toRegex())
            if (lines.size < 3) continue // Need at least: index, timestamp, text

            try {
                // Parse index (first line)
                val index = lines[0].trim().toIntOrNull() ?: continue

                // Parse timestamp (second line)
                val matcher = TIMESTAMP_PATTERN.matcher(lines[1].trim())
                if (!matcher.matches()) continue

                val startTime = parseTimeToMilliseconds(
                    matcher.group(1).toInt(), // hours
                    matcher.group(2).toInt(), // minutes
                    matcher.group(3).toInt(), // seconds
                    matcher.group(4).toInt()  // milliseconds
                )

                val endTime = parseTimeToMilliseconds(
                    matcher.group(5).toInt(), // hours
                    matcher.group(6).toInt(), // minutes
                    matcher.group(7).toInt(), // seconds
                    matcher.group(8).toInt()  // milliseconds
                )

                // Parse text (remaining lines joined together)
                val text = lines.drop(2).joinToString("\\n").trim()

                if (text.isNotEmpty()) {
                    subtitles.add(SubtitleEntry(index, startTime, endTime, text))
                }

            } catch (e: Exception) {
                // Skip malformed entries
                continue
            }
        }

        // Sort by start time to ensure proper order
        return subtitles.sortedBy { it.startTimeMs }
    }

    /**
     * Parse SRT content from InputStream
     */
    fun parse(inputStream: InputStream): List<SubtitleEntry> {
        val content = inputStream.bufferedReader().use { it.readText() }
        return parse(content)
    }

    /**
     * Convert time components to milliseconds
     */
    private fun parseTimeToMilliseconds(hours: Int, minutes: Int, seconds: Int, milliseconds: Int): Long {
        return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + milliseconds
    }

    /**
     * Find subtitle entry that should be displayed at given time
     */
    fun findActiveSubtitle(subtitles: List<SubtitleEntry>, currentTimeMs: Long): SubtitleEntry? {
        return subtitles.firstOrNull { it.isActiveAt(currentTimeMs) }
    }

    /**
     * Apply time offset to all subtitles (for manual sync)
     */
    fun applyTimeOffset(subtitles: List<SubtitleEntry>, offsetMs: Long): List<SubtitleEntry> {
        return subtitles.map { subtitle ->
            subtitle.copy(
                startTimeMs = subtitle.startTimeMs + offsetMs,
                endTimeMs = subtitle.endTimeMs + offsetMs
            )
        }
    }
}