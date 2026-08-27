package com.medianest.ui.image.hybrid

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
fun HybridGLSurface(
    modifier: Modifier = Modifier,
    viewportState: ViewportState,
    tileManager: TileManager,
    decoderEngine: RegionDecoderEngine
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            HybridGLSurfaceView(context).apply {
                this.viewportState = viewportState
                this.tileManager = tileManager
                this.decoderEngine = decoderEngine
            }
        },
        update = { view ->
            // Read state to force recomposition when zooming/panning
            val s = viewportState.scale
            val o = viewportState.offset
            view.requestRender()
        }
    )
}

class HybridGLSurfaceView(context: Context) : GLSurfaceView(context) {
    var viewportState: ViewportState? = null
        set(value) { field = value; renderer.viewportState = value }
    var tileManager: TileManager? = null
        set(value) { field = value; renderer.tileManager = value }
    var decoderEngine: RegionDecoderEngine? = null
        set(value) { field = value; renderer.decoderEngine = value }
    var tileCache: TileCache? = null
        set(value) { field = value; renderer.tileCache = value }

    private val renderer = HybridRenderer { requestRender() }

    init {
        setEGLContextClientVersion(3)
        setZOrderOnTop(true)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}

class HybridRenderer(private val requestRenderCallback: () -> Unit) : GLSurfaceView.Renderer {

    var viewportState: ViewportState? = null
    var tileManager: TileManager? = null
    var decoderEngine: RegionDecoderEngine? = null
    var tileCache: TileCache? = null

    // GLSL 3.00 ES Shader for Cross-Fading and Unsharp Masking (USM)
    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec4 aPosition;
        layout(location = 1) in vec2 aTexCoord;
        uniform mat4 uMVPMatrix;
        out vec2 vTexCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        precision highp float;
        
        in vec2 vTexCoord;
        // uniform sampler2D uBaseTexture; // Disabling base texture since we use AsyncImage fallback
        uniform sampler2D uHighResTile;
        uniform float uAlphaFade; // 0.0 to 1.0 crossfade
        uniform float uScaleFactor; // Current zoom scale
        
        out vec4 fragColor;
        
        // Simple 3x3 Unsharp Mask / Sharpening kernel
        vec4 applySharpening(sampler2D tex, vec2 uv) {
            vec2 texOffset = vec2(1.0 / 512.0, 1.0 / 512.0); // Assuming 512x512 tile
            vec4 center = texture(tex, uv);
            
            // Only apply if zoomed in (scale > 1.0)
            if (uScaleFactor <= 1.0) return center;
            
            float amount = 0.25; // 0.15 to 0.35 intensity
            vec4 top = texture(tex, uv + vec2(0.0, -texOffset.y));
            vec4 bottom = texture(tex, uv + vec2(0.0, texOffset.y));
            vec4 left = texture(tex, uv + vec2(-texOffset.x, 0.0));
            vec4 right = texture(tex, uv + vec2(texOffset.x, 0.0));
            
            vec4 edgeDetection = (center * 4.0) - (top + bottom + left + right);
            return center + (edgeDetection * amount);
        }

        void main() {
            vec4 highResColor = applySharpening(uHighResTile, vTexCoord);
            fragColor = vec4(highResColor.rgb, highResColor.a * uAlphaFade);
        }
    """.trimIndent()

    private var shaderProgram: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    
    private val vbo = IntArray(1)
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    // Texture IDs map
    private val textureMap = mutableMapOf<String, Int>()
    // Bitmaps waiting to be uploaded to GL on the render thread
    private val uploadQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, android.graphics.Bitmap>>()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        
        // Compile Shaders
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        shaderProgram = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertexShader)
            GLES30.glAttachShader(it, fragmentShader)
            GLES30.glLinkProgram(it)
        }
        
        positionHandle = GLES30.glGetAttribLocation(shaderProgram, "aPosition")
        texCoordHandle = GLES30.glGetAttribLocation(shaderProgram, "aTexCoord")
        mvpMatrixHandle = GLES30.glGetUniformLocation(shaderProgram, "uMVPMatrix")
        
        // Quad vertices (x, y, u, v) from (0,0) to (1,1)
        val quadVertices = floatArrayOf(
            0f, 0f, 0f, 0f,
            1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
        val vertexBuffer = java.nio.ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        vertexBuffer.position(0)
        
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadVertices.size * 4, vertexBuffer, GLES30.GL_STATIC_DRAW)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        // Ortho projection mapping (0,0) to Top-Left and (width,height) to Bottom-Right
        android.opengl.Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Process pending texture uploads on the GL Thread
        while (uploadQueue.isNotEmpty()) {
            val (tileId, bitmap) = uploadQueue.poll() ?: break
            val textureHandle = IntArray(1)
            GLES30.glGenTextures(1, textureHandle, 0)
            if (textureHandle[0] != 0) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0])
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                try {
                    android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
                } catch (e: IllegalArgumentException) {
                    val swBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                    if (swBitmap != null) {
                        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, swBitmap, 0)
                        swBitmap.recycle()
                    }
                }
                textureMap[tileId] = textureHandle[0]
            }
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(shaderProgram)
        
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, 16, 8)
        
        // Wire ViewportState and TileManager
        val state = viewportState ?: return
        val manager = tileManager ?: return
        val decoder = decoderEngine ?: return
        
        val fitScaleX = state.contentSize.width / manager.imageSize.width
        val fitScaleY = state.contentSize.height / manager.imageSize.height
        val fitScale = minOf(fitScaleX, fitScaleY).takeIf { !it.isNaN() && it > 0f } ?: 1f
        
        val imageLeft = (state.viewportSize.width - state.contentSize.width) / 2f
        val imageTop = (state.viewportSize.height - state.contentSize.height) / 2f
        val originX = state.viewportSize.width / 2f
        val originY = state.viewportSize.height / 2f
        
        // Map viewport corners (0,0) and (width,height) back to intrinsic image coordinates
        val left1x = (0f - state.offset.x - originX) / state.scale + originX
        val top1x = (0f - state.offset.y - originY) / state.scale + originY
        val right1x = (state.viewportSize.width - state.offset.x - originX) / state.scale + originX
        val bottom1x = (state.viewportSize.height - state.offset.y - originY) / state.scale + originY
        
        val intrinsicViewportBounds = androidx.compose.ui.geometry.Rect(
            (left1x - imageLeft) / fitScale,
            (top1x - imageTop) / fitScale,
            (right1x - imageLeft) / fitScale,
            (bottom1x - imageTop) / fitScale
        )
        
        val scaleLoc = GLES30.glGetUniformLocation(shaderProgram, "uScaleFactor")
        val alphaLoc = GLES30.glGetUniformLocation(shaderProgram, "uAlphaFade")
        val highResTileLoc = GLES30.glGetUniformLocation(shaderProgram, "uHighResTile")
        
        GLES30.glUniform1f(scaleLoc, state.scale)
        GLES30.glUniform1f(alphaLoc, 1.0f) // Hardcoded 1.0f alpha for now
        GLES30.glUniform1i(highResTileLoc, 1) // Texture unit 1

        val visibleTiles = manager.calculateVisibleTiles(intrinsicViewportBounds, state.scale * fitScale)
        
        for (tile in visibleTiles) {
            val tileId = "${tile.sampleSize}_${tile.x}_${tile.y}"
            if (!textureMap.containsKey(tileId)) {
                decoder.decodeTileAsync(tile) { bitmap ->
                    uploadQueue.offer(Pair(tileId, bitmap))
                    requestRenderCallback()
                }
            }
        }
        
        for (tile in visibleTiles) {
            val tileId = "${tile.sampleSize}_${tile.x}_${tile.y}"
            val textureId = textureMap[tileId]
            if (textureId != null) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
                
                // Map tile from intrinsic coordinates to final screen coordinates
                // 1. Calculate fit scale
                val fitScaleX = state.contentSize.width / manager.imageSize.width
                val fitScaleY = state.contentSize.height / manager.imageSize.height
                val fitScale = minOf(fitScaleX, fitScaleY).takeIf { !it.isNaN() && it > 0f } ?: 1f
                
                // 2. Base 1x screen position (centered)
                val imageLeft = (state.viewportSize.width - state.contentSize.width) / 2f
                val imageTop = (state.viewportSize.height - state.contentSize.height) / 2f
                
                val tile1xX = imageLeft + tile.bounds.left * fitScale
                val tile1xY = imageTop + tile.bounds.top * fitScale
                val tile1xW = tile.bounds.width * fitScale
                val tile1xH = tile.bounds.height * fitScale
                
                // 3. Apply viewport scale and offset around Center origin
                val originX = state.viewportSize.width / 2f
                val originY = state.viewportSize.height / 2f
                
                val screenX = (tile1xX - originX) * state.scale + originX + state.offset.x
                val screenY = (tile1xY - originY) * state.scale + originY + state.offset.y
                val screenW = tile1xW * state.scale
                val screenH = tile1xH * state.scale
                
                android.opengl.Matrix.setIdentityM(modelMatrix, 0)
                android.opengl.Matrix.translateM(modelMatrix, 0, screenX, screenY, 0f)
                android.opengl.Matrix.scaleM(modelMatrix, 0, screenW, screenH, 1f)
                
                android.opengl.Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
                GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
                
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }
        
        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glDisableVertexAttribArray(texCoordHandle)
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, shaderCode)
            GLES30.glCompileShader(shader)
        }
    }
}
