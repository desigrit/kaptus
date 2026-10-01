package com.example.kaptus.speech

import com.example.kaptus.data.RecognizedWord
import com.example.kaptus.sync.CaptionTextNormalizer
import java.io.ByteArrayOutputStream

data class DecodedSpeechToken(
    val text: String,
    val startCentiseconds: Long,
    val endCentiseconds: Long,
    val probability: Float
)

data class DecodedSpeechBytes(
    val bytes: ByteArray,
    val startCentiseconds: Long,
    val endCentiseconds: Long,
    val probability: Float
)

object RecognizedWordAssembler {
    fun assembleBytes(
        tokens: List<DecodedSpeechBytes>,
        captureStartNanos: Long,
        sourceLanguage: String = "en",
        translated: Boolean = false
    ): List<RecognizedWord> {
        val valid = tokens.filter { token -> token.startCentiseconds >= 0 &&
            token.endCentiseconds >= token.startCentiseconds &&
            !token.bytes.decodeToString().trim().startsWith("<|") }
        val groups = mutableListOf<MutableList<DecodedSpeechBytes>>()
        valid.forEach { token ->
            val startsWord = token.bytes.firstOrNull()?.toInt()?.let { it in listOf(9, 10, 13, 32) } == true
            if (groups.isEmpty() || (!translated && sourceLanguage !in setOf("zh", "ja") && startsWord)) {
                groups += mutableListOf<DecodedSpeechBytes>()
            }
            groups.last() += token
        }
        return buildList {
            groups.forEach { group ->
                val stream = ByteArrayOutputStream()
                group.forEach { stream.write(it.bytes) }
                // Decode only after joining byte-level BPE pieces. A piece may end midway
                // through a CJK, Indic or supplementary Unicode character.
                val decoded = stream.toByteArray().decodeToString()
                val normalized = CaptionTextNormalizer.tokens(decoded)
                if (normalized.isEmpty()) return@forEach
                val start = captureStartNanos + group.minOf { it.startCentiseconds } * 10_000_000L
                val end = captureStartNanos + group.maxOf { it.endCentiseconds } * 10_000_000L
                normalized.forEachIndexed { index, word ->
                    val startFraction = if (translated) 0.0 else index.toDouble() / normalized.size
                    val endFraction = if (translated) 1.0 else (index + 1).toDouble() / normalized.size
                    add(RecognizedWord(word, start + ((end - start) * startFraction).toLong(),
                        start + ((end - start) * endFraction).toLong(), group.map { it.probability }.average().toFloat()))
                }
            }
        }
    }

    fun assemble(
        tokens: List<DecodedSpeechToken>,
        captureStartNanos: Long
    ): List<RecognizedWord> {
        val pieces = mutableListOf<MutableList<DecodedSpeechToken>>()
        for (token in tokens) {
            if (token.startCentiseconds < 0L || token.endCentiseconds < token.startCentiseconds) continue
            val raw = token.text
            if (raw.isBlank() || raw.trim().let { it.startsWith("<|") || it.startsWith("[") }) continue
            val startsWord = raw.firstOrNull()?.isWhitespace() == true
            if (pieces.isEmpty() || startsWord) pieces.add(mutableListOf())
            pieces.last() += token
        }

        return buildList {
            pieces.forEach { wordPieces ->
                if (wordPieces.isEmpty()) return@forEach
                val normalized = CaptionTextNormalizer.tokens(wordPieces.joinToString("") { it.text })
                if (normalized.isEmpty()) return@forEach
                val first = wordPieces.first()
                val last = wordPieces.last()
                val tokenStartNanos = captureStartNanos + first.startCentiseconds * 10_000_000L
                val tokenEndNanos = captureStartNanos + last.endCentiseconds * 10_000_000L
                val confidence = wordPieces.map { it.probability }.average().toFloat()
                normalized.forEachIndexed { index, text ->
                    val startFraction = index.toDouble() / normalized.size
                    val endFraction = (index + 1).toDouble() / normalized.size
                    val duration = tokenEndNanos - tokenStartNanos
                    add(
                        RecognizedWord(
                            text = text,
                            startTimeNanos = tokenStartNanos + (duration * startFraction).toLong(),
                            endTimeNanos = tokenStartNanos + (duration * endFraction).toLong(),
                            confidence = confidence
                        )
                    )
                }
            }
        }
    }
}
