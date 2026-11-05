package com.example.kaptus.sync

import org.junit.Assert.*
import org.junit.Test

class AutoSeekCapabilityTest {
    @Test fun englishAndEveryRequestedPairCanListenWithTheRequiredModel() {
        assertTrue(AutoSeekCapabilities.evaluate("en", "en", true).canListen)
        assertEquals(setOf(MatchingPath.ENGLISH), AutoSeekCapabilities.evaluate("en", "en", true).paths)
        AutoSeekCapabilities.targetSpokenLanguages.forEach { source ->
            val ready = AutoSeekCapabilities.evaluate(source, "en", true)
            assertEquals(AutoSeekReason.READY, ready.reason)
            assertTrue(ready.canListen)
            assertEquals(setOf(MatchingPath.MATCHING_HELPER, MatchingPath.DIRECT_TRANSLATION), ready.paths)
            val missing = AutoSeekCapabilities.evaluate(source, "en", false)
            assertEquals(AutoSeekMode.MODEL_REQUIRED, missing.mode)
            assertFalse(missing.canListen)
        }
    }

    @Test fun pathPolicyCannotActivateUnlistedPairsOrNonEnglishDisplay() {
        val policy = AutoSeekPathPolicy { _, _ -> true }
        listOf("it" to "en", "yue" to "en", "en" to "es", "zh" to "zh-cn", "unknown" to "en").forEach { pair ->
            assertEquals(AutoSeekMode.MANUAL, AutoSeekCapabilities.evaluate(pair.first, pair.second, true, policy).mode)
        }
    }

    @Test fun enabledLanguageStillRequiresAnInstalledModelAndItsSpecificPath() {
        val policy = AutoSeekPathPolicy { language, path -> language == "ta" && path == MatchingPath.DIRECT_TRANSLATION }
        assertEquals(AutoSeekMode.MODEL_REQUIRED, AutoSeekCapabilities.evaluate("ta", "en", false, policy).mode)
        val ready = AutoSeekCapabilities.evaluate("ta", "en", true, policy)
        assertTrue(ready.canListen)
        assertEquals(setOf(MatchingPath.DIRECT_TRANSLATION), ready.paths)
    }

    @Test fun policyCanDisableAllPathsWithoutClaimingDeviceValidation() {
        val disabled = AutoSeekCapabilities.evaluate("hi", "en", true, AutoSeekPathPolicy { _, _ -> false })
        assertEquals(AutoSeekReason.PATH_UNAVAILABLE, disabled.reason)
        assertEquals(AutoSeekMode.MANUAL, disabled.mode)
    }
}
