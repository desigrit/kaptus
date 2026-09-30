package com.example.kaptus.data

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class SrtParser {
    companion object {
        const val MAX_BYTES = 16 * 1024 * 1024

        fun readBounded(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= MAX_BYTES) { "Caption file exceeds the 16 MB limit." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        private val TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*" +
                "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})"
        )
        private val MALFORMED_TIMESTAMP_LINE = Regex("\\d{1,3}:.*-->.*")
    }

    fun parse(inputStream: InputStream): List<SubtitleEntry> {
        val content = decode(readBounded(inputStream))
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .removePrefix("\uFEFF")
        val lines = content.split('\n')
        val htmlTag = Regex("<[^>]+>")
        // Character classes keep literal braces portable across the JVM and Android regex engines.
        val formattingOverride = Regex("""[{][^}]*[}]""")

        val headers = mutableListOf<CueHeader>()
        lines.forEachIndexed { lineIndex, line ->
            val matcher = TIMESTAMP_PATTERN.matcher(line)
            val matched = matcher.find()
            if (!matched && !line.trimStart().matches(MALFORMED_TIMESTAMP_LINE)) return@forEachIndexed
            val candidateIndex = lines.getOrNull(lineIndex - 1)?.trim()?.toIntOrNull()
            // Without a separator, only consecutive cue numbering identifies an index.
            // Other numeric dialogue, such as a year or phone number, remains caption text.
            val indexed = candidateIndex != null && (headers.isEmpty() ||
                lines.getOrNull(lineIndex - 2)?.isBlank() == true ||
                headers.lastOrNull()?.index?.takeIf { it >= 0 }?.let { candidateIndex == it + 1 } == true)
            val times = if (matched) runCatching {
                val start = timestamp(matcher.group(1)!!, matcher.group(2)!!, matcher.group(3)!!, matcher.group(4)!!)
                val end = timestamp(matcher.group(5)!!, matcher.group(6)!!, matcher.group(7)!!, matcher.group(8)!!)
                require(end > start)
                start to end
            }.getOrNull() else null
            headers += CueHeader(lineIndex, if (indexed) candidateIndex!! else -1,
                if (indexed) lineIndex - 1 else lineIndex, times)
        }
        return buildList {
            headers.forEachIndexed { position, header ->
                val times = header.times ?: return@forEachIndexed
                val endLine = headers.getOrNull(position + 1)?.indexLine ?: lines.size
                val text = lines.subList(header.line + 1, endLine)
                    .takeWhile { it.isNotBlank() }
                    .joinToString("\n")
                    .replace(htmlTag, "")
                    .replace(formattingOverride, "")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&#39;", "'")
                    .replace("&nbsp;", " ")
                    .trim()
                if (text.isNotEmpty()) add(SubtitleEntry(header.index, times.first, times.second, text))
            }
        }.sortedBy { it.startTimeMs }
    }

    private data class CueHeader(val line: Int, val index: Int, val indexLine: Int, val times: Pair<Long, Long>?)

    private fun timestamp(hours: String, minutes: String, seconds: String, millis: String): Long {
        require(minutes.toInt() in 0..59 && seconds.toInt() in 0..59)
        return parseTimeToMilliseconds(hours.toInt(), minutes.toInt(), seconds.toInt(), parseMilliseconds(millis))
    }

    private fun parseTimeToMilliseconds(hours: Int, minutes: Int, seconds: Int, millis: Int): Long =
        hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis

    private fun parseMilliseconds(value: String): Int = value.padEnd(3, '0').take(3).toInt()

    private fun decode(bytes: ByteArray): String {
        val (charset, offset) = when {
            bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() &&
                bytes[2] == 0.toByte() && bytes[3] == 0.toByte() ->
                throw IllegalArgumentException("Unsupported caption encoding. Save the SRT as UTF-8 or UTF-16.")
            bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
                bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte() ->
                throw IllegalArgumentException("Unsupported caption encoding. Save the SRT as UTF-8 or UTF-16.")
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> StandardCharsets.UTF_8 to 3
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                StandardCharsets.UTF_16LE to 2
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                StandardCharsets.UTF_16BE to 2
            else -> StandardCharsets.UTF_8 to 0
        }
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
                .also { require('\u0000' !in it) { "Unsupported caption encoding. Save the SRT as UTF-8 or UTF-16." } }
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Unsupported caption encoding. Save the SRT as UTF-8 or UTF-16.", error)
        }
    }
}
