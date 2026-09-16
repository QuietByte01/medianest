package com.medianest.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionUtils {

    data class RequiredPermissionInfo(
        val id: String,
        val permission: String,
        val title: String,
        val description: String,
        val isGranted: Boolean,
        val isSpecial: Boolean = false
    )

    fun hasAllFilesAccess(context: Context): Boolean {
        return hasStandardMediaPermissions(context)
    }

    fun hasStandardMediaPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            val hasPartial = if (Build.VERSION.SDK_INT >= 34) {
                ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED
            } else false

            (hasImages && hasVideo && hasAudio) || hasPartial
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAnyStorageAccess(context: Context): Boolean {
        return hasStandardMediaPermissions(context)
    }

    fun getRequiredPermissions(context: Context): List<RequiredPermissionInfo> {
        val list = mutableListOf<RequiredPermissionInfo>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                RequiredPermissionInfo(
                    id = "READ_MEDIA_IMAGES",
                    permission = Manifest.permission.READ_MEDIA_IMAGES,
                    title = "Photos & Images Access",
                    description = "Required to scan, display, and manage photo and image files in your library via MediaStore.",
                    isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                )
            )
            list.add(
                RequiredPermissionInfo(
                    id = "READ_MEDIA_VIDEO",
                    permission = Manifest.permission.READ_MEDIA_VIDEO,
                    title = "Videos Access",
                    description = "Required to discover, play, and organize video files in your library via MediaStore.",
                    isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                )
            )
            list.add(
                RequiredPermissionInfo(
                    id = "READ_MEDIA_AUDIO",
                    permission = Manifest.permission.READ_MEDIA_AUDIO,
                    title = "Music & Audio Access",
                    description = "Required to scan audio files, play music tracks, and organize playlists via MediaStore.",
                    isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                )
            )
            list.add(
                RequiredPermissionInfo(
                    id = "POST_NOTIFICATIONS",
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    title = "Notifications Access",
                    description = "Required to display active playback controls, track details, and media status in the notification panel.",
                    isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )
            )
        } else {
            list.add(
                RequiredPermissionInfo(
                    id = "READ_EXTERNAL_STORAGE",
                    permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                    title = "Device Storage Access",
                    description = "Required to access photos, videos, music, and media files stored on your device via MediaStore.",
                    isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                )
            )
        }

        // Microphone / Audio Recording permission for visualizer
        list.add(
            RequiredPermissionInfo(
                id = "RECORD_AUDIO",
                permission = Manifest.permission.RECORD_AUDIO,
                title = "Audio Visualizer (Microphone)",
                description = "Required to analyze real-time audio frequencies for rendering live visualizer waveforms during playback.",
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            )
        )

        return list
    }

    fun hasAllMandatoryPermissions(context: Context): Boolean {
        return getRequiredPermissions(context).all { it.isGranted }
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openStorageAccessSettings(context: Context) {
        openAppSettings(context)
    }
}
