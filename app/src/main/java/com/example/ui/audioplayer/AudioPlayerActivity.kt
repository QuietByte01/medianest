package com.example.ui.audioplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.example.MediaNestApp
import com.example.player.ExoPlayerManager
import com.example.ui.components.MiniPlayerOverlayManager
import com.example.ui.theme.MediaNestTheme

class AudioPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val exoPlayerManager = ExoPlayerManager.getInstance(applicationContext)
        val networkRepository = com.example.data.repository.NetworkRepository()
        val settingsManager = MediaNestApp.instance.settingsManager

        // Hide floating overlay while full player is open to avoid double UI
        MiniPlayerOverlayManager.hide()

        setContent {
            val themeMode by settingsManager.theme.collectAsState(initial = "DARK")

            MediaNestTheme(themeMode = themeMode) {
                AudioPlayerScreen(
                    playerManager = exoPlayerManager,
                    networkRepository = networkRepository,
                    onClose = {
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, android.R.anim.fade_out)
                    },
                    onOpenAlbum = {
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, android.R.anim.fade_out)
                    },
                    onOpenArtist = {
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, android.R.anim.fade_out)
                    },
                    onOpenFolder = {
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, android.R.anim.fade_out)
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val exoPlayerManager = ExoPlayerManager.getInstance(applicationContext)
        val isBgPlayEnabled = exoPlayerManager.playerState.value.isAudioBackgroundPlayEnabled
        
        if (exoPlayerManager.exoPlayer.isPlaying && !isFinishing && !isChangingConfigurations) {
            if (!isBgPlayEnabled) {
                exoPlayerManager.exoPlayer.pause()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // MiniPlayerOverlayManager is strictly reserved for quick view / external file manager playback.
        MiniPlayerOverlayManager.hide()
    }
}
