// app/src/main/java/com/example/kaptus/data/SrtParser.kt

package com.example.kaptus.data

import java.io.InputStream
import java.util.regex.Pattern

class SrtParser {

    companion object {
        private val TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})"
        )
    }

    fun parse(inputStream: InputStream): List<SubtitleEntry> {
        // First, read the whole file and normalize all different kinds of newlines to one standard (\n)
        val srtContent = inputStream.bufferedReader().use { it.readText() }
            .replace("\r\n", "\n").replace("\r", "\n")

        val subtitles = mutableListOf<SubtitleEntry>()
        // Split the file into blocks based on one or more empty lines
        val blocks = srtContent.trim().split(Regex("\n\n+"))

        // This regex will find and remove any HTML tag (e.g., <font>, </u>, <i>)
        val htmlTagRegex = Regex("<.*?>")

        for (block in blocks) {
            if (block.isBlank()) continue

            val lines = block.split("\n")
            if (lines.isEmpty()) continue

            try {
                // Find the line that contains the timestamp ("-->")
                val timestampLineIndex = lines.indexOfFirst { "-->" in it }
                if (timestampLineIndex == -1) continue // Skip block if no timestamp is found

                val timestampLine = lines[timestampLineIndex]
                val matcher = TIMESTAMP_PATTERN.matcher(timestampLine)
                if (!matcher.matches()) continue // Skip if the line isn't a valid timestamp

                // The index is the line right before the timestamp, if it exists and is a number.
                val index = lines.getOrNull(timestampLineIndex - 1)?.trim()?.toIntOrNull() ?: -1

                // The text is all the lines after the timestamp. Then, remove HTML tags.
                val text = lines.drop(timestampLineIndex + 1)
                    .joinToString("\n")
                    .replace(htmlTagRegex, "") // Remove HTML tags
                    .trim()

                if (text.isNotEmpty()) {
                    val startTime = parseTimeToMilliseconds(
                        matcher.group(1)!!.toInt(), matcher.group(2)!!.toInt(),
                        matcher.group(3)!!.toInt(), matcher.group(4)!!.toInt()
                    )
                    val endTime = parseTimeToMilliseconds(
                        matcher.group(5)!!.toInt(), matcher.group(6)!!.toInt(),
                        matcher.group(7)!!.toInt(), matcher.group(8)!!.toInt()
                    )
                    subtitles.add(SubtitleEntry(index, startTime, endTime, text))
                }
            } catch (e: Exception) {
                // Ignore any malformed blocks and continue
                continue
            }
        }
        return subtitles.sortedBy { it.startTimeMs }
    }

    private fun parseTimeToMilliseconds(h: Int, m: Int, s: Int, ms: Int): Long {
        return (h * 3600000L) + (m * 60000L) + (s * 1000L) + ms
    }
}