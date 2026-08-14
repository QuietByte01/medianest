package com.medianest.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsManagerTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager(context)
    }

    @Test
    fun `test default theme is DARK`() = runTest {
        val theme = settingsManager.theme.first()
        assertEquals("DARK", theme)
    }

    @Test
    fun `test setting theme updates value`() = runTest {
        settingsManager.setTheme("LIGHT")
        val theme = settingsManager.theme.first()
        assertEquals("LIGHT", theme)
    }

    @Test
    fun `test default accent color`() = runTest {
        val accent = settingsManager.accentColor.first()
        assertEquals(0xFF818CF8.toInt(), accent)
    }

    @Test
    fun `test setting accent color updates value`() = runTest {
        settingsManager.setAccentColor(0xFF00FF00.toInt())
        val accent = settingsManager.accentColor.first()
        assertEquals(0xFF00FF00.toInt(), accent)
    }

    @Test
    fun `test pin hashing logic`() {
        val pin = "1234"
        val hashed = settingsManager.hashPin(pin)
        assertNotEquals(pin, hashed)
        assertEquals(64, hashed.length) // SHA-256 hex string length
        
        val hashedAgain = settingsManager.hashPin(pin)
        assertEquals(hashed, hashedAgain)
        
        val differentHash = settingsManager.hashPin("5678")
        assertNotEquals(hashed, differentHash)
    }

    @Test
    fun `test default grid size level`() = runTest {
        val level = settingsManager.gridSizeLevel.first()
        assertEquals(1, level)
    }

    @Test
    fun `test setting grid size level updates value`() = runTest {
        settingsManager.setGridSizeLevel(3)
        val level = settingsManager.gridSizeLevel.first()
        assertEquals(3, level)
    }

    @Test
    fun `test enable trash default`() = runTest {
        val enabled = settingsManager.enableTrash.first()
        assertEquals(true, enabled)
    }
}
