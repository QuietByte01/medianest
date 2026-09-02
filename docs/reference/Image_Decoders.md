# Image & Animated Media Decoders in MediaNest

This document provides a comprehensive technical overview of the image, animated, and tiled decoders used in MediaNest, detailing their underlying Android platform APIs, memory allocation strategies, and the dynamic smart routing architecture used to prevent Out-Of-Memory (OOM) crashes.

---

## 1. Decoder Architecture Matrix

| Decoder Class | Underlying Android API | Formats Supported | Animation Support | Memory Strategy | Primary Use Case |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`ImageDecoderDecoder`** | `android.graphics.ImageDecoder`<br>`AnimatedImageDrawable` (API 28+) | GIF, Animated WebP, Animated HEIF, AVIF, JPEG, PNG | **Yes** | **Pre-allocated native buffers** for all frames in the sequence | High-fidelity stickers, reaction GIFs (<10MB), Animated WebP |
| **`GifDecoder`** | `android.graphics.Movie` / Canvas frame streaming | GIF (`image/gif`) | **Yes** | **Streaming frame-by-frame** (~5MB constant reusable buffer) | Large converted video GIFs (10MB–500MB+), screen captures |
| **`BitmapFactoryDecoder`** | `android.graphics.BitmapFactory` | JPEG, PNG, WEBP, BMP, GIF (1st frame) | **No** (Static 1st frame only) | **Single frame buffer** in Dalvik/Native or GPU VRAM | Grid thumbnails (when auto-play is OFF), crash-safe static fallback |
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
* **Failure Mode on Oversized Files (>10MB–20MB)**:
  * Designed for lightweight UI stickers (2–5 seconds, <10MB).
  * For full-length converted video GIFs (thousands of frames), `ImageDecoder` attempts to pre-allocate uncompressed pixel arrays in native memory all at once. A 200MB GIF requires 2GB–4GB of raw ARGB_8888 pixels, causing immediate `SIGABRT` / `OutOfMemoryError` aborts.

---

### B. `GifDecoder` (Streaming Frame Engine)
* **API Origin**: Coil's software decoder wrapping Android's classic `android.graphics.Movie` stream engine.
* **Advantages**:
  * **Zero Memory Creep**: Does **not** decompress all frames into RAM. It maintains a **single ~5MB frame buffer** and decodes the next frame on-demand as playback advances.
  * **Scale Invariant**: Memory consumption for a 500MB video GIF is identical to a 500KB GIF.
* **Trade-Off**:
  * Requires software canvas drawing (`allowHardware(false)`).
  * Limited to `.gif` (does not decode Animated WebP).

---

### C. `BitmapFactoryDecoder` (Static 1st-Frame Fast Path)
* **API Origin**: `android.graphics.BitmapFactory.decodeStream()`.
* **Advantages**:
  * Decodes only the header and the **first image frame**, ignoring all subsequent frames.
  * Extremely fast and supports GPU hardware bitmaps (`Bitmap.Config.HARDWARE`).
* **Use in MediaNest**:
  * Library grid items when "Auto-Play Animated GIFs" setting is disabled.
  * Graceful fallback when an oversized/damaged animated file fails to decode.

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
                                                ┌───────┴───────┐
                                                ▼ (<10MB)       ▼ (>=10MB)
                                       ImageDecoderDecoder   GifDecoder
                                           (Native HW)       (Streaming)
                                                │               │
                                                └───────┬───────┘
                                                        ▼ (On Error)
                                              BitmapFactoryDecoder
                                              (1st Static Frame)
```

### Implementation Details:
1. **Grid Thumbnails (`MediaGridItem.kt`)**:
   * If `autoPlayGifPreviews == false`: Uses `BitmapFactoryDecoder.Factory()`.
   * If `item.size >= 10MB`: Uses `GifDecoder.Factory(enforceMinimumFrameDelay = true)`.
   * If `item.size < 10MB`: Uses `ImageDecoderDecoder.Factory()` on API 28+.
2. **Full-Screen Viewer (`HybridImageViewer.kt`)**:
   * Queries file descriptor size (`statSize`).
   * Routes files `<10MB` to `ImageDecoderDecoder` and files `≥10MB` to `GifDecoder`.
   * Bypasses `BitmapRegionDecoder` and tile scheduler when `isGif == true`.
   * Falls back to `BitmapFactoryDecoder` if decoding ever encounters memory strain.
