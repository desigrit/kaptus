package com.example.kaptus.sync

import org.junit.Assert.*
import org.junit.Test

class AutoSeekCapabilityTest {
    @Test fun onlyTheExistingEnglishPairShipsEnabled() {
        assertTrue(AutoSeekCapabilities.evaluate("en", "en", true).canListen)
        AutoSeekCapabilities.targetSpokenLanguages.forEach { source ->
            assertEquals(AutoSeekReason.AWAITING_VALIDATION,
                AutoSeekCapabilities.evaluate(source, "en", true).reason)
            assertFalse(AutoSeekCapabilities.evaluate(source, "en", true).canListen)
        }
    }

    @Test fun registryCannotActivateUnlistedPairsOrNonEnglishDisplay() {
        val registry = AutoSeekValidationRegistry { _, _ -> true }
        listOf("it" to "en", "yue" to "en", "en" to "es", "zh" to "zh-cn", "unknown" to "en").forEach { pair ->
            assertEquals(AutoSeekMode.MANUAL, AutoSeekCapabilities.evaluate(pair.first, pair.second, true, registry).mode)
        }
    }

    @Test fun validatedLanguageStillRequiresAnInstalledModelAndItsSpecificPath() {
        val registry = AutoSeekValidationRegistry { language, path -> language == "ta" && path == MatchingPath.DIRECT_TRANSLATION }
        assertEquals(AutoSeekMode.MODEL_REQUIRED, AutoSeekCapabilities.evaluate("ta", "en", false, registry).mode)
        val ready = AutoSeekCapabilities.evaluate("ta", "en", true, registry)
        assertTrue(ready.canListen)
        assertEquals(setOf(MatchingPath.DIRECT_TRANSLATION), ready.paths)
    }
}
