package com.medianest.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.medianest.data.model.MediaItem
import com.medianest.util.Logger

/**
 * StudioIntegrationManager — Manages detection, intent dispatching, and fallback dialogs
 * for the Media Studio & Video Editor Add-On App (`com.medianest.studio`).
 */
object StudioIntegrationManager {

    private const val TAG = "StudioIntegrationManager"
    const val ACTION_EDIT_MEDIA = "com.medianest.studio.ACTION_EDIT_MEDIA"
    const val ACTION_EDIT_VIDEO = "com.medianest.studio.ACTION_EDIT_VIDEO"
    const val EXTRA_MEDIA_URI = "extra_media_uri"
    const val EXTRA_INITIAL_TAB = "extra_initial_tab"
    const val ADDON_PACKAGE_NAME = "com.medianest.studio"

    /**
     * Returns true if the Media Studio Add-On App is installed on the device.
     */
    fun isStudioInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    ADDON_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(ADDON_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            val intent = Intent(ACTION_EDIT_MEDIA).setPackage(ADDON_PACKAGE_NAME)
            val resolveInfo = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfo.isNotEmpty()
        } catch (e: Exception) {
            Logger.e(TAG, "isStudioInstalled error: ${e.message}")
            false
        }
    }

    /**
     * Launches the Media Studio Add-On App for conversion, compression, colorization, or extraction.
     * If the Add-On is not installed, invokes [onNotInstalled] to display the install dialog.
     */
    fun launchStudio(
        context: Context,
        mediaItem: MediaItem? = null,
        initialTab: String = "CONVERT",
        onNotInstalled: () -> Unit
    ) {
        if (!isStudioInstalled(context)) {
            onNotInstalled()
            return
        }

        try {
            val intent = Intent(ACTION_EDIT_MEDIA).apply {
                setPackage(ADDON_PACKAGE_NAME)
                mediaItem?.uri?.let { uri ->
                    val mime = mediaItem.mimeType.ifBlank { "video/*" }
                    setDataAndType(uri, mime)
                    putExtra(EXTRA_MEDIA_URI, uri.toString())
                }
                putExtra(EXTRA_INITIAL_TAB, initialTab)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to launch Media Studio Add-On", e)
            Toast.makeText(context, "Could not open Media Studio Add-On", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launches the Video Editor Sheet / Studio in the Add-On App.
     * If the Add-On is not installed, invokes [onNotInstalled] to display the install dialog.
     */
    fun launchEditor(
        context: Context,
        mediaItem: MediaItem,
        onNotInstalled: () -> Unit
    ) {
        if (!isStudioInstalled(context)) {
            onNotInstalled()
            return
        }

        try {
            val intent = Intent(ACTION_EDIT_VIDEO).apply {
                setPackage(ADDON_PACKAGE_NAME)
                val mime = mediaItem.mimeType.ifBlank { "video/*" }
                setDataAndType(mediaItem.uri, mime)
                putExtra(EXTRA_MEDIA_URI, mediaItem.uri.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to launch Video Editor Add-On", e)
            Toast.makeText(context, "Could not open Video Editor Add-On", Toast.LENGTH_SHORT).show()
        }
    }
}
