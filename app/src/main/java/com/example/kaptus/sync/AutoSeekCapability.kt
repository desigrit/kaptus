package com.example.kaptus.sync

enum class AutoSeekMode { AUTOMATIC, MANUAL, MODEL_REQUIRED }
enum class AutoSeekReason { READY, UNSUPPORTED_PAIR, PATH_UNAVAILABLE, MODEL_MISSING }
enum class MatchingPath { ENGLISH, MATCHING_HELPER, DIRECT_TRANSLATION }

data class AutoSeekCapability(
    val mode: AutoSeekMode,
    val reason: AutoSeekReason,
    val paths: Set<MatchingPath> = emptySet()
) {
    val canListen: Boolean get() = mode == AutoSeekMode.AUTOMATIC
}

/** Chooses the matching paths available for a supported spoken language. */
fun interface AutoSeekPathPolicy {
    fun isEnabled(spokenLanguage: String, path: MatchingPath): Boolean
}

object SupportedAutoSeekPaths : AutoSeekPathPolicy {
    override fun isEnabled(spokenLanguage: String, path: MatchingPath): Boolean = when {
        spokenLanguage == "en" -> path == MatchingPath.ENGLISH
        spokenLanguage in AutoSeekCapabilities.targetSpokenLanguages ->
            path == MatchingPath.MATCHING_HELPER || path == MatchingPath.DIRECT_TRANSLATION
        else -> false
    }
}

object AutoSeekCapabilities {
    val targetSpokenLanguages = setOf("zh", "ja", "es", "fr", "de", "ko", "hi", "te", "ta")

    fun evaluate(
        spokenLanguage: String,
        captionLanguage: String,
        modelReady: Boolean,
        pathPolicy: AutoSeekPathPolicy = SupportedAutoSeekPaths
    ): AutoSeekCapability {
        if (captionLanguage != "en" || (spokenLanguage != "en" && spokenLanguage !in targetSpokenLanguages)) {
            return AutoSeekCapability(AutoSeekMode.MANUAL, AutoSeekReason.UNSUPPORTED_PAIR)
        }
        val paths = if (spokenLanguage == "en") setOf(MatchingPath.ENGLISH) else
            setOf(MatchingPath.MATCHING_HELPER, MatchingPath.DIRECT_TRANSLATION)
        val enabled = paths.filter { pathPolicy.isEnabled(spokenLanguage, it) }.toSet()
        if (enabled.isEmpty()) return AutoSeekCapability(AutoSeekMode.MANUAL, AutoSeekReason.PATH_UNAVAILABLE)
        if (!modelReady) return AutoSeekCapability(AutoSeekMode.MODEL_REQUIRED, AutoSeekReason.MODEL_MISSING, enabled)
        return AutoSeekCapability(AutoSeekMode.AUTOMATIC, AutoSeekReason.READY, enabled)
    }
}

data class ValidatedCaptionMapping(
    val displayHash: String,
    val helperHash: String,
    val helperStartMs: Long,
    val helperEndMs: Long,
    val displayOffsetMs: Long,
    val confidence: Float
) {
    fun displayTimeAt(helperTimeMs: Long, displayHash: String, helperHash: String): Long? =
        if (this.displayHash == displayHash && this.helperHash == helperHash &&
            helperTimeMs in helperStartMs..helperEndMs) helperTimeMs + displayOffsetMs else null
}
