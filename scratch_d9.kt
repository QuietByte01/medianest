package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    PARTICLE_GLOBE_SPHERE,
    WAVE_GRID_TERRAIN
}

class VisualizerDataState(val numBands: Int = 256) {
    val rawFFT = FloatArray(numBands)
    val smoothedBands = FloatArray(numBands)
    var bassEnergy = 0f
    var midEnergy = 0f
    var trebleEnergy = 0f
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
        } catch (_: Exception) { null }
    }
}

class AudioVisualizerGLSurfaceView(
    context: Context,
    private val styleProvider: () -> VisualizerStyle
) : GLSurfaceView(context) {

    private val renderer: AudioVisualizerRenderer

    init {
        setEGLContextClientVersion(3)
        renderer = AudioVisualizerRenderer(styleProvider)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        isClickable = false
        isFocusable = false
        setOnTouchListener { _, _ -> false }
    }

    fun updateAudioData(
        bassEnergy: Float, midEnergy: Float, trebleEnergy: Float,
        amplitude: Float, timeSec: Float, bands: FloatArray
    ) {
        renderer.updateAudio(bassEnergy, midEnergy, trebleEnergy, amplitude, timeSec, bands)
    }
}

class AudioVisualizerRenderer(
    private val styleProvider: () -> VisualizerStyle
) : GLSurfaceView.Renderer {

    private var programBars = 0
    private var programRing = 0
    private var programWave = 0
    private var programParticles = 0
    private var programOrb = 0
    private var programTerrain = 0

    private var orbVao = 0; private var terrainVao = 0; private var simpleVao = 0
    private var orbVbo = 0; private var terrainVbo = 0; private var simpleVbo = 0

    private var orbCount = 0
    private var terrainPtsPerLine = 120
    private var terrainLinesCount = 80

    private var fftTextureId = intArrayOf(0)
    private val fftByteBuffer = ByteBuffer.allocateDirect(256).order(ByteOrder.nativeOrder())

    private var bassEnergy = 0.5f; private var midEnergy = 0.5f; private var trebleEnergy = 0.5f
    private var amplitude = 0.5f; private var timeSec = 0f
    private var bands = FloatArray(256)

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun updateAudio(bass: Float, mid: Float, treble: Float, amp: Float, time: Float, audioBands: FloatArray) {
        bassEnergy = bass; midEnergy = mid; trebleEnergy = treble; amplitude = amp; timeSec = time
        if (audioBands.size >= 256) {
            System.arraycopy(audioBands, 0, bands, 0, 256)
        } else {
            for (i in 0 until 256) bands[i] = audioBands[(i * audioBands.size) / 256.coerceIn(0, audioBands.lastIndex)]
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.003f, 0.001f, 0.010f, 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)

        programBars = createProgram(VERTEX_SHADER_BARS, FRAGMENT_SHADER_BARS)
        programRing = createProgram(VERTEX_SHADER_RING, FRAGMENT_SHADER_RING)
        programWave = createProgram(VERTEX_SHADER_WAVE, FRAGMENT_SHADER_WAVE)
        programParticles = createProgram(VERTEX_SHADER_PARTICLES, FRAGMENT_SHADER_PARTICLES)
        programOrb = createProgram(VERTEX_SHADER_ORB, FRAGMENT_SHADER_ORB)
        programTerrain = createProgram(VERTEX_SHADER_TERRAIN, FRAGMENT_SHADER_TERRAIN)

        setupGeometryBuffers()
        setupFFTTexture()
    }

    private fun setupFFTTexture() {
        GLES30.glGenTextures(1, fftTextureId, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fftTextureId[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 256, 1, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, null)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 50f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        fftByteBuffer.clear()
        for (b in bands) fftByteBuffer.put((b.coerceIn(0f, 1f) * 255f).toInt().toByte())
        fftByteBuffer.position(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fftTextureId[0])
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, 256, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, fftByteBuffer)

        when (styleProvider()) {
            VisualizerStyle.GLOSSY_SPECTRUM_BARS -> drawSimple(programBars, 384, GLES30.GL_TRIANGLES)
            VisualizerStyle.CIRCULAR_RING_WAVE -> drawSimple(programRing, 1024, GLES30.GL_POINTS)
            VisualizerStyle.NEON_WAVEFORM -> drawSimple(programWave, 1024, GLES30.GL_POINTS)
            VisualizerStyle.BASS_PARTICLE_HALO -> drawSimple(programParticles, 1024, GLES30.GL_POINTS)
            VisualizerStyle.PARTICLE_GLOBE_SPHERE -> drawOrb()
            VisualizerStyle.WAVE_GRID_TERRAIN -> drawTerrain()
        }
    }

    private fun setupGeometryBuffers() {
        val vaos = IntArray(3); val vbos = IntArray(3)
        GLES30.glGenVertexArrays(3, vaos, 0); GLES30.glGenBuffers(3, vbos, 0)
        simpleVao = vaos[0]; orbVao = vaos[1]; terrainVao = vaos[2]
        simpleVbo = vbos[0]; orbVbo = vbos[1]; terrainVbo = vbos[2]

        // 64 bars x 24 samples per bar = 1,536 GPU points.
        val simpleVerts = FloatArray(1536 * 3)
        for (i in 0 until 1536) { simpleVerts[i * 3] = i.toFloat(); simpleVerts[i*3+1] = 0f; simpleVerts[i*3+2] = 0f }
        bindVaoVbo(simpleVao, simpleVbo, createFloatBuffer(simpleVerts), simpleVerts.size * 4)

        val latSteps = 240; val lonSteps = 480
        val orbVerts = ArrayList<Float>()
        for (lat in 0..latSteps) {
            val theta = lat * Math.PI / latSteps
            val sinTheta = sin(theta).toFloat(); val cosTheta = cos(theta).toFloat()
            for (lon in 0..lonSteps) {
                val phi = lon * 2 * Math.PI / lonSteps
                orbVerts.add(sinTheta * cos(phi).toFloat())
                orbVerts.add(cosTheta)
                orbVerts.add(sinTheta * sin(phi).toFloat())
            }
        }
        for (i in 0 until 350) {
            orbVerts.add(((i * 37.0f) % 12.0f) - 6.0f)
            orbVerts.add(((i * 53.0f) % 12.0f) - 6.0f)
            orbVerts.add(((i * 71.0f) % 12.0f) - 6.0f)
        }
        orbCount = orbVerts.size / 3
        bindVaoVbo(orbVao, orbVbo, createFloatBuffer(orbVerts.toFloatArray()), orbVerts.size * 4)

        val terrainVerts = ArrayList<Float>()
        for (l in 0 until terrainLinesCount) {
            val x = (l.toFloat() / (terrainLinesCount - 1)) * 8f - 4f
            for (p in 0 until terrainPtsPerLine) {
                val z = -1.0f - (p.toFloat() / (terrainPtsPerLine - 1)) * 11.0f
                terrainVerts.add(x); terrainVerts.add(0f); terrainVerts.add(z)
            }
        }
        bindVaoVbo(terrainVao, terrainVbo, createFloatBuffer(terrainVerts.toFloatArray()), terrainVerts.size * 4)

    }

    private fun bindVaoVbo(vao: Int, vbo: Int, buffer: FloatBuffer, byteSize: Int) {
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, byteSize, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun createFloatBuffer(array: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(array.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(array).apply { position(0) }
    }

    private fun drawSimple(program: Int, count: Int, primitive: Int) {
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3.5f, 0f, 0f, 0f, 0f, 1f, 0f)
        GLES30.glUseProgram(program)
        updateUniforms(program)
        GLES30.glBindVertexArray(simpleVao)
        GLES30.glDrawArrays(primitive, 0, count)
        GLES30.glBindVertexArray(0)
    }

    private fun drawOrb() {
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3.1f, 0f, 0f, 0f, 0f, 1f, 0f)
        GLES30.glUseProgram(programOrb)
        updateUniforms(programOrb)
        GLES30.glBindVertexArray(orbVao)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, orbCount)
        GLES30.glBindVertexArray(0)
    }

    private fun drawTerrain() {
        Matrix.setLookAtM(viewMatrix, 0, 0f, 1.8f, 1.2f, 0f, -0.5f, -6f, 0f, 1f, 0f)
        GLES30.glUseProgram(programTerrain)
        updateUniforms(programTerrain)
        GLES30.glBindVertexArray(terrainVao)
        for (l in 0 until terrainLinesCount) { GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, l * terrainPtsPerLine, terrainPtsPerLine) }
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, terrainLinesCount * terrainPtsPerLine)
        GLES30.glBindVertexArray(0)
    }

    private fun updateUniforms(program: Int) {
        val mvpLoc = GLES30.glGetUniformLocation(program, "u_MVPMatrix")
        val timeLoc = GLES30.glGetUniformLocation(program, "u_Time")
        val bassLoc = GLES30.glGetUniformLocation(program, "u_BassEnergy")
        val midLoc = GLES30.glGetUniformLocation(program, "u_MidEnergy")
        val trebleLoc = GLES30.glGetUniformLocation(program, "u_TrebleEnergy")
        val fftLoc = GLES30.glGetUniformLocation(program, "u_FFTTexture")

        Matrix.setIdentityM(modelMatrix, 0)
        if (program == programOrb) {
            Matrix.rotateM(modelMatrix, 0, timeSec * 8f, 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, 10f, 1f, 0f, 0f)
        } else if (program == programTerrain) {
            Matrix.rotateM(modelMatrix, 0, 22f, 1f, 0f, 0f)
            Matrix.translateM(modelMatrix, 0, 0f, -0.42f, 0f)
        } else {
            Matrix.rotateM(modelMatrix, 0, timeSec * 5f, 0f, 0f, 1f)
        }
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        if (mvpLoc >= 0) GLES30.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        if (timeLoc >= 0) GLES30.glUniform1f(timeLoc, timeSec)
        if (bassLoc >= 0) GLES30.glUniform1f(bassLoc, bassEnergy)
        if (midLoc >= 0) GLES30.glUniform1f(midLoc, midEnergy)
        if (trebleLoc >= 0) GLES30.glUniform1f(trebleLoc, trebleEnergy)

        if (fftLoc >= 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fftTextureId[0])
            GLES30.glUniform1i(fftLoc, 0)
        }
    }

    private fun createProgram(vCode: String, fCode: String): Int {
        val vShader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER).apply { GLES30.glShaderSource(this, vCode); GLES30.glCompileShader(this) }
        val fShader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER).apply { GLES30.glShaderSource(this, fCode); GLES30.glCompileShader(this) }
        return GLES30.glCreateProgram().apply {
            GLES30.glAttachShader(this, vShader); GLES30.glAttachShader(this, fShader); GLES30.glLinkProgram(this)
        }
    }

    companion object {
        private const val COMMON_UNIFORMS = """
            uniform mat4 u_MVPMatrix; uniform float u_Time; uniform float u_BassEnergy;
            uniform float u_MidEnergy; uniform float u_TrebleEnergy; uniform sampler2D u_FFTTexture;
        """

        private const val VERTEX_SHADER_BARS = """
            #version 300 es
            $COMMON_UNIFORMS
            layout(location = 0) in vec4 a_Position;
            out float v_Level; out float v_Band;
            void main() {
                float id = a_Position.x;
                float bar = floor(id / 6.0);
                float corner = mod(id, 6.0);
                float u = (bar + 0.5) / 64.0;
                float fft = texture(u_FFTTexture, vec2(u, 0.0)).r;
                float level = 0.055 + pow(fft, 0.70) * (1.72 + u_BassEnergy * 0.42);
                float xCenter = (bar / 63.0) * 3.20 - 1.60;
                float halfWidth = 0.020;
                float left = xCenter - halfWidth;
                float right = xCenter + halfWidth;
                float bottom = -1.18;
                float top = bottom + level;
                vec2 pos;
                if (corner < 0.5) pos = vec2(left, bottom);
                else if (corner < 1.5) pos = vec2(right, bottom);
                else if (corner < 2.5) pos = vec2(right, top);
                else if (corner < 3.5) pos = vec2(right, top);
                else if (corner < 4.5) pos = vec2(left, top);
                else pos = vec2(left, bottom);
                v_Level = clamp((pos.y - bottom) / max(level, 0.001), 0.0, 1.0);
                v_Band = fft;
                gl_Position = u_MVPMatrix * vec4(pos, 0.0, 1.0);
            }
        """
        private const val FRAGMENT_SHADER_BARS = """
            #version 300 es
            precision mediump float;
            in float v_Level; in float v_Band; out vec4 fragColor;
            void main() {
                vec2 p = gl_PointCoord - vec2(0.5);
                float d = length(p);
                if (d > 0.5) discard;
                vec3 bottom = vec3(0.16, 0.30, 0.95);
                vec3 top = vec3(0.48, 0.88, 1.0);
                vec3 col = mix(bottom, top, smoothstep(0.0, 1.0, v_Level));
                float core = smoothstep(0.50, 0.04, d);
                float brightness = 0.72 + v_Band * 1.8 + v_Level * 0.35;
                fragColor = vec4(col * brightness, core * (0.72 + v_Band * 0.28));
            }
        """
        private const val VERTEX_SHADER_RING = """
            #version 300 es
            $COMMON_UNIFORMS
            layout(location = 0) in vec4 a_Position;
            out float v_Val;
            out float v_Radius;
            void main() {
                float id = a_Position.x;
                float angle = (id / 1024.0) * 6.2831853;
                float u = fract(id / 1024.0);
                float fft = texture(u_FFTTexture, vec2(u, 0.0)).r;
                float wave = 0.035 * sin(angle * 5.0 - u_Time * 1.4) + 0.025 * sin(angle * 11.0 + u_Time * 2.1);
                float r = 0.72 + fft * (0.52 + u_BassEnergy * 0.32) + wave;
                float wobble = 0.05 * sin(angle * 3.0 + u_Time) * u_MidEnergy;
                vec2 pos = vec2(cos(angle), sin(angle)) * (r + wobble);
                v_Val = fft; v_Radius = r;
                gl_Position = u_MVPMatrix * vec4(pos, 0.0, 1.0);
                gl_PointSize = 3.2 + fft * 5.5;
            }
        """
        private const val FRAGMENT_SHADER_RING = """
            #version 300 es
            precision mediump float;
            in float v_Val; in float v_Radius; out vec4 fragColor;
            void main() {
                vec2 p = gl_PointCoord - vec2(0.5);
                float d = length(p); if (d > 0.5) discard;
                vec3 cyan = vec3(0.18, 0.78, 1.0);
                vec3 violet = vec3(0.48, 0.35, 1.0);
                vec3 col = mix(cyan, violet, smoothstep(0.15, 1.15, v_Val));
                float a = smoothstep(0.5, 0.03, d) * (0.68 + v_Val * 0.32);
                fragColor = vec4(col * (1.0 + v_Val * 2.0), a);
            }
        """
        private const val VERTEX_SHADER_WAVE = """
            #version 300 es
            $COMMON_UNIFORMS
            layout(location = 0) in vec4 a_Position;
            out float v_Val; out float v_Band;
            void main() {
                float id = a_Position.x;
                float u = id / 1023.0;
                float x = u * 3.35 - 1.675;
                float fft = texture(u_FFTTexture, vec2(u, 0.0)).r;
                float audioWave = fft * (0.85 + u_BassEnergy * 0.55);
                float y = sin(x * 5.2 - u_Time * 3.0) * 0.08
                        + sin(x * 11.0 + u_Time * 1.7) * 0.035
                        + audioWave * (0.18 + 0.82 * sin(u * 3.14159));
                y *= 0.82 + u_MidEnergy * 0.35;
                v_Val = u; v_Band = fft;
                gl_Position = u_MVPMatrix * vec4(x, y, 0.0, 1.0);
                gl_PointSize = 2.8 + fft * 5.5;
            }
        """
        private const val FRAGMENT_SHADER_WAVE = """
            #version 300 es
            precision mediump float;
            in float v_Val; in float v_Band; out vec4 fragColor;
            void main() {
                vec2 p = gl_PointCoord - vec2(0.5);
                float d = length(p); if (d > 0.5) discard;
                vec3 c1 = vec3(0.25, 0.55, 1.0);
                vec3 c2 = vec3(0.76, 0.35, 1.0);
                vec3 col = mix(c1, c2, v_Val);
                float a = smoothstep(0.5, 0.02, d);
                fragColor = vec4(col * (0.85 + v_Band * 2.0), a * (0.72 + v_Band * 0.28));
            }
        """
        private const val VERTEX_SHADER_PARTICLES = """
            #version 300 es
            $COMMON_UNIFORMS
            layout(location = 0) in vec4 a_Position;
            out float v_Val;
            void main() {
                float id = a_Position.x;
                float rx = fract(sin(id * 12.9898) * 43758.5453) * 2.0 - 1.0;
                float ry = fract(sin(id * 78.233) * 43758.5453) * 2.0 - 1.0;
                float rz = fract(sin(id * 37.719) * 43758.5453);
                
                float z = mod(rz - u_Time * (0.15 + u_BassEnergy * 1.5), 1.0);
                v_Val = z;
                float x = rx / (z + 0.1);
                float y = ry / (z + 0.1);
                
                gl_Position = u_MVPMatrix * vec4(x, y, 0.0, 1.0);
                gl_PointSize = (1.0 - z) * 10.0;
            }
        """
        private const val FRAGMENT_SHADER_PARTICLES = """
            #version 300 es
            precision mediump float;
            in float v_Val; out vec4 fragColor;
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                if (length(coord) > 0.5) discard;
                vec3 deepIndigo = vec3(0.08, 0.12, 0.48);
                vec3 lightIndigoBlue = vec3(0.42, 0.58, 1.0);
                vec3 col = mix(deepIndigo, lightIndigoBlue, 1.0 - v_Val);
                float alpha = (1.0 - v_Val) * smoothstep(0.5, 0.0, length(coord));
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
    val state = remember { VisualizerDataState(256) }
    var internalStyle by remember { mutableStateOf(style) }

    LaunchedEffect(style) { internalStyle = style }

    val cycleToNextStyle = {
        val styles = VisualizerStyle.entries
        val nextStyle = styles[(internalStyle.ordinal + 1) % styles.size]
        internalStyle = nextStyle
        onStyleChange?.invoke(nextStyle)
    }

    DisposableEffect(audioSessionId, isPlaying) {
        var androidVisualizer: Visualizer? = null
        if (isPlaying && audioSessionId > 0 && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                androidVisualizer = Visualizer(audioSessionId).apply {
                    setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(256))
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int) {}
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, r: Int) {
                            if (fft == null || fft.isEmpty()) return
                            val bins = (fft.size / 2).coerceAtLeast(2)
                            for (i in 0 until 256) {
                                val normalized = i / 255f
                                val bin = (1 + normalized * (bins - 2)).toInt().coerceIn(1, bins - 1)
                                val real = fft[bin * 2].toFloat()
                                val imag = fft[bin * 2 + 1].toFloat()
                                state.rawFFT[i] = (sqrt(real * real + imag * imag) / 128f).coerceIn(0f, 1f)
                            }
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true)
                    enabled = true
                }
            } catch (e: Exception) { androidVisualizer?.release(); androidVisualizer = null }
        }
        onDispose { try { androidVisualizer?.enabled = false; androidVisualizer?.release() } catch (_: Exception) {} }
    }

    var lastNanos by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        var prevNanos = System.nanoTime()
        if (isPlaying) {
            while (isPlaying) {
                withFrameNanos { frameNanos ->
                    prevNanos = frameNanos; lastNanos = frameNanos.toFloat()
                    var bassSum = 0f; var midSum = 0f; var trebleSum = 0f; var totalSum = 0f
                    for (i in 0 until state.numBands) {
                        val rawVal = state.rawFFT[i]; val prev = state.smoothedBands[i]
                        val smoothed = prev + (rawVal - prev) * if (rawVal > prev) 0.5f else 0.15f
                        state.smoothedBands[i] = smoothed
                        if (i <= 10) bassSum += smoothed else if (i <= 60) midSum += smoothed else trebleSum += smoothed
                        totalSum += smoothed
                    }
                    state.bassEnergy = (bassSum / 11f).coerceIn(0f, 1f)
                    state.midEnergy = (midSum / 50f).coerceIn(0f, 1f)
                    state.trebleEnergy = (trebleSum / 195f).coerceIn(0f, 1f)
                    state.overallAmplitude = (totalSum / state.numBands.toFloat()).coerceIn(0f, 1f)
                }
            }
        } else {
            for (i in 0 until state.numBands) { state.smoothedBands[i] = 0f; state.rawFFT[i] = 0f }
            state.bassEnergy = 0f; state.midEnergy = 0f; state.trebleEnergy = 0f; state.overallAmplitude = 0f
            lastNanos = System.nanoTime().toFloat()
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val animNanos = lastNanos

        AndroidView(
            factory = { ctx -> AudioVisualizerGLSurfaceView(ctx) { internalStyle } },
            update = { view -> view.updateAudioData(state.bassEnergy, state.midEnergy, state.trebleEnergy, state.overallAmplitude, animNanos / 1_000_000_000f, state.smoothedBands) },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier.fillMaxSize().zIndex(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { cycleToNextStyle() }
        )

        if (showControls) {
            StyleSelectorBar(
                currentStyle = internalStyle,
                onSelectStyle = { internalStyle = it; onStyleChange?.invoke(it) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).zIndex(2f)
            )
        }
    }
}

@Composable
private fun StyleSelectorBar(currentStyle: VisualizerStyle, onSelectStyle: (VisualizerStyle) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.60f), tonalElevation = 6.dp) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VisualizerStyleItem(Icons.Default.BarChart, "Bars", currentStyle == VisualizerStyle.GLOSSY_SPECTRUM_BARS) { onSelectStyle(VisualizerStyle.GLOSSY_SPECTRUM_BARS) }
            VisualizerStyleItem(Icons.Default.Lens, "Ring", currentStyle == VisualizerStyle.CIRCULAR_RING_WAVE) { onSelectStyle(VisualizerStyle.CIRCULAR_RING_WAVE) }
            VisualizerStyleItem(Icons.Default.Waves, "Wave", currentStyle == VisualizerStyle.NEON_WAVEFORM) { onSelectStyle(VisualizerStyle.NEON_WAVEFORM) }
            VisualizerStyleItem(Icons.Default.Grain, "Particles", currentStyle == VisualizerStyle.BASS_PARTICLE_HALO) { onSelectStyle(VisualizerStyle.BASS_PARTICLE_HALO) }
            VisualizerStyleItem(Icons.Default.Lens, "Globe", currentStyle == VisualizerStyle.PARTICLE_GLOBE_SPHERE) { onSelectStyle(VisualizerStyle.PARTICLE_GLOBE_SPHERE) }
            VisualizerStyleItem(Icons.Default.Waves, "Terrain", currentStyle == VisualizerStyle.WAVE_GRID_TERRAIN) { onSelectStyle(VisualizerStyle.WAVE_GRID_TERRAIN) }
        }
    }
}

@Composable
private fun VisualizerStyleItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(imageVector = icon, contentDescription = label, tint = if (isSelected) Color.Black else Color.White, modifier = Modifier.size(16.dp))
            if (isSelected) Text(text = label, fontSize = 11.sp, color = Color.Black)
        }
    }
}