package com.medianest.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import com.medianest.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingPlayerService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_PREVIOUS = "PREVIOUS"
        const val ACTION_PLAY_PAUSE = "PLAY_PAUSE"
        const val ACTION_NEXT = "NEXT"
        const val ACTION_SHUFFLE = "SHUFFLE"
        const val ACTION_REPEAT = "REPEAT"
        const val ACTION_STOP = "STOP"
        const val CHANNEL_ID = "media_playback_channel"
        const val NOTIF_ID = 101

        fun startOrUpdateService(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            artworkUri: String? = null,
            isVideo: Boolean = false
        ) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_START
                putExtra("media_title", title)
                putExtra("media_artist", artist)
                putExtra("is_playing", isPlaying)
                putExtra("artwork_uri", artworkUri)
                putExtra("is_video", isVideo)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            try {
                context.stopService(Intent(context, FloatingPlayerService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var currentTitle: String = "Media Playback"
    private var currentArtist: String = ""
    private var isPlayingState: Boolean = false
    private var isVideoState: Boolean = false
    private var currentArtworkUri: String? = null
    private var currentArtworkBitmap: Bitmap? = null
    private var mediaSession: MediaSessionCompat? = null

    private val serviceJob = kotlinx.coroutines.Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        try {
            mediaSession = MediaSessionCompat(this, "MediaNestSession").apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    private fun mgr() = ExoPlayerManager.activeManager
                        ?: ExoPlayerManager.getInstance(applicationContext)

                    override fun onPlay() {
                        mgr().play()
                        updateNotification()
                    }

                    override fun onPause() {
                        mgr().pause()
                        updateNotification()
                    }

                    override fun onSkipToNext() {
                        mgr().next()
                        updateNotification()
                    }

                    override fun onSkipToPrevious() {
                        mgr().previous()
                        updateNotification()
                    }

                    override fun onSeekTo(pos: Long) {
                        mgr().seekTo(pos)
                        updateNotification()
                    }

                    override fun onSetShuffleMode(shuffleMode: Int) {
                        val m = mgr()
                        m.setShuffleMode(shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE)
                        updateNotification()
                    }

                    override fun onSetRepeatMode(repeatMode: Int) {
                        val m = mgr()
                        val nextExoMode = when (repeatMode) {
                            PlaybackStateCompat.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                            PlaybackStateCompat.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
                            else -> Player.REPEAT_MODE_OFF
                        }
                        m.setRepeatMode(nextExoMode)
                        updateNotification()
                    }

                    override fun onStop() {
                        stopService(applicationContext)
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Player Notification",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls in notification bar and lockscreen"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val titleExtra = intent?.getStringExtra("media_title")
        val artistExtra = intent?.getStringExtra("media_artist")
        val artworkExtra = intent?.getStringExtra("artwork_uri")

        if (!titleExtra.isNullOrBlank()) currentTitle = titleExtra
        if (artistExtra != null) currentArtist = artistExtra
        if (intent?.hasExtra("is_playing") == true) {
            isPlayingState = intent.getBooleanExtra("is_playing", false)
        }
        if (intent?.hasExtra("is_video") == true) {
            isVideoState = intent.getBooleanExtra("is_video", false)
        }

        if (artworkExtra != null && artworkExtra != currentArtworkUri) {
            currentArtworkUri = artworkExtra
            loadArtworkBitmap(artworkExtra)
        }

        val activeManager = ExoPlayerManager.activeManager

        when (action) {
            ACTION_START -> {
                updateNotification()
            }
            ACTION_PREVIOUS -> {
                activeManager?.previous()
                updateNotification()
            }
            ACTION_PLAY_PAUSE -> {
                if (activeManager != null) {
                    activeManager.togglePlayPause()
                    // Update local state based on player
                    isPlayingState = activeManager.isPlaying
                } else {
                    isPlayingState = !isPlayingState
                }
                updateNotification()
            }
            ACTION_NEXT -> {
                activeManager?.next()
                updateNotification()
            }
            ACTION_SHUFFLE -> {
                activeManager?.setShuffleMode(!activeManager.playerState.value.isShuffle)
                updateNotification()
            }
            ACTION_REPEAT -> {
                if (activeManager != null) {
                    val nextMode = when (activeManager.playerState.value.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    activeManager.setRepeatMode(nextMode)
                }
                updateNotification()
            }
            ACTION_STOP -> {
                val isVideoActive = com.medianest.ui.videoplayer.VideoPlayerActivity.activePlayerManager != null
                if (!isVideoActive) {
                    activeManager?.pause()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            else -> {
                updateNotification()
            }
        }
        return START_NOT_STICKY


    }

    private fun loadArtworkBitmap(uriString: String?) {
        if (uriString.isNullOrBlank()) {
            currentArtworkBitmap = null
            updateNotification()
            return
        }
        serviceScope.launch {
            var bitmap: Bitmap? = null
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriString)
                    // 1. Try MediaMetadataRetriever embedded picture for audio
                    try {
                        val mmr = android.media.MediaMetadataRetriever()
                        mmr.setDataSource(applicationContext, uri)
                        val artBytes = mmr.embeddedPicture
                        if (artBytes != null) {
                            bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                        }
                        mmr.release()
                    } catch (e: Exception) {
                        // ignore
                    }

                    // 2. Try loadThumbnail for Q+
                    if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            bitmap = contentResolver.loadThumbnail(uri, Size(300, 300), null)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }

                    // 3. Try openInputStream
                    if (bitmap == null) {
                        try {
                            contentResolver.openInputStream(uri)?.use { stream ->
                                bitmap = BitmapFactory.decodeStream(stream)
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            currentArtworkBitmap = bitmap?.let { blurBitmap(it, 8) }
            updateNotification()
        }
    }

    private fun blurBitmap(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return bitmap
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Very minimal blur (0.85 scale)
        val smallBitmap = Bitmap.createScaledBitmap(bitmap, (width * 0.85f).toInt().coerceAtLeast(1), (height * 0.85f).toInt().coerceAtLeast(1), true)
        return Bitmap.createScaledBitmap(smallBitmap, width, height, true)
    }

    private fun updateNotification() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val activeManager = ExoPlayerManager.activeManager
        val isPlaying = activeManager?.playerState?.value?.isPlaying ?: isPlayingState

        val contentIntent = if (isVideoState) {
            Intent(this, com.medianest.ui.videoplayer.VideoPlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                action = "OPEN_AUDIO_PLAYER"
                putExtra("open_screen", "AUDIO_PLAYER")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val prevIntent = Intent(this, FloatingPlayerService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPendingIntent = PendingIntent.getService(
            this, 1, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val playPauseIntent = Intent(this, FloatingPlayerService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val nextIntent = Intent(this, FloatingPlayerService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, FloatingPlayerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 4, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val shuffleIntent = Intent(this, FloatingPlayerService::class.java).apply { action = ACTION_SHUFFLE }
        val shufflePendingIntent = PendingIntent.getService(
            this, 5, shuffleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val repeatIntent = Intent(this, FloatingPlayerService::class.java).apply { action = ACTION_REPEAT }
        val repeatPendingIntent = PendingIntent.getService(
            this, 6, repeatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val statusText = if (isPlaying) "Playing" else "Paused"
        val subtitle = if (currentArtist.isNotBlank()) "$currentArtist • $statusText" else statusText

        // Symmetrical 5 actions for expanded view (Indices 1, 2, 3 shown in compact view centered)
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(1, 2, 3)

        mediaSession?.let { session ->
            mediaStyle.setMediaSession(session.sessionToken)

            val posMs = activeManager?.playerState?.value?.currentPositionMs ?: 0L
            val durMs = activeManager?.playerState?.value?.durationMs ?: 0L
            val speed = if (isPlaying) 1.0f else 0.0f
            val stateVal = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED

            val playbackState = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                    PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                )
                .setState(stateVal, posMs, speed, SystemClock.elapsedRealtime())
                .build()
            session.setPlaybackState(playbackState)

            if (activeManager != null) {
                val sMode = if (activeManager.playerState.value.isShuffle) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE
                val rMode = when (activeManager.playerState.value.repeatMode) {
                    Player.REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                }
                session.setShuffleMode(sMode)
                session.setRepeatMode(rMode)
            }

            val metaBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durMs)
            if (currentArtworkBitmap != null) {
                metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArtworkBitmap)
            }
            session.setMetadata(metaBuilder.build())
            
            // Critical for Android 11+ System Media UI to show shuffle/repeat
            session.setShuffleMode(if (activeManager?.playerState?.value?.isShuffle == true) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
            session.setRepeatMode(when (activeManager?.playerState?.value?.repeatMode) {
                Player.REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            })
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(android.R.drawable.ic_menu_rotate, "Shuffle", shufflePendingIntent) // Action 0
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent) // Action 1
            .addAction(playPauseIcon, playPauseTitle, playPausePendingIntent) // Action 2
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent) // Action 3
            .addAction(android.R.drawable.ic_menu_revert, "Repeat", repeatPendingIntent) // Action 4

        if (currentArtworkBitmap != null) {
            // Show album art ONLY
            builder.setLargeIcon(currentArtworkBitmap)
        } else {
            // Show app icon ONLY if album art is not available
            try {
                val appIcon = BitmapFactory.decodeResource(resources, com.medianest.R.mipmap.ic_launcher)
                builder.setLargeIcon(appIcon)
            } catch (e: Exception) {
                // ignore
            }
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}
