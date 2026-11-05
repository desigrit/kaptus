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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAmbiguousLegacyEncodingInsteadOfDisplayingMojibake() {
        val source = "1\n00:00:00,000 --> 00:00:01,000\nCaf\u00e9\n"
        val bytes = source.toByteArray(Charsets.ISO_8859_1)

        parser.parse(ByteArrayInputStream(bytes))
    }

    @Test
    fun parsesUtf16BomInBothByteOrdersAndPreservesScripts() {
        val text = "العربية 日本語 हिन्दी தமிழ் తెలుగు"
        val source = "1\n00:00:00,000 --> 00:00:01,000\n$text\n"
        listOf(Charsets.UTF_16LE to byteArrayOf(0xff.toByte(), 0xfe.toByte()),
            Charsets.UTF_16BE to byteArrayOf(0xfe.toByte(), 0xff.toByte())).forEach { (charset, bom) ->
            assertEquals(text, parser.parse(ByteArrayInputStream(bom + source.toByteArray(charset))).single().text)
        }
        assertEquals(text, parser.parse(source.byteInputStream()).single().text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedUtf16() {
        parser.parse(ByteArrayInputStream(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 1)))
    }

    @Test
    fun recoversAdjacentCuesWithoutBlankSeparatorsAndKeepsMultilineNumericDialogue() {
        val source = """
            1
            00:00:01,000 --> 00:00:02,000
            Room number:
            2026
            2
            00:00:03,000 --> 00:00:04,000
            42
            Second line.
            3
            00:00:05,000 --> 00:00:06,000
            Last cue.
        """.trimIndent()
        val entries = parser.parse(source.byteInputStream())
        assertEquals(listOf(1, 2, 3), entries.map { it.index })
        assertEquals(listOf("Room number:\n2026", "42\nSecond line.", "Last cue."), entries.map { it.text })
        assertEquals(listOf(1_000L, 3_000L, 5_000L), entries.map { it.startTimeMs })
    }

    @Test
    fun numericDialogueBeforeAnUnnumberedAdjacentCueIsPreserved() {
        val source = "1\n00:00:01,000 --> 00:00:02,000\nThe year is\n2026\n" +
            "00:00:03,000 --> 00:00:04,000\nNext scene.\n"
        val entries = parser.parse(source.byteInputStream())
        assertEquals(2, entries.size)
        assertEquals("The year is\n2026", entries[0].text)
        assertEquals("Next scene.", entries[1].text)
    }

    @Test
    fun invalidTimingDoesNotLeakIntoEarlierCaptions() {
        val source = """
            1
            00:00:01,000 --> 00:00:02,000
            Valid first cue.
            2
            00:60:01,000 --> 01:01:02,000
            Invalid minutes.
            3
            00:00:60,000 --> 00:02:00,000
            Invalid seconds.
            4
            00:00:03,000 --> 00:00:03,000
            Zero duration.
            5
            00:00:05,000 --> 00:00:04,000
            Backwards.
            6
            00:00:xx,000 --> 00:00:06,000
            Malformed time.
            7
            00:00:07,000 --> 00:00:08,000
            Valid final cue.
        """.trimIndent()
        val entries = parser.parse(source.byteInputStream())
        assertEquals(listOf("Valid first cue.", "Valid final cue."), entries.map { it.text })
        assertEquals(listOf(1, 7), entries.map { it.index })
    }
}
