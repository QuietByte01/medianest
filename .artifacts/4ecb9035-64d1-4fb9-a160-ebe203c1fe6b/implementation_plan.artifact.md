# Hardware Accelerated Film Grain Plan

Implement a high-performance, hardware-accelerated film grain effect using a noise texture shader approach. This replaces the expensive 3000-point loop with a single GPU draw call.

---

## Proposed Changes

### Video Player Enhancements

#### [MODIFY] [VideoPlayerScreen.kt](file:///Users/sachin/medianest/app/src/main/java/com/example/ui/videoplayer/VideoPlayerScreen.kt)
- **Optimize `FilmGrainOverlay`**:
    - Procedurally generate a small noise `Bitmap` (128x128) and `remember` it.
    - Create a `BitmapShader` from this bitmap with `Shader.TileMode.REPEAT`.
    - Use the GPU's hardware acceleration to draw the shader over the entire `Canvas` in a single call.
    - Animate a randomized `Offset` and `Alpha` to create a natural cinematic flicker without taxing the CPU.

---

## Verification Plan

### Manual Verification
1. **Performance**: Play a 4K video and enable Film Grain. Verify the player remains perfectly smooth (60fps) with no stuttering.
2. **Visuals**: Verify the film grain looks more realistic (dense static texture) rather than distinct dots.
3. **Control**: Use the "Grain Intensity" slider in settings and verify it scales the visibility of the noise texture correctly.
