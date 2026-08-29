# Comprehensive Android High-Resolution Imaging & Gallery Guide

---

## 1. Android Image Decoding Architecture

`ImageDecoder` is the modern, unified API in Android designed to convert encoded image formats (PNG, JPEG, WebP, GIF, HEIF) into usable graphic objects or raw pixel buffers.

Depending on the development stack (Kotlin/Java vs. C++ NDK), Android offers two implementations:

### A. Java / Kotlin API (`android.graphics.ImageDecoder`)

Introduced to supersede legacy classes like `BitmapFactory` and `BitmapFactory.Options`, `ImageDecoder` provides a safer, consistent framework with built-in modern feature support.

* **Automatic Format Handling:** Decodes modern formats natively without manual configuration.
* **Drawables & Bitmaps:** Directly produces standard `Bitmap` instances or `Drawable` objects (including `AnimatedImageDrawable` for animated GIFs and WebP).
* **Post-processing:** Supports custom Canvas operations (e.g., rounded corners, custom masks, color filters) directly inside the decode pipeline.
* **Scaling & Cropping:** Allows dynamic downsampling or cropping during the decode step to minimize memory allocations.

#### Basic Usage Example (Kotlin)

```kotlin
val source = ImageDecoder.createSource(contentResolver, imageUri)
val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, src ->
    // Optional: Scale down the image during decode
    decoder.setTargetSampleSize(2) 
}

// Handle animations if the asset is an animated GIF/WebP
if (drawable is AnimatedImageDrawable) {
    drawable.start()
}

```

### B. Native C/C++ NDK API (`AImageDecoder`)

Designed for high-performance native applications, game engines, or media pipelines using C/C++ (available starting with **API level 30 / Android 11**).

* **Zero JNI Overhead:** Decodes straight into client-allocated memory buffers without passing data through the Java runtime.
* **Direct Asset/FD Support:** Ingests raw data from `AAssetManager`, native file descriptors, or raw byte buffers.
* **Memory Efficiency:** Enables streaming raw pixel data directly into native rendering APIs such as OpenGL ES or Vulkan.

### Selection Matrix

* **Use `android.graphics.ImageDecoder`:** For standard Android application development in Kotlin or Java.
* **Use `AImageDecoder`:** Exclusively for performance-critical native C/C++ pipelines via the Android NDK.

---

## 2. Multi-Page Formats & Telephoto Lens Contexts

Handling high-resolution media (e.g., 12MP to 200MP captures from telephoto sensors, deep P3 wide color gamuts, multi-page HEIF/AVIF, bracketed exposures, or burst sequences) requires specialized loading strategies.

### Multi-Page Handling

* **In Kotlin/Java (`android.graphics.ImageDecoder`):** Extracts primary images cleanly and automates animated frame sequences via `AnimatedImageDrawable`. For static multi-frame containers requiring manual index selection, container parsing or fallback APIs are needed.
* **In Native C/C++ (`AImageDecoder`):** Efficiently iterates through multi-frame sequences natively without allocating intermediate Java-side heap buffers.

### The Telephoto Challenge: Memory & Zoom

Loading massive assets directly into viewport containers will trigger `OutOfMemoryError` (OOM). Two complementary techniques mitigate this:

1. **Downsampling for Grid/Thumbnails:** Never decode full-resolution assets for preview grids. Compute an optimal sample size:
```kotlin
val source = ImageDecoder.createSource(contentResolver, telephotoUri)
val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
    decoder.setTargetSampleSize(calculateSampleSize(info.size, targetWidth, targetHeight))
}

```


2. **Region Decoding for Detail View:** Do not simply scale up the low-resolution thumbnail when zooming. Reuse the image source or use region-based decoding (`BitmapRegionDecoder` / tile streamers) to decode only the high-resolution sub-rectangles visible in the current viewport.

### Summary Checklist for a Telephoto Gallery App

1. **Source Creation:** Retain a reusable `ImageDecoder.Source` or file descriptor.
2. **Grid View (Thumbnails):** Use `ImageDecoder` with `setTargetSampleSize()` for fluid scrolling.
3. **Detail/Zoom View:** Transition to dynamic regional/subsampled tile rendering to preserve optical resolution without memory bloat.

---

## 3. High-Resolution Zooming & Subsampling Architectures

### The Core Problem

* **Standard Downsampling (Coil/Glide):** Resizes an image to the device display (e.g., 1080p). Pinch-to-zoom merely enlarges these low-resolution pixels, creating blurriness.
* **Direct Full-Scale Decode:** Loading an uncompressed 50MP–100MP image consumes hundreds of megabytes of RAM, triggering an immediate `OutOfMemoryError` (OOM).
* **The Subsampling Solution:** Inspects dimensions via headers first, then uses low-level region decoders to extract only the visible tile coordinates at native resolution.

```
[50MB / 100MP Image on Disk]
            │
            ▼
[BitmapRegionDecoder] ─── Reads image header (dimensions) ONLY
            │
            ▼
[Viewport Calculation] ── Only decodes visible Rect(left, top, right, bottom)
            │
            ▼
[Screen RAM Buffer] ──── Only 5–15 MB active in memory at any zoom level

```

* **Header-First Loading:** Avoids allocating memory for the full pixel grid.
* **On-Demand Rect Decoding:** When panning or zooming, only the viewport’s visible bounding box (`Rect`) is pulled into memory and drawn.

---

## 4. MediaNest Production Hybrid Image Viewer Architecture

MediaNest implements a Google Photos / Samsung Gallery-grade **Hybrid 2-Layer Image Viewer** (`com.medianest.ui.image.hybrid`) designed for smooth pan/pinch, pull-to-dismiss physics, deep zoom up to 25x, and crystal-clear tile rendering without memory bloat or visual flickering.

### Core Architecture (`HybridImageViewer`)

```
                      [ User Touch Gestures ]
                                │
                 [ GooglePhotosPhysics / ViewportState ]
                                │
         ┌──────────────────────┴──────────────────────┐
         ▼                                             ▼
  [ Layer 1: Base Image ]                   [ Layer 2: Deep Zoom Tiles ]
  - Coil AsyncImage                         - BitmapRegionDecoder Engine
  - Full-res render (software bitmap)       - 1024px dynamic visible tiles
  - graphicsLayer (scale, offset)           - TileCache (2-tier L1/L2)
  - Always active for instant render        - FilterQuality.High (Bicubic)
                                            - Preloads at 1.0x, fades in at 1.1x
```

### Subsystems & Responsibilities

1. **`ViewportState`**:
   - Manages interactive transforms (`scale`, `offset`, `contentSize`, `viewportSize`).
   - Supports fluid double-tap zoom toggles (smart fit vs 2.5x zoom centroid targeting).
   - Handles pull-to-dismiss physics with spring animations (`DampingRatioNoBouncy`).
   - Clamps pan translation to image bounds when zoomed in.

2. **`GesturePhysics` (`detectGooglePhotosGestures`)**:
   - Single-pointer at 1.0x: Restricts gestures so lateral movements pass directly to parent `HorizontalPager`, downward drag triggers pull-to-dismiss, and upward swipe opens the info bottom sheet.
   - Multi-touch or zoomed state (`scale > 1.05x`): Transitions smoothly to 2D pan and pinch-to-zoom with inertia and velocity decay.

3. **`RegionDecoderEngine`**:
   - Asynchronously decodes high-res sub-rectangles via Android native `BitmapRegionDecoder`.
   - Direct `ARGB_8888` decode configuration with Display P3 color space preservation.
   - Mutex-protected thread-safe decoding dispatched onto `Dispatchers.IO`.

4. **`TileManager`**:
   - Calculates visible grid tiles based on intrinsic image coordinates and current scale factor.
   - Uses `1024px` tile sizing to minimize tile seam overhead and maximize GPU cache efficiency.
   - Computes optimal power-of-two `inSampleSize` (1, 2, 4, 8) dynamically.

5. **`TileCache`**:
   - 2-Tier memory caching:
     - **L1 Cache**: `HardwareBuffer` / GraphicBuffers for zero-copy GPU texturing.
     - **L2 Cache**: High-resolution `Bitmap` cache using 1/8th of total app memory.

6. **Zero-Flicker Sharpness Optimizations**:
   - **Immediate Preload at 1.0x**: Pre-decodes tiles as soon as zoom begins.
   - **Crossfade at 1.1x**: Animates tile layer alpha smoothly over 120ms (`Animatable`) once tiles arrive, preventing hard cut pop-in.
   - **Safe Stale Eviction**: Retains previous resolution tiles until all new sample-size tiles are fully decoded and ready.
   - **Bicubic Rendering (`FilterQuality.High`)**: Ensures sub-pixel sharpness matching Samsung Gallery and Google Photos.

---

## 5. Implementation Options & Comparison

### Comparison of Android Image Zooming Approaches

| Approach | Max Clarity on Zoom | Memory Usage (RAM) | Gesture / Pager Handling | Compose Native? | Best For |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MediaNest `HybridImageViewer`** | ✔️ **Crystal Clear (Bicubic 1024px Tiles)** | Minimal (5–15 MB active tile buffer) | Full Google Photos-style physics + Pager lock + Pull-to-dismiss | ✔️ Yes (Pure Compose) | Production gallery apps with deep zoom (up to 25x) |
| **Standard Compose `Image` + `graphicsLayer`** | ❌ **Poor (Blurry)** (Scales downsampled preview) | High (if uncompressed) / Low (if downsampled) | Requires manual gesture implementation | ✔️ Yes | Basic icons, avatars, small static images |
| **`SubsamplingScaleImageView` (via `AndroidView`)** | ✔️ **Crystal Clear** (Dynamic regional tiles) | Minimal (5–15 MB active buffer) | Built-in gestures; requires custom bridge for pagers | ❌ No (Legacy Android View) | Legacy View-based applications |
| **Telephoto (`ZoomableAsyncImage`)** | ✔️ **Crystal Clear** (Native Compose subsampling) | Minimal (Visible tiles only) | Built-in gesture locking with `HorizontalPager` | ✔️ Yes (Pure Compose) | Modern Jetpack Compose apps |
| **Pre-sliced Tile Pyramid (DZI / OpenSeadragon)** | ✔️ **Crystal Clear** (Pre-rendered web tiles) | Minimal | Requires custom canvas/stitching logic | ❌ Custom Canvas / WebView | Gigapixel assets (>100MP–10GP+), interactive maps |

---

## 6. QuickView & Gallery Integration

The `HybridImageViewer` is integrated directly with `QuickViewScreen` and `GalleryViewerScreen`:

- **Background Styling**: Supports solid color selection and dynamic ambient hue gradient extraction from the active image.
- **Top Controls**: Includes quick-access Picture Mode (effects/filters) toggle and wallpaper configuration.
- **Hierarchical Back Navigation**: Swiping back from sub-filters/folders transitions back to the primary "All Images" grid before navigating to the dashboard.