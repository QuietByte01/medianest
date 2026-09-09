package com.medianest.ui.videoplayer

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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.medianest.data.repository.NetworkRepository
import com.medianest.player.ExoPlayerManager
import com.medianest.player.FloatingPlayerService
import com.medianest.ui.components.SlideShowVideoPreviewCoordinator
import com.medianest.ui.theme.MediaNestTheme

class VideoPlayerActivity : ComponentActivity() {

    companion object {
        var activeList: List<com.medianest.data.model.MediaItem>? = null
        var activeContextTitle: String? = null
    }

    private lateinit var playerManager: ExoPlayerManager
    private val networkRepository by lazy { NetworkRepository() }

    private fun getPipAspectRatio(): Rational {
        val player = playerManager.exoPlayer ?: return Rational(16, 9)
        
        val videoSize = player.videoSize
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
        
        // Pause all in-grid video previews so the full video player has 100% decoder access
        SlideShowVideoPreviewCoordinator.pause()

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                com.medianest.R.anim.viewer_open_enter,
                com.medianest.R.anim.viewer_open_exit
            )
        }

        playerManager = ExoPlayerManager.getInstance(applicationContext)

        val uriString = intent.getStringExtra("media_uri")
        val title = intent.getStringExtra("media_title") ?: "Video"
        val mimeType = intent.getStringExtra("mime_type") ?: "video/*"
        val contextTitle = intent.getStringExtra("context_title") ?: activeContextTitle
        val startIndex = intent.getIntExtra("start_index", 0)

        // Edge-to-edge appearance with transparent system bars (ensures immediate edge swipe gestures)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (activeList != null) {
            playerManager.playMediaList(activeList!!, startIndex.coerceIn(0, activeList!!.size - 1), queueTitle = contextTitle)
        } else if (uriString != null) {
            val uri = Uri.parse(uriString)
            playerManager.playSingleUri(uri, title, mimeType)
        }

        setContent {
            val settingsManager = com.medianest.MediaNestApp.instance.settingsManager
            val themeMode by settingsManager.theme.collectAsState(initial = "DARK")
            val playerState by playerManager.playerState.collectAsState()

            // Automatically switch window color mode to HDR on Android 8.0+ (API 26+)
            // to enable full wide-color-gamut (BT.2020 / PQ / HLG) display pipeline without SDR clipping
            androidx.compose.runtime.LaunchedEffect(playerState.isHdrContent) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        window.colorMode = if (playerState.isHdrContent) {
                            android.content.pm.ActivityInfo.COLOR_MODE_HDR
                        } else {
                            android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
                        }
                    } catch (_: Exception) {}
                }
            }

            MediaNestTheme(themeMode = themeMode) {
                VideoPlayerScreen(
                    playerManager = playerManager,
                    networkRepository = networkRepository,
                    onClose = {
                        // Always stop playback on explicit close via UI (Back button or Close icon)
                        playerManager.stop()
                        stopService(Intent(this@VideoPlayerActivity, FloatingPlayerService::class.java))
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
                        val intent = Intent(this@VideoPlayerActivity, com.medianest.MainActivity::class.java).apply {
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

    override fun onResume() {
        super.onResume()
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Stop background notification when full player activity is visible
        stopService(Intent(this, FloatingPlayerService::class.java))
        if (wasPlayingBeforePause && !playerManager.isPlaying) {
            wasPlayingBeforePause = false
            playerManager.play()
        }
    }

    private var wasPlayingBeforePause = false

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
        // If app is minimized / sent to recent apps (not finishing/destroying) and not in Picture-in-Picture mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode) {
            val state = playerManager.playerState.value
            val isBgPlayEnabled = state.isVideoBackgroundPlayEnabled
            
            if (playerManager.isPlaying && !isFinishing && !isChangingConfigurations) {
                if (!isBgPlayEnabled) {
                    wasPlayingBeforePause = true
                    playerManager.pause()
                } else {
                    wasPlayingBeforePause = false
                    val currentItem = state.currentItem
                    if (currentItem != null) {
                        val isVideo = currentItem.mimeType.startsWith("video") || currentItem.type == com.medianest.data.db.MediaType.VIDEO
                        FloatingPlayerService.startOrUpdateService(
                            context = this,
                            title = currentItem.title,
                            artist = currentItem.artist ?: currentItem.album ?: currentItem.bucketName ?: "MediaNest",
                            isPlaying = true,
                            artworkUri = currentItem.albumArtUri?.toString() ?: currentItem.uri.toString(),
                            isVideo = isVideo
                        )
                    }
                }
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            val state = playerManager.playerState.value
            // If finishing or background play is disabled, stop playback and service
            if (isFinishing || !state.isVideoBackgroundPlayEnabled) {
                playerManager.stop()
                stopService(Intent(this, FloatingPlayerService::class.java))
            } else {
                playerManager.pause()
            }
            
            if (isFinishing) {
                activeList = null
                activeContextTitle = null
                SlideShowVideoPreviewCoordinator.resume()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
