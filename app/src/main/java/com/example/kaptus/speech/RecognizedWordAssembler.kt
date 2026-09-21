package com.example.kaptus.speech

import com.example.kaptus.data.RecognizedWord
import com.example.kaptus.sync.CaptionTextNormalizer

data class DecodedSpeechToken(
    val text: String,
    val startCentiseconds: Long,
    val endCentiseconds: Long,
    val probability: Float
)

object RecognizedWordAssembler {
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
