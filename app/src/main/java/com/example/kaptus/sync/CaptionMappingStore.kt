package com.example.kaptus.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The caller supplies an app-private directory excluded from backup. */
class CaptionMappingStore(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun load(displayHash: String, helperHash: String): ValidatedCaptionMapping? {
        val file = destination(displayHash, helperHash) ?: return null
        return runCatching {
            if (!file.isFile || file.length() > 4096) return null
            val stored = json.decodeFromString<StoredMapping>(file.readText(Charsets.UTF_8))
            stored.mapping().takeIf {
                it.displayHash == displayHash && it.helperHash == helperHash &&
                    it.helperStartMs >= 0 && it.helperEndMs >= it.helperStartMs &&
                    it.confidence.isFinite() && it.confidence in 0f..1f
            }
        }.getOrNull()
    }

    @Synchronized
    fun save(mapping: ValidatedCaptionMapping) {
        val file = destination(mapping.displayHash, mapping.helperHash) ?: return
        if (mapping.helperStartMs < 0 || mapping.helperEndMs < mapping.helperStartMs ||
            !mapping.confidence.isFinite() || mapping.confidence !in 0f..1f) return
        directory.mkdirs()
        val temporary = File(directory, "${file.name}.partial")
        temporary.writeText(json.encodeToString(StoredMapping(mapping)), Charsets.UTF_8)
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun destination(displayHash: String, helperHash: String): File? {
        val digestPattern = Regex("[a-f0-9]{64}")
        if (!digestPattern.matches(displayHash) || !digestPattern.matches(helperHash)) return null
        return File(directory, "$displayHash-$helperHash.json")
    }

    @Serializable
    private data class StoredMapping(val displayHash: String, val helperHash: String,
                                     val helperStartMs: Long, val helperEndMs: Long,
                                     val displayOffsetMs: Long, val confidence: Float) {
        constructor(mapping: ValidatedCaptionMapping) : this(mapping.displayHash, mapping.helperHash,
            mapping.helperStartMs, mapping.helperEndMs, mapping.displayOffsetMs, mapping.confidence)
        fun mapping() = ValidatedCaptionMapping(displayHash, helperHash, helperStartMs, helperEndMs, displayOffsetMs, confidence)
    }
}
