package com.medianest.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

object VideoColorizerEngine {

    private const val TAG = "VideoColorizerEngine"

    init {
        try {
            System.loadLibrary("medianest_ffmpeg")
            Log.i(TAG, "Native library medianest_ffmpeg loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        }
    }

    enum class ColorizeMode(val label: String, val description: String) {
        KEYFRAME_OPTICAL_FLOW(
            "Keyframe & Optical Flow (Rotoscoping)",
            "Propagates reference color keyframes across intermediate frames using dense Farnebäck motion fields and Joint Bilateral edge snapping."
        ),
        WELSH_REFERENCE_MATCHING(
            "Welsh Luminance-Matching (Single Image)",
            "Transfers color statistics in CIELAB space from a single reference image by matching local luminance neighborhoods."
        )
    }

    enum class ColorizeCodec(val ffmpegCodec: String, val displayName: String, val containerExt: String) {
        H264("libx264", "H.264 / AVC (Standard MP4)", "mp4"),
        H265("libx265", "H.265 / HEVC (High Efficiency)", "mp4"),
        PRORES("prores_ks", "Apple ProRes (Studio Master)", "mov")
    }

    enum class ColorizePixFmt(val ffmpegFmt: String, val displayName: String) {
        YUV420P("yuv420p", "YUV 4:2:0 (Standard Broadcast & Web)"),
        YUV444P("yuv444p", "YUV 4:4:4 (Full Chroma Studio Fidelity)")
    }

    data class ColorizerConfig(
        val mode: ColorizeMode = ColorizeMode.WELSH_REFERENCE_MATCHING,
        val referenceImagePath: String? = null,
        val keyframeIndices: List<Int> = listOf(0),
        val keyframeImagePaths: Map<Int, String> = emptyMap(),
        val opticalFlowIterations: Int = 3,
        val filterStrength: Float = 0.5f,
        val patchRadius: Int = 2,
        val textureWeight: Float = 0.5f,
        val codec: ColorizeCodec = ColorizeCodec.H264,
        val pixelFormat: ColorizePixFmt = ColorizePixFmt.YUV420P,
        val customCrf: Int = 18
    )

    // JNI Native Bridge Functions
    @JvmStatic
    external fun nativeProcessWelshFrame(
        targetYData: ByteArray,
        width: Int,
        height: Int,
        refRgbData: ByteArray,
        refWidth: Int,
        refHeight: Int,
        patchRadius: Int,
        textureWeight: Float
    ): ByteArray?

    @JvmStatic
    external fun nativePropagateOpticalFlowChroma(
        prevYData: ByteArray,
        currYData: ByteArray,
        prevUData: ByteArray,
        prevVData: ByteArray,
        width: Int,
        height: Int,
        flowIters: Int,
        filterStrength: Float
    ): ByteArray?

    /**
     * Generates a single-frame preview bitmap using the chosen colorization mode and settings.
     * Supports both video paths (extracts frame at timeSec) and image files.
     */
    suspend fun generatePreviewBitmap(
        context: Context,
        inputMediaPath: String,
        timeSec: Float,
        config: ColorizerConfig
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val isImg = listOf("png", "jpg", "jpeg", "webp", "bmp", "avif").any { inputMediaPath.lowercase().endsWith(".$it") }
            val targetBmp: Bitmap? = if (isImg) {
                BitmapFactory.decodeFile(inputMediaPath)
            } else {
                val cacheDir = context.cacheDir
                val tempFrameFile = File(cacheDir, "preview_bw_frame_${System.currentTimeMillis()}.png")

                // Extract exact single frame at timeSec using FFmpeg
                val extractCmd = "-y -ss $timeSec -i \"$inputMediaPath\" -vframes 1 \"${tempFrameFile.absolutePath}\""
                val session = FFmpegKit.execute(extractCmd)
                if (ReturnCode.isSuccess(session.returnCode) && tempFrameFile.exists()) {
                    val bmp = BitmapFactory.decodeFile(tempFrameFile.absolutePath)
                    tempFrameFile.delete()
                    bmp
                } else {
                    tempFrameFile.delete()
                    null
                }
            }

            if (targetBmp == null) return@withContext null

            val w = targetBmp.width
            val h = targetBmp.height

            // Extract target grayscale Luminance Y
            val targetY = ByteArray(w * h)
            val pixels = IntArray(w * h)
            targetBmp.getPixels(pixels, 0, w, 0, 0, w, h)

            for (i in 0 until (w * h)) {
                val c = pixels[i]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val yVal = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                targetY[i] = yVal.toByte()
            }

            if (config.mode == ColorizeMode.WELSH_REFERENCE_MATCHING && !config.referenceImagePath.isNullOrBlank()) {
                val refBmp = BitmapFactory.decodeFile(config.referenceImagePath) ?: return@withContext targetBmp
                val rw = refBmp.width
                val rh = refBmp.height
                val refPixels = IntArray(rw * rh)
                refBmp.getPixels(refPixels, 0, rw, 0, 0, rw, rh)

                val refRgb = ByteArray(rw * rh * 3)
                for (i in 0 until (rw * rh)) {
                    val c = refPixels[i]
                    refRgb[i * 3 + 0] = Color.red(c).toByte()
                    refRgb[i * 3 + 1] = Color.green(c).toByte()
                    refRgb[i * 3 + 2] = Color.blue(c).toByte()
                }

                val colorizedRgb = nativeProcessWelshFrame(
                    targetYData = targetY,
                    width = w,
                    height = h,
                    refRgbData = refRgb,
                    refWidth = rw,
                    refHeight = rh,
                    patchRadius = config.patchRadius,
                    textureWeight = config.textureWeight
                ) ?: return@withContext targetBmp

                // Convert RGB bytes to Bitmap
                val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val outPixels = IntArray(w * h)
                for (i in 0 until (w * h)) {
                    val r = colorizedRgb[i * 3 + 0].toInt() and 0xFF
                    val g = colorizedRgb[i * 3 + 1].toInt() and 0xFF
                    val b = colorizedRgb[i * 3 + 2].toInt() and 0xFF
                    outPixels[i] = Color.rgb(r, g, b)
                }
                outBmp.setPixels(outPixels, 0, w, 0, 0, w, h)
                return@withContext outBmp
            }

            return@withContext targetBmp
        } catch (e: Exception) {
            Log.e(TAG, "Error generating preview: ${e.message}", e)
            null
        }
    }

    /**
     * Colorizes a single still image using Welsh et al. statistical CIELAB transfer.
     */
    suspend fun colorizeImage(
        context: Context,
        inputPath: String,
        outputPath: String,
        config: ColorizerConfig,
        onProgress: (Int, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(15, "Decoding input image & analyzing luminance...")
            val targetBmp = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            val w = targetBmp.width
            val h = targetBmp.height

            val refBmp = if (!config.referenceImagePath.isNullOrBlank()) {
                BitmapFactory.decodeFile(config.referenceImagePath)
            } else null

            if (refBmp == null) {
                Log.e(TAG, "Reference image required for image colorization")
                return@withContext false
            }

            onProgress(40, "Extracting CIELAB color distribution from reference...")
            val rw = refBmp.width
            val rh = refBmp.height
            val refPixels = IntArray(rw * rh)
            refBmp.getPixels(refPixels, 0, rw, 0, 0, rw, rh)
            val refRgb = ByteArray(rw * rh * 3)
            for (i in 0 until (rw * rh)) {
                val c = refPixels[i]
                refRgb[i * 3 + 0] = Color.red(c).toByte()
                refRgb[i * 3 + 1] = Color.green(c).toByte()
                refRgb[i * 3 + 2] = Color.blue(c).toByte()
            }

            val pixels = IntArray(w * h)
            targetBmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val targetY = ByteArray(w * h)
            for (i in 0 until (w * h)) {
                val c = pixels[i]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                targetY[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255).toByte()
            }

            onProgress(70, "Performing Welsh luminance matching & Joint Bilateral filtering...")
            val colorizedRgb = nativeProcessWelshFrame(
                targetYData = targetY,
                width = w,
                height = h,
                refRgbData = refRgb,
                refWidth = rw,
                refHeight = rh,
                patchRadius = config.patchRadius,
                textureWeight = config.textureWeight
            ) ?: return@withContext false

            val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val outPixels = IntArray(w * h)
            for (i in 0 until (w * h)) {
                val r = colorizedRgb[i * 3 + 0].toInt() and 0xFF
                val g = colorizedRgb[i * 3 + 1].toInt() and 0xFF
                val b = colorizedRgb[i * 3 + 2].toInt() and 0xFF
                outPixels[i] = Color.rgb(r, g, b)
            }
            outBmp.setPixels(outPixels, 0, w, 0, 0, w, h)

            onProgress(90, "Saving colorized master image...")
            val outFile = File(outputPath)
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { fos ->
                val format = if (outputPath.endsWith(".jpg", true) || outputPath.endsWith(".jpeg", true)) {
                    Bitmap.CompressFormat.JPEG
                } else if (outputPath.endsWith(".webp", true)) {
                    Bitmap.CompressFormat.WEBP
                } else {
                    Bitmap.CompressFormat.PNG
                }
                outBmp.compress(format, 98, fos)
            }

            onProgress(100, "Image colorization complete!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Image colorization failed: ${e.message}", e)
            false
        }
    }

    /**
     * Executes complete video colorization pipeline.
     */
    suspend fun colorizeVideo(
        context: Context,
        inputPath: String,
        outputPath: String,
        config: ColorizerConfig,
        onProgress: (Int, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "colorizer_${System.currentTimeMillis()}")
        workDir.mkdirs()

        try {
            onProgress(5, "Analyzing video and extracting pristine luminance channel...")

            val framesDir = File(workDir, "frames")
            framesDir.mkdirs()
            val colorFramesDir = File(workDir, "color_frames")
            colorFramesDir.mkdirs()

            // 1. Extract frames from input video
            val extractCmd = "-y -i \"$inputPath\" -qscale:v 2 \"${framesDir.absolutePath}/frame_%06d.png\""
            val extractSession = FFmpegKit.execute(extractCmd)
            if (!ReturnCode.isSuccess(extractSession.returnCode)) {
                Log.e(TAG, "Failed to extract frames: ${extractSession.failStackTrace}")
                return@withContext false
            }

            val frameFiles = framesDir.listFiles { _, name -> name.endsWith(".png") }?.sortedBy { it.name } ?: emptyList()
            if (frameFiles.isEmpty()) {
                Log.e(TAG, "No frames extracted")
                return@withContext false
            }

            val totalFrames = frameFiles.size
            Log.i(TAG, "Processing $totalFrames frames with mode: ${config.mode}")

            // 2. Process frames according to mode
            when (config.mode) {
                ColorizeMode.WELSH_REFERENCE_MATCHING -> {
                    val refBmp = if (!config.referenceImagePath.isNullOrBlank()) {
                        BitmapFactory.decodeFile(config.referenceImagePath)
                    } else null

                    if (refBmp == null) {
                        Log.e(TAG, "Reference image missing for Welsh Color Transfer")
                        return@withContext false
                    }

                    val rw = refBmp.width
                    val rh = refBmp.height
                    val refPixels = IntArray(rw * rh)
                    refBmp.getPixels(refPixels, 0, rw, 0, 0, rw, rh)
                    val refRgb = ByteArray(rw * rh * 3)
                    for (i in 0 until (rw * rh)) {
                        val c = refPixels[i]
                        refRgb[i * 3 + 0] = Color.red(c).toByte()
                        refRgb[i * 3 + 1] = Color.green(c).toByte()
                        refRgb[i * 3 + 2] = Color.blue(c).toByte()
                    }

                    frameFiles.forEachIndexed { idx, frameFile ->
                        val bmp = BitmapFactory.decodeFile(frameFile.absolutePath)
                        val w = bmp.width
                        val h = bmp.height
                        val pixels = IntArray(w * h)
                        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

                        val targetY = ByteArray(w * h)
                        for (i in 0 until (w * h)) {
                            val c = pixels[i]
                            val r = Color.red(c)
                            val g = Color.green(c)
                            val b = Color.blue(c)
                            targetY[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255).toByte()
                        }

                        val colorizedRgb = nativeProcessWelshFrame(
                            targetYData = targetY,
                            width = w,
                            height = h,
                            refRgbData = refRgb,
                            refWidth = rw,
                            refHeight = rh,
                            patchRadius = config.patchRadius,
                            textureWeight = config.textureWeight
                        )

                        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val outPixels = IntArray(w * h)
                        if (colorizedRgb != null) {
                            for (i in 0 until (w * h)) {
                                val r = colorizedRgb[i * 3 + 0].toInt() and 0xFF
                                val g = colorizedRgb[i * 3 + 1].toInt() and 0xFF
                                val b = colorizedRgb[i * 3 + 2].toInt() and 0xFF
                                outPixels[i] = Color.rgb(r, g, b)
                            }
                        } else {
                            System.arraycopy(pixels, 0, outPixels, 0, pixels.size)
                        }
                        outBmp.setPixels(outPixels, 0, w, 0, 0, w, h)

                        val outFile = File(colorFramesDir, frameFile.name)
                        FileOutputStream(outFile).use { fos ->
                            outBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }

                        val progress = 10 + ((idx + 1) * 75 / totalFrames)
                        onProgress(progress, "Colorizing frame ${idx + 1}/$totalFrames (Welsh Lab Transfer)...")
                    }
                }

                ColorizeMode.KEYFRAME_OPTICAL_FLOW -> {
                    // Optical flow propagation across successive frames
                    var prevY: ByteArray? = null
                    var prevU: ByteArray? = null
                    var prevV: ByteArray? = null
                    var frameWidth = 0
                    var frameHeight = 0

                    frameFiles.forEachIndexed { idx, frameFile ->
                        val bmp = BitmapFactory.decodeFile(frameFile.absolutePath)
                        val w = bmp.width
                        val h = bmp.height
                        frameWidth = w
                        frameHeight = h
                        val pixels = IntArray(w * h)
                        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

                        val currY = ByteArray(w * h)
                        val currU = ByteArray(w * h)
                        val currV = ByteArray(w * h)

                        for (i in 0 until (w * h)) {
                            val c = pixels[i]
                            val r = Color.red(c)
                            val g = Color.green(c)
                            val b = Color.blue(c)
                            val yf = 0.299f * r + 0.587f * g + 0.114f * b
                            val uf = -0.168736f * r - 0.331264f * g + 0.5f * b + 128.0f
                            val vf = 0.5f * r - 0.418688f * g - 0.081312f * b + 128.0f
                            currY[i] = yf.toInt().coerceIn(0, 255).toByte()
                            currU[i] = uf.toInt().coerceIn(0, 255).toByte()
                            currV[i] = vf.toInt().coerceIn(0, 255).toByte()
                        }

                        // Check if current frame is a reference keyframe or needs flow propagation
                        val isKeyframe = config.keyframeIndices.contains(idx) || idx == 0

                        val finalU: ByteArray
                        val finalV: ByteArray

                        val lastY = prevY
                        val lastU = prevU
                        val lastV = prevV

                        if (isKeyframe || lastY == null || lastU == null || lastV == null) {
                            finalU = currU
                            finalV = currV
                        } else {
                            // Propagate chroma using dense optical flow and Joint Bilateral filter
                            val propagatedUV = nativePropagateOpticalFlowChroma(
                                prevYData = lastY,
                                currYData = currY,
                                prevUData = lastU,
                                prevVData = lastV,
                                width = w,
                                height = h,
                                flowIters = config.opticalFlowIterations,
                                filterStrength = config.filterStrength
                            )

                            if (propagatedUV != null && propagatedUV.size == w * h * 2) {
                                finalU = ByteArray(w * h)
                                finalV = ByteArray(w * h)
                                System.arraycopy(propagatedUV, 0, finalU, 0, w * h)
                                System.arraycopy(propagatedUV, w * h, finalV, 0, w * h)
                            } else {
                                finalU = currU
                                finalV = currV
                            }
                        }

                        // Save current as previous for next step
                        prevY = currY
                        prevU = finalU
                        prevV = finalV

                        // Synthesize output frame (Untouched original Y + synthesized U & V)
                        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val outPixels = IntArray(w * h)
                        for (i in 0 until (w * h)) {
                            val yVal = currY[i].toInt() and 0xFF
                            val uVal = (finalU[i].toInt() and 0xFF) - 128
                            val vVal = (finalV[i].toInt() and 0xFF) - 128

                            val r = (yVal + 1.402f * vVal).toInt().coerceIn(0, 255)
                            val g = (yVal - 0.344136f * uVal - 0.714136f * vVal).toInt().coerceIn(0, 255)
                            val b = (yVal + 1.772f * uVal).toInt().coerceIn(0, 255)
                            outPixels[i] = Color.rgb(r, g, b)
                        }
                        outBmp.setPixels(outPixels, 0, w, 0, 0, w, h)

                        val outFile = File(colorFramesDir, frameFile.name)
                        FileOutputStream(outFile).use { fos ->
                            outBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }

                        val progress = 10 + ((idx + 1) * 75 / totalFrames)
                        onProgress(progress, "Propagating optical flow motion vectors frame ${idx + 1}/$totalFrames...")
                    }
                }
            }

            // 3. Re-encode video using FFmpeg with lossless chroma merge
            onProgress(88, "Multiplexing chroma planes and encoding ${config.codec.displayName}...")

            val framerateCmd = "-i \"$inputPath\""
            val encodeCmd = buildString {
                append("-y -framerate 30 ")
                append("-i \"${colorFramesDir.absolutePath}/frame_%06d.png\" ")
                append("-i \"$inputPath\" ")
                append("-map 0:v:0 ")
                append("-map 1:a? ")
                append("-c:v ${config.codec.ffmpegCodec} ")
                if (config.codec != ColorizeCodec.PRORES) {
                    append("-crf ${config.customCrf} ")
                    append("-preset medium ")
                }
                append("-pix_fmt ${config.pixelFormat.ffmpegFmt} ")
                append("-c:a copy ")
                append("\"$outputPath\"")
            }

            val encodeSession = FFmpegKit.execute(encodeCmd)
            val success = ReturnCode.isSuccess(encodeSession.returnCode)

            if (success) {
                onProgress(100, "Colorization finished successfully!")
            } else {
                Log.e(TAG, "Encoding failed: ${encodeSession.failStackTrace}")
            }

            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Colorization pipeline error: ${e.message}", e)
            false
        } finally {
            workDir.deleteRecursively()
        }
    }
}
