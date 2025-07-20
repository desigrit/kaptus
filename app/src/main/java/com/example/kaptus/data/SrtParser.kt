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
        val srtContent = inputStream.bufferedReader().use { it.readText() }
        val subtitles = mutableListOf<SubtitleEntry>()
        val blocks = srtContent.trim().split("\\r?\\n\\s*\\r?\\n".toRegex())

        for (block in blocks) {
            if (block.isBlank()) continue

            val lines = block.trim().split("\\r?\\n".toRegex())
            if (lines.size < 3) continue

            try {
                val index = lines[0].trim().toIntOrNull() ?: continue
                val matcher = TIMESTAMP_PATTERN.matcher(lines[1].trim())
                if (!matcher.matches()) continue

                val startTime = parseTimeToMilliseconds(
                    matcher.group(1)!!.toInt(),
                    matcher.group(2)!!.toInt(),
                    matcher.group(3)!!.toInt(),
                    matcher.group(4)!!.toInt()
                )

                val endTime = parseTimeToMilliseconds(
                    matcher.group(5)!!.toInt(),
                    matcher.group(6)!!.toInt(),
                    matcher.group(7)!!.toInt(),
                    matcher.group(8)!!.toInt()
                )

                val text = lines.drop(2).joinToString("\n").trim()

                if (text.isNotEmpty()) {
                    subtitles.add(SubtitleEntry(index, startTime, endTime, text))
                }

            } catch (e: Exception) {
                continue
            }
        }
        return subtitles.sortedBy { it.startTimeMs }
    }

    private fun parseTimeToMilliseconds(h: Int, m: Int, s: Int, ms: Int): Long {
        return (h * 3600000L) + (m * 60000L) + (s * 1000L) + ms
    }
}