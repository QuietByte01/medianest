package com.medianest.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaProcessorEngine — High-Performance Media Conversion, Compression,
 * Multi-file Batch Processing, Extraction, Cropping, and Corruption Repair Studio powered by FFmpeg.
 *
 * Safety Guarantee: All operations default to non-destructive creation of new output files.
 * Source files are opened in strict read-only mode and are never modified or corrupted.
 */
object MediaProcessorEngine {

    private const val TAG = "MediaProcessorEngine"

    sealed class ProcessingState {
        object Idle : ProcessingState()
        data class Processing(
            val progressPercent: Int,
            val speed: Double,
            val timeMs: Long,
            val statusText: String,
            val recentLog: String
        ) : ProcessingState()
        data class BatchProcessing(
            val currentIndex: Int,
            val totalFiles: Int,
            val currentFileName: String,
            val fileProgressPercent: Int,
            val overallProgressPercent: Int,
            val speed: Double,
            val statusText: String
        ) : ProcessingState()
        data class Completed(
            val outputFile: File,
            val outputUri: Uri?,
            val originalSizeBytes: Long,
            val newSizeBytes: Long,
            val durationMs: Long
        ) : ProcessingState()
        data class BatchCompleted(
            val outputFiles: List<File>,
            val totalOriginalBytes: Long,
            val totalNewBytes: Long,
            val durationMs: Long
        ) : ProcessingState()
        data class Failed(
            val errorMessage: String,
            val fullLog: String
        ) : ProcessingState()
        object Cancelled : ProcessingState()
    }

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private var activeSessionId: Long? = null
    @Volatile
    private var isCancellationRequested = false

    fun cancelCurrent() {
        isCancellationRequested = true
        activeSessionId?.let { id ->
            try {
                FFmpegKit.cancel(id)
            } catch (e: Exception) {
                Log.w(TAG, "Cancel session error: ${e.message}")
            }
        }
        _processingState.value = ProcessingState.Cancelled
    }

    fun resetState() {
        isCancellationRequested = false
        _processingState.value = ProcessingState.Idle
    }

    // ==========================================
    // 1. CONVERT / REMUX
    // ==========================================
    suspend fun convertMedia(
        context: Context,
        inputPath: String,
        inputUri: Uri?,
        originalName: String? = null,
        outputFormat: String,
        isLosslessCopy: Boolean,
        videoCodec: String = "libx264",
        audioCodec: String = "aac",
        qualityCrf: Int = 20,
        audioBitrateKbps: Int = 256,
        keepSubtitles: Boolean = true,
        isCreateNewFile: Boolean = true,
        customSuffix: String = "_converted"
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedInput = resolveInputPath(context, inputPath, inputUri) ?: return@withContext false
        val inputFile = File(inputPath)
        val origSize = if (inputFile.exists()) inputFile.length() else 0L

        val outputDir = getOutputDirForFormat(outputFormat, "Converted")
        val baseName = getBaseName(inputPath, originalName)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = if (isCreateNewFile) "${customSuffix}_$timeStamp" else "_$timeStamp"
        val outputFile = File(outputDir, "${baseName}${suffix}.${outputFormat.lowercase()}")

        val totalDurationMs = estimateDurationMs(resolvedInput)
        val cmd = buildConvertCommand(
            resolvedInput = resolvedInput,
            outputFilePath = outputFile.absolutePath,
            outputFormat = outputFormat,
            isLosslessCopy = isLosslessCopy,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            qualityCrf = qualityCrf,
            audioBitrateKbps = audioBitrateKbps,
            keepSubtitles = keepSubtitles
        )

        executeFFmpegCommand(context, cmd, outputFile, origSize, totalDurationMs, "Converting media to ${outputFormat.uppercase()}...")
    }

    fun buildConvertCommand(
        resolvedInput: String,
        outputFilePath: String,
        outputFormat: String,
        isLosslessCopy: Boolean,
        videoCodec: String = "libx264",
        audioCodec: String = "aac",
        qualityCrf: Int = 20,
        audioBitrateKbps: Int = 256,
        keepSubtitles: Boolean = true
    ): String {
        val cmd = StringBuilder("-y -i \"$resolvedInput\" ")
        if (isLosslessCopy) {
            cmd.append("-map 0? -c copy ")
        } else {
            val isAudioOnly = isAudioFormat(outputFormat)
            val isImageOnly = isImageFormat(outputFormat)
            if (isAudioOnly) {
                cmd.append("-vn ")
                if (audioBitrateKbps <= 0 || audioCodec == "copy") {
                    cmd.append("-c:a copy ")
                } else {
                    when (outputFormat.lowercase()) {
                        "flac" -> cmd.append("-c:a flac ")
                        "wav" -> cmd.append("-c:a pcm_s16le ")
                        "mp3" -> cmd.append("-c:a libmp3lame -b:a ${audioBitrateKbps}k ")
                        "aac", "m4a" -> cmd.append("-c:a aac -b:a ${audioBitrateKbps}k ")
                        "opus" -> cmd.append("-c:a libopus -b:a ${audioBitrateKbps.coerceAtMost(192)}k ")
                        "ogg" -> cmd.append("-c:a libvorbis -q:a 6 ")
                        else -> cmd.append("-c:a aac -b:a ${audioBitrateKbps}k ")
                    }
                }
            } else if (isImageOnly) {
                cmd.append("-vframes 1 ")
            } else {
                // Map all streams to keep multi-audio and subtitles if enabled
                if (keepSubtitles) {
                    cmd.append("-map 0? ")
                }

                when (videoCodec.lowercase()) {
                    "libx265", "libaom-av1", "av1" -> cmd.append("-c:v libx265 -crf $qualityCrf -preset 6 -pix_fmt yuv420p10le ")
                    "libx265", "hevc", "h265" -> cmd.append("-c:v libx265 -crf $qualityCrf -preset medium -tag:v hvc1 ")
                    "hevc_mediacodec" -> cmd.append("-c:v hevc_mediacodec -b:v 0 -crf $qualityCrf ")
                    "h264_mediacodec" -> cmd.append("-c:v h264_mediacodec -b:v 0 -crf $qualityCrf ")
                    "libvpx-vp9", "vp9" -> cmd.append("-c:v libvpx-vp9 -crf $qualityCrf -b:v 0 ")
                    "copy" -> cmd.append("-c:v copy ")
                    else -> cmd.append("-c:v libx264 -crf $qualityCrf -preset fast -pix_fmt yuv420p ")
                }

                if (audioBitrateKbps <= 0 || audioCodec == "copy") {
                    cmd.append("-c:a copy ")
                } else {
                    when (audioCodec) {
                        "copy" -> cmd.append("-c:a copy ")
                        "flac" -> cmd.append("-c:a flac ")
                        "libmp3lame" -> cmd.append("-c:a libmp3lame -b:a ${audioBitrateKbps}k ")
                        "libopus" -> cmd.append("-c:a libopus -b:a ${audioBitrateKbps.coerceAtMost(192)}k ")
                        else -> cmd.append("-c:a aac -b:a ${audioBitrateKbps}k ")
                    }
                }

                if (keepSubtitles) {
                    cmd.append("-c:s copy ")
                }
            }
        }

        if (outputFormat.equals("mp4", ignoreCase = true) || outputFormat.equals("mov", ignoreCase = true)) {
            cmd.append("-movflags +faststart ")
        }
        cmd.append("\"$outputFilePath\"")
        return cmd.toString()
    }

    // ==========================================
    // 2. SMART COMPRESSOR (SINGLE & BATCH)
    // ==========================================
    suspend fun compressMedia(
        context: Context,
        inputPath: String,
        inputUri: Uri?,
        originalName: String? = null,
        targetMode: String,
        targetPercentage: Int = 50,
        targetLimitMb: Int = 25,
        crf: Int = 26,
        resolutionScale: String = "ORIGINAL",
        videoCodec: String = "libx265",
        isCreateNewFile: Boolean = true,
        customSuffix: String = "_compressed"
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedInput = resolveInputPath(context, inputPath, inputUri) ?: return@withContext false
        val inputFile = File(inputPath)
        val origSize = if (inputFile.exists()) inputFile.length() else 0L

        val outputDir = getOutputDirForFormat("mp4", "Compressed")
        val baseName = getBaseName(inputPath, originalName)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = if (isCreateNewFile) "${customSuffix}_$timeStamp" else "_$timeStamp"
        val outputFile = File(outputDir, "${baseName}${suffix}.mp4")

        val totalDurationMs = estimateDurationMs(resolvedInput)
        val totalSecs = (totalDurationMs / 1000.0).coerceAtLeast(1.0)

        val cmd = buildCompressCommand(
            resolvedInput = resolvedInput,
            outputFilePath = outputFile.absolutePath,
            targetMode = targetMode,
            targetPercentage = targetPercentage,
            targetLimitMb = targetLimitMb,
            crf = crf,
            resolutionScale = resolutionScale,
            videoCodec = videoCodec,
            durationSecs = totalSecs
        )

        executeFFmpegCommand(context, cmd, outputFile, origSize, totalDurationMs, "Compressing video...")
    }

    fun buildCompressCommand(
        resolvedInput: String,
        outputFilePath: String,
        targetMode: String,
        targetPercentage: Int = 50,
        targetLimitMb: Int = 25,
        crf: Int = 26,
        resolutionScale: String = "ORIGINAL",
        videoCodec: String = "libx265",
        durationSecs: Double = 60.0
    ): String {
        val cmd = StringBuilder("-y -i \"$resolvedInput\" ")
        val scaleFilter = when (resolutionScale.uppercase()) {
            "1440P" -> "scale='min(2560,iw)':-2"
            "1080P" -> "scale='min(1920,iw)':-2"
            "720P" -> "scale='min(1280,iw)':-2"
            "480P" -> "scale='min(854,iw)':-2"
            "360P" -> "scale='min(640,iw)':-2"
            else -> null
        }
        if (scaleFilter != null) {
            cmd.append("-vf \"$scaleFilter\" ")
        }

        val isAv1 = videoCodec.contains("av1", ignoreCase = true) || videoCodec.contains("svt", ignoreCase = true) || targetMode.equals("AV1", ignoreCase = true)

        when (targetMode.uppercase()) {
            "AUTO" -> {
                if (isAv1) {
                    cmd.append("-c:v libx265 -crf 26 -preset 6 -pix_fmt yuv420p10le ")
                } else {
                    cmd.append("-c:v libx265 -crf 26 -preset medium -tag:v hvc1 ")
                }
                cmd.append("-c:a aac -b:a 128k ")
            }
            "AV1" -> {
                cmd.append("-c:v libx265 -crf $crf -preset 6 -pix_fmt yuv420p10le ")
                cmd.append("-c:a aac -b:a 128k ")
            }
            "LIMIT_SIZE" -> {
                val targetBitsTotal = targetLimitMb.toDouble() * 8.0 * 1024.0 * 1024.0 * 0.94
                val totalBitrateKbps = (targetBitsTotal / durationSecs / 1000.0).coerceAtLeast(200.0)
                val audioBitrateKbps = if (totalBitrateKbps > 800) 128 else 64
                val videoBitrateKbps = (totalBitrateKbps - audioBitrateKbps).toInt().coerceAtLeast(150)

                if (isAv1) {
                    cmd.append("-c:v libx265 -b:v ${videoBitrateKbps}k -maxrate ${(videoBitrateKbps * 1.3).toInt()}k -bufsize ${videoBitrateKbps * 2}k -preset 6 -pix_fmt yuv420p10le ")
                } else {
                    cmd.append("-c:v libx265 -b:v ${videoBitrateKbps}k -maxrate ${(videoBitrateKbps * 1.3).toInt()}k -bufsize ${videoBitrateKbps * 2}k -preset medium -tag:v hvc1 ")
                }
                cmd.append("-c:a aac -b:a ${audioBitrateKbps}k ")
            }
            "PERCENT" -> {
                val effectiveCrf = when {
                    targetPercentage >= 75 -> 30
                    targetPercentage >= 50 -> 26
                    else -> 23
                }
                if (isAv1) {
                    cmd.append("-c:v libx265 -crf $effectiveCrf -preset 6 -pix_fmt yuv420p10le ")
                } else {
                    cmd.append("-c:v libx265 -crf $effectiveCrf -preset medium -tag:v hvc1 ")
                }
                cmd.append("-c:a aac -b:a 128k ")
            }
            else -> {
                if (isAv1) {
                    cmd.append("-c:v libx265 -crf $crf -preset 6 -pix_fmt yuv420p10le ")
                } else {
                    cmd.append("-c:v libx265 -crf $crf -preset medium -tag:v hvc1 ")
                }
                cmd.append("-c:a aac -b:a 160k ")
            }
        }

        cmd.append("-movflags +faststart \"$outputFilePath\"")
        return cmd.toString()
    }

    /**
     * Batch Compressor for Multiple Files (e.g. Images, Videos) with Real-Time Queue Feedback.
     */
    suspend fun batchCompressMedia(
        context: Context,
        inputList: List<Pair<String, Uri?>>, // Pair<path, uri>
        imageQuality: Int = 80, // 100 = lossless webp/png, 80 = high, 60 = medium
        imageFormat: String = "webp", // "webp", "jpg", "png"
        isCreateNewFile: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        if (inputList.isEmpty()) return@withContext false
        isCancellationRequested = false

        val startTimeMs = System.currentTimeMillis()
        val outputDir = getOutputDirForFormat(imageFormat, "Extracted")
        val successfulOutputs = mutableListOf<File>()
        var totalOrigSize = 0L
        var totalNewSize = 0L

        _processingState.value = ProcessingState.BatchProcessing(
            currentIndex = 1,
            totalFiles = inputList.size,
            currentFileName = getBaseName(inputList[0].first),
            fileProgressPercent = 0,
            overallProgressPercent = 0,
            speed = 1.0,
            statusText = "Starting batch compression of ${inputList.size} files..."
        )

        for (i in inputList.indices) {
            if (isCancellationRequested) {
                _processingState.value = ProcessingState.Cancelled
                return@withContext false
            }

            val (path, uri) = inputList[i]
            val resolvedInput = resolveInputPath(context, path, uri) ?: continue
            val origFile = File(path)
            val origLen = if (origFile.exists()) origFile.length() else 0L
            totalOrigSize += origLen

            val baseName = getBaseName(path)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val suffix = if (isCreateNewFile) "_compressed_${timeStamp}_$i" else "_$timeStamp"
            val outputFile = File(outputDir, "${baseName}${suffix}.${imageFormat.lowercase()}")

            val overallPercent = ((i.toFloat() / inputList.size.toFloat()) * 100f).toInt()
            _processingState.value = ProcessingState.BatchProcessing(
                currentIndex = i + 1,
                totalFiles = inputList.size,
                currentFileName = origFile.name.ifBlank { "Item ${i + 1}" },
                fileProgressPercent = 0,
                overallProgressPercent = overallPercent,
                speed = 1.0,
                statusText = "Compressing ${i + 1} of ${inputList.size} files..."
            )

            val cmd = if (imageFormat.equals("webp", ignoreCase = true)) {
                if (imageQuality >= 100) "-y -i \"$resolvedInput\" -lossless 1 \"${outputFile.absolutePath}\""
                else "-y -i \"$resolvedInput\" -q:v $imageQuality \"${outputFile.absolutePath}\""
            } else if (imageFormat.equals("png", ignoreCase = true)) {
                "-y -i \"$resolvedInput\" -vframes 1 \"${outputFile.absolutePath}\""
            } else {
                val qScale = ((100 - imageQuality) / 3.3).toInt().coerceIn(1, 31)
                "-y -i \"$resolvedInput\" -q:v $qScale \"${outputFile.absolutePath}\""
            }

            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists()) {
                successfulOutputs.add(outputFile)
                totalNewSize += outputFile.length()
                MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)
            } else {
                outputFile.delete()
            }
        }

        val elapsed = System.currentTimeMillis() - startTimeMs
        _processingState.value = ProcessingState.BatchCompleted(
            outputFiles = successfulOutputs,
            totalOriginalBytes = totalOrigSize,
            totalNewBytes = totalNewSize,
            durationMs = elapsed
        )
        return@withContext successfulOutputs.isNotEmpty()
    }

    // ==========================================
    // 3. VIDEO CROP & REFRAME
    // ==========================================
    suspend fun cropMedia(
        context: Context,
        inputPath: String,
        inputUri: Uri?,
        originalName: String? = null,
        cropPreset: String,
        customCropW: Int = 0,
        customCropH: Int = 0,
        customCropX: Int = 0,
        customCropY: Int = 0,
        isCreateNewFile: Boolean = true,
        customSuffix: String = "_cropped"
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedInput = resolveInputPath(context, inputPath, inputUri) ?: return@withContext false
        val inputFile = File(inputPath)
        val origSize = if (inputFile.exists()) inputFile.length() else 0L

        val outputDir = getOutputDirForFormat("mp4", "Cropped")
        val baseName = getBaseName(inputPath, originalName)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = if (isCreateNewFile) "${customSuffix}_$timeStamp" else "_$timeStamp"
        val outputFile = File(outputDir, "${baseName}${suffix}.mp4")

        val totalDurationMs = estimateDurationMs(resolvedInput)
        val cmd = buildCropCommand(
            resolvedInput = resolvedInput,
            outputFilePath = outputFile.absolutePath,
            cropPreset = cropPreset,
            customCropW = customCropW,
            customCropH = customCropH,
            customCropX = customCropX,
            customCropY = customCropY
        )

        executeFFmpegCommand(context, cmd, outputFile, origSize, totalDurationMs, "Cropping video framing...")
    }

    fun buildCropCommand(
        resolvedInput: String,
        outputFilePath: String,
        cropPreset: String,
        customCropW: Int = 0,
        customCropH: Int = 0,
        customCropX: Int = 0,
        customCropY: Int = 0
    ): String {
        val cropFilter = when (cropPreset) {
            "9_16" -> "crop='min(iw,ih*9/16)':'min(ih,iw*16/9)':(iw-out_w)/2:(ih-out_h)/2"
            "1_1" -> "crop='min(iw,ih)':'min(iw,ih)':(iw-out_w)/2:(ih-out_h)/2"
            "16_9" -> "crop='min(iw,ih*16/9)':'min(ih,iw*9/16)':(iw-out_w)/2:(ih-out_h)/2"
            "21_9" -> "crop='min(iw,ih*21/9)':'min(ih,iw*9/21)':(iw-out_w)/2:(ih-out_h)/2"
            "4_3" -> "crop='min(iw,ih*4/3)':'min(ih,iw*3/4)':(iw-out_w)/2:(ih-out_h)/2"
            "3_4", "P_3_4" -> "crop='min(iw,ih*3/4)':'min(ih,iw*4/3)':(iw-out_w)/2:(ih-out_h)/2"
            "4_5", "P_4_5" -> "crop='min(iw,ih*4/5)':'min(ih,iw*5/4)':(iw-out_w)/2:(ih-out_h)/2"
            "CUSTOM" -> if (customCropW > 0 && customCropH > 0) "crop=$customCropW:$customCropH:$customCropX:$customCropY" else "crop='min(iw,ih)':'min(iw,ih)'"
            else -> "crop='min(iw,ih)':'min(iw,ih)'"
        }
        return "-y -i \"$resolvedInput\" -vf \"$cropFilter\" -c:v libx264 -crf 19 -preset fast -pix_fmt yuv420p -c:a copy -movflags +faststart \"$outputFilePath\""
    }

    // ==========================================
    // 4. EXTRACTOR SUITE (AUDIO / SUBTITLE / GIF / FRAME)
    // ==========================================
    suspend fun extractMedia(
        context: Context,
        inputPath: String,
        inputUri: Uri?,
        originalName: String? = null,
        extractType: String,
        audioOutputFormat: String = "original", // "original", "flac", "wav", "mp3", "aac"
        subtitleTrackIdx: Int = 0,
        gifFps: Int = 15,
        gifWidth: Int = 480,
        isCreateNewFile: Boolean = true,
        customSuffix: String = "_extracted"
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedInput = resolveInputPath(context, inputPath, inputUri) ?: return@withContext false
        val inputFile = File(inputPath)
        val origSize = if (inputFile.exists()) inputFile.length() else 0L
        val baseName = getBaseName(inputPath, originalName)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = if (isCreateNewFile) "${customSuffix}_$timeStamp" else "_$timeStamp"

        val totalDurationMs = estimateDurationMs(resolvedInput)

        when (extractType) {
            "AUDIO" -> {
                val isOriginal = audioOutputFormat.equals("original", ignoreCase = true) || audioOutputFormat.equals("copy", ignoreCase = true)
                val outExt = if (isOriginal) "aac" else audioOutputFormat.lowercase()
                val outputDir = getOutputDirForFormat(outExt, "Extracted")
                val outputFile = File(outputDir, "${baseName}${suffix}.$outExt")

                val cmd = if (isOriginal) {
                    "-y -i \"$resolvedInput\" -vn -c:a copy \"${outputFile.absolutePath}\""
                } else if (audioOutputFormat == "flac") {
                    "-y -i \"$resolvedInput\" -vn -c:a flac \"${outputFile.absolutePath}\""
                } else if (audioOutputFormat == "wav") {
                    "-y -i \"$resolvedInput\" -vn -c:a pcm_s16le \"${outputFile.absolutePath}\""
                } else {
                    "-y -i \"$resolvedInput\" -vn -c:a libmp3lame -b:a 320k \"${outputFile.absolutePath}\""
                }
                executeFFmpegCommand(context, cmd, outputFile, origSize, totalDurationMs, if (isOriginal) "Extracting original lossless audio track..." else "Extracting audio track...")
            }
            "SUBTITLE" -> {
                val outputDir = getOutputDirForFormat("srt", "Extracted")
                val outputFile = File(outputDir, "${baseName}${suffix}.srt")
                val cmd = "-y -i \"$resolvedInput\" -map 0:s:$subtitleTrackIdx \"${outputFile.absolutePath}\""
                executeFFmpegCommand(context, cmd, outputFile, origSize, totalDurationMs, "Extracting subtitle text...")
            }
            "GIF" -> {
                val outputDir = getOutputDirForFormat("gif", "Extracted")
                val outputFile = File(outputDir, "${baseName}${suffix}.gif")
                val filter = "fps=$gifFps,scale=$gifWidth:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer"
                val cmd = "-y -i \"$resolvedInput\" -vf \"$filter\" \"${outputFile.absolutePath}\""
                executeFFmpegCommand(context, cmd, outputFile, origSize, totalDurationMs, "Rendering high-fps animated GIF...")
            }
            "FRAME", "COVER" -> {
                val isPng = audioOutputFormat.equals("png", ignoreCase = true)
                val ext = if (isPng) "png" else "jpg"
                val outputDir = getOutputDirForFormat(ext, "Customized")
                val outputFile = File(outputDir, "${baseName}${suffix}.$ext")

                val cmd = if (extractType == "COVER") {
                    "-y -i \"$resolvedInput\" -map 0:v -map -0:V -c copy \"${outputFile.absolutePath}\""
                } else if (isPng) {
                    "-y -ss 00:00:01 -i \"$resolvedInput\" -vframes 1 -c:v png \"${outputFile.absolutePath}\""
                } else {
                    "-y -ss 00:00:01 -i \"$resolvedInput\" -vframes 1 -q:v 1 \"${outputFile.absolutePath}\""
                }
                executeFFmpegCommand(context, cmd, outputFile, origSize, totalDurationMs, "Extracting full-resolution original frame...")
            }
            else -> false
        }
    }

    // ==========================================
    // 5. CORRUPTED FILE REPAIR & RECOVERY
    // ==========================================
    suspend fun repairMedia(
        context: Context,
        inputPath: String,
        inputUri: Uri?,
        originalName: String? = null,
        isLosslessRepackage: Boolean = true,
        isCreateNewFile: Boolean = true,
        customSuffix: String = "_repaired"
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedInput = resolveInputPath(context, inputPath, inputUri) ?: return@withContext false
        val inputFile = File(inputPath)
        val origSize = if (inputFile.exists()) inputFile.length() else 0L
        val baseName = getBaseName(inputPath, originalName)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = if (isCreateNewFile) "${customSuffix}_$timeStamp" else "_$timeStamp"

        val outputDir = getOutputDirForFormat("mp4", "Repaired")
        val outputFile = File(outputDir, "${baseName}${suffix}.mp4")
        val totalDurationMs = estimateDurationMs(resolvedInput)

        val cmd = if (isLosslessRepackage) {
            "-y -fflags +genpts+discardcorrupt -err_detect ignore_err -i \"$resolvedInput\" -c copy -avoid_negative_ts make_zero -movflags +faststart \"${outputFile.absolutePath}\""
        } else {
            "-y -fflags +genpts+discardcorrupt -err_detect ignore_err -i \"$resolvedInput\" -c:v libx264 -crf 20 -preset fast -pix_fmt yuv420p -c:a aac -b:a 192k -avoid_negative_ts make_zero -movflags +faststart \"${outputFile.absolutePath}\""
        }

        executeFFmpegCommand(context, cmd, outputFile, origSize, totalDurationMs, "Repairing damaged bitstream and rebuilding container index...")
    }

    // ==========================================
    // 6. VIDEO EDITOR STUDIO MASTER EXPORT PIPELINE
    // ==========================================
    suspend fun exportStudioVideo(
        context: Context,
        inputPath: String,
        inputUri: Uri?,
        originalName: String? = null,
        startMs: Long = 0L,
        endMs: Long = 0L,
        videoSpeed: Float = 1.0f,
        cropPreset: String = "ORIGINAL",
        cropNormX: Float = 0f,
        cropNormY: Float = 0f,
        cropNormW: Float = 1f,
        cropNormH: Float = 1f,
        isCustomCrop: Boolean = false,
        rotationDegrees: Int = 0,
        flipH: Boolean = false,
        flipV: Boolean = false,
        filterEffect: String = "ORIGINAL",
        blurMode: String = "NONE",
        blurIntensity: Float = 0f,
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f,
        videoVolume: Float = 1.0f,
        bgmUri: Uri? = null,
        bgmVolume: Float = 0.5f,
        exportFormat: String = "MP4", // "MP4", "GIF", "WEBP"
        gifFps: Int = 15,
        gifWidth: Int = 480
    ): Boolean = withContext(Dispatchers.IO) {
        val resolvedInput = resolveInputPath(context, inputPath, inputUri) ?: return@withContext false
        val inputFile = File(inputPath)
        val origSize = if (inputFile.exists()) inputFile.length() else 0L
        val baseName = getBaseName(inputPath, originalName)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = when (exportFormat.uppercase()) {
            "GIF" -> "gif"
            "WEBP" -> "webp"
            else -> "mp4"
        }
        val outputDir = getOutputDirForFormat(ext, "Customized")
        val outputFile = File(outputDir, "${baseName}_edit_${timeStamp}.$ext")

        val totalDurationMs = estimateDurationMs(resolvedInput)
        val effectiveStartMs = startMs.coerceAtLeast(0L)
        val effectiveEndMs = if (endMs > effectiveStartMs) endMs else totalDurationMs
        val clipDurationMs = (effectiveEndMs - effectiveStartMs).coerceAtLeast(500L)
        val startSec = effectiveStartMs / 1000.0
        val clipSec = clipDurationMs / 1000.0

        val vFilters = mutableListOf<String>()

        // 1. Playback Speed (Video PTS)
        if (kotlin.math.abs(videoSpeed - 1.0f) > 0.01f) {
            val ptsMultiplier = 1.0f / videoSpeed.coerceIn(0.25f, 4.0f)
            vFilters.add("setpts=${String.format(Locale.US, "%.4f", ptsMultiplier)}*PTS")
        }

        // 2. Crop
        if (isCustomCrop && cropNormW in 0.05f..1f && cropNormH in 0.05f..1f) {
            val wExpr = "trunc(iw*${String.format(Locale.US, "%.4f", cropNormW.coerceIn(0.05f, 1f))}/2)*2"
            val hExpr = "trunc(ih*${String.format(Locale.US, "%.4f", cropNormH.coerceIn(0.05f, 1f))}/2)*2"
            val xExpr = "trunc(iw*${String.format(Locale.US, "%.4f", cropNormX.coerceIn(0f, 0.95f))}/2)*2"
            val yExpr = "trunc(ih*${String.format(Locale.US, "%.4f", cropNormY.coerceIn(0f, 0.95f))}/2)*2"
            vFilters.add("crop=$wExpr:$hExpr:$xExpr:$yExpr")
        } else {
            when (cropPreset) {
                "P_9_16", "9_16" -> vFilters.add("crop='min(iw,ih*9/16)':'min(ih,iw*16/9)':(iw-out_w)/2:(ih-out_h)/2")
                "P_1_1", "1_1" -> vFilters.add("crop='min(iw,ih)':'min(iw,ih)':(iw-out_w)/2:(ih-out_h)/2")
                "P_16_9", "16_9" -> vFilters.add("crop='min(iw,ih*16/9)':'min(ih,iw*9/16)':(iw-out_w)/2:(ih-out_h)/2")
                "P_21_9", "21_9" -> vFilters.add("crop='min(iw,ih*21/9)':'min(ih,iw*9/21)':(iw-out_w)/2:(ih-out_h)/2")
                "P_4_3", "4_3" -> vFilters.add("crop='min(iw,ih*4/3)':'min(ih,iw*3/4)':(iw-out_w)/2:(ih-out_h)/2")
                "P_3_4", "3_4" -> vFilters.add("crop='min(iw,ih*3/4)':'min(ih,iw*4/3)':(iw-out_w)/2:(ih-out_h)/2")
                "P_4_5", "4_5" -> vFilters.add("crop='min(iw,ih*4/5)':'min(ih,iw*5/4)':(iw-out_w)/2:(ih-out_h)/2")
            }
        }

        // 3. Rotation & Flip
        when (rotationDegrees % 360) {
            90 -> vFilters.add("transpose=1")
            180 -> { vFilters.add("hflip"); vFilters.add("vflip") }
            270 -> vFilters.add("transpose=2")
        }
        if (flipH) vFilters.add("hflip")
        if (flipV) vFilters.add("vflip")

        // 4. Color Adjustments (Brightness, Contrast, Saturation)
        if (kotlin.math.abs(brightness) > 0.01f || kotlin.math.abs(contrast - 1f) > 0.01f || kotlin.math.abs(saturation - 1f) > 0.01f) {
            vFilters.add("eq=brightness=${String.format(Locale.US, "%.2f", brightness)}:contrast=${String.format(Locale.US, "%.2f", contrast)}:saturation=${String.format(Locale.US, "%.2f", saturation)}")
        }

        // 5. Film / Stylized FX
        when (filterEffect.uppercase()) {
            "CINEMA" -> vFilters.add("curves=preset=cross_process,colorbalance=rs=0.1:gs=-0.05:bs=-0.1")
            "VIVID" -> vFilters.add("eq=contrast=1.2:saturation=1.35")
            "NOIR" -> vFilters.add("hue=s=0,eq=contrast=1.35:brightness=-0.02")
            "VINTAGE" -> vFilters.add("curves=vintage,eq=saturation=0.85")
            "WARM" -> vFilters.add("colorbalance=rs=0.15:gs=0.05:bs=-0.1")
            "COOL" -> vFilters.add("colorbalance=rs=-0.1:gs=0.0:bs=0.15")
            "CYBERPUNK" -> vFilters.add("colorbalance=rs=0.2:bs=0.3:gs=-0.1,eq=contrast=1.3")
            "DREAMY" -> vFilters.add("gblur=sigma=1:steps=1,eq=brightness=0.05:contrast=1.1")
        }

        // 6. Blur Options
        if (blurIntensity > 0.01f) {
            val sigma = (blurIntensity * 25f).coerceIn(1f, 35f)
            when (blurMode.uppercase()) {
                "GAUSSIAN" -> vFilters.add("gblur=sigma=${String.format(Locale.US, "%.1f", sigma)}:steps=2")
                "RADIAL_FOCUS", "TILT_SHIFT" -> vFilters.add("gblur=sigma=${String.format(Locale.US, "%.1f", sigma / 2)},vignette=PI/4")
                "PIXELATE", "MOSAIC" -> {
                    val blockSize = (blurIntensity * 32f).toInt().coerceIn(4, 48)
                    vFilters.add("scale=iw/$blockSize:ih/$blockSize,scale=iw*$blockSize:ih*$blockSize:flags=neighbor")
                }
                "MOTION_BLUR" -> vFilters.add("tblend=all_mode=average")
            }
        }

        val resolvedBgm = if (bgmUri != null) resolveInputPath(context, bgmUri.toString(), bgmUri) else null

        val cmd = StringBuilder("-y ")
        // Seek & duration
        if (startSec > 0.0) {
            cmd.append("-ss ${String.format(Locale.US, "%.3f", startSec)} ")
        }
        cmd.append("-i \"$resolvedInput\" ")
        if (clipSec > 0.0 && effectiveEndMs < totalDurationMs) {
            cmd.append("-t ${String.format(Locale.US, "%.3f", clipSec)} ")
        }

        if (resolvedBgm != null) {
            cmd.append("-i \"$resolvedBgm\" ")
        }

        when (ext) {
            "gif" -> {
                val baseVf = if (vFilters.isNotEmpty()) vFilters.joinToString(",") + "," else ""
                val paletteVf = "${baseVf}fps=$gifFps,scale=$gifWidth:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer"
                cmd.append("-filter_complex \"$paletteVf\" \"${outputFile.absolutePath}\"")
                executeFFmpegCommand(context, cmd.toString(), outputFile, origSize, clipDurationMs, "Rendering Animated GIF...")
            }
            "webp" -> {
                val vfStr = if (vFilters.isNotEmpty()) "-vf \"${vFilters.joinToString(",")},fps=$gifFps,scale=$gifWidth:-1\"" else "-vf \"fps=$gifFps,scale=$gifWidth:-1\""
                cmd.append("$vfStr -loop 0 -vcodec libwebp -lossless 0 -q:v 75 \"${outputFile.absolutePath}\"")
                executeFFmpegCommand(context, cmd.toString(), outputFile, origSize, clipDurationMs, "Rendering Animated WebP...")
            }
            else -> {
                // MP4 Export
                val vfStr = if (vFilters.isNotEmpty()) "-vf \"${vFilters.joinToString(",")}\" " else ""
                cmd.append(vfStr)

                // Audio handling
                if (resolvedBgm != null) {
                    val atempoStr = if (kotlin.math.abs(videoSpeed - 1.0f) > 0.01f) {
                        "atempo=${String.format(Locale.US, "%.2f", videoSpeed.coerceIn(0.5f, 2.0f))},"
                    } else ""
                    val vVolStr = String.format(Locale.US, "%.2f", videoVolume.coerceIn(0f, 2f))
                    val bVolStr = String.format(Locale.US, "%.2f", bgmVolume.coerceIn(0f, 2f))
                    val filterComplex = "[0:a]${atempoStr}volume=$vVolStr[a0];[1:a]volume=$bVolStr[a1];[a0][a1]amix=inputs=2:duration=first[aout]"
                    cmd.append("-filter_complex \"$filterComplex\" -map 0:v -map \"[aout]\" ")
                } else {
                    val aFilters = mutableListOf<String>()
                    if (kotlin.math.abs(videoSpeed - 1.0f) > 0.01f) {
                        aFilters.add("atempo=${String.format(Locale.US, "%.2f", videoSpeed.coerceIn(0.5f, 2.0f))}")
                    }
                    if (kotlin.math.abs(videoVolume - 1.0f) > 0.01f || videoVolume == 0f) {
                        aFilters.add("volume=${String.format(Locale.US, "%.2f", videoVolume.coerceIn(0f, 2f))}")
                    }
                    if (aFilters.isNotEmpty()) {
                        cmd.append("-af \"${aFilters.joinToString(",")}\" ")
                    }
                }

                cmd.append("-c:v libx264 -crf 19 -preset fast -pix_fmt yuv420p -c:a aac -b:a 192k -movflags +faststart \"${outputFile.absolutePath}\"")
                executeFFmpegCommand(context, cmd.toString(), outputFile, origSize, clipDurationMs, "Exporting customized video...")
            }
        }
    }

    // ==========================================
    // INTERNAL EXECUTION & HELPER PIPELINE
    // ==========================================
    private suspend fun executeFFmpegCommand(
        context: Context,
        commandString: String,
        outputFile: File,
        originalSizeBytes: Long,
        totalDurationMs: Long,
        initialStatusText: String
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing FFmpeg: $commandString")

        // Pre-flight Storage Safety Check
        try {
            val targetDir = outputFile.parentFile ?: context.cacheDir
            if (!targetDir.exists()) targetDir.mkdirs()
            val statFs = StatFs(targetDir.path)
            val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            val minRequiredBytes = 100L * 1024 * 1024 // 100MB minimum safety margin
            if (availableBytes < minRequiredBytes) {
                _processingState.value = ProcessingState.Failed(
                    errorMessage = "Insufficient device storage space. Please free up space before processing.",
                    fullLog = "Available: ${formatBytesReport(availableBytes)}, Minimum required: 100 MB"
                )
                return@withContext false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Disk space pre-check warning: ${e.message}")
        }

        _processingState.value = ProcessingState.Processing(
            progressPercent = 0,
            speed = 1.0,
            timeMs = 0L,
            statusText = initialStatusText,
            recentLog = "Initializing FFmpeg pipeline..."
        )

        val fullLogs = StringBuilder()
        var isSuccess = false
        val startTimeMs = System.currentTimeMillis()

        try {
            val session = FFmpegKit.executeAsync(
                commandString,
                { completedSession ->
                    val returnCode = completedSession.returnCode
                    if (ReturnCode.isSuccess(returnCode)) {
                        isSuccess = true
                        MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)
                        val elapsed = System.currentTimeMillis() - startTimeMs
                        _processingState.value = ProcessingState.Completed(
                            outputFile = outputFile,
                            outputUri = Uri.fromFile(outputFile),
                            originalSizeBytes = originalSizeBytes,
                            newSizeBytes = outputFile.length(),
                            durationMs = elapsed
                        )
                    } else if (ReturnCode.isCancel(returnCode)) {
                        _processingState.value = ProcessingState.Cancelled
                        outputFile.delete()
                    } else {
                        val failMsg = completedSession.failStackTrace ?: "Process failed with code $returnCode"
                        _processingState.value = ProcessingState.Failed(
                            errorMessage = failMsg,
                            fullLog = fullLogs.toString()
                        )
                        outputFile.delete()
                    }
                },
                { log ->
                    val message = log.message
                    if (message.isNotBlank()) {
                        fullLogs.append(message)
                    }
                },
                { statistics ->
                    val timeMs = statistics.time.toLong()
                    val speed = statistics.speed
                    val percent = if (totalDurationMs > 0) {
                        ((timeMs.toFloat() / totalDurationMs.toFloat()) * 100f).toInt().coerceIn(0, 99)
                    } else {
                        (timeMs / 1000).toInt().coerceIn(0, 99)
                    }

                    _processingState.value = ProcessingState.Processing(
                        progressPercent = percent,
                        speed = speed,
                        timeMs = timeMs,
                        statusText = initialStatusText,
                        recentLog = "Processed ${(timeMs / 1000)}s · Speed: ${String.format(Locale.US, "%.1fx", speed)}"
                    )
                }
            )

            activeSessionId = session.sessionId
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg launch error", e)
            _processingState.value = ProcessingState.Failed(e.message ?: "Unknown execution error", fullLogs.toString())
            return@withContext false
        }
    }

    private fun resolveInputPath(context: Context, inputPath: String, inputUri: Uri?): String? {
        if (inputPath.isNotBlank() && File(inputPath).exists()) {
            return inputPath
        }
        if (inputUri != null) {
            try {
                return FFmpegKitConfig.getSafParameterForRead(context, inputUri)
            } catch (_: Exception) {
                try {
                    val temp = File(context.cacheDir, "input_proc_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(inputUri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    return temp.absolutePath
                } catch (_: Exception) {}
            }
        }
        return if (inputPath.isNotBlank()) inputPath else null
    }

    private fun estimateDurationMs(filePath: String): Long {
        return try {
            val report = MediaAnalyzer.getReportIfCached(filePath)
            val sec = report?.format?.duration ?: 0.0
            if (sec > 0.0) (sec * 1000).toLong() else 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun getOutputDirForFormat(format: String, operationType: String? = null): File {
        val f = format.lowercase()
        val dir = when {
            isAudioFormat(f) -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            isImageFormat(f) || f == "gif" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            f == "srt" || f == "vtt" || f == "ass" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        val target = if (operationType != null) File(dir, "MediaNest Studio/$operationType") else File(dir, "MediaNest Studio")
        if (!target.exists()) target.mkdirs()
        return target
    }

    private fun isAudioFormat(ext: String): Boolean =
        listOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma").contains(ext.lowercase())

    private fun isImageFormat(ext: String): Boolean =
        listOf("png", "jpg", "jpeg", "webp", "avif", "bmp").contains(ext.lowercase())

    private fun getBaseName(path: String, originalName: String? = null): String {
        if (!originalName.isNullOrBlank()) {
            val lastDot = originalName.lastIndexOf(".")
            return if (lastDot > 0) originalName.substring(0, lastDot) else originalName
        }
        return try {
            val file = File(path)
            file.nameWithoutExtension.ifBlank { "media_export" }
        } catch (_: Exception) {
            "media_export"
        }
    }
}
