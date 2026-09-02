# Image & Animated Media Decoders in MediaNest

This document provides a comprehensive technical overview of the image, animated, and tiled decoders used in MediaNest, detailing their underlying Android platform APIs, memory allocation strategies, and the dynamic smart routing architecture used to prevent Out-Of-Memory (OOM) crashes.

---

## 1. Decoder Architecture Matrix

| Decoder Class | Underlying Android API | Formats Supported | Animation Support | Memory Strategy | Primary Use Case |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`ImageDecoderDecoder`** | `android.graphics.ImageDecoder`<br>`AnimatedImageDrawable` (API 28+) | GIF (<10MB), Animated WebP, Animated HEIF, AVIF | **Yes** | **Pre-allocated native buffers** for all frames in the sequence | High-fidelity stickers, reaction GIFs (<10MB), Animated WebP & AVIF |
| **`GifDecoder`** | `android.graphics.Movie` / Canvas frame streaming | GIF (`image/gif`) (10MB–25MB) | **Yes** | **Streaming frame-by-frame** (~5MB constant reusable buffer) | Medium-to-large animated GIFs (10MB–25MB) |
| **`BitmapFactoryDecoder`** | `android.graphics.BitmapFactory` | JPEG, PNG, WEBP, BMP, GIF (>25MB or 1st frame) | **No** (Static 1st frame only) | **Single frame buffer** in Dalvik/Native or GPU VRAM | Grid thumbnails (when auto-play is OFF), heavy video GIFs (>25MB), crash-safe static fallback |
| **`RegionDecoderEngine`** | `android.graphics.BitmapRegionDecoder` | JPEG, PNG, WEBP, HEIF | **No** (Static only) | **512×512 on-demand tiles** mapped to visible viewport | Ultra-high-resolution deep zooming (>1.0x to 10.0x) on 48MP–200MP photos |

---

## 2. Deep Dive: Decoders & Engines

### A. `ImageDecoderDecoder` (Android OS Native Skia Pipeline)
* **API Origin**: Introduced in Android 9 (API 28 / Pie) via `android.graphics.ImageDecoder`.
* **Output**: Produces an `android.graphics.drawable.AnimatedImageDrawable`.
* **Advantages**:
  * **Hardware Rendering**: Drawn directly on the GPU render thread.
  * **Wide Color Gamuts & Blending**: Preserves Display P3 / sRGB profiles and provides sub-pixel transparency dithering.
  * **120Hz VSYNC**: Frame timing is synchronized with the display refresh rate.
  * **WebP & AVIF**: Full hardware animation support for modern animated image formats.
* **Failure Mode on Oversized Files (>25MB)**:
  * Designed for lightweight UI stickers (2–5 seconds, <10MB).
  * For full-length converted video GIFs (thousands of frames), `ImageDecoder` attempts to pre-allocate uncompressed pixel arrays in native memory all at once. A 200MB GIF requires 2GB–4GB of raw ARGB_8888 pixels, causing immediate `SIGABRT` / `OutOfMemoryError` aborts.

---

### B. `GifDecoder` (Streaming Frame Engine)
* **API Origin**: Coil's software decoder wrapping Android's classic `android.graphics.Movie` stream engine.
* **Advantages**:
  * **Zero Memory Creep**: Does **not** decompress all frames into RAM. It maintains a **single ~5MB frame buffer** and decodes the next frame on-demand as playback advances.
  * **Scale Invariant**: Memory consumption for a 20MB GIF is identical to a 500KB GIF.
* **Threshold**:
  * Optimal for medium GIFs between **10MB and 25MB**.

---

### C. `BitmapFactoryDecoder` & Direct Stream (Static 1st-Frame Fast Path)
* **API Origin**: `android.graphics.BitmapFactory.decodeStream()`.
* **Advantages**:
  * Decodes only the header and the **first image frame**, ignoring all subsequent frames.
  * Extremely fast and supports GPU hardware bitmaps (`Bitmap.Config.HARDWARE`).
* **Use in MediaNest**:
  * Heavy converted video GIFs (>25MB) to instantly render the full-resolution preview in <2MB RAM.
  * Library grid items when "Auto-Play Animated GIFs" setting is disabled.
  * Graceful fallback when an oversized animated file fails to decode.

---

### D. `RegionDecoderEngine` (Tiled Viewport Zoom)
* **API Origin**: `android.graphics.BitmapRegionDecoder`.
* **How it Works**:
  * When opening 48MP–200MP photos, the base layer renders a screen-resolution preview (`Size(2560, 2560)` with hardware acceleration).
  * When pinching to zoom beyond 1.0x, `RegionDecoderEngine` crops and decodes small 512×512 tiles on background threads, discarding off-screen tiles.
* **Important Constraint**:
  * `BitmapRegionDecoder` only supports static image formats (JPEG, PNG, WEBP, HEIF). It throws exceptions on `.gif` files and is automatically bypassed for animated media.

---

## 3. MediaNest Dynamic Smart Routing Architecture

To combine visual fidelity for small animations with crash immunity for giant converted video GIFs, MediaNest uses **dynamic threshold-based routing**:

```
                       Media Item Request
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
       Static Image (JPG/PNG/etc)          Animated Media
               │                               │
        Hardware Bitmaps               Is it WebP / AVIF?
      + RegionDecoder Tiles                    │
                                      ┌────────┴────────┐
                                      ▼ (Yes)           ▼ (No - GIF)
                              ImageDecoderDecoder   File Size Check
                           (AnimatedImageDrawable)      │
                                                ┌───────┼───────┐
                                                ▼       ▼       ▼
                                             < 10MB  10-25MB  > 25MB
                                                │       │       │
                             ImageDecoderDecoder┘       │       │
                                (Native HW 120Hz)       │       │
                                                        │       │
                                             GifDecoder─┘       │
                                              (Streaming)       │
                                                                │
                                            BitmapFactoryDecoder┘
                                            (1st Frame Static Preview)
                                                        │
                                                        ▼ (Tap "Play Full Video")
                                            VideoPlayerActivity
                                            (FFmpeg Native Video Engine)
```

### Implementation Details:
1. **Grid Thumbnails (`MediaGridItem.kt`)**:
   * If `item.size >= 25MB` or `autoPlayGifPreviews == false`: Uses `BitmapFactoryDecoder.Factory()`.
   * If `10MB <= item.size < 25MB`: Uses `GifDecoder.Factory(enforceMinimumFrameDelay = true)`.
   * If `item.size < 10MB` or `isAnimatedWebpOrAvif`: Uses `ImageDecoderDecoder.Factory()` on API 28+.
2. **Full-Screen Viewer (`HybridImageViewer.kt`)**:
   * Queries file size (`statSize` / `openAssetFileDescriptor`).
   * Routes files `< 10MB` (and Animated WebP/AVIF) to `ImageDecoderDecoder`.
   * Routes files `10MB – 25MB` to `GifDecoder`.
   * For files `> 25MB`: Uses background streaming `BitmapFactory` to load the preview in <20ms, showing a floating **"Large GIF (200MB) • Play Full Video"** button.
   * Bypasses `BitmapRegionDecoder` and tile scheduler when `isGif == true`.
3. **Video Player (`ExoPlayerManager.kt`)**:
   * Automatically falls back to native FFmpeg demuxer on container unsupported errors (`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`), playing full video GIFs as hardware-accelerated video streams with seekable timelines.
