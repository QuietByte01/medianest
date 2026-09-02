package com.medianest.ui.quickview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.medianest.MediaNestApp
import com.medianest.data.model.MediaItem
import com.medianest.data.repository.MediaStoreRepository
import com.medianest.player.ExoPlayerManager
import com.medianest.ui.components.MiniPlayerOverlayManager
import com.medianest.ui.theme.MediaNestTheme
import com.medianest.ui.videoplayer.VideoPlayerActivity
import kotlinx.coroutines.launch

class QuickViewActivity : ComponentActivity() {

    companion object {
        var activeList: List<MediaItem>? = null
    }

    private val mediaStoreRepository by lazy {
        MediaStoreRepository(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Edge-to-edge appearance with transparent system bars (ensures immediate edge swipe gestures)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                com.medianest.R.anim.viewer_open_enter,
                com.medianest.R.anim.viewer_open_exit
            )
        }

        val intentData: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> intent?.data ?: intent?.getStringExtra("media_uri")?.let { Uri.parse(it) }
        }

        var mimeType = intent?.type ?: intentData?.let { contentResolver.getType(it) }

        if (intentData == null) {
            finish()
            return
        }

        // Infer MIME type if unknown or general
        if (mimeType.isNullOrBlank() || mimeType == "*/*") {
            val path = intentData.toString().lowercase()
            mimeType = when {
                path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".3gp") || path.endsWith(".avi") || path.endsWith(".mov") || path.endsWith(".ts") -> "video/*"
                path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".flac") || path.endsWith(".aac") || path.endsWith(".m4a") || path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/*"
                path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".heic") || path.endsWith(".bmp") || path.endsWith(".svg") -> "image/*"
                else -> mimeType
            }
        }

        when {
            mimeType?.startsWith("video/") == true -> {
                // Video: skip Quick View entirely, go straight to VideoPlayerActivity
                val playerIntent = Intent(this, VideoPlayerActivity::class.java).apply {
                    putExtra("media_uri", intentData.toString())
                    putExtra("mime_type", mimeType)
                }
                startActivity(playerIntent)
                finish()
                return
            }

            mimeType?.startsWith("audio/") == true -> {
                // Audio: no full-screen UI — start playback with ID3 metadata and show floating mini player overlay
                val exoPlayerManager = ExoPlayerManager.getInstance(applicationContext)
                
                // Refined MIME for audio
                val bestMime = if (mimeType == "audio/*") {
                    val path = intentData.toString().lowercase()
                    when {
                        path.endsWith(".mp3") -> "audio/mpeg"
                        path.endsWith(".wav") -> "audio/wav"
                        path.endsWith(".flac") -> "audio/flac"
                        path.endsWith(".m4a") -> "audio/mp4"
                        path.endsWith(".ogg") -> "audio/ogg"
                        else -> mimeType
                    }
                } else mimeType

                val mediaItem = com.medianest.util.AudioMetadataUtils.extractMetadata(
                    context = applicationContext,
                    uri = intentData,
                    rawTitleHint = intentData.lastPathSegment,
                    mimeTypeHint = bestMime
                )

                exoPlayerManager.playMediaList(listOf(mediaItem), 0, 0L)

                com.medianest.player.FloatingPlayerService.startOrUpdateService(
                    context = applicationContext,
                    title = mediaItem.title,
                    artist = mediaItem.artist ?: "Unknown Artist",
                    isPlaying = true,
                    artworkUri = mediaItem.albumArtUri?.toString() ?: "",
                    isVideo = false
                )

                // Show floating mini player bar overlay
                MiniPlayerOverlayManager.show(applicationContext)

                // Finish activity so the launching app (e.g. file manager) stays visible and usable underneath
                finish()
                return
            }

            else -> {
                // Image or default: render QuickViewScreen pager
                setContent {
                    val settingsManager = MediaNestApp.instance.settingsManager
                    val themeMode by settingsManager.theme.collectAsState(initial = "DARK")

                    MediaNestTheme(themeMode = themeMode) {
                        var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                        var initialIndex by remember { mutableIntStateOf(0) }
                        var isLoading by remember { mutableStateOf(true) }

                        LaunchedEffect(intentData) {
                            lifecycleScope.launch {
                                isLoading = true
                                val startIndex = intent.getIntExtra("start_index", -1)

                                if (activeList != null) {
                                    mediaList = activeList!!
                                    initialIndex = if (startIndex != -1) startIndex else {
                                        mediaList.indexOfFirst { it.uri == intentData || it.uri.toString() == intentData.toString() }.coerceAtLeast(0)
                                    }
                                } else {
                                    val resolved = mediaStoreRepository.resolveSiblingsForUri(intentData, mimeType)
                                    if (resolved.isNotEmpty()) {
                                        val firstType = resolved.first().type
                                        if (firstType == com.medianest.data.db.MediaType.VIDEO) {
                                            val playerIntent = Intent(this@QuickViewActivity, VideoPlayerActivity::class.java).apply {
                                                putExtra("media_uri", intentData.toString())
                                                putExtra("mime_type", mimeType)
                                            }
                                            startActivity(playerIntent)
                                            finish()
                                            return@launch
                                        } else if (firstType == com.medianest.data.db.MediaType.AUDIO) {
                                            val exoPlayerManager = ExoPlayerManager.getInstance(applicationContext)
                                            exoPlayerManager.playMediaList(resolved, 0)
                                            com.medianest.ui.components.MiniPlayerOverlayManager.show(applicationContext)
                                            finish()
                                            return@launch
                                        }
                                        
                                        mediaList = resolved
                                        val idx = resolved.indexOfFirst { it.uri.toString() == intentData.toString() || it.uri == intentData }
                                        initialIndex = if (idx != -1) idx else 0
                                    } else {
                                        // Fallback to single item if resolution failed
                                        val single = MediaItem(
                                            id = System.currentTimeMillis(),
                                            uri = intentData,
                                            title = intentData.lastPathSegment ?: "Image",
                                            mimeType = mimeType ?: "image/*",
                                            type = com.medianest.data.db.MediaType.IMAGE
                                        )
                                        mediaList = listOf(single)
                                        initialIndex = 0
                                    }
                                }
                                isLoading = false
                            }
                        }

                        QuickViewScreen(
                            mediaList = mediaList,
                            initialIndex = initialIndex,
                            isLoading = isLoading,
                            onClose = { finish() },
                            onOpenFullPlayer = { item: com.medianest.data.model.MediaItem ->
                                val playerIntent = Intent(this@QuickViewActivity, VideoPlayerActivity::class.java).apply {
                                    putExtra("media_uri", item.uri.toString())
                                    putExtra("media_title", item.title)
                                    putExtra("mime_type", item.mimeType)
                                }
                                startActivity(playerIntent)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
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
        if (isFinishing) {
            activeList = null
        }
    }
}
