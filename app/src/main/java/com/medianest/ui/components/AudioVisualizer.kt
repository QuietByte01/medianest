package com.medianest.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.media.audiofx.Visualizer
import android.net.Uri
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class VisualizerStyle {
    GLOSSY_SPECTRUM_BARS,
    CIRCULAR_RING_WAVE,
    NEON_WAVEFORM,
    BASS_PARTICLE_HALO,
    DOT_MATRIX_BARS,
    INDIGO_HYPERSPACE,
    PARTICLE_GLOBE_SPHERE,
    WAVE_GRID_TERRAIN,
    ENERGY_PARTICLES;

    fun is3D(): Boolean = this == INDIGO_HYPERSPACE ||
            this == PARTICLE_GLOBE_SPHERE ||
            this == WAVE_GRID_TERRAIN
}

class VisualizerDataState(val numBands: Int = 256) {
    val rawFFT = FloatArray(numBands)
    val smoothedBands = FloatArray(numBands)
    val peakCaps = FloatArray(numBands)
    val peakVelocities = FloatArray(numBands)
    var bassEnergy = 0f
    var midEnergy = 0f
    var trebleEnergy = 0f
    var overallAmplitude = 0f
    val artBaseHue = mutableFloatStateOf(210f)
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
            val hueSums = FloatArray(12) // Tracks the exact hues for averaging
            var count = 0

            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                android.graphics.Color.RGBToHSV(r, g, b, hsv)

                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                // STRICT FILTER: Ignore darks (<15% brightness) and neutrals (<15% saturation)
                if (value > 0.15f && sat > 0.15f) {
                    val bin = ((hue / 30f).toInt()) % 12
                    val weight = (sat * value) + 0.1f

                    hueBins[bin] += weight
                    hueSums[bin] += hue * weight // Weight the exact hue
                    count++
                }
            }

            // If the image was entirely black/white/gray, return null
            if (count == 0) return@withContext null

            var bestBin = 0
            var maxWeight = -1f

            for (i in 0 until 12) {
                if (hueBins[i] > maxWeight) {
                    maxWeight = hueBins[i]
                    bestBin = i
                }
            }

            // RETURN OPTION 1: The exact weighted average hue of the dominant color
            return@withContext hueSums[bestBin] / hueBins[bestBin]

            // RETURN OPTION 2: If you prefer the 12 "locked" colors, use your original return:
            // return@withContext bestBin * 30f + 15f

        } catch (_: Exception) {
            null
        }
    }
}

class AudioVisualizerGLSurfaceView(
    context: Context,
    private val styleProvider: () -> VisualizerStyle
) : FrameLayout(context) {

    private val glView = GLSurfaceView(context)
    private val renderer: AudioVisualizerRenderer
    private val canvasView = AudioVisualizer2DView(context)

    init {
        glView.setEGLContextClientVersion(3)
        renderer = AudioVisualizerRenderer(styleProvider)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        glView.isClickable = false
        glView.isFocusable = false

        addView(glView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        canvasView.setWillNotDraw(false)
        canvasView.setOpaqueBackground(false)
        updateMode(styleProvider())
    }

    private var lastStyle: VisualizerStyle? = null

    fun updateAudioData(
        bassEnergy: Float, midEnergy: Float, trebleEnergy: Float,
        amplitude: Float, timeSec: Float, bands: FloatArray, artBaseHue: Float, peakCaps: FloatArray
    ) {
        renderer.updateAudio(bassEnergy, midEnergy, trebleEnergy, amplitude, timeSec, bands)
        renderer.updatePeakCaps(peakCaps)
        canvasView.updateAudio(bassEnergy, midEnergy, trebleEnergy, amplitude, timeSec, bands, artBaseHue, renderer.currentPeakCaps())

        val currentStyle = styleProvider()
        if (currentStyle != lastStyle) {
            updateMode(currentStyle)
            lastStyle = currentStyle
        }
    }

    fun getTagStyle(): VisualizerStyle = styleProvider()

    fun setFullscreenBackground(enabled: Boolean) {
        val bg = if (enabled) android.graphics.Color.rgb(2, 1, 8) else android.graphics.Color.TRANSPARENT
        setBackgroundColor(bg)
        canvasView.setOpaqueBackground(enabled)
        invalidate()
    }

    private fun updateMode(style: VisualizerStyle) {
        val is3D = style.is3D()
        // Always keep canvas visible so it can draw background in fullscreen if needed
        canvasView.visibility = View.VISIBLE
        glView.visibility = if (is3D) View.VISIBLE else View.GONE
    }
}

private data class HaloParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radius: Float = 0f,
    var alpha: Float = 0f,
    var norm: Float = 0f
)

private data class EnergyParticle(
    var angle: Float,
    var radius: Float,
    var speed: Float,
    var size: Float
)

private class AudioVisualizer2DView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val bands = FloatArray(256)
    private val boostedBands = FloatArray(256)
    private val peakCaps = FloatArray(256)
    private val peaks = FloatArray(256)
    private val haloParticles = Array(32) { HaloParticle() }
    private val energyParticles = Array(80) {
        EnergyParticle(
            angle = Math.random().toFloat() * (Math.PI * 2.0).toFloat(),
            radius = Math.random().toFloat() * 150f,
            speed = Math.random().toFloat() * 2f + 0.5f,
            size = Math.random().toFloat() * 3f + 1f
        )
    }
    private var bass = 0f
    private var mid = 0f
    private var treble = 0f
    private var amplitude = 0f
    private var timeSec = 0f
    private var hue = 210f
    private var lastTime = 0f
    private var initialized = false
    private var opaqueBackground = false

    fun setOpaqueBackground(enabled: Boolean) {
        opaqueBackground = enabled
        invalidate()
    }

    fun updateAudio(
        bassEnergy: Float, midEnergy: Float, trebleEnergy: Float,
        amplitudeEnergy: Float, time: Float, audioBands: FloatArray, artBaseHue: Float, peakCaps: FloatArray? = null
    ) {
        bass = bassEnergy.coerceIn(0f, 1f)
        mid = midEnergy.coerceIn(0f, 1f)
        treble = trebleEnergy.coerceIn(0f, 1f)
        amplitude = amplitudeEnergy.coerceIn(0f, 1f)
        timeSec = time
        hue = if (artBaseHue.isFinite()) artBaseHue else 210f

        if (audioBands.size >= 256) {
            System.arraycopy(audioBands, 0, bands, 0, 256)
        } else if (audioBands.isNotEmpty()) {
            for (i in bands.indices) {
                val src = ((i.toFloat() / 255f) * audioBands.lastIndex).toInt().coerceIn(0, audioBands.lastIndex)
                bands[i] = audioBands[src]
            }
        }

        // NOISE FLOOR REMOVED. Applying aggressive square root multiplier
        // to ensure even microscopic audio signals display massive jumps visually.
        for (i in boostedBands.indices) {
            val raw = bands[i].coerceIn(0f, 1f)
            boostedBands[i] = (kotlin.math.sqrt(raw) * 1.5f).coerceIn(0f, 1f)
        }

        if (peakCaps != null) {
            if (peakCaps.size >= 256) System.arraycopy(peakCaps, 0, peaks, 0, 256)
            else if (peakCaps.isNotEmpty()) for (i in peaks.indices) {
                val src = ((i.toFloat() / 255f) * peakCaps.lastIndex).toInt().coerceIn(0, peakCaps.lastIndex)
                peaks[i] = peakCaps[src]
            }

            // Sync the peak scaling curve perfectly with the bars
            for (i in peaks.indices) {
                val raw = peaks[i].coerceIn(0f, 1f)
                peaks[i] = (kotlin.math.sqrt(raw) * 1.5f).coerceIn(0f, 1f)
            }
        }

        initialized = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dt = if (lastTime == 0f) 0.016f else (timeSec - lastTime).coerceIn(0f, 0.05f)
        lastTime = timeSec

        val style = currentStyle()
        val is3D = style.is3D()

        if (opaqueBackground && !is3D) {
            canvas.drawColor(android.graphics.Color.rgb(2, 1, 8))
        } else {
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        }
        val size = minOf(width, height).toFloat()
        if (size <= 1f) return
        val left = (width - size) * 0.5f
        val top = (height - size) * 0.5f
        canvas.save()
        canvas.translate(left, top)
        when (currentStyle()) {
            VisualizerStyle.GLOSSY_SPECTRUM_BARS -> drawBars(canvas, size)
            VisualizerStyle.CIRCULAR_RING_WAVE -> drawRing(canvas, size)
            VisualizerStyle.NEON_WAVEFORM -> drawWave(canvas, size)
            VisualizerStyle.BASS_PARTICLE_HALO -> drawHalo(canvas, size, dt)
            VisualizerStyle.DOT_MATRIX_BARS -> drawDotMatrix(canvas, size)
            VisualizerStyle.ENERGY_PARTICLES -> drawEnergy(canvas, size, dt)
            else -> Unit
        }
        canvas.restore()
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

    private fun currentStyle(): VisualizerStyle = (parent as? AudioVisualizerGLSurfaceView)?.let { it.getTagStyle() } ?: VisualizerStyle.GLOSSY_SPECTRUM_BARS

    private fun rgba(h: Float, s: Float, l: Float, a: Float): Int {
        val c = hslToRgb(h, s, l)
        return android.graphics.Color.argb((a.coerceIn(0f,1f)*255f).toInt(), (c.x*255f).toInt(), (c.y*255f).toInt(), (c.z*255f).toInt())
    }

    private data class Vec3f(val x: Float, val y: Float, val z: Float)
    private fun hslToRgb(hueDeg: Float, satPct: Float, lightPct: Float): Vec3f {
        val h = ((hueDeg % 360f) + 360f) % 360f / 360f
        val s = satPct / 100f
        val l = lightPct / 100f
        if (s == 0f) return Vec3f(l,l,l)
        val q = if (l < 0.5f) l * (1f + s) else l + s - l*s
        val p = 2f*l-q
        fun hue2rgb(t0: Float): Float {
            var t=t0
            if(t<0f)t+=1f; if(t>1f)t-=1f
            return when { t < 1f/6f -> p+(q-p)*6f*t; t < 1f/2f -> q; t < 2f/3f -> p+(q-p)*(2f/3f-t)*6f; else -> p }
        }
        return Vec3f(hue2rgb(h+1f/3f), hue2rgb(h), hue2rgb(h-1f/3f))
    }

    private fun boostedBand(index: Int): Float {
        return boostedBands[index.coerceIn(0, 255)].coerceIn(0f, 1f)
    }

    // Returns real boosted audio when present; falls back to a time-based sine wave
    // so 2D visualizers always animate even when RECORD_AUDIO is denied or Visualizer
    // is not yet attached. Consistent with GL shaders which self-animate via u_Time.
    private fun activeBand(index: Int): Float {
        val real = boostedBand(index)
        return if (real > 0.005f) real
        else {
            val freq = 1.1f + index * 0.045f
            val phase = index * 0.38f
            val raw = kotlin.math.sin((timeSec * freq + phase).toDouble()).toFloat() * 0.5f + 0.5f
            raw * (0.15f + 0.25f * kotlin.math.abs(kotlin.math.sin((timeSec * 0.35f + index * 0.12f).toDouble()).toFloat()))
        }
    }

    // Helper to generate the smooth synthetic sine-wave envelope (the "paused" look)
    private fun getSyntheticWave(index: Int): Float {
        val freq = 1.1f + index * 0.045f
        val phase = index * 0.38f
        val raw = kotlin.math.sin((timeSec * freq + phase).toDouble()).toFloat() * 0.5f + 0.5f
        return raw * (0.15f + 0.25f * kotlin.math.abs(kotlin.math.sin((timeSec * 0.35f + index * 0.12f).toDouble()).toFloat()))
    }

    // New function specifically for Bars, Dots, and Wave to give them the
    // smooth "paused" wavy look but highly reactive to live music.
    private fun wavyReactBand(index: Int): Float {
        val real = boostedBand(index)
        val synthetic = getSyntheticWave(index)

        return if (real > 0.005f) {
            // Modulate the smooth synthetic wave with the real audio data.
            // It uses the curvy shape of the synthetic wave as a base envelope,
            // but scales its height and intensity dynamically with the live music.
            (synthetic * (0.5f + real * 2.5f) + (real * 0.15f)).coerceIn(0f, 1f)
        } else {
            synthetic
        }
    }

    private fun drawBars(c: Canvas, s: Float) {
        val spacing = 4f
        val barW = (s - spacing * 31f) / 32f
        val bhue = (hue + kotlin.math.sin(timeSec * 0.8f) * 8f + 360f) % 360f

        for (i in 0 until 32) {
            val idx = (i * 8).coerceIn(0, 255)
            // Uses the new blended wavy band for the beautiful rolling bar shape
            val v = wavyReactBand(idx)
            val barH = maxOf(4f, s * v * 0.88f)
            val x = i * (barW + spacing)
            val top = s - barH
            val hueI = (bhue + (i / 32f - 0.5f) * 40f + 360f) % 360f

            val grad = LinearGradient(
                0f, top, 0f, s,
                intArrayOf(
                    android.graphics.Color.WHITE,
                    rgba(hueI, 88f, 65f, 1f),
                    rgba(hueI, 88f, 65f, 0.25f)
                ),
                floatArrayOf(0f, 0.1f, 1f),
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = grad
            c.drawRoundRect(RectF(x, top, x + barW, s), barW / 2f, barW / 2f, fillPaint)
            fillPaint.shader = null

            // Note: Bouncing peak dots have been intentionally removed from here.
        }
    }

    private fun drawRing(c: Canvas, s: Float) {
        val cx = s / 2f
        val cy = s / 2f
        val baseRadius = maxOf(20f, kotlin.math.min(s, s) * 0.25f)
        val bassPulseRadius = baseRadius + bass * 25f
        val baseHue = (hue + kotlin.math.sin(timeSec * 0.8f) * 8f + 360f) % 360f

        val radialGrad = RadialGradient(
            cx, cy, bassPulseRadius * 1.6f,
            intArrayOf(
                rgba(baseHue, 88f, 80f, 0.45f * bass + 0.12f),
                rgba((baseHue + 15f) % 360f, 85f, 70f, 0.28f * bass + 0.06f),
                rgba((baseHue - 15f + 360f) % 360f, 85f, 70f, 0.12f * bass),
                android.graphics.Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = radialGrad
        c.drawRect(0f, 0f, s, s, fillPaint)
        fillPaint.shader = null

        val totalSpikes = 64
        val angleStep = (Math.PI * 2.0 / totalSpikes).toFloat()
        val rotationOffset = (timeSec * 0.5f) % (Math.PI * 2f).toFloat()
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 2.5f
        strokePaint.strokeCap = Paint.Cap.ROUND

        for (i in 0 until totalSpikes) {
            val bandIdx = if (i < 32) i else (63 - i)
            // Left unchanged as requested (Ring remains pure FFT shape)
            val mag = activeBand(bandIdx)
            val angle = i * angleStep + rotationOffset
            val spikeLen = maxOf(3f, mag * 50f)
            val sx = cx + kotlin.math.cos(angle) * bassPulseRadius
            val sy = cy + kotlin.math.sin(angle) * bassPulseRadius
            val ex = cx + kotlin.math.cos(angle) * (bassPulseRadius + spikeLen)
            val ey = cy + kotlin.math.sin(angle) * (bassPulseRadius + spikeLen)
            val spikeHue = (baseHue + (bandIdx / 31f - 0.5f) * 40f + 360f) % 360f

            strokePaint.shader = LinearGradient(
                sx, sy, ex, ey,
                rgba(spikeHue, 88f, 70f, 0.95f),
                android.graphics.Color.WHITE,
                Shader.TileMode.CLAMP
            )
            c.drawLine(sx, sy, ex, ey, strokePaint)
            strokePaint.shader = null
        }

        strokePaint.color = rgba(baseHue, 88f, 70f, 1f)
        strokePaint.strokeWidth = 5f
        c.drawCircle(cx, cy, bassPulseRadius, strokePaint)

        strokePaint.color = android.graphics.Color.argb(235, 255, 255, 255)
        strokePaint.strokeWidth = 1.5f
        c.drawCircle(cx, cy, bassPulseRadius - 2f, strokePaint)
    }

    private fun drawWave(c: Canvas, s: Float) {
        val cy = s / 2f
        val dx = s / 31f
        val pts = Array(32) { i ->
            val idx = (i * 4).coerceIn(0, 255)
            // Uses the new blended wavy band
            val amp = maxOf(2f, wavyReactBand(idx) * (s * 0.4f))
            android.graphics.PointF(i * dx, if (i % 2 == 0) cy - amp else cy + amp)
        }

        path.reset()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until 32) {
            val cp1x = pts[i - 1].x + dx / 2f
            val cp2x = pts[i].x - dx / 2f
            path.cubicTo(cp1x, pts[i - 1].y, cp2x, pts[i].y, pts[i].x, pts[i].y)
        }

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeWidth = 6f
        strokePaint.color = rgba(hue, 88f, 70f, 1f)
        c.drawPath(path, strokePaint)
        strokePaint.strokeWidth = 2f
        strokePaint.color = android.graphics.Color.WHITE
        c.drawPath(path, strokePaint)
    }

    private fun drawHalo(c: Canvas, s: Float, dt: Float) {
        val cx=s/2f; val cy=s/2f; val baseR=s*0.25f; val bh=(hue+kotlin.math.sin(timeSec*0.8f)*8f+360f)%360f
        val coreR=baseR*(0.6f+bass*0.45f)
        val grad=RadialGradient(cx,cy,coreR*1.5f,intArrayOf(android.graphics.Color.WHITE,rgba(bh,88f,70f,0.75f*bass+0.20f),rgba((bh+15f)%360f,85f,70f,0.35f*bass+0.10f),android.graphics.Color.TRANSPARENT),floatArrayOf(0f,0.3f,0.6f,1f),Shader.TileMode.CLAMP)
        fillPaint.shader=grad; c.drawCircle(cx,cy,coreR*1.5f,fillPaint); fillPaint.shader=null
        for((i,p) in haloParticles.withIndex()){
            if(p.radius<=0f||p.alpha<=0.05f){
                val angle=(i/32f)*(Math.PI*2)+timeSec*0.3f; val dist=baseR*(0.75f+(i%5)*0.12f)
                p.x=cx+kotlin.math.cos(angle).toFloat()*dist; p.y=cy+kotlin.math.sin(angle).toFloat()*dist
                val speed=30f+bass*80f; p.vx=kotlin.math.cos(angle).toFloat()*speed; p.vy=kotlin.math.sin(angle).toFloat()*speed
                p.radius=3f+(i%4)*1.5f; p.alpha=0.85f; p.norm=i/32f
            }
            p.x+=p.vx*dt; p.y+=p.vy*dt; p.alpha-=0.8f*dt
            val ph=(bh+(p.norm-0.5f)*40f+360f)%360f
            fillPaint.color=rgba(ph,88f,70f,p.alpha*0.45f); c.drawCircle(p.x,p.y,p.radius*1.8f,fillPaint)
            fillPaint.color=android.graphics.Color.argb((p.alpha*255f).toInt(),255,255,255); c.drawCircle(p.x,p.y,p.radius,fillPaint)
        }
    }

    private fun drawDotMatrix(c: Canvas, s: Float) {
        val cols = 32
        val rows = 16
        val bw = s / cols
        val bh = s / rows
        for (i in 0 until cols) {
            // Uses the new blended wavy band for the flowing dotted wave look
            val fft = wavyReactBand(((i / cols.toFloat()) * 256f).toInt().coerceIn(0, 255))
            val activeRows = (fft * rows + bass * 2f).toInt().coerceIn(0, rows)
            fillPaint.color = rgba(hue + i * 2f, 80f, 60f, 1f)
            for (j in 0 until activeRows) {
                c.drawCircle(
                    i * bw + bw / 2f,
                    s - (j * bh + bh / 2f),
                    bw / 2.5f,
                    fillPaint
                )
            }
        }
    }

    private fun drawEnergy(c: Canvas, s: Float, dt: Float) {
        val cx=s/2f; val cy=s/2f; val maxR=maxOf(s,s)*0.62f; val push=if(bass>0.65f) bass*18f else 0f
        for((i,p) in energyParticles.withIndex()){
            p.radius+=(p.speed+push)*(dt*60f); p.angle+=0.008f+mid*0.002f
            if(p.radius>maxR){p.radius=(Math.random()*55).toFloat();p.angle=(Math.random()*Math.PI*2).toFloat();p.speed=(Math.random()*2+0.5).toFloat();p.size=(Math.random()*3+1).toFloat()}
            val x=cx+kotlin.math.cos(p.angle)*p.radius; val y=cy+kotlin.math.sin(p.angle)*p.radius; val fade=(1f-p.radius/maxR).coerceAtLeast(0f)
            val hh=135f+(i%20)*3.2f; val rr=p.size+bass*3.5f
            fillPaint.color=rgba(hh,88f,62f,fade); c.drawCircle(x,y,rr,fillPaint)
            if(p.radius< s*0.45f){ strokePaint.color=android.graphics.Color.argb(((0.05f+bass*0.10f)*255f).toInt(),80,255,190); strokePaint.strokeWidth=1f; c.drawLine(cx,cy,x,y,strokePaint) }
        }
        val core=RadialGradient(cx,cy, s*0.28f,intArrayOf(android.graphics.Color.argb(((0.18f+bass*0.20f)*255f).toInt(),210,255,235),android.graphics.Color.argb(((0.08f+bass*0.12f)*255f).toInt(),70,255,180),android.graphics.Color.TRANSPARENT),floatArrayOf(0f,0.35f,1f),Shader.TileMode.CLAMP)
        fillPaint.shader=core; c.drawCircle(cx,cy,s*0.28f,fillPaint); fillPaint.shader=null
    }
}

class AudioVisualizerRenderer(
    private val styleProvider: () -> VisualizerStyle
) : GLSurfaceView.Renderer {
    private var programHyperspace = 0
    private var programOrb = 0
    private var programTerrain = 0
    private var orbVao = 0
    private var terrainVao = 0
    private var simpleVao = 0
    private var orbVbo = 0
    private var terrainVbo = 0
    private var simpleVbo = 0
    private var orbCount = 0
    private val terrainPtsPerLine = 120
    private val terrainLinesCount = 80
    private val fftTextureId = intArrayOf(0)
    private val fftByteBuffer = ByteBuffer.allocateDirect(256).order(ByteOrder.nativeOrder())
    private val bands = FloatArray(256)
    private val peakCaps = FloatArray(256)
    private var bassEnergy=0f; private var midEnergy=0f; private var trebleEnergy=0f; private var amplitude=0f; private var timeSec=0f
    private var surfaceWidth=1; private var surfaceHeight=1
    private val projectionMatrix=FloatArray(16); private val viewMatrix=FloatArray(16); private val modelMatrix=FloatArray(16); private val mvpMatrix=FloatArray(16)

    fun updateAudio(bass:Float, mid:Float, treble:Float, amp:Float, time:Float, audioBands:FloatArray){
        bassEnergy=bass; midEnergy=mid; trebleEnergy=treble; amplitude=amp; timeSec=time
        if(audioBands.size>=256) System.arraycopy(audioBands,0,bands,0,256) else if(audioBands.isNotEmpty()) for(i in bands.indices){val src=((i.toFloat()/255f)*audioBands.lastIndex).toInt().coerceIn(0,audioBands.lastIndex); bands[i]=audioBands[src]}
    }
    fun updatePeakCaps(source: FloatArray) {
        if (source.size >= 256) System.arraycopy(source, 0, peakCaps, 0, 256)
        else if (source.isNotEmpty()) for (i in peakCaps.indices) {
            val src = ((i.toFloat() / 255f) * source.lastIndex).toInt().coerceIn(0, source.lastIndex)
            peakCaps[i] = source[src]
        }
    }

    fun currentPeakCaps(): FloatArray = peakCaps

    override fun onSurfaceCreated(gl:GL10?, config:EGLConfig?){
        GLES30.glClearColor(0.003f,0.001f,0.010f,1f); GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE)
        programHyperspace=createProgram(VERTEX_SHADER_HYPERSPACE,FRAGMENT_SHADER_HYPERSPACE)
        programOrb=createProgram(VERTEX_SHADER_ORB,FRAGMENT_SHADER_ORB)
        programTerrain=createProgram(VERTEX_SHADER_TERRAIN,FRAGMENT_SHADER_TERRAIN)
        setupGeometryBuffers(); setupFFTTexture()
    }
    private fun setupFFTTexture(){
        GLES30.glGenTextures(1,fftTextureId,0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,fftTextureId[0]);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_R8,256,1,0,GLES30.GL_RED,GLES30.GL_UNSIGNED_BYTE,null)
    }
    override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int){ surfaceWidth=width.coerceAtLeast(1); surfaceHeight=height.coerceAtLeast(1); GLES30.glViewport(0,0,surfaceWidth,surfaceHeight); Matrix.perspectiveM(projectionMatrix,0,50f,surfaceWidth.toFloat()/surfaceHeight.toFloat(),0.1f,100f) }
    override fun onDrawFrame(gl:GL10?){
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT); fftByteBuffer.clear(); for(b in bands) fftByteBuffer.put((b.coerceIn(0f,1f)*255f).toInt().toByte()); fftByteBuffer.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,fftTextureId[0]); GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D,0,0,0,256,1,GLES30.GL_RED,GLES30.GL_UNSIGNED_BYTE,fftByteBuffer)
        when(styleProvider()){
            VisualizerStyle.INDIGO_HYPERSPACE -> drawHyperspace()
            VisualizerStyle.PARTICLE_GLOBE_SPHERE -> drawOrb()
            VisualizerStyle.WAVE_GRID_TERRAIN -> drawTerrain()
            else -> Unit
        }
    }
    private fun setupGeometryBuffers(){
        val vaos=IntArray(3); val vbos=IntArray(3); GLES30.glGenVertexArrays(3,vaos,0); GLES30.glGenBuffers(3,vbos,0); simpleVao=vaos[0]; orbVao=vaos[1]; terrainVao=vaos[2]; simpleVbo=vbos[0]; orbVbo=vbos[1]; terrainVbo=vbos[2]
        val simpleVerts=FloatArray(1024*3){idx->if(idx%3==0)(idx/3).toFloat() else 0f}; bindVaoVbo(simpleVao,simpleVbo,createFloatBuffer(simpleVerts),simpleVerts.size*4)
        val orbVerts=ArrayList<Float>(); val latSteps=240; val lonSteps=480
        for(lat in 0..latSteps){ val th=lat*Math.PI/latSteps; val st=sin(th).toFloat(); val ct=cos(th).toFloat(); for(lon in 0..lonSteps){ val ph=lon*2*Math.PI/lonSteps; orbVerts.add(st*cos(ph).toFloat()); orbVerts.add(ct); orbVerts.add(st*sin(ph).toFloat()) } }
        for(i in 0 until 350){orbVerts.add(((i*37.0f)%12.0f)-6f);orbVerts.add(((i*53.0f)%12.0f)-6f);orbVerts.add(((i*71.0f)%12.0f)-6f)}; orbCount=orbVerts.size/3; bindVaoVbo(orbVao,orbVbo,createFloatBuffer(orbVerts.toFloatArray()),orbVerts.size*4)
        val terrainVerts=ArrayList<Float>(); for(l in 0 until terrainLinesCount){val x=(l.toFloat()/(terrainLinesCount-1))*8f-4f; for(p in 0 until terrainPtsPerLine){val z=-1f-(p.toFloat()/(terrainPtsPerLine-1))*11f; terrainVerts.add(x);terrainVerts.add(0f);terrainVerts.add(z)}}; bindVaoVbo(terrainVao,terrainVbo,createFloatBuffer(terrainVerts.toFloatArray()),terrainVerts.size*4)
    }
    private fun bindVaoVbo(vao:Int,vbo:Int,buffer:FloatBuffer,byteSize:Int){GLES30.glBindVertexArray(vao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,byteSize,buffer,GLES30.GL_STATIC_DRAW);GLES30.glEnableVertexAttribArray(0);GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,12,0);GLES30.glBindVertexArray(0)}
    private fun createFloatBuffer(a:FloatArray):FloatBuffer=ByteBuffer.allocateDirect(a.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).apply{position(0)}
    private fun updateUniforms(program:Int){
        Matrix.setIdentityM(modelMatrix,0)
        when(program){programOrb->{Matrix.rotateM(modelMatrix,0,timeSec*8f,0f,1f,0f);Matrix.rotateM(modelMatrix,0,10f,1f,0f,0f)};programTerrain->{Matrix.rotateM(modelMatrix,0,22f,1f,0f,0f);Matrix.translateM(modelMatrix,0,0f,-0.42f,0f)}}
        Matrix.setLookAtM(viewMatrix,0,0f,0f,if(program==programOrb)3.1f else 3.5f,0f,0f,if(program==programTerrain)-6f else 0f,0f,1f,0f)
        if(program==programTerrain){Matrix.setLookAtM(viewMatrix,0,0f,1.8f,1.2f,0f,-0.5f,-6f,0f,1f,0f)}
        Matrix.multiplyMM(mvpMatrix,0,viewMatrix,0,modelMatrix,0);Matrix.multiplyMM(mvpMatrix,0,projectionMatrix,0,mvpMatrix,0)
        val mvp=GLES30.glGetUniformLocation(program,"u_MVPMatrix"); if(mvp>=0)GLES30.glUniformMatrix4fv(mvp,1,false,mvpMatrix,0)
        val t=GLES30.glGetUniformLocation(program,"u_Time"); if(t>=0)GLES30.glUniform1f(t,timeSec); val b=GLES30.glGetUniformLocation(program,"u_BassEnergy"); if(b>=0)GLES30.glUniform1f(b,bassEnergy); val m=GLES30.glGetUniformLocation(program,"u_MidEnergy"); if(m>=0)GLES30.glUniform1f(m,midEnergy); val tr=GLES30.glGetUniformLocation(program,"u_TrebleEnergy"); if(tr>=0)GLES30.glUniform1f(tr,trebleEnergy); val fft=GLES30.glGetUniformLocation(program,"u_FFTTexture"); if(fft>=0){GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,fftTextureId[0]);GLES30.glUniform1i(fft,0)}
    }
    private fun drawHyperspace(){GLES30.glUseProgram(programHyperspace);updateUniforms(programHyperspace);GLES30.glBindVertexArray(simpleVao);GLES30.glDrawArrays(GLES30.GL_POINTS,0,1024);GLES30.glBindVertexArray(0)}
    private fun drawOrb(){GLES30.glUseProgram(programOrb);updateUniforms(programOrb);GLES30.glBindVertexArray(orbVao);GLES30.glDrawArrays(GLES30.GL_POINTS,0,orbCount);GLES30.glBindVertexArray(0)}
    private fun drawTerrain(){GLES30.glUseProgram(programTerrain);updateUniforms(programTerrain);GLES30.glBindVertexArray(terrainVao);for(l in 0 until terrainLinesCount)GLES30.glDrawArrays(GLES30.GL_LINE_STRIP,l*terrainPtsPerLine,terrainPtsPerLine);GLES30.glDrawArrays(GLES30.GL_POINTS,0,terrainLinesCount*terrainPtsPerLine);GLES30.glBindVertexArray(0)}
    private fun createProgram(vCode:String,fCode:String):Int{fun c(type:Int,src:String):Int{val sh=GLES30.glCreateShader(type);GLES30.glShaderSource(sh,src);GLES30.glCompileShader(sh);val ok=IntArray(1);GLES30.glGetShaderiv(sh,GLES30.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){android.util.Log.e("AudioVisualizerGL",GLES30.glGetShaderInfoLog(sh));GLES30.glDeleteShader(sh);return 0};return sh};val v=c(GLES30.GL_VERTEX_SHADER,vCode);val f=c(GLES30.GL_FRAGMENT_SHADER,fCode);if(v==0||f==0)return 0;val p=GLES30.glCreateProgram();GLES30.glAttachShader(p,v);GLES30.glAttachShader(p,f);GLES30.glLinkProgram(p);val ok=IntArray(1);GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0);if(ok[0]==0){android.util.Log.e("AudioVisualizerGL",GLES30.glGetProgramInfoLog(p));GLES30.glDeleteProgram(p);return 0};GLES30.glDeleteShader(v);GLES30.glDeleteShader(f);return p}
    companion object {
        private const val COMMON_UNIFORMS = """
            uniform mat4 u_MVPMatrix; uniform float u_Time; uniform float u_BassEnergy;
            uniform float u_MidEnergy; uniform float u_TrebleEnergy; uniform sampler2D u_FFTTexture;
        """
        private const val VERTEX_SHADER_HYPERSPACE = """
            #version 300 es
            $COMMON_UNIFORMS
            layout(location = 0) in vec4 a_Position;
            out float v_Val;
            void main() {
                float id = a_Position.x;
                float rx = fract(sin(id * 12.9898) * 43758.5453) * 2.0 - 1.0;
                float ry = fract(sin(id * 78.233) * 43758.5453) * 2.0 - 1.0;
                float rz = fract(sin(id * 37.719) * 43758.5453);
                float z = mod(rz - u_Time * 0.45, 1.0);
                v_Val = z;
                float x = rx / (z + 0.08);
                float y = ry / (z + 0.08);
                gl_Position = u_MVPMatrix * vec4(x, y, 0.0, 1.0);
                gl_PointSize = (1.0 - z) * (12.0 + u_BassEnergy * 20.0);
            }
        """
        private const val FRAGMENT_SHADER_HYPERSPACE = """
            #version 300 es
            precision mediump float;
            in float v_Val;
            out vec4 fragColor;
            void main() {
                vec2 p = gl_PointCoord - vec2(0.5);
                float d = length(p);
                if (d > 0.5) discard;
                vec3 deepIndigo = vec3(0.08, 0.12, 0.48);
                vec3 lightIndigoBlue = vec3(0.42, 0.58, 1.0);
                vec3 col = mix(deepIndigo, lightIndigoBlue, 1.0 - v_Val);
                float alpha = (1.0 - v_Val) * smoothstep(0.5, 0.0, d);
                fragColor = vec4(col * (1.25 + (1.0 - v_Val) * 1.15), alpha);
            }
        """
        private const val VERTEX_SHADER_ORB = """
            #version 300 es
            $COMMON_UNIFORMS
            layout(location = 0) in vec4 a_Position;
            out vec3 v_Position; out float v_Mag; out float v_IsDust;
            
            vec3 mod289(vec3 x) { return x - floor(x * (1.0/289.0)) * 289.0; }
            vec4 mod289(vec4 x) { return x - floor(x * (1.0/289.0)) * 289.0; }
            vec4 permute(vec4 x) { return mod289(((x*34.0)+1.0)*x); }
            vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
            float snoise(vec3 v) {
                const vec2 C = vec2(1.0/6.0, 1.0/3.0); const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);
                vec3 i = floor(v + dot(v, C.yyy)); vec3 x0 = v - i + dot(i, C.xxx);
                vec3 g = step(x0.yzx, x0.xyz); vec3 l = 1.0 - g;
                vec3 i1 = min(g.xyz, l.zxy); vec3 i2 = max(g.xyz, l.zxy);
                vec3 x1 = x0 - i1 + C.xxx; vec3 x2 = x0 - i2 + C.yyy; vec3 x3 = x0 - D.yyy;
                i = mod289(i);
                vec4 p = permute(permute(permute(i.z + vec4(0.0, i1.z, i2.z, 1.0)) + i.y + vec4(0.0, i1.y, i2.y, 1.0)) + i.x + vec4(0.0, i1.x, i2.x, 1.0));
                float n_ = 0.142857142857; vec3 ns = n_ * D.wyz - D.xzx;
                vec4 j = p - 49.0 * floor(p * ns.z); vec4 x_ = floor(j * ns.z); vec4 y_ = floor(j - 7.0 * x_);
                vec4 x = x_ *ns.x + ns.yyyy; vec4 y = y_ *ns.x + ns.yyyy; vec4 h = 1.0 - abs(x) - abs(y);
                vec4 b0 = vec4(x.xy, y.xy); vec4 b1 = vec4(x.zw, y.zw);
                vec4 s0 = floor(b0)*2.0 + 1.0; vec4 s1 = floor(b1)*2.0 + 1.0; vec4 sh = -step(h, vec4(0.0));
                vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy; vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww;
                vec3 p0 = vec3(a0.xy, h.x); vec3 p1 = vec3(a0.zw, h.y); vec3 p2 = vec3(a1.xy, h.z); vec3 p3 = vec3(a1.zw, h.w);
                vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2, p2), dot(p3,p3)));
                p0 *= norm.x; p1 *= norm.y; p2 *= norm.y; p3 *= norm.w;
                vec4 m = max(0.6 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0); m = m * m;
                return 42.0 * dot(m*m, vec4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3)));
            }

            void main() {
                vec3 pos = a_Position.xyz;
                if (length(pos) > 3.0) {
                    v_IsDust = 1.0;
                    vec3 dustPos = pos + vec3(sin(u_Time*0.15 + pos.x), cos(u_Time*0.12 + pos.y), sin(u_Time*0.18 + pos.z)) * 0.4;
                    gl_Position = u_MVPMatrix * vec4(dustPos, 1.0);
                    gl_PointSize = 2.0; return;
                }
                v_IsDust = 0.0;
                vec3 normal = normalize(pos);
                float phi = atan(normal.z, normal.x); float theta = acos(clamp(normal.y, -1.0, 1.0));
                
                float harmonicWave = 0.06 * sin(12.0 * theta + u_Time * 2.0) * cos(16.0 * phi + u_Time * 1.5)
                                   + 0.04 * sin(20.0 * theta - u_Time * 3.0);
                float noiseVal = snoise(2.5 * pos + u_Time * vec3(0.0, 0.25, 0.15));
                float fftVal = texture(u_FFTTexture, vec2(abs(phi) / 6.28318, 0.0)).r;
                v_Mag = fftVal;
                
                float radius = 1.15 + harmonicWave + u_BassEnergy * noiseVal * 0.20 + fftVal * sin(theta) * sin(theta) * 0.35;
                vec3 finalPos = normal * radius;
                v_Position = finalPos;
                
                gl_Position = u_MVPMatrix * vec4(finalPos, 1.0);
                gl_PointSize = max(1.0, (2.5 + fftVal * 6.0 + u_BassEnergy * 2.0) * (2.2 / gl_Position.w));
            }
        """
        private const val FRAGMENT_SHADER_ORB = """
            #version 300 es
            precision mediump float;
            in vec3 v_Position; in float v_Mag; in float v_IsDust;
            out vec4 fragColor;
            void main() {
                if (v_IsDust > 0.5) { fragColor = vec4(0.3, 0.6, 1.0, 0.25); return; }
                vec2 coord = gl_PointCoord - vec2(0.5);
                float dist = length(coord);
                if (dist > 0.5) discard;
                
                vec3 redBase = vec3(0.95, 0.05, 0.15);
                vec3 brightCyan = vec3(0.0, 0.85, 1.0);
                float mixFactor = clamp((v_Position.x + v_Position.y + 1.2) / 2.4, 0.0, 1.0);
                vec3 color = mix(redBase, brightCyan, mixFactor);
                
                float centerFade = smoothstep(0.02, 0.72, length(v_Position.xy));
                float alpha = smoothstep(0.5, 0.0, dist) * 1.5 * centerFade;
                vec3 flarePos = vec3(0.75, 0.75, 0.5);
                float flare = smoothstep(1.1, 0.0, distance(v_Position, flarePos)) * 0.95;
                
                color *= mix(0.18, 1.0, centerFade);
                fragColor = vec4(color * (1.6 + v_Mag * 2.0) + redBase * flare * 1.6, alpha * (0.85 + v_Mag * 0.3));
            }
        """
        private const val VERTEX_SHADER_TERRAIN = """
            #version 300 es
            $COMMON_UNIFORMS
            layout(location = 0) in vec4 a_Position;
            out float v_Height; out float v_Depth; out vec3 v_ViewPos;
            void main() {
                vec3 pos = a_Position.xyz;
                float zNorm = (-pos.z - 1.0) / 11.0;
                float fftVal = texture(u_FFTTexture, vec2(clamp(zNorm, 0.0, 1.0), 0.0)).r;
                float height = 0.45 * sin(1.4*pos.x + 2.8*u_Time) * cos(0.9*pos.z + 1.4*u_Time) 
                             + 0.20 * sin(2.8*pos.x - 2.0*u_Time) + fftVal * 2.2 * u_MidEnergy;
                pos.y = height; v_Height = height; v_Depth = zNorm;
                vec4 mvPos = u_MVPMatrix * vec4(pos, 1.0);
                v_ViewPos = mvPos.xyz; gl_Position = mvPos;
                gl_PointSize = clamp(16.0 / -mvPos.z, 2.5, 14.0);
            }
        """
        private const val FRAGMENT_SHADER_TERRAIN = """
            #version 300 es
            precision mediump float;
            in float v_Height; in float v_Depth; in vec3 v_ViewPos; out vec4 fragColor;
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                float dist = length(coord); if (dist > 0.5) discard;
                vec3 pink = vec3(1.0, 0.05, 0.60); vec3 cyan = vec3(0.0, 0.80, 1.0);
                vec3 color = mix(pink, cyan, v_Depth + v_Height * 0.25);
                float beta = clamp(abs(-v_ViewPos.z - 4.5) / 5.5, 0.0, 1.0);
                float alpha = (1.0 - dist * 2.0) * (1.0 - beta * 0.55);
                fragColor = vec4(color * 2.2, alpha * 0.95);
            }
        """
    }
}

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    audioSessionId: Int = 0,
    currentPosMs: Long = 0L,
    trackSeed: Long = 0L,
    albumArtUri: Uri? = null,
    style: VisualizerStyle = VisualizerStyle.GLOSSY_SPECTRUM_BARS,
    primaryColor: Color = Color(0xFF00E5FF),
    secondaryColor: Color = Color(0xFFD500F9),
    accentColor: Color = Color(0xFFFFD600),
    numBands: Int = 32,
    showControls: Boolean = false,
    isFullscreen: Boolean = false,
    onStyleChange: ((VisualizerStyle) -> Unit)? = null
) {
    val context = LocalContext.current
    val state = remember { VisualizerDataState(256) }
    var internalStyle by remember { mutableStateOf(style) }
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    var visualizerView by remember { mutableStateOf<AudioVisualizerGLSurfaceView?>(null) }
    // Track RECORD_AUDIO grant reactively so DisposableEffect re-runs when user grants it.
    var recordAudioGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    // Re-check on every recomposition (e.g. user returns from Settings after granting permission).
    recordAudioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(isFullscreen) {
        visualizerView?.setFullscreenBackground(isFullscreen)
    }

    LaunchedEffect(style) { internalStyle = style }
    LaunchedEffect(albumArtUri) {
        state.artBaseHue.floatValue = albumArtUri?.let { extractBaseHueFromArt(context, it) ?: 210f } ?: 210f
    }

    val cycleToNextStyle = {
        val styles = VisualizerStyle.entries
        val nextStyle = styles[(internalStyle.ordinal + 1) % styles.size]
        internalStyle = nextStyle
        onStyleChange?.invoke(nextStyle)
    }

    DisposableEffect(audioSessionId, isPlaying, recordAudioGranted) {
        var androidVisualizer: Visualizer? = null
        // On Android 11+, Visualizer(0) attempts to attach to the global output mix
        // which is blocked by the OS for privacy. We must receive the real ExoPlayer
        // audio session ID (> 0) before attaching.
        val hasValidSession = audioSessionId > 0 && audioSessionId != android.media.audiofx.AudioEffect.ERROR_BAD_VALUE
        if (isPlaying && hasValidSession && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                androidVisualizer = Visualizer(audioSessionId).apply {
                    setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(256))
                    try { setScalingMode(Visualizer.SCALING_MODE_NORMALIZED) } catch (_: Exception) { }
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int) {}
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, r: Int) {
                            if (fft == null || fft.isEmpty()) return
                            val captureSize = fft.size
                            val nyquistBin = (captureSize / 2).coerceAtLeast(2)
                            val magnitudes = FloatArray(nyquistBin + 1)

                            // Highly aggressive extraction: Multiply raw values
                            // to mathematically guarantee visual jumps.
                            magnitudes[0] = (abs(fft[0].toInt()) * 1.5f / 128f).coerceIn(0f, 1f)
                            magnitudes[nyquistBin] = (abs(fft[1].toInt()) * 1.5f / 128f).coerceIn(0f, 1f)

                            for (k in 1 until nyquistBin) {
                                val re = fft[k * 2].toFloat()
                                val im = fft[k * 2 + 1].toFloat()
                                val mag = sqrt(re * re + im * im)
                                magnitudes[k] = ((mag * 1.5f) / 128f).coerceIn(0f, 1f)
                            }

                            for (i in 0 until 256) {
                                val t = i / 255f * nyquistBin
                                val k0 = kotlin.math.floor(t).toInt().coerceIn(0, nyquistBin)
                                val k1 = (k0 + 1).coerceAtMost(nyquistBin)
                                val frac = t - k0
                                state.rawFFT[i] = (magnitudes[k0] + (magnitudes[k1] - magnitudes[k0]) * frac).coerceIn(0f, 1f)
                            }
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true)
                    enabled = true
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioVisualizer", "Failed to init Visualizer(session=$audioSessionId): ${e.message}")
                androidVisualizer?.release()
                androidVisualizer = null
            }
        }
        onDispose { try { androidVisualizer?.enabled = false; androidVisualizer?.release() } catch (_: Exception) {} }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                val nowSeconds = frameNanos / 1_000_000_000f

                // When playing but rawFFT is all-zero (RECORD_AUDIO denied or Visualizer not yet
                // attached), inject synthetic frequency data so energy scalars (bass/mid/treble)
                // are non-zero. The 2D view's activeBand() handles per-band synthesis locally;
                // this keeps Halo/Energy pulsing correctly with bass too.
                val rawIsEmpty = currentIsPlaying && state.rawFFT.all { it < 0.001f }
                if (rawIsEmpty) {
                    for (i in 0 until state.numBands) {
                        val freq = 1.1f + i * 0.045f
                        val phase = i * 0.38f
                        val raw = kotlin.math.sin((nowSeconds * freq + phase).toDouble()).toFloat() * 0.5f + 0.5f
                        state.rawFFT[i] = raw * (0.15f + 0.25f * kotlin.math.abs(kotlin.math.sin((nowSeconds * 0.35f + i * 0.12f).toDouble()).toFloat()))
                    }
                }

                var bassSum = 0f
                var midSum = 0f
                var trebleSum = 0f
                var totalSum = 0f

                for (i in 0 until state.numBands) {
                    val target = if (currentIsPlaying) state.rawFFT[i] else 0f
                    val prev = state.smoothedBands[i]
                    val factor = if (target > prev) 0.5f else 0.15f
                    val smoothed = prev + (target - prev) * factor
                    state.smoothedBands[i] = smoothed

                    if (smoothed > state.peakCaps[i]) {
                        state.peakCaps[i] = smoothed
                        state.peakVelocities[i] = 0f
                    } else {
                        state.peakVelocities[i] += 3.2f * (1f / 60f)
                        state.peakCaps[i] = (state.peakCaps[i] - state.peakVelocities[i] * (1f / 60f)).coerceAtLeast(0f)
                    }

                    if (i <= 10) bassSum += smoothed
                    else if (i <= 60) midSum += smoothed
                    else trebleSum += smoothed
                    totalSum += smoothed
                }

                state.bassEnergy = (bassSum / 11f).coerceIn(0f, 1f)
                state.midEnergy = (midSum / 50f).coerceIn(0f, 1f)
                state.trebleEnergy = (trebleSum / 195f).coerceIn(0f, 1f)
                state.overallAmplitude = (totalSum / state.numBands.toFloat()).coerceIn(0f, 1f)

                visualizerView?.updateAudioData(
                    state.bassEnergy,
                    state.midEnergy,
                    state.trebleEnergy,
                    state.overallAmplitude,
                    nowSeconds,
                    state.smoothedBands,
                    state.artBaseHue.floatValue,
                    state.peakCaps
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                AudioVisualizerGLSurfaceView(ctx) { internalStyle }.also { view ->
                    visualizerView = view
                    view.setFullscreenBackground(isFullscreen)
                }
            },
            update = { view ->
                visualizerView = view
                view.setFullscreenBackground(isFullscreen)
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier.fillMaxSize().zIndex(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { cycleToNextStyle() }
        )

        if (showControls) {
            StyleSelectorBar(
                currentStyle = internalStyle,
                onSelectStyle = { internalStyle = it; onStyleChange?.invoke(it) },
                isFullscreen = isFullscreen,
                hue = state.artBaseHue.floatValue,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).zIndex(2f)
            )
        }
    }
}

@Composable
fun StyleSelectorBar(
    currentStyle: VisualizerStyle,
    onSelectStyle: (VisualizerStyle) -> Unit,
    isFullscreen: Boolean = false,
    hue: Float = 210f,
    modifier: Modifier = Modifier
) {
    val styles = listOf(
        Triple(VisualizerStyle.GLOSSY_SPECTRUM_BARS,  Icons.Default.BarChart, "Bars"),
        Triple(VisualizerStyle.CIRCULAR_RING_WAVE,    Icons.Default.Lens,     "Ring"),
        Triple(VisualizerStyle.NEON_WAVEFORM,         Icons.Default.Waves,    "Wave"),
        Triple(VisualizerStyle.BASS_PARTICLE_HALO,    Icons.Default.Grain,    "Particles"),
        Triple(VisualizerStyle.DOT_MATRIX_BARS,       Icons.Default.Lens,     "Dots"),
        Triple(VisualizerStyle.INDIGO_HYPERSPACE,     Icons.Default.Grain,    "Hyperspace"),
        Triple(VisualizerStyle.PARTICLE_GLOBE_SPHERE, Icons.Default.Lens,     "Globe"),
        Triple(VisualizerStyle.WAVE_GRID_TERRAIN,     Icons.Default.Waves,    "Terrain"),
        Triple(VisualizerStyle.ENERGY_PARTICLES,      Icons.Default.Grain,    "Energy"),
    )
    val selectedIndex = styles.indexOfFirst { it.first == currentStyle }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Animate the list so the selected item is always visually centered.
    LaunchedEffect(selectedIndex) {
        // Scroll so the selected index lands at the center of the viewport.
        // scrollOffset is negative so the item starts at left edge minus half of remaining space;
        // using Int.MAX_VALUE/2 as a safe large offset then correcting in the layout isn't
        // reliable across item widths, so we snap to the item and let contentPadding do the rest.
        listState.animateScrollToItem(
            index = selectedIndex,
            scrollOffset = -200 // pulls selected item toward center; contentPadding handles edges
        )
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        // Equal padding on both sides so the first/last items can be centered
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(styles.size) { idx ->
            val (styleVal, icon, label) = styles[idx]
            val isSelected = styleVal == currentStyle
            VisualizerStyleItem(
                icon = icon,
                label = label,
                isSelected = isSelected,
                isFullscreen = isFullscreen,
                hue = hue,
                onClick = {
                    onSelectStyle(styleVal)
                    scope.launch {
                        listState.animateScrollToItem(
                            index = idx,
                            scrollOffset = -200
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun VisualizerStyleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isFullscreen: Boolean,
    hue: Float,
    onClick: () -> Unit
) {
    val brush = remember(hue) {
        Brush.linearGradient(
            colors = listOf(
                Color.hsv(hue, 0.8f, 1f),
                Color.hsv((hue + 40f) % 360f, 0.7f, 1f)
            )
        )
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected && !isFullscreen) Color(0x55FFFFFF) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .then(
                        if (isSelected) {
                            Modifier
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(brush = brush, blendMode = BlendMode.SrcIn)
                                }
                        } else Modifier
                    )
            )
            if (isSelected) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    style = TextStyle(brush = brush)
                )
            }
        }
    }
}