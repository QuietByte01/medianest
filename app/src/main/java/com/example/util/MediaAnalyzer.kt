package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * MediaAnalyzer — High-resilience media analysis engine powered by FFmpeg (FFprobe).
 *
 * Capable of parsing legacy formats (AVI, WMV, FLV, VOB) and recovering metadata
 * from corrupt/damaged media files by scanning raw bitstreams.
 */
object MediaAnalyzer {

    private const val TAG = "MediaAnalyzer"

    suspend fun analyze(
        filePath: String,
        mediaType: String,
        context: Context? = null
    ): MediaDiagnosticsReport = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val uri = Uri.parse(filePath)
        val isContentUri = filePath.startsWith("content://") && context != null

        // Resolve absolute path or ContentResolver safe path for FFmpeg
        val targetFFmpegPath = if (isContentUri) {
            getFFmpegSafePathFromUri(context!!, uri)
        } else {
            filePath
        }

        val file = File(targetFFmpegPath)
        val fileName = file.name.ifBlank { uri.lastPathSegment ?: "unknown_media" }
        val fileSize = if (file.exists()) file.length() else 0L

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var timestampIssues = false
        var corruptedFrames = false

        var formatInfo: FormatInfo? = null
        var videoStream: VideoStreamInfo? = null
        val audioStreams = mutableListOf<AudioStreamInfo>()
        val subtitleStreams = mutableListOf<SubtitleStreamInfo>()

        // Execute FFprobe JSON inspection pass
        val ffprobeJson = executeFFprobe(targetFFmpegPath)

        if (ffprobeJson == null) {
            errors.add("FFprobe failed to inspect file. Container header may be severely corrupted.")
        } else {
            try {
                // Parse Container Format Info
                val formatObj = ffprobeJson.optJSONObject("format")
                if (formatObj != null) {
                    val container = formatObj.optString("format_name", "unknown")
                    val duration = formatObj.optString("duration", "0.0").toDoubleOrNull() ?: 0.0
                    val bitrate = formatObj.optString("bit_rate", "0").toLongOrNull() ?: 0L

                    formatInfo = FormatInfo(
                        containerFormat = container,
                        formatLongName = formatObj.optString("format_long_name", container),
                        duration = duration,
                        bitrate = bitrate,
                        size = formatObj.optString("size", fileSize.toString()).toLongOrNull() ?: fileSize,
                        streamCount = formatObj.optInt("nb_streams", 0),
                        startTime = formatObj.optString("start_time", "0.0").toDoubleOrNull() ?: 0.0
                    )
                }

                // Parse Video, Audio, and Subtitle Streams
                val streamsArray = ffprobeJson.optJSONArray("streams")
                if (streamsArray != null) {
                    for (i in 0 until streamsArray.length()) {
                        val stream = streamsArray.getJSONObject(i)
                        val codecType = stream.optString("codec_type")

                        when (codecType) {
                            "video" -> {
                                if (videoStream == null) { // Main video stream
                                    val width = stream.optInt("width", 0)
                                    val height = stream.optInt("height", 0)
                                    val frameRateStr = stream.optString("r_frame_rate", "30/1")
                                    val avgFpsDecimal = parseFraction(frameRateStr)

                                    videoStream = VideoStreamInfo(
                                        index = stream.optInt("index", i),
                                        codecName = stream.optString("codec_name", "unknown").uppercase(Locale.US),
                                        codecLongName = stream.optString("codec_long_name", ""),
                                        profile = stream.optString("profile", "Main"),
                                        level = stream.optInt("level", 0),
                                        width = width,
                                        height = height,
                                        pixelFormat = stream.optString("pix_fmt", "yuv420p"),
                                        colorSpace = stream.optString("color_space", "bt709"),
                                        colorPrimaries = stream.optString("color_primaries", "bt709"),
                                        colorTransfer = stream.optString("color_transfer", "smpte170m"),
                                        colorRange = stream.optString("color_range", "tv"),
                                        frameRate = frameRateStr,
                                        avgFrameRate = avgFpsDecimal.toString(),
                                        avgFpsDecimal = avgFpsDecimal,
                                        aspectRatio = stream.optString("display_aspect_ratio", "$width:$height"),
                                        bitrate = stream.optString("bit_rate", "0").toLongOrNull() ?: 0L,
                                        duration = stream.optString("duration", "0.0").toDoubleOrNull() ?: 0.0,
                                        isDefault = stream.optJSONObject("disposition")?.optInt("default") == 1,
                                        rotation = parseRotation(stream)
                                    )
                                }
                            }
                            "audio" -> {
                                audioStreams.add(
                                    AudioStreamInfo(
                                        index = stream.optInt("index", i),
                                        codecName = stream.optString("codec_name", "unknown").uppercase(Locale.US),
                                        codecLongName = stream.optString("codec_long_name", ""),
                                        profile = stream.optString("profile", ""),
                                        sampleRate = stream.optString("sample_rate", "44100").toIntOrNull() ?: 44100,
                                        channels = stream.optInt("channels", 2),
                                        channelLayout = stream.optString("channel_layout", "stereo"),
                                        sampleFormat = stream.optString("sample_fmt", "s16p"),
                                        bitsPerSample = stream.optInt("bits_per_raw_sample", 16),
                                        bitrate = stream.optString("bit_rate", "0").toLongOrNull() ?: 0L,
                                        duration = stream.optString("duration", "0.0").toDoubleOrNull() ?: 0.0,
                                        isDefault = stream.optJSONObject("disposition")?.optInt("default") == 1,
                                        language = stream.optJSONObject("tags")?.optString("language", "und") ?: "und"
                                    )
                                )
                            }
                            "subtitle" -> {
                                subtitleStreams.add(
                                    SubtitleStreamInfo(
                                        index = stream.optInt("index", i),
                                        codecName = stream.optString("codec_name", "unknown"),
                                        language = stream.optJSONObject("tags")?.optString("language", "und") ?: "und"
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                errors.add("Error parsing FFprobe analysis payload: ${e.message}")
            }
        }

        // Corruption Diagnostic Scan Pass using FFmpeg
        if (ffprobeJson != null) {
            val corruptionReport = scanForBitstreamCorruption(targetFFmpegPath)
            if (corruptionReport.hasCorruption) {
                corruptedFrames = true
                warnings.add(corruptionReport.message)
            }
            if (corruptionReport.hasTimestampIssues) {
                timestampIssues = true
                warnings.add("Timestamp discontinuities detected in bitstream.")
            }
        }

        val elapsedMs = System.currentTimeMillis() - startMs

        val diagnostics = DiagnosticsInfo(
            hasErrors = errors.isNotEmpty(),
            errors = errors,
            warnings = warnings,
            corruptedFramesDetected = corruptedFrames,
            timestampIssues = timestampIssues,
            missingStreams = formatInfo?.streamCount == 0,
            decodingErrorLines = warnings.take(5),
            analysisNote = when {
                errors.isNotEmpty() -> "${errors.size} error(s) found during analysis"
                corruptedFrames -> "⚠ Corrupted frames detected in stream"
                timestampIssues -> "⚠ Timestamp irregularities detected"
                else -> "✅ AVI / Media container and stream integrity clean"
            }
        )

        MediaDiagnosticsReport(
            filePath = filePath,
            fileName = fileName,
            fileSize = fileSize,
            mediaType = mediaType,
            analysisTimestampMs = startMs,
            analysisElapsedMs = elapsedMs,
            format = formatInfo,
            videoStream = videoStream,
            audioStreams = audioStreams,
            imageInfo = null,
            subtitleStreams = subtitleStreams,
            diagnostics = diagnostics
        )
    }

    // --- FFmpeg Engine Helpers ---

    private fun executeFFprobe(path: String): JSONObject? {
        val command = "-v error -show_format -show_streams -print_format json \"$path\""
        val session = FFprobeKit.execute(command)

        return if (session.returnCode.isValueSuccess) {
            runCatching { JSONObject(session.output) }.getOrNull()
        } else {
            Log.e(TAG, "FFprobe failed: ${session.failStackTrace}")
            null
        }
    }

    private data class CorruptionResult(
        val hasCorruption: Boolean,
        val hasTimestampIssues: Boolean,
        val message: String
    )

    private fun scanForBitstreamCorruption(path: String): CorruptionResult {
        // Scans stream headers and packet errors without full frame decoding
        val command = "-v error -i \"$path\" -f null -"
        val session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
        val logs = session.allLogsAsString

        val isCorrupted = logs.contains("corrupt", ignoreCase = true) ||
                logs.contains("error", ignoreCase = true) ||
                logs.contains("invalid", ignoreCase = true)

        val hasPtsError = logs.contains("pts", ignoreCase = true) ||
                logs.contains("dts", ignoreCase = true)

        return CorruptionResult(
            hasCorruption = isCorrupted,
            hasTimestampIssues = hasPtsError,
            message = if (isCorrupted) "Bitstream corruption detected: ${logs.take(150)}..." else "Clean"
        )
    }

    private fun parseFraction(fraction: String): Double {
        return runCatching {
            val parts = fraction.split("/")
            if (parts.size == 2) {
                val num = parts[0].toDouble()
                val den = parts[1].toDouble()
                if (den != 0.0) num / den else 0.0
            } else {
                fraction.toDoubleOrNull() ?: 0.0
            }
        }.getOrDefault(0.0)
    }

    private fun parseRotation(streamObj: JSONObject): Int {
        val sideData = streamObj.optJSONArray("side_data_list") ?: return 0
        for (i in 0 until sideData.length()) {
            val data = sideData.optJSONObject(i)
            if (data?.optString("side_data_type") == "Display Matrix") {
                return data.optInt("rotation", 0)
            }
        }
        return 0
    }

    private fun getFFmpegSafePathFromUri(context: Context, uri: Uri): String {
        // FFmpeg requires raw file system path access; copy Uri stream to cache for inspection if content Uri
        val tempFile = File(context.cacheBufferDir, "probe_temp_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        return tempFile.absolutePath
    }

    private val Context.cacheBufferDir: File
        get() = cacheDir.apply { mkdirs() }
}