package com.medianest

import com.medianest.util.MetadataUtils
import org.junit.Assert.*
import org.junit.Test

class MetadataUtilsTest {

    @Test
    fun `sanitizeTitle removes domain names and website spam`() {
        assertEquals("Unknown Track", MetadataUtils.sanitizeTitle(null))
        assertEquals("Unknown Track", MetadataUtils.sanitizeTitle(""))
        assertEquals("SongTitle", MetadataUtils.sanitizeTitle("SongTitle"))
        assertEquals("My Cool Track", MetadataUtils.sanitizeTitle("My Cool Track www.mp3downloads.com"))
    }

    @Test
    fun `sanitizeArtist detects unknown or website artists`() {
        assertEquals("Unknown Artist", MetadataUtils.sanitizeArtist("<unknown>"))
        assertEquals("Unknown Artist", MetadataUtils.sanitizeArtist("unknown"))
        assertEquals("Unknown Artist", MetadataUtils.sanitizeArtist("www.musicblog.net"))
        assertEquals("Coldplay", MetadataUtils.sanitizeArtist("Coldplay"))
    }

    @Test
    fun `parseMultipleArtists splits featured and collaborated artists`() {
        val artists = MetadataUtils.parseMultipleArtists("Daft Punk feat. Pharrell Williams & Nile Rodgers")
        assertEquals(listOf("Daft Punk", "Pharrell Williams", "Nile Rodgers"), artists)
    }

    @Test
    fun `hasDomainOrFalseInfo identifies URLs and missing tags`() {
        assertTrue(MetadataUtils.hasDomainOrFalseInfo("https://free-music.ru"))
        assertTrue(MetadataUtils.hasDomainOrFalseInfo("http://legacy-site.ru"))
        assertTrue(MetadataUtils.hasDomainOrFalseInfo("Unknown Artist"))
        assertFalse(MetadataUtils.hasDomainOrFalseInfo("Hans Zimmer"))
    }
}
