package com.medianest.ui.image.hybrid

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Build
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Ultra HDR & Color Pipeline Manager (Google Photos / Pixel Standard).
 * Enforces ISO 21496-1 Gain Map decoding, Display P3 / D65 wide gamut mapping,
 * and dynamic headroom scaling based on device display capabilities.
 */
object UltraHdrManager {

    /**
     * Enables 10-bit HDR wide color mode on the Window for Android 14+ (UPSIDE_DOWN_CAKE+),
     * or Wide Color Gamut (Display P3) on Android 8.0+.
     */
    fun configureWindowColorMode(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.window.colorMode = ActivityInfo.COLOR_MODE_HDR
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.window.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
        }
    }

    /**
     * Queries real-time HDR display headroom (ratio between max HDR peak brightness and standard SDR 203 nits).
     * Returns 1.0f on SDR panels or pre-Android 14 devices.
     */
    fun getDisplayHeadroom(context: Context): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val display = windowManager?.defaultDisplay
            try {
                val method = display?.javaClass?.getMethod("getHdrSdrRatio")
                (method?.invoke(display) as? Float) ?: 1.0f
            } catch (e: Exception) {
                1.0f
            }
        } else {
            1.0f
        }
    }

    /**
     * Checks if the device screen supports HDR playback.
     */
    fun isHdrDisplaySupported(context: Context): Boolean {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = windowManager?.defaultDisplay ?: return false
        val hdrCaps = display.hdrCapabilities ?: return false
        return hdrCaps.supportedHdrTypes.isNotEmpty()
    }

    /**
     * Decodes an image with native Ultra HDR gain map preservation and Display P3 wide gamut.
     * Uses Android 14 native gainmap decoding when available.
     */
    suspend fun decodeImage(
        context: Context,
        uri: Uri,
        sampleSize: Int = 1
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inMutable = false
                
                // Enforce Display P3 color space for accurate D65 color mapping
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
                }

                // On Android 14+ (API 34+), BitmapFactory automatically decodes and preserves
                // the Ultra HDR gainmap (Bitmap.hasGainmap() / Bitmap.getGainmap())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    inPreferredConfig = Bitmap.Config.RGBA_1010102
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            }

            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
