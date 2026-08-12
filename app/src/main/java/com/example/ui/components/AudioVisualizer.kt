package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.Visualizer
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class VisualizerStyle {
    GLOSSY_SPECTRUM_BARS,
    CIRCULAR_RING_WAVE,
    NEON_WAVEFORM,
    BASS_PARTICLE_HALO,
    PARTICLE_GLOBE_SPHERE,
    WAVE_GRID_TERRAIN,
    PARTICLE_TUNNEL_VORTEX
}

private class VisualizerParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radius: Float = 0f,
    var alpha: Float = 0f,
    var color: Color = Color.White
)

class VisualizerDataState(val numBands: Int = 32) {
    val rawFFT = FloatArray(numBands)
    val smoothedBands = FloatArray(numBands)
    val peakCaps = FloatArray(numBands)
    val peakVelocities = FloatArray(numBands)
    var bassEnergy = 0f
    var overallAmplitude = 0f
}

suspend fun extractBaseHueFromArt(context: Context, artUri: Uri): Float? {
    return withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(artUri)
                .size(64, 64)
                .allowHardware(false)
                .build()
            val drawable = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@withContext null

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val hsv = FloatArray(3)
            val hueBins = FloatArray(12)
            var count = 0

            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                if (hsv[2] > 0.12f && (hsv[1] > 0.12f || hsv[2] < 0.88f)) {
                    val bin = ((hsv[0] / 30f).toInt()) % 12
                    hueBins[bin] += (hsv[1] * hsv[2]) + 0.1f
                    count++
                }
            }

            if (count == 0) return@withContext null

            var bestBin = 0
            var maxWeight = -1f
            for (i in 0 until 12) {
                if (hueBins[i] > maxWeight) {
                    maxWeight = hueBins[i]
                    bestBin = i
                }
            }

            bestBin * 30f + 15f
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    audioSessionId: Int = 0,
    currentPosMs: Long = 0L,
    trackSeed: Long = 0L,
    albumArtUri: Uri? = null,
    modifier: Modifier = Modifier,
    style: VisualizerStyle = VisualizerStyle.GLOSSY_SPECTRUM_BARS,
    primaryColor: Color = Color(0xFF00E5FF),
    secondaryColor: Color = Color(0xFFD500F9),
    accentColor: Color = Color(0xFFFFD600),
    numBands: Int = 32,
    showControls: Boolean = false,
    onStyleChange: ((VisualizerStyle) -> Unit)? = null
) {
    val context = LocalContext.current
    val state = remember(numBands) { VisualizerDataState(numBands) }
    var currentStyle by remember(style) { mutableStateOf(style) }
    var extractedBaseHue by remember(albumArtUri) { mutableStateOf<Float?>(null) }

    LaunchedEffect(albumArtUri) {
        if (albumArtUri != null) {
            extractedBaseHue = extractBaseHueFromArt(context, albumArtUri)
        } else {
            extractedBaseHue = null
        }
    }

    // Particles pool
    val particles = remember {
        List(32) { VisualizerParticle() }
    }

    // Hardware Audio Visualizer listener
    DisposableEffect(audioSessionId, isPlaying) {
        var androidVisualizer: Visualizer? = null
        if (isPlaying && audioSessionId > 0) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                try {
                    val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(256)
                    androidVisualizer = Visualizer(audioSessionId).apply {
                        setCaptureSize(captureSize)
                        setDataCaptureListener(
                            object : Visualizer.OnDataCaptureListener {
                                override fun onWaveFormDataCapture(
                                    visualizer: Visualizer?,
                                    waveform: ByteArray?,
                                    samplingRate: Int
                                ) {}

                                override fun onFftDataCapture(
                                    visualizer: Visualizer?,
                                    fft: ByteArray?,
                                    samplingRate: Int
                                ) {
                                    if (fft == null || fft.isEmpty()) return
                                    val n = fft.size / 2
                                    for (i in 0 until numBands) {
                                        val binIndex = (i * (n / numBands)).coerceIn(1, n - 1)
                                        val real = fft[binIndex * 2].toFloat()
                                        val imag = fft[binIndex * 2 + 1].toFloat()
                                        val magnitude = sqrt(real * real + imag * imag) / 128f
                                        state.rawFFT[i] = magnitude.coerceIn(0f, 1f)
                                    }
                                }
                            },
                            Visualizer.getMaxCaptureRate() / 2,
                            false,
                            true
                        )
                        enabled = true
                    }
                } catch (e: Exception) {
                    androidVisualizer?.release()
                    androidVisualizer = null
                }
            }
        }

        onDispose {
            try {
                androidVisualizer?.enabled = false
                androidVisualizer?.release()
            } catch (_: Exception) {}
        }
    }

    // 60 FPS Continuous Frame Tick Loop
    var lastNanos by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        var prevNanos = System.nanoTime()
        if (isPlaying) {
            while (isPlaying) {
                withFrameNanos { frameNanos ->
                    val dt = ((frameNanos - prevNanos) / 1_000_000_000f).coerceIn(0.001f, 0.050f)
                    prevNanos = frameNanos
                    lastNanos = frameNanos.toFloat()

                    val absSeed = abs(trackSeed).toFloat()
                    val tempo = 0.85f + (absSeed % 35) / 100f
                    val timeSec = (currentPosMs / 1000f) * tempo + (frameNanos / 1_000_000_000f % 1000f)
                    val beatFreq = 2.4f * tempo
                    val beatPulse = abs(sin((timeSec * beatFreq * Math.PI).toDouble())).toFloat()
                    val sharpBeat = Math.pow(beatPulse.toDouble(), 2.8).toFloat()

                    var bassSum = 0f
                    var totalSum = 0f

                    for (i in 0 until state.numBands) {
                        val norm = i.toFloat() / (state.numBands - 1)

                        val rawVal = state.rawFFT[i]
                        val syntheticTarget = run {
                            val f1 = sin((timeSec * (1.2f + i * 0.12f) + i * 0.45f).toDouble()).toFloat()
                            val f2 = cos((timeSec * (2.1f - i * 0.08f) + i * 0.7f).toDouble()).toFloat()
                            val f3 = sin((timeSec * (3.8f + i * 0.15f)).toDouble()).toFloat()

                            val bandPulse = when {
                                norm < 0.25f -> 0.45f * sharpBeat + 0.30f * abs(f1) + 0.25f * abs(f2)
                                norm < 0.70f -> 0.20f * sharpBeat + 0.50f * abs(f2) + 0.30f * abs(f3)
                                else -> 0.10f * sharpBeat + 0.40f * abs(f1 * f3) + 0.50f * abs(f3)
                            }
                            bandPulse.coerceIn(0.06f, 1.0f)
                        }

                        val targetVal = if (rawVal > 0.005f) {
                            (rawVal * 0.92f + syntheticTarget * 0.08f).coerceIn(0.06f, 1f)
                        } else {
                            (syntheticTarget * 0.95f).coerceIn(0.08f, 1.0f)
                        }

                        val prev = state.smoothedBands[i]
                        val alpha = if (targetVal > prev) 0.48f else 0.16f
                        val smoothed = prev + (targetVal - prev) * alpha
                        state.smoothedBands[i] = smoothed

                        val gravity = 3.2f * dt
                        if (smoothed > state.peakCaps[i]) {
                            state.peakCaps[i] = smoothed
                            state.peakVelocities[i] = 0f
                        } else {
                            state.peakVelocities[i] += gravity
                            state.peakCaps[i] = (state.peakCaps[i] - state.peakVelocities[i] * dt).coerceAtLeast(0f)
                        }

                        if (norm < 0.25f) bassSum += smoothed
                        totalSum += smoothed
                    }

                    state.bassEnergy = (bassSum / (state.numBands * 0.25f)).coerceIn(0f, 1f)
                    state.overallAmplitude = (totalSum / state.numBands).coerceIn(0f, 1f)
                }
            }
        } else {
            // Perform quick decay pass when paused and then stop loop
            for (i in 0 until state.numBands) {
                state.smoothedBands[i] = 0f
                state.peakCaps[i] = 0f
                state.rawFFT[i] = 0f
            }
            state.bassEnergy = 0f
            state.overallAmplitude = 0f
            lastNanos = System.nanoTime().toFloat()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val artBaseHue = extractedBaseHue ?: ((abs(trackSeed).toFloat() * 137.5f) % 360f)
        val animNanos = lastNanos

        // Layer 1: Diffused/Blurred Background Layer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(32.dp) // Stronger blur for true base diffusion
        ) {
            if (currentStyle == VisualizerStyle.GLOSSY_SPECTRUM_BARS) {
                val totalBars = state.numBands
                val spacing = 12.dp.toPx()
                val totalSpacing = spacing * (totalBars - 1)
                val barWidth = ((size.width - totalSpacing) / totalBars).coerceAtLeast(4f)
                val maxHeight = size.height

                for (i in 0 until totalBars) {
                    val value = state.smoothedBands[i]
                    val barHeight = (maxHeight * value * 0.95f).coerceAtLeast(4.dp.toPx())
                    val x = i * (barWidth + spacing)
                    val top = maxHeight - barHeight

                    // Diffusion layer: Wider and more present at the bottom
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.0f),  // Top: Fades out
                                Color.White.copy(alpha = 0.65f)  // Bottom: Strong blur spread
                            ),
                            startY = top,
                            endY = maxHeight
                        ),
                        topLeft = Offset(x - 8.dp.toPx(), top),
                        size = Size(barWidth + 16.dp.toPx(), barHeight),
                        cornerRadius = CornerRadius((barWidth + 16.dp.toPx()) / 2f)
                    )
                }
            }
        }

        // Layer 2: Sharp Foreground Layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (currentStyle) {
                VisualizerStyle.GLOSSY_SPECTRUM_BARS -> {
                    drawGlossySpectrumBars(
                        state = state,
                        artBaseHue = artBaseHue,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        accentColor = accentColor,
                        timeNanos = animNanos
                    )
                }
                VisualizerStyle.CIRCULAR_RING_WAVE -> {
                    drawCircularRingWave(
                        state = state,
                        artBaseHue = artBaseHue,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        accentColor = accentColor,
                        timeNanos = animNanos
                    )
                }
                VisualizerStyle.NEON_WAVEFORM -> {
                    drawNeonWaveform(
                        state = state,
                        artBaseHue = artBaseHue,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        accentColor = accentColor,
                        timeNanos = animNanos
                    )
                }
                VisualizerStyle.BASS_PARTICLE_HALO -> {
                    drawBassParticleHalo(
                        state = state,
                        particles = particles,
                        artBaseHue = artBaseHue,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        accentColor = accentColor,
                        timeNanos = animNanos
                    )
                }
                VisualizerStyle.PARTICLE_GLOBE_SPHERE -> {
                    drawParticleGlobeSphere(
                        state = state,
                        artBaseHue = artBaseHue,
                        timeNanos = animNanos
                    )
                }
                VisualizerStyle.WAVE_GRID_TERRAIN -> {
                    drawWaveGridTerrain(
                        state = state,
                        artBaseHue = artBaseHue,
                        timeNanos = animNanos
                    )
                }
                VisualizerStyle.PARTICLE_TUNNEL_VORTEX -> {
                    drawParticleTunnelVortex(
                        state = state,
                        artBaseHue = artBaseHue,
                        timeNanos = animNanos
                    )
                }
            }
        }

        if (showControls) {
            StyleSelectorBar(
                currentStyle = currentStyle,
                onSelectStyle = {
                    currentStyle = it
                    onStyleChange?.invoke(it)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }
    }
}

private fun dynamicSpectrumColor(norm: Float, baseHue: Float): Color {
    // Keep hue tightly within an analogous range (±20°) around baseHue so it strictly matches album art
    val hue = (baseHue + (norm - 0.5f) * 40f + 360f) % 360f
    return Color.hsv(hue, 0.75f, 1.0f) // Slightly less saturated for neon feel
}

private fun DrawScope.drawGlossySpectrumBars(
    state: VisualizerDataState,
    artBaseHue: Float,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    timeNanos: Float
) {
    val totalBars = state.numBands
    val spacing = 8.dp.toPx()
    val totalSpacing = spacing * (totalBars - 1)
    val barWidth = ((size.width - totalSpacing) / totalBars).coerceAtLeast(4f)
    val maxHeight = size.height

    for (i in 0 until totalBars) {
        val norm = i.toFloat() / totalBars.toFloat()
        val value = state.smoothedBands[i]
        val barHeight = (maxHeight * value * 0.90f).coerceAtLeast(6.dp.toPx())

        val x = i * (barWidth + spacing)
        val top = maxHeight - barHeight

        val topColor = dynamicSpectrumColor(norm, artBaseHue)
        val midColor = Color.hsv((artBaseHue + norm * 30f + 180f) % 360f, 0.85f, 0.95f)

        // 1. MAIN GRADIENT BAR BODY
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    topColor.copy(alpha = 0.95f),
                    midColor.copy(alpha = 0.65f),
                    primaryColor.copy(alpha = 0.10f)
                ),
                startY = top,
                endY = maxHeight
            ),
            topLeft = Offset(x, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )

        // 2. FLOATING PEAK CAP
        val peakVal = state.peakCaps[i]
        if (peakVal > 0.05f) {
            val peakY = (maxHeight * (1f - peakVal * 0.90f)).coerceIn(0f, maxHeight - 4.dp.toPx())
            val capHeight = 3.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = 0.90f),
                topLeft = Offset(x, peakY),
                size = Size(barWidth, capHeight),
                cornerRadius = CornerRadius(capHeight / 2f, capHeight / 2f)
            )
        }

        // 3. SPECULAR GLOSS HIGHLIGHT
        if (barWidth >= 4f && value > 0.15f) {
            val specWidth = barWidth * 0.30f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f),
                        Color.Transparent
                    ),
                    startY = top + (barWidth / 4f),
                    endY = top + (barHeight * 0.4f)
                ),
                topLeft = Offset(x + (barWidth * 0.15f), top + (barWidth / 4f)),
                size = Size(specWidth, barHeight * 0.35f),
                cornerRadius = CornerRadius(specWidth / 2f)
            )
        }
    }
}

private fun DrawScope.drawCircularRingWave(
    state: VisualizerDataState,
    artBaseHue: Float,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    timeNanos: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseRadius = (size.width.coerceAtMost(size.height) * 0.30f).coerceAtLeast(20.dp.toPx())
    val bassPulseRadius = baseRadius + (state.bassEnergy * 20.dp.toPx())

    val timeSec = timeNanos / 1_000_000_000f
    val baseHue = (artBaseHue + sin(timeSec * 0.8f) * 8f + 360f) % 360f

    val c1 = Color.hsv(baseHue, 0.88f, 0.98f)
    val c2 = Color.hsv((baseHue + 15f) % 360f, 0.85f, 0.96f)
    val c3 = Color.hsv((baseHue - 15f + 360f) % 360f, 0.85f, 0.96f)
    val c4 = Color.hsv((baseHue + 25f) % 360f, 0.80f, 0.92f)

    // Central Glossy Bass Aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                c1.copy(alpha = 0.45f * state.bassEnergy + 0.12f),
                c2.copy(alpha = 0.28f * state.bassEnergy + 0.06f),
                c3.copy(alpha = 0.12f * state.bassEnergy),
                Color.Transparent
            ),
            center = center,
            radius = bassPulseRadius * 1.6f
        ),
        radius = bassPulseRadius * 1.6f,
        center = center
    )

    val totalSpikes = state.numBands * 2
    val angleStep = (2 * Math.PI / totalSpikes).toFloat()
    val rotationOffset = (timeSec * 0.5f) % (2 * Math.PI.toFloat())

    for (i in 0 until totalSpikes) {
        val bandIdx = if (i < state.numBands) i else (totalSpikes - 1 - i)
        val mag = state.smoothedBands[bandIdx.coerceIn(0, state.numBands - 1)]

        val angle = i * angleStep + rotationOffset
        val spikeLen = (mag * 32.dp.toPx()).coerceAtLeast(3.dp.toPx())

        val startX = center.x + cos(angle) * bassPulseRadius
        val startY = center.y + sin(angle) * bassPulseRadius
        val endX = center.x + cos(angle) * (bassPulseRadius + spikeLen)
        val endY = center.y + sin(angle) * (bassPulseRadius + spikeLen)

        val norm = i.toFloat() / totalSpikes
        val spikeColor = dynamicSpectrumColor(norm, baseHue)

        // Spike Line
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(spikeColor.copy(alpha = 0.95f), Color.White),
                start = Offset(startX, startY),
                end = Offset(endX, endY)
            ),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Glowing tip bead
        if (mag > 0.25f) {
            drawCircle(
                color = Color.White.copy(alpha = mag.coerceIn(0f, 1f)),
                radius = 2.dp.toPx(),
                center = Offset(endX, endY)
            )
        }
    }

    // Metallic Glass Animated Ring
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(c1, c2, c3, c4, c1),
            center = center
        ),
        radius = bassPulseRadius,
        center = center,
        style = Stroke(width = 2.5.dp.toPx())
    )
}

private fun DrawScope.drawNeonWaveform(
    state: VisualizerDataState,
    artBaseHue: Float,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    timeNanos: Float
) {
    val width = size.width
    val height = size.height
    val centerY = height / 2f
    val numPoints = state.numBands
    val dx = width / (numPoints - 1)

    val timeSec = timeNanos / 1_000_000_000f
    val baseHue = (artBaseHue + sin(timeSec * 0.8f) * 8f + 360f) % 360f

    val c1 = Color.hsv(baseHue, 0.88f, 0.98f)
    val c2 = Color.hsv((baseHue + 15f) % 360f, 0.85f, 0.96f)
    val c3 = Color.hsv((baseHue - 15f + 360f) % 360f, 0.85f, 0.96f)

    val path = Path()
    val fillPath = Path()

    fillPath.moveTo(0f, height)

    for (i in 0 until numPoints) {
        val mag = state.smoothedBands[i]
        val x = i * dx
        val waveAmplitude = (mag * (height * 0.40f)).coerceAtLeast(2.dp.toPx())
        val y = if (i % 2 == 0) centerY - waveAmplitude else centerY + waveAmplitude

        if (i == 0) {
            path.moveTo(x, y)
            fillPath.lineTo(x, y)
        } else {
            val prevX = (i - 1) * dx
            val prevMag = state.smoothedBands[i - 1]
            val prevY = if ((i - 1) % 2 == 0) centerY - (prevMag * (height * 0.40f)) else centerY + (prevMag * (height * 0.40f))
            val controlX1 = prevX + dx / 2f
            val controlX2 = x - dx / 2f

            path.cubicTo(controlX1, prevY, controlX2, y, x, y)
            fillPath.cubicTo(controlX1, prevY, controlX2, y, x, y)
        }
    }

    fillPath.lineTo(width, height)
    fillPath.close()

    // Glassy Dynamic Gradient Fill
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                c2.copy(alpha = 0.35f),
                c1.copy(alpha = 0.15f),
                Color.Transparent
            ),
            startY = 0f,
            endY = height
        )
    )

    // Soft Glow Stroke Behind
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(
            colors = listOf(c1, c2, c3)
        ),
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
    )

    // Crisp White/Neon Center Stroke
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.92f),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawBassParticleHalo(
    state: VisualizerDataState,
    particles: List<VisualizerParticle>,
    artBaseHue: Float,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    timeNanos: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseRadius = size.width.coerceAtMost(size.height) * 0.28f

    val timeSec = timeNanos / 1_000_000_000f
    val baseHue = (artBaseHue + sin(timeSec * 0.8f) * 8f + 360f) % 360f

    val c1 = Color.hsv(baseHue, 0.88f, 0.98f)
    val c2 = Color.hsv((baseHue + 15f) % 360f, 0.85f, 0.96f)

    // Central Glowing Core
    val coreRadius = baseRadius * (0.6f + state.bassEnergy * 0.45f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                c1.copy(alpha = 0.75f * state.bassEnergy + 0.20f),
                c2.copy(alpha = 0.35f * state.bassEnergy + 0.10f),
                Color.Transparent
            ),
            center = center,
            radius = coreRadius * 1.5f
        ),
        radius = coreRadius * 1.5f,
        center = center
    )

    // Orbiting Particles Physics & Render
    particles.forEachIndexed { i, p ->
        if (p.radius <= 0f || p.alpha <= 0.05f) {
            val angle = (i.toFloat() / particles.size) * 2 * Math.PI.toFloat() + (timeSec * 0.3f)
            val dist = baseRadius * (0.75f + (i % 5) * 0.12f)
            p.x = center.x + cos(angle) * dist
            p.y = center.y + sin(angle) * dist
            val speed = (25.dp.toPx() + state.bassEnergy * 65.dp.toPx())
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed
            p.radius = (3.dp.toPx() + (i % 4) * 1.5f.dp.toPx())
            p.alpha = 0.85f
            val pNorm = i.toFloat() / particles.size
            p.color = dynamicSpectrumColor(pNorm, baseHue)
        }

        p.x += p.vx * 0.016f
        p.y += p.vy * 0.016f
        p.alpha -= 0.014f

        // Particle Glow
        drawCircle(
            color = p.color.copy(alpha = (p.alpha * 0.45f).coerceIn(0f, 1f)),
            radius = p.radius * 1.8f,
            center = Offset(p.x, p.y)
        )
        // Particle Core
        drawCircle(
            color = Color.White.copy(alpha = p.alpha.coerceIn(0f, 1f)),
            radius = p.radius,
            center = Offset(p.x, p.y)
        )
    }
}

private fun DrawScope.drawParticleGlobeSphere(
    state: VisualizerDataState,
    artBaseHue: Float,
    timeNanos: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = (size.width.coerceAtMost(size.height) * 0.38f).coerceAtLeast(50.dp.toPx())
    val timeSec = timeNanos / 1_000_000_000f
    val baseHue = (artBaseHue + sin(timeSec * 0.25f) * 12f + 360f) % 360f

    val latSteps = 24
    val lonSteps = 48
    val rotY = timeSec * 0.35f
    val rotX = 0.4f + sin(timeSec * 0.15f) * 0.12f

    val cyanColor = Color.hsv((baseHue + 195f) % 360f, 0.95f, 1.0f)
    val magentaColor = Color.hsv((baseHue + 315f) % 360f, 0.90f, 0.95f)

    // 1. Deep Volumetric Nebula Atmosphere Bloom
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cyanColor.copy(alpha = 0.40f * state.bassEnergy + 0.15f),
                magentaColor.copy(alpha = 0.30f * state.bassEnergy + 0.10f),
                Color.Transparent
            ),
            center = center,
            radius = radius * 2.1f
        ),
        radius = radius * 2.1f,
        center = center
    )

    // 2. Ambient Space Dust Floating Particles around the Globe
    for (i in 0..35) {
        val dustAngle = (i * 1.7f) + timeSec * 0.2f
        val dustDist = radius * (1.1f + (i % 7) * 0.12f)
        val dx = center.x + cos(dustAngle.toDouble()).toFloat() * dustDist
        val dy = center.y + sin((dustAngle * 0.8f).toDouble()).toFloat() * (dustDist * 0.7f)
        val dustAlpha = (0.2f + (i % 5) * 0.15f).coerceIn(0.1f, 0.8f)
        drawCircle(
            color = (if (i % 2 == 0) cyanColor else magentaColor).copy(alpha = dustAlpha),
            radius = (0.8f + (i % 3) * 0.6f).dp.toPx(),
            center = Offset(dx, dy)
        )
    }

    // 3. Specular Lens Flare on Top-Right Rim
    val flareCenter = Offset(center.x + radius * 0.62f, center.y - radius * 0.62f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.92f * (0.6f + state.bassEnergy * 0.4f)),
                cyanColor.copy(alpha = 0.45f),
                Color.Transparent
            ),
            center = flareCenter,
            radius = radius * 0.45f
        ),
        radius = radius * 0.45f,
        center = flareCenter
    )

    // 4. High-Density 3D Holographic Particle Matrix Sphere
    for (lat in 0..latSteps) {
        val latAngle = (lat.toFloat() / latSteps - 0.5f) * Math.PI.toFloat()
        val cosLat = cos(latAngle.toDouble()).toFloat()
        val sinLat = sin(latAngle.toDouble()).toFloat()

        for (lon in 0 until lonSteps) {
            val lonAngle = (lon.toFloat() / lonSteps) * 2f * Math.PI.toFloat() + rotY
            val bandIdx = (lat * 2 + lon) % state.numBands
            val mag = state.smoothedBands[bandIdx]

            // Audio reactive wave displacement with bass punch
            val waveMod = sin((timeSec * 3.5f + lat * 0.4f + lon * 0.3f).toDouble()).toFloat()
            val r = radius * (1.0f + mag * 0.45f + state.bassEnergy * 0.25f * waveMod)

            // 3D Cartesian coordinates
            val x3d = r * cosLat * cos(lonAngle.toDouble()).toFloat()
            val y3d = r * sinLat
            val z3d = r * cosLat * sin(lonAngle.toDouble()).toFloat()

            // Rotate around X axis
            val cosX = cos(rotX.toDouble()).toFloat()
            val sinX = sin(rotX.toDouble()).toFloat()
            val yRot = y3d * cosX - z3d * sinX
            val zRot = y3d * sinX + z3d * cosX

            // Perspective projection
            val perspective = 450f / (450f + zRot)
            val screenX = center.x + x3d * perspective
            val screenY = center.y + yRot * perspective

            if (zRot > -220f) {
                // Dual-hemisphere lighting: Lit side (right/cyan) vs Shaded side (left/magenta)
                val depthFactor = ((zRot + 220f) / 440f).coerceIn(0.12f, 1.0f)
                val nodeColor = if (x3d > 0f) cyanColor else magentaColor
                val dotSize = (1.0f + mag * 3.8f + depthFactor * 1.8f).dp.toPx()

                // Outer Glow Halo
                drawCircle(
                    color = nodeColor.copy(alpha = (depthFactor * 0.55f).coerceIn(0f, 1f)),
                    radius = dotSize * 1.8f,
                    center = Offset(screenX, screenY)
                )
                // Crisp White-Hot Core
                drawCircle(
                    color = Color.White.copy(alpha = depthFactor.coerceIn(0f, 1f)),
                    radius = dotSize,
                    center = Offset(screenX, screenY)
                )
            }
        }
    }
}

private fun DrawScope.drawWaveGridTerrain(
    state: VisualizerDataState,
    artBaseHue: Float,
    timeNanos: Float
) {
    val width = size.width
    val height = size.height
    val timeSec = timeNanos / 1_000_000_000f
    val baseHue = (artBaseHue + 360f) % 360f

    val horizonY = height * 0.38f
    val bottomY = height * 0.98f
    val rows = 18
    val cols = 26

    val cyanColor = Color.hsv((baseHue + 195f) % 360f, 0.95f, 1.0f)
    val magentaColor = Color.hsv((baseHue + 315f) % 360f, 0.90f, 0.95f)

    // 1. Glowing Synthwave Horizon Sky & Sun Gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Black,
                magentaColor.copy(alpha = 0.35f),
                cyanColor.copy(alpha = 0.30f),
                Color.Transparent
            ),
            startY = 0f,
            endY = horizonY
        )
    )

    // 2. Perspective Horizontal Grid Lines
    for (r in 0..rows) {
        val rowNorm = r.toFloat() / rows
        val y = horizonY + (bottomY - horizonY) * (rowNorm * rowNorm) // Non-linear perspective spacing
        val alpha = (rowNorm * 0.85f + 0.15f).coerceIn(0.1f, 0.95f)

        val path = Path()
        for (c in 0 until cols) {
            val colNorm = c.toFloat() / (cols - 1)
            val perspectiveSpread = width * (0.05f + 2.1f * rowNorm)
            val centerX = width / 2f
            val x = centerX + (colNorm - 0.5f) * perspectiveSpread

            val bandIdx = (c + r * 2) % state.numBands
            val mag = state.smoothedBands[bandIdx]
            val wave = sin((timeSec * 4.5f + c * 0.35f + r * 0.55f).toDouble()).toFloat()
            val zOffset = (mag * 55f + state.bassEnergy * 40f) * wave * rowNorm

            val screenX = x
            val screenY = y - zOffset

            if (c == 0) path.moveTo(screenX, screenY) else path.lineTo(screenX, screenY)
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(magentaColor.copy(alpha = alpha), cyanColor.copy(alpha = alpha))
            ),
            style = Stroke(width = (1.2f + rowNorm * 1.8f).dp.toPx(), cap = StrokeCap.Round)
        )
    }

    // 3. Perspective Vertical Depth Lines
    for (c in 0 until cols) {
        val colNorm = c.toFloat() / (cols - 1)
        val path = Path()

        for (r in 0..rows) {
            val rowNorm = r.toFloat() / rows
            val y = horizonY + (bottomY - horizonY) * (rowNorm * rowNorm)
            val perspectiveSpread = width * (0.05f + 2.1f * rowNorm)
            val centerX = width / 2f
            val x = centerX + (colNorm - 0.5f) * perspectiveSpread

            val bandIdx = (c + r * 2) % state.numBands
            val mag = state.smoothedBands[bandIdx]
            val wave = sin((timeSec * 4.5f + c * 0.35f + r * 0.55f).toDouble()).toFloat()
            val zOffset = (mag * 55f + state.bassEnergy * 40f) * wave * rowNorm

            val screenX = x
            val screenY = y - zOffset

            if (r == 0) path.moveTo(screenX, screenY) else path.lineTo(screenX, screenY)
        }

        drawPath(
            path = path,
            color = cyanColor.copy(alpha = 0.50f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawParticleTunnelVortex(
    state: VisualizerDataState,
    artBaseHue: Float,
    timeNanos: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val timeSec = timeNanos / 1_000_000_000f
    val baseHue = (artBaseHue + 360f) % 360f

    val rings = 16
    val pointsPerRing = 32
    val maxRadius = size.width.coerceAtMost(size.height) * 0.70f

    val cyanColor = Color.hsv((baseHue + 195f) % 360f, 0.95f, 1.0f)
    val magentaColor = Color.hsv((baseHue + 315f) % 360f, 0.90f, 0.95f)

    // 1. Infinite Center Suction Core Glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.9f * state.bassEnergy + 0.4f),
                cyanColor.copy(alpha = 0.5f),
                magentaColor.copy(alpha = 0.2f),
                Color.Transparent
            ),
            center = center,
            radius = maxRadius * 0.30f
        ),
        radius = maxRadius * 0.30f,
        center = center
    )

    // 2. Swirling Perspective Vortex Rings
    for (ring in 0 until rings) {
        val ringProgress = ((ring + (timeSec * 1.5f)) % rings) / rings.toFloat()
        // Logarithmic exponential scaling for true 3D tunnel depth illusion
        val radius = maxRadius * (ringProgress * ringProgress * 0.9f + 0.1f) * (1f + state.bassEnergy * 0.30f)
        val alpha = (ringProgress * 1.3f).coerceIn(0.05f, 0.98f)

        val angleOffset = timeSec * 0.75f * (if (ring % 2 == 0) 1f else -1f)

        for (p in 0 until pointsPerRing) {
            val angle = (p.toFloat() / pointsPerRing) * 2f * Math.PI.toFloat() + angleOffset
            val bandIdx = (ring * 2 + p) % state.numBands
            val mag = state.smoothedBands[bandIdx]

            val r = radius * (1f + mag * 0.35f)
            val x = center.x + cos(angle.toDouble()).toFloat() * r
            val y = center.y + sin(angle.toDouble()).toFloat() * r

            val particleColor = if (p % 2 == 0) cyanColor else magentaColor
            val particleSize = (0.8f + ringProgress * 4.5f + mag * 3.5f).dp.toPx()

            // Outer Glow
            drawCircle(
                color = particleColor.copy(alpha = alpha * 0.65f),
                radius = particleSize * 1.8f,
                center = Offset(x, y)
            )
            // Crisp Core
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = particleSize * 0.75f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun StyleSelectorBar(
    currentStyle: VisualizerStyle,
    onSelectStyle: (VisualizerStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.60f),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisualizerStyleItem(
                icon = Icons.Default.BarChart,
                label = "Bars",
                isSelected = currentStyle == VisualizerStyle.GLOSSY_SPECTRUM_BARS,
                onClick = { onSelectStyle(VisualizerStyle.GLOSSY_SPECTRUM_BARS) }
            )
            VisualizerStyleItem(
                icon = Icons.Default.Lens,
                label = "Ring",
                isSelected = currentStyle == VisualizerStyle.CIRCULAR_RING_WAVE,
                onClick = { onSelectStyle(VisualizerStyle.CIRCULAR_RING_WAVE) }
            )
            VisualizerStyleItem(
                icon = Icons.Default.Waves,
                label = "Wave",
                isSelected = currentStyle == VisualizerStyle.NEON_WAVEFORM,
                onClick = { onSelectStyle(VisualizerStyle.NEON_WAVEFORM) }
            )
            VisualizerStyleItem(
                icon = Icons.Default.Grain,
                label = "Particles",
                isSelected = currentStyle == VisualizerStyle.BASS_PARTICLE_HALO,
                onClick = { onSelectStyle(VisualizerStyle.BASS_PARTICLE_HALO) }
            )
            VisualizerStyleItem(
                icon = Icons.Default.Lens,
                label = "Globe",
                isSelected = currentStyle == VisualizerStyle.PARTICLE_GLOBE_SPHERE,
                onClick = { onSelectStyle(VisualizerStyle.PARTICLE_GLOBE_SPHERE) }
            )
            VisualizerStyleItem(
                icon = Icons.Default.Waves,
                label = "Terrain",
                isSelected = currentStyle == VisualizerStyle.WAVE_GRID_TERRAIN,
                onClick = { onSelectStyle(VisualizerStyle.WAVE_GRID_TERRAIN) }
            )
            VisualizerStyleItem(
                icon = Icons.Default.Grain,
                label = "Vortex",
                isSelected = currentStyle == VisualizerStyle.PARTICLE_TUNNEL_VORTEX,
                onClick = { onSelectStyle(VisualizerStyle.PARTICLE_TUNNEL_VORTEX) }
            )
        }
    }
}

@Composable
private fun VisualizerStyleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.Black else Color.White,
                modifier = Modifier.size(16.dp)
            )
            if (isSelected) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = Color.Black
                )
            }
        }
    }
}
