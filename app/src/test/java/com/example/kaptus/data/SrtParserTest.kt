package com.example.kaptus.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtParserTest {
    private val parser = SrtParser()

    @Test
    fun parsesBomCrLfMultilineAndFormattingTags() {
        val source = "\uFEFF1\r\n00:00:01,250 --> 00:00:03,500\r\n<i>Hello &amp; welcome.</i>\r\nSecond line.\r\n\r\n" +
            "2\r\n00:00:04.00 --> 00:00:05.250\r\n{\\an8}<b>Next&nbsp;cue</b>\r\n"

        val entries = parser.parse(source.byteInputStream())

        assertEquals(2, entries.size)
        assertEquals(1_250L, entries[0].startTimeMs)
        assertEquals(3_500L, entries[0].endTimeMs)
        assertEquals("Hello & welcome.\nSecond line.", entries[0].text)
        assertEquals("Next cue", entries[1].text)
        assertEquals(4_000L, entries[1].startTimeMs)
    }

    @Test
    fun skipsMalformedBlocksAndSortsUsableCues() {
        val source = """
            broken
            not a timestamp

            8
            00:00:09,000 --> 00:00:08,000
            backwards

            3
            00:00:05,000 --> 00:00:06,000
            later

            2
            00:00:02,000 --> 00:00:03,000
            earlier
        """.trimIndent()

        val entries = parser.parse(source.byteInputStream())

        assertEquals(listOf("earlier", "later"), entries.map { it.text })
        assertTrue(entries.zipWithNext().all { (first, second) -> first.startTimeMs <= second.startTimeMs })
    }

    @Test
    fun fallsBackForCommonSingleByteEncoding() {
        val source = "1\n00:00:00,000 --> 00:00:01,000\nCaf\u00e9\n"
        val bytes = source.toByteArray(Charsets.ISO_8859_1)

        val entries = parser.parse(ByteArrayInputStream(bytes))

        assertEquals("Caf\u00e9", entries.single().text)
    }
}
