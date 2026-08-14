package com.medianest

import com.medianest.data.repository.NetworkRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcLyricParserTest {

    private val repository = NetworkRepository()

    @Test
    fun `parseLrcLyrics correctly parses timestamps and sorts lines`() {
        val lrcInput = """
            [01:15.50] Second lyric line
            [00:05.10] First lyric line
            [02:00.00] Third lyric line
        """.trimIndent()

        val parsed = repository.parseLrcLyrics(lrcInput)

        assertEquals(3, parsed.size)
        assertEquals("First lyric line", parsed[0].text)
        assertEquals(5100L, parsed[0].timeMs)

        assertEquals("Second lyric line", parsed[1].text)
        assertEquals(75500L, parsed[1].timeMs)

        assertEquals("Third lyric line", parsed[2].text)
        assertEquals(120000L, parsed[2].timeMs)
    }
}
