package com.medianest.ui.audioplayer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.medianest.MediaNestApp
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.components.MiniPlayerOverlayManager
import com.medianest.ui.theme.MediaNestTheme

class AudioPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Edge-to-edge appearance with transparent system bars (ensures immediate edge swipe gestures)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                com.medianest.R.anim.viewer_open_enter,
                com.medianest.R.anim.viewer_open_exit
            )
        }

        val exoPlayerManager = ExoPlayerManager.getInstance(applicationContext)
        val networkRepository = com.medianest.data.repository.NetworkRepository()
        val settingsManager = MediaNestApp.instance.settingsManager

        // Hide floating overlay while full player is open to avoid double UI
        MiniPlayerOverlayManager.hide()

        setContent {
            val themeMode by settingsManager.theme.collectAsState(initial = "DARK")

            MediaNestTheme(themeMode = themeMode) {
                AudioPlayerScreen(
                    playerManager = exoPlayerManager,
                    networkRepository = networkRepository,
                    onClose = { finish() },
                    onOpenAlbum = { finish() },
                    onOpenArtist = { finish() },
                    onOpenFolder = { _, _ -> finish() }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                com.medianest.R.anim.viewer_close_enter,
                com.medianest.R.anim.viewer_close_exit
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                com.medianest.R.anim.viewer_close_enter,
                com.medianest.R.anim.viewer_close_exit
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onPause() {
        super.onPause()
        val exoPlayerManager = ExoPlayerManager.getInstance(applicationContext)
        val isBgPlayEnabled = exoPlayerManager.playerState.value.isAudioBackgroundPlayEnabled
        
        val player = exoPlayerManager.exoPlayer
        if (player != null && player.isPlaying && !isFinishing && !isChangingConfigurations) {
            if (!isBgPlayEnabled) {
                player.pause()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // MiniPlayerOverlayManager is strictly reserved for quick view / external file manager playback.
        MiniPlayerOverlayManager.hide()
    }
}
