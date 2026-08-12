package com.example.ui.quickview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.MediaNestApp
import com.example.data.model.MediaItem
import com.example.data.repository.MediaStoreRepository
import com.example.player.ExoPlayerManager
import com.example.ui.components.MiniPlayerOverlayManager
import com.example.ui.theme.MediaNestTheme
import com.example.ui.videoplayer.VideoPlayerActivity
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
                path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".3gp") || path.endsWith(".avi") -> "video/*"
                path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".flac") || path.endsWith(".aac") || path.endsWith(".m4a") || path.endsWith(".ogg") -> "audio/*"
                path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".gif") -> "image/*"
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
                // Audio: no full-screen UI — start playback and show floating mini player overlay
                val exoPlayerManager = ExoPlayerManager.getInstance(applicationContext)
                val title = intentData.lastPathSegment ?: "Audio Track"
                
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

                exoPlayerManager.playSingleUri(intentData, title, bestMime)

                // IMPORTANT: Poke the service immediately so it holds onto the URI permission 
                // before this activity finishes.
                com.example.player.FloatingPlayerService.startOrUpdateService(
                    context = applicationContext,
                    title = title,
                    artist = "Loading...",
                    isPlaying = true,
                    artworkUri = intentData.toString(),
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

                        LaunchedEffect(intentData) {
                            lifecycleScope.launch {
                                val startIndex = intent.getIntExtra("start_index", -1)

                                if (activeList != null) {
                                    mediaList = activeList!!
                                    initialIndex = if (startIndex != -1) startIndex else {
                                        mediaList.indexOfFirst { it.uri == intentData || it.uri.toString() == intentData.toString() }.coerceAtLeast(0)
                                    }
                                } else {
                                    val resolved = mediaStoreRepository.resolveSiblingsForUri(intentData, mimeType)
                                    // ... existing sibling resolution logic
                                    if (resolved.isNotEmpty()) {
                                        val firstType = resolved.first().type
                                        if (firstType == com.example.data.db.MediaType.VIDEO) {
                                            val playerIntent = Intent(this@QuickViewActivity, VideoPlayerActivity::class.java).apply {
                                                putExtra("media_uri", intentData.toString())
                                                putExtra("mime_type", mimeType)
                                            }
                                            startActivity(playerIntent)
                                            finish()
                                            return@launch
                                        } else if (firstType == com.example.data.db.MediaType.AUDIO) {
                                            val exoPlayerManager = ExoPlayerManager.getInstance(applicationContext)
                                            exoPlayerManager.playMediaList(resolved, 0)
                                            MiniPlayerOverlayManager.show(applicationContext)
                                            finish()
                                            return@launch
                                        }
                                    }
                                    mediaList = resolved
                                    val idx = resolved.indexOfFirst { it.uri == intentData || it.uri.toString() == intentData.toString() }
                                    initialIndex = if (idx != -1) idx else 0
                                }
                            }
                        }

                        QuickViewScreen(
                            mediaList = mediaList,
                            initialIndex = initialIndex,
                            onClose = { finish() },
                            onOpenFullPlayer = { item ->
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

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            activeList = null
        }
    }
}
