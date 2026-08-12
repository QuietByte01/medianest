package com.example.ui.components

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class AudioVisualizerGLSurfaceView(context: Context, private val styleProvider: () -> VisualizerStyle) : GLSurfaceView(context) {
    private val renderer: AudioVisualizerRenderer

    init {
        setEGLContextClientVersion(3)
        renderer = AudioVisualizerRenderer(styleProvider)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun updateAudioData(bassEnergy: Float, amplitude: Float, timeSec: Float, bands: FloatArray) {
        renderer.updateAudio(bassEnergy, amplitude, timeSec, bands)
    }
}

class AudioVisualizerRenderer(private val styleProvider: () -> VisualizerStyle) : GLSurfaceView.Renderer {
    private var programBars = 0
    private var programRing = 0
    private var programWave = 0
    private var programParticles = 0
    private var programGlobe = 0
    private var programTerrain = 0
    private var programVortex = 0

    private lateinit var sphereVertexBuffer: FloatBuffer
    private var sphereVertexCount = 0
    private lateinit var bandsBuffer: FloatBuffer

    private var bassEnergy = 0.5f
    private var amplitude = 0.5f
    private var timeSec = 0f
    private var bands = FloatArray(32)

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun updateAudio(bass: Float, amp: Float, time: Float, audioBands: FloatArray) {
        bassEnergy = bass
        amplitude = amp
        timeSec = time
        bands = audioBands
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.01f, 0.01f, 0.03f, 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE) // Additive blending for glow

        programBars = createProgram(VERTEX_SHADER_BARS, FRAGMENT_SHADER_GLOW)
        programRing = createProgram(VERTEX_SHADER_RING, FRAGMENT_SHADER_GLOW)
        programWave = createProgram(VERTEX_SHADER_WAVE, FRAGMENT_SHADER_GLOW)
        programParticles = createProgram(VERTEX_SHADER_PARTICLES, FRAGMENT_SHADER_GLOW)
        programGlobe = createProgram(VERTEX_SHADER_GLOBE, FRAGMENT_SHADER_GLOBE)
        programTerrain = createProgram(VERTEX_SHADER_TERRAIN, FRAGMENT_SHADER_TERRAIN)
        programVortex = createProgram(VERTEX_SHADER_VORTEX, FRAGMENT_SHADER_VORTEX)

        setupSphereData()
        bandsBuffer = ByteBuffer.allocateDirect(32 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3.5f, 0f, 0f, 0f, 0f, 1f, 0f)
        bandsBuffer.clear()
        bandsBuffer.put(bands)
        bandsBuffer.position(0)

        when (styleProvider()) {
            VisualizerStyle.GLOSSY_SPECTRUM_BARS -> drawBars()
            VisualizerStyle.CIRCULAR_RING_WAVE -> drawRing()
            VisualizerStyle.NEON_WAVEFORM -> drawWave()
            VisualizerStyle.BASS_PARTICLE_HALO -> drawParticles()
            VisualizerStyle.PARTICLE_GLOBE_SPHERE -> drawGlobe()
            VisualizerStyle.WAVE_GRID_TERRAIN -> drawTerrain()
            VisualizerStyle.PARTICLE_TUNNEL_VORTEX -> drawVortex()
        }
    }

    private fun setupSphereData() {
        val latSteps = 40
        val lonSteps = 80
        val vertices = ArrayList<Float>()
        for (lat in 0..latSteps) {
            val theta = lat * Math.PI / latSteps
            val sinTheta = sin(theta).toFloat()
            val cosTheta = cos(theta).toFloat()
            for (lon in 0..lonSteps) {
                val phi = lon * 2 * Math.PI / lonSteps
                vertices.add(sinTheta * cos(phi).toFloat())
                vertices.add(cosTheta)
                vertices.add(sinTheta * sin(phi).toFloat())
            }
        }
        sphereVertexCount = vertices.size / 3
        sphereVertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        sphereVertexBuffer.put(vertices.toFloatArray()).position(0)
    }

    private fun drawBars() {
        GLES30.glUseProgram(programBars)
        updateUniforms(programBars)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 32)
    }

    private fun drawRing() {
        GLES30.glUseProgram(programRing)
        updateUniforms(programRing)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 64)
    }

    private fun drawWave() {
        GLES30.glUseProgram(programWave)
        updateUniforms(programWave)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 100)
    }

    private fun drawParticles() {
        GLES30.glUseProgram(programParticles)
        updateUniforms(programParticles)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 64)
    }

    private fun drawGlobe() {
        GLES30.glUseProgram(programGlobe)
        updateUniforms(programGlobe)
        val posLoc = GLES30.glGetAttribLocation(programGlobe, "a_Position")
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 3, GLES30.GL_FLOAT, false, 0, sphereVertexBuffer)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, sphereVertexCount)
        GLES30.glDisableVertexAttribArray(posLoc)
    }

    private fun drawTerrain() {
        GLES30.glUseProgram(programTerrain)
        updateUniforms(programTerrain)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 1200)
    }

    private fun drawVortex() {
        GLES30.glUseProgram(programVortex)
        updateUniforms(programVortex)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 2000)
    }

    private fun updateUniforms(program: Int) {
        val mvpLoc = GLES30.glGetUniformLocation(program, "u_MVPMatrix")
        val timeLoc = GLES30.glGetUniformLocation(program, "u_Time")
        val bassLoc = GLES30.glGetUniformLocation(program, "u_BassEnergy")
        val bandsLoc = GLES30.glGetUniformLocation(program, "u_Bands")

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        GLES30.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(timeLoc, timeSec)
        GLES30.glUniform1f(bassLoc, bassEnergy)
        GLES30.glUniform1fv(bandsLoc, 32, bandsBuffer)
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentCode)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val COMMON_UNIFORMS = """
            uniform mat4 u_MVPMatrix;
            uniform float u_Time;
            uniform float u_BassEnergy;
            uniform float u_Bands[32];
        """

        private const val VERTEX_SHADER_BARS = """
            #version 300 es
            $COMMON_UNIFORMS
            void main() {
                float id = float(gl_VertexID);
                float val = u_Bands[gl_VertexID % 32];
                vec3 pos = vec3((id / 16.0) - 1.0, (val * 2.0) - 1.0, 0.0);
                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
                gl_PointSize = 20.0 * val + 5.0;
            }
        """

        private const val VERTEX_SHADER_RING = """
            #version 300 es
            $COMMON_UNIFORMS
            void main() {
                float id = float(gl_VertexID);
                float angle = (id / 64.0) * 6.28318;
                float val = u_Bands[int(mod(id, 32.0))];
                float r = 0.8 + val * 0.4;
                vec3 pos = vec3(cos(angle) * r, sin(angle) * r, 0.0);
                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
                gl_PointSize = 10.0 * val + 4.0;
            }
        """

        private const val VERTEX_SHADER_WAVE = """
            #version 300 es
            $COMMON_UNIFORMS
            void main() {
                float id = float(gl_VertexID);
                float x = (id / 100.0) * 2.0 - 1.0;
                float val = u_Bands[int(mod(id, 32.0))];
                float y = sin(x * 5.0 + u_Time * 5.0) * val;
                gl_Position = u_MVPMatrix * vec4(x, y, 0.0, 1.0);
                gl_PointSize = 6.0;
            }
        """

        private const val VERTEX_SHADER_PARTICLES = """
            #version 300 es
            $COMMON_UNIFORMS
            void main() {
                float id = float(gl_VertexID);
                float angle = id * 137.5 + u_Time * 0.5;
                float r = mod(id * 0.1 + u_Time * 0.2, 2.0);
                float val = u_Bands[int(mod(id, 32.0))];
                vec3 pos = vec3(cos(angle) * r, sin(angle) * r, 0.0);
                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
                gl_PointSize = 5.0 * val + 2.0;
            }
        """

        private const val VERTEX_SHADER_GLOBE = """
            #version 300 es
            $COMMON_UNIFORMS
            in vec4 a_Position;
            out vec3 v_Pos;
            out float v_Mag;
            void main() {
                v_Pos = a_Position.xyz;
                float angle = atan(v_Pos.z, v_Pos.x);
                int idx = int(mod(angle * 5.0 + u_Time, 32.0));
                v_Mag = u_Bands[idx];
                vec3 pos = v_Pos * (1.0 + v_Mag * 0.35 + u_BassEnergy * 0.15);
                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
                gl_PointSize = max(2.0, (4.0 + 3.0 * v_Mag) * (2.0 / gl_Position.w));
            }
        """

        private const val FRAGMENT_SHADER_GLOBE = """
            #version 300 es
            precision mediump float;
            in vec3 v_Pos;
            in float v_Mag;
            out vec4 fragColor;
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                if (length(coord) > 0.5) discard;
                vec3 cyan = vec3(0.0, 0.9, 1.0);
                vec3 magenta = vec3(1.0, 0.1, 0.8);
                vec3 color = mix(magenta, cyan, v_Pos.x * 0.5 + 0.5);
                float glow = smoothstep(0.5, 0.0, length(coord));
                fragColor = vec4(color * (1.5 + v_Mag), glow);
            }
        """

        private const val VERTEX_SHADER_TERRAIN = """
            #version 300 es
            $COMMON_UNIFORMS
            out float v_Height;
            void main() {
                float id = float(gl_VertexID);
                float x = mod(id, 40.0) - 20.0;
                float z = floor(id / 40.0) - 15.0;
                float dist = length(vec2(x, z));
                int idx = int(mod(dist + u_Time * 2.0, 32.0));
                v_Height = u_Bands[idx] * (1.0 + u_BassEnergy);
                vec3 pos = vec3(x * 0.25, v_Height - 0.8, z * 0.3 - 2.0);
                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
                gl_PointSize = max(1.0, 6.0 * (1.0 / gl_Position.w));
            }
        """

        private const val FRAGMENT_SHADER_TERRAIN = """
            #version 300 es
            precision mediump float;
            in float v_Height;
            out vec4 fragColor;
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                if (length(coord) > 0.5) discard;
                vec3 color = mix(vec3(0.4, 0.0, 0.8), vec3(0.0, 1.0, 1.0), v_Height);
                fragColor = vec4(color * 1.5, smoothstep(0.5, 0.0, length(coord)));
            }
        """

        private const val VERTEX_SHADER_VORTEX = """
            #version 300 es
            $COMMON_UNIFORMS
            out float v_Depth;
            void main() {
                float id = float(gl_VertexID);
                float ring = floor(id / 64.0);
                float angle = mod(id, 64.0) / 64.0 * 6.28 + u_Time + ring * 0.2;
                float t = mod(ring * 0.1 + u_Time * 0.4, 4.0);
                float r = t * (1.0 + u_BassEnergy * 0.2);
                int idx = int(mod(ring, 32.0));
                float mag = u_Bands[idx];
                vec3 pos = vec3(cos(angle) * r, sin(angle) * r, -t);
                v_Depth = t / 4.0;
                gl_Position = u_MVPMatrix * vec4(pos, 1.0);
                gl_PointSize = max(2.0, (8.0 - t * 2.0) * (1.0 + mag));
            }
        """

        private const val FRAGMENT_SHADER_VORTEX = """
            #version 300 es
            precision mediump float;
            in float v_Depth;
            out vec4 fragColor;
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                if (length(coord) > 0.5) discard;
                vec3 color = mix(vec3(0.0, 1.0, 1.0), vec3(1.0, 0.0, 0.8), v_Depth);
                fragColor = vec4(color * 2.0, smoothstep(0.5, 0.0, length(coord)) * (1.0 - v_Depth));
            }
        """

        private const val FRAGMENT_SHADER_GLOW = """
            #version 300 es
            precision mediump float;
            out vec4 fragColor;
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                if (length(coord) > 0.5) discard;
                float glow = smoothstep(0.5, 0.0, length(coord));
                fragColor = vec4(1.0, 1.0, 1.0, glow);
            }
        """
    }
}
