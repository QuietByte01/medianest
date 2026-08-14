//package com.medianest.ui.components
//
//import android.Manifest
//import android.content.Context
//import android.content.pm.PackageManager
//import android.graphics.drawable.BitmapDrawable
//import android.media.audiofx.Visualizer
//import android.net.Uri
//import android.opengl.GLES30
//import android.opengl.GLSurfaceView
//import android.opengl.Matrix
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.gestures.detectTapGestures
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.BarChart
//import androidx.compose.material.icons.filled.Grain
//import androidx.compose.material.icons.filled.Lens
//import androidx.compose.material.icons.filled.Waves
//import androidx.compose.material3.Icon
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.DisposableEffect
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableFloatStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.runtime.withFrameNanos
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.core.content.ContextCompat
//import coil.ImageLoader
//import coil.request.ImageRequest
//import coil.request.SuccessResult
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.nio.FloatBuffer
//import javax.microedition.khronos.egl.EGLConfig
//import javax.microedition.khronos.opengles.GL10
//import kotlin.math.abs
//import kotlin.math.cos
//import kotlin.math.sin
//import kotlin.math.sqrt
//
//enum class VisualizerStyle {
//    GLOSSY_SPECTRUM_BARS,
//    CIRCULAR_RING_WAVE,
//    NEON_WAVEFORM,
//    BASS_PARTICLE_HALO,
//    PARTICLE_GLOBE_SPHERE,
//    WAVE_GRID_TERRAIN,
//    PARTICLE_TUNNEL_VORTEX
//}
//
//class VisualizerDataState(val numBands: Int = 256) {
//    val rawFFT = FloatArray(numBands)
//    val smoothedBands = FloatArray(numBands)
//    var bassEnergy = 0f
//    var midEnergy = 0f
//    var trebleEnergy = 0f
//    var overallAmplitude = 0f
//}
//
//// -----------------------------------------------------------------------------
//// OPENGL ES 3.0 SURFACE VIEW & RENDERER
//// -----------------------------------------------------------------------------
//
//class AudioVisualizerGLSurfaceView(
//    context: Context,
//    private val styleProvider: () -> VisualizerStyle
//) : GLSurfaceView(context) {
//
//    private val renderer: AudioVisualizerRenderer
//
//    init {
//        setEGLContextClientVersion(3)
//        renderer = AudioVisualizerRenderer(styleProvider)
//        setRenderer(renderer)
//        renderMode = RENDERMODE_CONTINUOUSLY
//
//        // Crucial: Disable native touch intercept so Jetpack Compose captures gestures
//        isClickable = false
//        isFocusable = false
//        setOnTouchListener { _, _ -> false }
//    }
//
//    fun updateAudioData(
//        bassEnergy: Float,
//        midEnergy: Float,
//        trebleEnergy: Float,
//        amplitude: Float,
//        timeSec: Float,
//        bands: FloatArray
//    ) {
//        renderer.updateAudio(bassEnergy, midEnergy, trebleEnergy, amplitude, timeSec, bands)
//    }
//}
//
//class AudioVisualizerRenderer(
//    private val styleProvider: () -> VisualizerStyle
//) : GLSurfaceView.Renderer {
//
//    private var programBars = 0
//    private var programRing = 0
//    private var programWave = 0
//    private var programParticles = 0
//    private var programOrb = 0
//    private var programTerrain = 0
//    private var programVortex = 0
//
//    private var orbVao = 0
//    private var terrainVao = 0
//    private var vortexVao = 0
//    private var simpleVao = 0
//
//    private var orbVbo = 0
//    private var terrainVbo = 0
//    private var vortexVbo = 0
//    private var simpleVbo = 0
//
//    private var orbCount = 0
//    private var terrainPtsPerLine = 120
//    private var terrainLinesCount = 80
//    private var vortexCount = 0
//
//    private var fftTextureId = intArrayOf(0)
//    private val fftByteBuffer = ByteBuffer.allocateDirect(256).order(ByteOrder.nativeOrder())
//
//    private var bassEnergy = 0.5f
//    private var midEnergy = 0.5f
//    private var trebleEnergy = 0.5f
//    private var amplitude = 0.5f
//    private var timeSec = 0f
//    private var bands = FloatArray(256)
//
//    private val projectionMatrix = FloatArray(16)
//    private val viewMatrix = FloatArray(16)
//    private val modelMatrix = FloatArray(16)
//    private val mvpMatrix = FloatArray(16)
//
//    fun updateAudio(bass: Float, mid: Float, treble: Float, amp: Float, time: Float, audioBands: FloatArray) {
//        bassEnergy = bass
//        midEnergy = mid
//        trebleEnergy = treble
//        amplitude = amp
//        timeSec = time
//        if (audioBands.size >= 256) {
//            System.arraycopy(audioBands, 0, bands, 0, 256)
//        } else {
//            for (i in 0 until 256) {
//                val srcIdx = (i * audioBands.size) / 256
//                bands[i] = audioBands[srcIdx.coerceIn(0, audioBands.lastIndex)]
//            }
//        }
//    }
//
//    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
//        GLES30.glClearColor(0.005f, 0.002f, 0.015f, 1f)
//        GLES30.glEnable(GLES30.GL_BLEND)
//        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE) // Additive Glow Blend Mode
//
//        programBars = createProgram(VERTEX_SHADER_BARS, FRAGMENT_SHADER_BARS)
//        programRing = createProgram(VERTEX_SHADER_RING, FRAGMENT_SHADER_RING)
//        programWave = createProgram(VERTEX_SHADER_WAVE, FRAGMENT_SHADER_WAVE)
//        programParticles = createProgram(VERTEX_SHADER_PARTICLES, FRAGMENT_SHADER_PARTICLES)
//        programOrb = createProgram(VERTEX_SHADER_ORB, FRAGMENT_SHADER_ORB)
//        programTerrain = createProgram(VERTEX_SHADER_TERRAIN, FRAGMENT_SHADER_TERRAIN)
//        programVortex = createProgram(VERTEX_SHADER_VORTEX, FRAGMENT_SHADER_VORTEX)
//
//        setupGeometryBuffers()
//        setupFFTTexture()
//    }
//
//    private fun setupFFTTexture() {
//        GLES30.glGenTextures(1, fftTextureId, 0)
//        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fftTextureId[0])
//        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
//        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
//        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
//        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
//        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 256, 1, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, null)
//    }
//
//    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
//        GLES30.glViewport(0, 0, width, height)
//        val ratio = width.toFloat() / height.toFloat()
//        Matrix.perspectiveM(projectionMatrix, 0, 50f, ratio, 0.1f, 100f)
//    }
//
//    override fun onDrawFrame(gl: GL10?) {
//        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
//
//        fftByteBuffer.clear()
//        for (b in bands) {
//            fftByteBuffer.put((b.coerceIn(0f, 1f) * 255f).toInt().toByte())
//        }
//        fftByteBuffer.position(0)
//        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fftTextureId[0])
//        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, 256, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, fftByteBuffer)
//
//        when (styleProvider()) {
//            VisualizerStyle.GLOSSY_SPECTRUM_BARS -> drawSimple(programBars, 32)
//            VisualizerStyle.CIRCULAR_RING_WAVE -> drawSimple(programRing, 64)
//            VisualizerStyle.NEON_WAVEFORM -> drawSimple(programWave, 120)
//            VisualizerStyle.BASS_PARTICLE_HALO -> drawSimple(programParticles, 100)
//            VisualizerStyle.PARTICLE_GLOBE_SPHERE -> drawOrb()
//            VisualizerStyle.WAVE_GRID_TERRAIN -> drawTerrain()
//            VisualizerStyle.PARTICLE_TUNNEL_VORTEX -> drawVortex()
//        }
//    }
//
//    private fun setupGeometryBuffers() {
//        val vaos = IntArray(4)
//        val vbos = IntArray(4)
//        GLES30.glGenVertexArrays(4, vaos, 0)
//        GLES30.glGenBuffers(4, vbos, 0)
//
//        simpleVao = vaos[0]; orbVao = vaos[1]; terrainVao = vaos[2]; vortexVao = vaos[3]
//        simpleVbo = vbos[0]; orbVbo = vbos[1]; terrainVbo = vbos[2]; vortexVbo = vbos[3]
//
//        // Simple Buffer
//        val simpleVerts = FloatArray(120 * 3) { it.toFloat() }
//        bindVaoVbo(simpleVao, simpleVbo, createFloatBuffer(simpleVerts), simpleVerts.size * 4)
//
//        // Orb (Sphere) Buffer
//        val latSteps = 200
//        val lonSteps = 400
//        val orbVerts = ArrayList<Float>()
//        for (lat in 0..latSteps) {
//            val theta = lat * Math.PI / latSteps
//            val sinTheta = sin(theta).toFloat()
//            val cosTheta = cos(theta).toFloat()
//            for (lon in 0..lonSteps) {
//                val phi = lon * 2 * Math.PI / lonSteps
//                orbVerts.add(sinTheta * cos(phi).toFloat())
//                orbVerts.add(cosTheta)
//                orbVerts.add(sinTheta * sin(phi).toFloat())
//            }
//        }
//        // Ambient Star/Dust Field
//        for (i in 0 until 250) {
//            orbVerts.add(((i * 37.0f) % 10.0f) - 5.0f)
//            orbVerts.add(((i * 53.0f) % 10.0f) - 5.0f)
//            orbVerts.add(((i * 71.0f) % 10.0f) - 5.0f)
//        }
//        orbCount = orbVerts.size / 3
//        val orbArray = orbVerts.toFloatArray()
//        bindVaoVbo(orbVao, orbVbo, createFloatBuffer(orbArray), orbArray.size * 4)
//
//        // Terrain Buffer
//        val terrainVerts = ArrayList<Float>()
//        for (l in 0 until terrainLinesCount) {
//            val x = (l.toFloat() / (terrainLinesCount - 1)) * 8f - 4f
//            for (p in 0 until terrainPtsPerLine) {
//                val z = -1.0f - (p.toFloat() / (terrainPtsPerLine - 1)) * 11.0f
//                terrainVerts.add(x)
//                terrainVerts.add(0f)
//                terrainVerts.add(z)
//            }
//        }
//        val terrainArray = terrainVerts.toFloatArray()
//        bindVaoVbo(terrainVao, terrainVbo, createFloatBuffer(terrainArray), terrainArray.size * 4)
//
//        // Vortex Buffer
//        val rings = 150
//        val ptsPerRing = 180
//        val vortexVerts = ArrayList<Float>()
//        for (r in 0 until rings) {
//            for (p in 0 until ptsPerRing) {
//                vortexVerts.add(r.toFloat())
//                vortexVerts.add(p.toFloat())
//                vortexVerts.add(0f)
//            }
//        }
//        vortexCount = vortexVerts.size / 3
//        val vortexArray = vortexVerts.toFloatArray()
//        bindVaoVbo(vortexVao, vortexVbo, createFloatBuffer(vortexArray), vortexArray.size * 4)
//    }
//
//    private fun bindVaoVbo(vao: Int, vbo: Int, buffer: FloatBuffer, byteSize: Int) {
//        GLES30.glBindVertexArray(vao)
//        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
//        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, byteSize, buffer, GLES30.GL_STATIC_DRAW)
//        GLES30.glEnableVertexAttribArray(0)
//        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)
//        GLES30.glBindVertexArray(0)
//    }
//
//    private fun createFloatBuffer(array: FloatArray): FloatBuffer {
//        return ByteBuffer.allocateDirect(array.size * 4)
//            .order(ByteOrder.nativeOrder())
//            .asFloatBuffer()
//            .put(array)
//            .apply { position(0) }
//    }
//
//    private fun drawSimple(program: Int, count: Int) {
//        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3.5f, 0f, 0f, 0f, 0f, 1f, 0f)
//        GLES30.glUseProgram(program)
//        updateUniforms(program)
//        GLES30.glBindVertexArray(simpleVao)
//        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)
//        GLES30.glBindVertexArray(0)
//    }
//
//    private fun drawOrb() {
//        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3.8f, 0f, 0f, 0f, 0f, 1f, 0f)
//        GLES30.glUseProgram(programOrb)
//        updateUniforms(programOrb)
//        GLES30.glBindVertexArray(orbVao)
//        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, orbCount)
//        GLES30.glBindVertexArray(0)
//    }
//
//    private fun drawTerrain() {
//        Matrix.setLookAtM(viewMatrix, 0, 0f, 1.8f, 1.2f, 0f, -0.5f, -6f, 0f, 1f, 0f)
//        GLES30.glUseProgram(programTerrain)
//        updateUniforms(programTerrain)
//        GLES30.glBindVertexArray(terrainVao)
//        for (l in 0 until terrainLinesCount) {
//            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, l * terrainPtsPerLine, terrainPtsPerLine)
//        }
//        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, terrainLinesCount * terrainPtsPerLine)
//        GLES30.glBindVertexArray(0)
//    }
//
//    private fun drawVortex() {
//        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 0.1f, 0f, 0f, -10f, 0f, 1f, 0f)
//        GLES30.glUseProgram(programVortex)
//        updateUniforms(programVortex)
//        GLES30.glBindVertexArray(vortexVao)
//        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, vortexCount)
//        GLES30.glBindVertexArray(0)
//    }
//
//    private fun updateUniforms(program: Int) {
//        val mvpLoc = GLES30.glGetUniformLocation(program, "u_MVPMatrix")
//        val timeLoc = GLES30.glGetUniformLocation(program, "u_Time")
//        val bassLoc = GLES30.glGetUniformLocation(program, "u_BassEnergy")
//        val midLoc = GLES30.glGetUniformLocation(program, "u_MidEnergy")
//        val trebleLoc = GLES30.glGetUniformLocation(program, "u_TrebleEnergy")
//        val fftLoc = GLES30.glGetUniformLocation(program, "u_FFTTexture")
//
//        Matrix.setIdentityM(modelMatrix, 0)
//        if (program == programOrb) {
//            Matrix.rotateM(modelMatrix, 0, timeSec * 12f, 0f, 1f, 0f)
//            Matrix.rotateM(modelMatrix, 0, 15f, 1f, 0f, 0f)
//        } else if (program == programTerrain) {
//            Matrix.rotateM(modelMatrix, 0, 22f, 1f, 0f, 0f)
//        } else if (program != programVortex) {
//            Matrix.rotateM(modelMatrix, 0, timeSec * 5f, 0f, 0f, 1f)
//        }
//
//        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
//        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
//
//        if (mvpLoc >= 0) GLES30.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
//        if (timeLoc >= 0) GLES30.glUniform1f(timeLoc, timeSec)
//        if (bassLoc >= 0) GLES30.glUniform1f(bassLoc, bassEnergy)
//        if (midLoc >= 0) GLES30.glUniform1f(midLoc, midEnergy)
//        if (trebleLoc >= 0) GLES30.glUniform1f(trebleLoc, trebleEnergy)
//
//        if (fftLoc >= 0) {
//            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
//            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fftTextureId[0])
//            GLES30.glUniform1i(fftLoc, 0)
//        }
//    }
//
//    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
//        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexCode)
//        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentCode)
//        val program = GLES30.glCreateProgram()
//        GLES30.glAttachShader(program, vertexShader)
//        GLES30.glAttachShader(program, fragmentShader)
//        GLES30.glLinkProgram(program)
//        return program
//    }
//
//    private fun loadShader(type: Int, shaderCode: String): Int {
//        val shader = GLES30.glCreateShader(type)
//        GLES30.glShaderSource(shader, shaderCode)
//        GLES30.glCompileShader(shader)
//        return shader
//    }
//
//    companion object {
//        private const val COMMON_UNIFORMS = """
//            uniform mat4 u_MVPMatrix;
//            uniform float u_Time;
//            uniform float u_BassEnergy;
//            uniform float u_MidEnergy;
//            uniform float u_TrebleEnergy;
//            uniform sampler2D u_FFTTexture;
//        """
//
//        private const val VERTEX_SHADER_BARS = """
//            #version 300 es
//            $COMMON_UNIFORMS
//            layout(location = 0) in vec4 a_Position;
//            out float v_Val;
//            void main() {
//                float id = a_Position.x;
//                float val = texture(u_FFTTexture, vec2(id / 32.0, 0.0)).r;
//                v_Val = val;
//                vec3 pos = vec3((id / 16.0) - 1.0, (val * 2.2) - 1.1, 0.0);
//                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
//                gl_PointSize = 22.0 * val + 8.0;
//            }
//        """
//        private const val FRAGMENT_SHADER_BARS = """
//            #version 300 es
//            precision mediump float;
//            in float v_Val;
//            out vec4 fragColor;
//            void main() {
//                vec2 coord = gl_PointCoord - vec2(0.5);
//                if (length(coord) > 0.5) discard;
//                vec3 cyan = vec3(0.0, 0.85, 1.0);
//                vec3 blue = vec3(0.0, 0.3, 0.9);
//                fragColor = vec4(mix(blue, cyan, v_Val) * 2.0, smoothstep(0.5, 0.0, length(coord)));
//            }
//        """
//
//        private const val VERTEX_SHADER_RING = """
//            #version 300 es
//            $COMMON_UNIFORMS
//            layout(location = 0) in vec4 a_Position;
//            out float v_Val;
//            void main() {
//                float id = a_Position.x;
//                float angle = (id / 64.0) * 6.28318;
//                float val = texture(u_FFTTexture, vec2(mod(id, 32.0) / 32.0, 0.0)).r;
//                v_Val = val;
//                float r = 0.8 + val * 0.5 + u_BassEnergy * 0.2;
//                vec3 pos = vec3(cos(angle) * r, sin(angle) * r, 0.0);
//                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
//                gl_PointSize = 12.0 * val + 6.0;
//            }
//        """
//        private const val FRAGMENT_SHADER_RING = """
//            #version 300 es
//            precision mediump float;
//            in float v_Val;
//            out vec4 fragColor;
//            void main() {
//                vec2 coord = gl_PointCoord - vec2(0.5);
//                if (length(coord) > 0.5) discard;
//                vec3 pink = vec3(1.0, 0.1, 0.7);
//                vec3 cyan = vec3(0.0, 0.9, 1.0);
//                fragColor = vec4(mix(pink, cyan, v_Val) * 2.2, smoothstep(0.5, 0.0, length(coord)));
//            }
//        """
//
//        private const val VERTEX_SHADER_WAVE = """
//            #version 300 es
//            $COMMON_UNIFORMS
//            layout(location = 0) in vec4 a_Position;
//            out float v_Val;
//            void main() {
//                float id = a_Position.x;
//                float x = (id / 120.0) * 2.6 - 1.3;
//                float val = texture(u_FFTTexture, vec2(mod(id, 32.0) / 32.0, 0.0)).r;
//                v_Val = val;
//                float y = sin(x * 6.0 + u_Time * 5.0) * (val * 1.3 + 0.15);
//                gl_Position = u_MVPMatrix * vec4(x, y, 0.0, 1.0);
//                gl_PointSize = 10.0 * val + 5.0;
//            }
//        """
//        private const val FRAGMENT_SHADER_WAVE = """
//            #version 300 es
//            precision mediump float;
//            in float v_Val;
//            out vec4 fragColor;
//            void main() {
//                vec2 coord = gl_PointCoord - vec2(0.5);
//                if (length(coord) > 0.5) discard;
//                vec3 color = vec3(0.0, 0.95, 1.0);
//                fragColor = vec4(color * 2.5, smoothstep(0.5, 0.0, length(coord)));
//            }
//        """
//
//        private const val VERTEX_SHADER_PARTICLES = """
//            #version 300 es
//            $COMMON_UNIFORMS
//            layout(location = 0) in vec4 a_Position;
//            out float v_Val;
//            void main() {
//                float id = a_Position.x;
//                float angle = id * 137.5 + u_Time * 0.8;
//                float r = mod(id * 0.08 + u_Time * 0.3, 2.2) * (1.0 + u_BassEnergy * 0.5);
//                float val = texture(u_FFTTexture, vec2(mod(id, 32.0) / 32.0, 0.0)).r;
//                v_Val = val;
//                vec3 pos = vec3(cos(angle) * r, sin(angle) * r, 0.0);
//                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
//                gl_PointSize = max(3.0, (8.0 * val + 4.0) * (1.0 + u_BassEnergy));
//            }
//        """
//        private const val FRAGMENT_SHADER_PARTICLES = """
//            #version 300 es
//            precision mediump float;
//            in float v_Val;
//            out vec4 fragColor;
//            void main() {
//                vec2 coord = gl_PointCoord - vec2(0.5);
//                if (length(coord) > 0.5) discard;
//                vec3 purple = vec3(0.8, 0.1, 1.0);
//                vec3 cyan = vec3(0.2, 0.9, 1.0);
//                fragColor = vec4(mix(purple, cyan, v_Val) * 2.2, smoothstep(0.5, 0.0, length(coord)));
//            }
//        """
//
//        // ---------------------------------------------------------------------
//        // Visualizer 1: Audio Orb Sphere (Image-1 Exact Spec)
//        // ---------------------------------------------------------------------
//        private const val VERTEX_SHADER_ORB = """
//            #version 300 es
//            $COMMON_UNIFORMS
//            layout(location = 0) in vec4 a_Position;
//            out vec3 v_Position;
//            out float v_Mag;
//            out float v_IsDust;
//
//            vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
//            vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
//            vec4 permute(vec4 x) { return mod289(((x*34.0)+1.0)*x); }
//            vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
//            float snoise(vec3 v) {
//                const vec2 C = vec2(1.0/6.0, 1.0/3.0);
//                const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);
//                vec3 i  = floor(v + dot(v, C.yyy));
//                vec3 x0 = v - i + dot(i, C.xxx);
//                vec3 g = step(x0.yzx, x0.xyz);
//                vec3 l = 1.0 - g;
//                vec3 i1 = min(g.xyz, l.zxy);
//                vec3 i2 = max(g.xyz, l.zxy);
//                vec3 x1 = x0 - i1 + C.xxx;
//                vec3 x2 = x0 - i2 + C.yyy;
//                vec3 x3 = x0 - D.yyy;
//                i = mod289(i);
//                vec4 p = permute(permute(permute(
//                            i.z + vec4(0.0, i1.z, i2.z, 1.0))
//                        + i.y + vec4(0.0, i1.y, i2.y, 1.0))
//                        + i.x + vec4(0.0, i1.x, i2.x, 1.0));
//                float n_ = 0.142857142857;
//                vec3  ns = n_ * D.wyz - D.xzx;
//                vec4 j = p - 49.0 * floor(p * ns.z);
//                vec4 x_ = floor(j * ns.z);
//                vec4 y_ = floor(j - 7.0 * x_);
//                vec4 x = x_ *ns.x + ns.yyyy;
//                vec4 y = y_ *ns.x + ns.yyyy;
//                vec4 h = 1.0 - abs(x) - abs(y);
//                vec4 b0 = vec4(x.xy, y.xy);
//                vec4 b1 = vec4(x.zw, y.zw);
//                vec4 s0 = floor(b0)*2.0 + 1.0;
//                vec4 s1 = floor(b1)*2.0 + 1.0;
//                vec4 sh = -step(h, vec4(0.0));
//                vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy;
//                vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww;
//                vec3 p0 = vec3(a0.xy, h.x);
//                vec3 p1 = vec3(a0.zw, h.y);
//                vec3 p2 = vec3(a1.xy, h.z);
//                vec3 p3 = vec3(a1.zw, h.w);
//                vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2, p2), dot(p3,p3)));
//                p0 *= norm.x; p1 *= norm.y; p2 *= norm.y; p3 *= norm.w;
//                vec4 m = max(0.6 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0);
//                m = m * m;
//                return 42.0 * dot(m*m, vec4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3)));
//            }
//
//            void main() {
//                vec3 pos = a_Position.xyz;
//                float r = length(pos);
//                if (r > 3.0) {
//                    v_IsDust = 1.0;
//                    vec3 dustPos = pos + vec3(sin(u_Time * 0.2 + pos.x), cos(u_Time * 0.1 + pos.y), sin(u_Time * 0.15 + pos.z)) * 0.5;
//                    gl_Position = u_MVPMatrix * vec4(dustPos, 1.0);
//                    gl_PointSize = 3.0;
//                    return;
//                }
//
//                v_IsDust = 0.0;
//                vec3 normal = normalize(pos);
//                float phi = atan(normal.z, normal.x);
//                float theta = acos(clamp(normal.y, -1.0, 1.0));
//
//                float noiseVal = snoise(1.85 * pos + u_Time * vec3(0.0, 0.2, 0.1));
//                float fftVal = texture(u_FFTTexture, vec2(abs(phi) / (2.0 * 3.14159), 0.0)).r;
//                v_Mag = fftVal;
//
//                float radius = 1.25 + u_BassEnergy * noiseVal * 0.35 + fftVal * sin(theta) * sin(theta) * 0.45;
//                vec3 finalPos = normal * radius;
//                v_Position = finalPos;
//
//                gl_Position = u_MVPMatrix * vec4(finalPos, 1.0);
//                gl_PointSize = max(1.5, (5.0 + fftVal * 10.0 + u_BassEnergy * 4.0) * (2.8 / gl_Position.w));
//            }
//        """
//
//        private const val FRAGMENT_SHADER_ORB = """
//            #version 300 es
//            precision mediump float;
//            in vec3 v_Position;
//            in float v_Mag;
//            in float v_IsDust;
//            out vec4 fragColor;
//            void main() {
//                if (v_IsDust > 0.5) {
//                    fragColor = vec4(0.4, 0.7, 1.0, 0.35);
//                    return;
//                }
//                vec2 coord = gl_PointCoord - vec2(0.5);
//                float dist = length(coord);
//                if (dist > 0.5) discard;
//
//                vec3 magenta = vec3(0.75, 0.10, 0.85);
//                vec3 cyan = vec3(0.0, 0.75, 1.0);
//                float mixFactor = clamp((v_Position.y + 1.25) / 2.5 + 0.25 * v_Position.x, 0.0, 1.0);
//                vec3 color = mix(magenta, cyan, mixFactor);
//
//                float alpha = smoothstep(0.5, 0.0, dist) * 1.8;
//                vec3 flarePos = vec3(0.85, 0.85, 0.85);
//                float flare = smoothstep(1.5, 0.0, distance(v_Position, flarePos)) * 0.9;
//
//                fragColor = vec4(color * (1.8 + v_Mag * 2.2) + vec3(flare), alpha * (0.85 + v_Mag * 0.3));
//            }
//        """
//
//        // ---------------------------------------------------------------------
//        // Visualizer 2: Wavefield Terrain Grid (Image-2 Exact Spec)
//        // ---------------------------------------------------------------------
//        private const val VERTEX_SHADER_TERRAIN = """
//            #version 300 es
//            $COMMON_UNIFORMS
//            layout(location = 0) in vec4 a_Position;
//            out float v_Height;
//            out float v_Depth;
//            out vec3 v_ViewPos;
//            void main() {
//                vec3 pos = a_Position.xyz;
//                float zNorm = (-pos.z - 1.0) / 11.0;
//                float fftVal = texture(u_FFTTexture, vec2(clamp(zNorm, 0.0, 1.0), 0.0)).r;
//
//                float height = 0.45 * sin(1.4 * pos.x + 2.8 * u_Time) * cos(0.9 * pos.z + 1.4 * u_Time)
//                             + 0.20 * sin(2.8 * pos.x - 2.0 * u_Time)
//                             + fftVal * 2.2 * u_MidEnergy;
//
//                pos.y = height;
//                v_Height = height;
//                v_Depth = zNorm;
//
//                vec4 mvPos = u_MVPMatrix * vec4(pos, 1.0);
//                v_ViewPos = mvPos.xyz;
//                gl_Position = mvPos;
//                gl_PointSize = clamp(16.0 / -mvPos.z, 2.5, 14.0);
//            }
//        """
//
//        private const val FRAGMENT_SHADER_TERRAIN = """
//            #version 300 es
//            precision mediump float;
//            in float v_Height;
//            in float v_Depth;
//            in vec3 v_ViewPos;
//            out vec4 fragColor;
//            void main() {
//                vec2 coord = gl_PointCoord - vec2(0.5);
//                float dist = length(coord);
//                if (dist > 0.5) discard;
//
//                vec3 pink = vec3(1.0, 0.05, 0.60);
//                vec3 cyan = vec3(0.0, 0.80, 1.0);
//                vec3 color = mix(pink, cyan, v_Depth + v_Height * 0.25);
//
//                float beta = clamp(abs(-v_ViewPos.z - 4.5) / 5.5, 0.0, 1.0);
//                float alpha = (1.0 - dist * 2.0) * (1.0 - beta * 0.55);
//                fragColor = vec4(color * 2.2, alpha * 0.95);
//            }
//        """
//
//        // ---------------------------------------------------------------------
//        // Visualizer 3: Particle Tunnel Vortex (Image-3 Exact Spec)
//        // ---------------------------------------------------------------------
//        private const val VERTEX_SHADER_VORTEX = """
//            #version 300 es
//            $COMMON_UNIFORMS
//            layout(location = 0) in vec4 a_Position;
//            out float v_Depth;
//            void main() {
//                float ringIdx = a_Position.x;
//                float ptIdx = a_Position.y;
//
//                float zInit = -(ringIdx / 150.0) * 15.0;
//                float zView = mod(zInit + u_Time * 3.5, 15.0) - 15.0;
//
//                float thetaInit = (ptIdx / 180.0) * 6.28318;
//                float thetaTwisted = thetaInit + 0.38 * zView + u_Time * (0.45 + 0.85 * u_TrebleEnergy);
//
//                float fftVal = texture(u_FFTTexture, vec2(abs(zView) / 15.0, 0.0)).r;
//                float radius = 2.2 + 0.30 * sin(6.0 * thetaTwisted + 3.2 * u_Time) + fftVal * 1.4;
//
//                vec3 pos = vec3(cos(thetaTwisted) * radius, sin(thetaTwisted) * radius, zView);
//                v_Depth = abs(zView) / 15.0;
//
//                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
//                gl_PointSize = clamp(18.0 * (1.0 - v_Depth * 0.55), 2.0, 18.0);
//            }
//        """
//
//        private const val FRAGMENT_SHADER_VORTEX = """
//            #version 300 es
//            precision mediump float;
//            in float v_Depth;
//            out vec4 fragColor;
//            void main() {
//                vec2 coord = gl_PointCoord - vec2(0.5);
//                float dist = length(coord);
//                if (dist > 0.5) discard;
//
//                vec3 voidColor = vec3(0.01, 0.0, 0.04);
//                vec3 purple = vec3(0.68, 0.08, 0.98);
//                vec3 cyan = vec3(0.15, 0.85, 1.0);
//
//                vec3 color = mix(voidColor, purple, v_Depth);
//                color = mix(color, cyan, (1.0 - v_Depth));
//
//                float nearClip = smoothstep(0.0, 0.08, v_Depth);
//                float farClip = smoothstep(1.0, 0.88, v_Depth);
//                float alpha = (1.0 - dist * 2.0) * nearClip * farClip * 1.6;
//
//                fragColor = vec4(color * 2.4, alpha);
//            }
//        """
//    }
//}
