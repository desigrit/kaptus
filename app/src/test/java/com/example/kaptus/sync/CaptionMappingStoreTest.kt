package com.example.kaptus.sync

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptionMappingStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val display = "a".repeat(64)
    private val helper = "b".repeat(64)

    @Test fun reopensOnlyTheVerifiedTrackPairAndRegion() {
        val store = CaptionMappingStore(temporary.newFolder())
        val mapping = ValidatedCaptionMapping(display, helper, 1000, 3000, 500, .9f)
        store.save(mapping)
        assertEquals(mapping, store.load(display, helper))
        assertEquals(2500L, store.load(display, helper)?.displayTimeAt(2000, display, helper))
        assertNull(store.load(display, helper)?.displayTimeAt(4000, display, helper))
        assertNull(store.load("c".repeat(64), helper))
    }

    @Test fun corruptedCacheDoesNotBecomeAnAcceptedMapping() {
        val folder = temporary.newFolder()
        val store = CaptionMappingStore(folder)
        store.save(ValidatedCaptionMapping(display, helper, 1000, 3000, 500, .9f))
        folder.listFiles()!!.single().writeText("invalid")
        assertNull(store.load(display, helper))
        assertNull(store.load("../elsewhere", helper))
    }
}
