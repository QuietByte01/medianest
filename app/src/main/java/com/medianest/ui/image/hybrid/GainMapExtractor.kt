package com.medianest.ui.image.hybrid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build

class GainMapExtractor {
    
    fun extractGainMap(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    return bitmap?.gainmap?.gainmapContents
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        return null
    }
}
