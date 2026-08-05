package com.example.ui.videoplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.data.repository.NetworkRepository
import com.example.player.ExoPlayerManager
import com.example.player.FloatingPlayerService
import com.example.ui.theme.MediaNestTheme

class VideoPlayerActivity : ComponentActivity() {

    companion object {
        var activePlayerManager: ExoPlayerManager? = null
    }

    private lateinit var playerManager: ExoPlayerManager
    private val networkRepository by lazy { NetworkRepository() }

    private fun getPipAspectRatio(): Rational {
        val videoSize = playerManager.exoPlayer.videoSize
        val width = videoSize.width
        val height = videoSize.height
        if (width > 0 && height > 0) {
            val floatRatio = width.toFloat() / height.toFloat()
            // Android PiP aspect ratio must be between 1:2.39 (0.418) and 2.39:1 (2.39)
            return when {
                floatRatio in 0.418f..2.39f -> Rational(width, height)
                floatRatio < 0.418f -> Rational(418, 1000)
                else -> Rational(2390, 1000)
            }
        }
        return Rational(16, 9)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playerManager = ExoPlayerManager.getInstance(applicationContext)
        activePlayerManager = playerManager

        val uriString = intent.getStringExtra("media_uri")
        val title = intent.getStringExtra("media_title") ?: "Video"
        val mimeType = intent.getStringExtra("mime_type") ?: "video/*"
        val uris = intent.getStringArrayListExtra("video_uris")
        val titles = intent.getStringArrayListExtra("video_titles")
        val startIndex = intent.getIntExtra("start_index", 0)

        // Hide notification bar & status bar for immersive video playback
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (!uris.isNullOrEmpty()) {
            val items = uris.mapIndexed { idx, uStr ->
                com.example.data.model.MediaItem(
                    id = idx.toLong(),
                    uri = Uri.parse(uStr),
                    title = titles?.getOrNull(idx) ?: "Video ${idx + 1}",
                    mimeType = "video/*",
                    type = com.example.data.db.MediaType.VIDEO
                )
            }
            playerManager.playMediaList(items, startIndex.coerceIn(0, items.size - 1))
        } else if (uriString != null) {
            val uri = Uri.parse(uriString)
            playerManager.playSingleUri(uri, title, mimeType)
        }

        setContent {
            val settingsManager = com.example.MediaNestApp.instance.settingsManager
            val themeMode by settingsManager.theme.collectAsState(initial = "DARK")

            MediaNestTheme(themeMode = themeMode) {
                VideoPlayerScreen(
                    playerManager = playerManager,
                    networkRepository = networkRepository,
                    onClose = {
                        stopService(Intent(this@VideoPlayerActivity, FloatingPlayerService::class.java))
                        playerManager.exoPlayer.stop()
                        finish()
                    },
                    onEnterPip = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(getPipAspectRatio())
                                    .build()
                                enterPictureInPictureMode(params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onOpenAppSettings = {
                        val intent = Intent(this@VideoPlayerActivity, com.example.MainActivity::class.java).apply {
                            putExtra("open_screen", "SETTINGS")
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(getPipAspectRatio())
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Stop background notification when full player activity is visible
        stopService(Intent(this, FloatingPlayerService::class.java))
    }

    override fun onStop() {
        super.onStop()
        // If app is minimized (not finishing/destroying) and not in Picture-in-Picture mode, start background notification if playing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode) {
            if (playerManager.exoPlayer.isPlaying && !isFinishing && !isChangingConfigurations) {
                val title = playerManager.playerState.value.currentItem?.title ?: "Video Playback"
                val serviceIntent = Intent(this, FloatingPlayerService::class.java).apply {
                    action = FloatingPlayerService.ACTION_START
                    putExtra("media_title", title)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopService(Intent(this, FloatingPlayerService::class.java))
            if (activePlayerManager == playerManager) {
                activePlayerManager = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
