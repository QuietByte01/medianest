package com.medianest.player

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.medianest.data.model.MediaItem
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ExoPlayerManagerTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        Dispatchers.setMain(testDispatcher)
        
        // 1. Mock ExoPlayer.Builder and its chain BEFORE instance creation
        val mockPlayer = mockk<ExoPlayer>(relaxed = true)
        val mockBuilder = mockk<ExoPlayer.Builder>(relaxed = true)
        
        every { mockBuilder.setMediaSourceFactory(any()) } returns mockBuilder
        every { mockBuilder.setLoadControl(any()) } returns mockBuilder
        every { mockBuilder.setAudioAttributes(any(), any()) } returns mockBuilder
        every { mockBuilder.build() } returns mockPlayer
        
        mockkConstructor(ExoPlayer.Builder::class)
        // Ensure any new Builder() call returns our chained mock
        every { anyConstructed<ExoPlayer.Builder>().setMediaSourceFactory(any()) } returns mockBuilder
        
        // 2. Reset Singleton instance for testing
        val instanceField: Field = ExoPlayerManager::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)
        
        mockkObject(MediaCapabilityInspector)
    }

    @After
    fun teardown() {
        unmockkConstructor(ExoPlayer.Builder::class)
        unmockkObject(MediaCapabilityInspector)
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `test releaseAudioEffects correctly clears effects`() {
        val manager = ExoPlayerManager.getInstance(context)
        val mockEq = mockk<Equalizer>(relaxed = true)
        
        val eqField = ExoPlayerManager::class.java.getDeclaredField("androidEqualizer")
        eqField.isAccessible = true
        eqField.set(manager, mockEq)
        
        val releaseMethod = ExoPlayerManager::class.java.getDeclaredMethod("releaseAudioEffects")
        releaseMethod.isAccessible = true
        releaseMethod.invoke(manager)
        
        verify { mockEq.release() }
        assertEquals(null, eqField.get(manager))
    }

    @Test
    fun `test audio session change triggers release of previous effects`() {
        val manager = ExoPlayerManager.getInstance(context)
        val mockEq = mockk<Equalizer>(relaxed = true)
        
        val sessionIdField = ExoPlayerManager::class.java.getDeclaredField("activeAudioSessionId")
        sessionIdField.isAccessible = true
        sessionIdField.set(manager, 100)
        
        val eqField = ExoPlayerManager::class.java.getDeclaredField("androidEqualizer")
        eqField.isAccessible = true
        eqField.set(manager, mockEq)
        
        val updateMethod = ExoPlayerManager::class.java.getDeclaredMethod("updateAndroidAudioEffects", Int::class.java)
        updateMethod.isAccessible = true
        updateMethod.invoke(manager, 200)
        
        verify { mockEq.release() }
        assertEquals(200, sessionIdField.get(manager))
    }

    @Test
    fun `test updateAndroidAudioEffects ignores invalid session IDs`() {
        val manager = ExoPlayerManager.getInstance(context)
        val sessionIdField = ExoPlayerManager::class.java.getDeclaredField("activeAudioSessionId")
        sessionIdField.isAccessible = true
        sessionIdField.set(manager, 42)
        
        val updateMethod = ExoPlayerManager::class.java.getDeclaredMethod("updateAndroidAudioEffects", Int::class.java)
        updateMethod.isAccessible = true
        
        // Call with 0 - should not change state
        updateMethod.invoke(manager, 0)
        assertEquals(42, sessionIdField.get(manager))
        
        // Call with UNSET - should not change state
        updateMethod.invoke(manager, C.AUDIO_SESSION_ID_UNSET)
        assertEquals(42, sessionIdField.get(manager))
    }

    @Test
    fun `test hardware decoder error triggers ffmpeg fallback logic`() {
        val manager = ExoPlayerManager.getInstance(context)
        
        val mockItem = mockk<MediaItem>(relaxed = true)
        val playerStateField = ExoPlayerManager::class.java.getDeclaredField("_playerState")
        playerStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = playerStateField.get(manager) as kotlinx.coroutines.flow.MutableStateFlow<PlayerState>
        stateFlow.value = PlayerState(currentItem = mockItem)

        val activeEngineField = ExoPlayerManager::class.java.getDeclaredField("activeEngine")
        activeEngineField.isAccessible = true
        
        val ffmpegEngineField = ExoPlayerManager::class.java.getDeclaredField("ffmpegEngine")
        ffmpegEngineField.isAccessible = true
        val ffmpegEngine = ffmpegEngineField.get(manager)

        val scopeField = ExoPlayerManager::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        val scope = scopeField.get(manager) as kotlinx.coroutines.CoroutineScope

        scope.launch(Dispatchers.Main) {
            activeEngineField.set(manager, ffmpegEngine)
            stateFlow.value = stateFlow.value.copy(
                activeEngineName = "FFmpeg (Fallback)",
                isHardwareAccelerated = false
            )
        }
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(ffmpegEngine, activeEngineField.get(manager))
        assertEquals("FFmpeg (Fallback)", manager.playerState.value.activeEngineName)
    }

    @Test
    fun `test engine diagnostic reporting for HW vs SW`() {
        val mockPlayer = mockk<ExoPlayer>(relaxed = true)
        val engine = Media3PlaybackEngine(context, mockPlayer)
        
        val listenerField = Media3PlaybackEngine::class.java.getDeclaredField("analyticsListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(engine) as androidx.media3.exoplayer.analytics.AnalyticsListener
        
        // SW decoder
        listener.onVideoDecoderInitialized(mockk(), "c2.android.avc.decoder", 0, 0)
        assertFalse(engine.diagnosticState.value.isHardwareAccelerated)
        
        // HW decoder
        listener.onVideoDecoderInitialized(mockk(), "OMX.qcom.video.decoder.avc", 0, 0)
        assertTrue(engine.diagnosticState.value.isHardwareAccelerated)
    }
}
