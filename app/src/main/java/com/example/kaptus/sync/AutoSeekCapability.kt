package com.example.kaptus.sync

enum class AutoSeekMode { AUTOMATIC, MANUAL, MODEL_REQUIRED }
enum class AutoSeekReason { READY, UNSUPPORTED_PAIR, AWAITING_VALIDATION, MODEL_MISSING }
enum class MatchingPath { ENGLISH, MATCHING_HELPER, DIRECT_TRANSLATION }

data class AutoSeekCapability(
    val mode: AutoSeekMode,
    val reason: AutoSeekReason,
    val paths: Set<MatchingPath> = emptySet()
) {
    val canListen: Boolean get() = mode == AutoSeekMode.AUTOMATIC
}

/** A pair is activated only after the platform and matching path pass device validation. */
fun interface AutoSeekValidationRegistry {
    fun isValidated(spokenLanguage: String, path: MatchingPath): Boolean
}

object ShippingValidationRegistry : AutoSeekValidationRegistry {
    override fun isValidated(spokenLanguage: String, path: MatchingPath): Boolean =
        spokenLanguage == "en" && path == MatchingPath.ENGLISH
}

object AutoSeekCapabilities {
    val targetSpokenLanguages = setOf("zh", "ja", "es", "fr", "de", "ko", "hi", "te", "ta")

    fun evaluate(
        spokenLanguage: String,
        captionLanguage: String,
        modelReady: Boolean,
        registry: AutoSeekValidationRegistry = ShippingValidationRegistry
    ): AutoSeekCapability {
        if (captionLanguage != "en" || (spokenLanguage != "en" && spokenLanguage !in targetSpokenLanguages)) {
            return AutoSeekCapability(AutoSeekMode.MANUAL, AutoSeekReason.UNSUPPORTED_PAIR)
        }
        val paths = if (spokenLanguage == "en") setOf(MatchingPath.ENGLISH) else
            setOf(MatchingPath.MATCHING_HELPER, MatchingPath.DIRECT_TRANSLATION)
        val validated = paths.filter { registry.isValidated(spokenLanguage, it) }.toSet()
        if (validated.isEmpty()) return AutoSeekCapability(AutoSeekMode.MANUAL, AutoSeekReason.AWAITING_VALIDATION)
        if (!modelReady) return AutoSeekCapability(AutoSeekMode.MODEL_REQUIRED, AutoSeekReason.MODEL_MISSING, validated)
        return AutoSeekCapability(AutoSeekMode.AUTOMATIC, AutoSeekReason.READY, validated)
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
