package com.example.kaptus.data

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class SrtParser {
    companion object {
        private val TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*" +
                "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})"
        )
    }

    fun parse(inputStream: InputStream): List<SubtitleEntry> {
        val content = decode(inputStream.readBytes())
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .removePrefix("\uFEFF")
        val blocks = content.trim().split(Regex("\n[ \\t]*\n+"))
        val htmlTag = Regex("<[^>]+>")
        // Character classes keep literal braces portable across the JVM and Android regex engines.
        val formattingOverride = Regex("""[{][^}]*[}]""")

        return buildList {
            for (block in blocks) {
                if (block.isBlank()) continue
                val lines = block.split('\n')
                val timestampLineIndex = lines.indexOfFirst { "-->" in it }
                if (timestampLineIndex < 0) continue
                val matcher = TIMESTAMP_PATTERN.matcher(lines[timestampLineIndex])
                if (!matcher.find()) continue

                val index = lines.getOrNull(timestampLineIndex - 1)?.trim()?.toIntOrNull() ?: -1
                val text = lines.drop(timestampLineIndex + 1)
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
                if (text.isEmpty()) continue

                runCatching {
                    val start = parseTimeToMilliseconds(
                        matcher.group(1)!!.toInt(), matcher.group(2)!!.toInt(),
                        matcher.group(3)!!.toInt(), parseMilliseconds(matcher.group(4)!!)
                    )
                    val end = parseTimeToMilliseconds(
                        matcher.group(5)!!.toInt(), matcher.group(6)!!.toInt(),
                        matcher.group(7)!!.toInt(), parseMilliseconds(matcher.group(8)!!)
                    )
                    if (end > start) add(SubtitleEntry(index, start, end, text))
                }
            }
        }.sortedBy { it.startTimeMs }
    }

    private fun parseTimeToMilliseconds(hours: Int, minutes: Int, seconds: Int, millis: Int): Long =
        hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis

    private fun parseMilliseconds(value: String): Int = value.padEnd(3, '0').take(3).toInt()

    private fun decode(bytes: ByteArray): String {
        val payload = if (
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) bytes.copyOfRange(3, bytes.size) else bytes
        return runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload))
                .toString()
        }.getOrElse { payload.toString(Charsets.ISO_8859_1) }
    }
}
