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

## 4. Implementation Options for Zoomable Image Viewers

### Option 1: Modern Jetpack Compose via Telephoto (`ZoomableImage`)

Telephoto is a pure Compose library engineered for deep zoom, pan, double-tap gestures, and automatic `BitmapRegionDecoder` subsampling.

#### 1. Dependency

```groovy
implementation("me.saket.telephoto:zoomable-image-coil:0.14.0")

```

#### 2. Compose Implementation

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

@Composable
fun FullscreenImageViewer(
    imageUrl: String,
    contentDescription: String? = null
) {
    val state = rememberZoomableImageState()

    ZoomableAsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        state = state,
        modifier = Modifier.fillMaxSize()
    )
}

```

---

### Option 2: Classic Android View via `SubsamplingScaleImageView`

A robust, legacy View-based solution embedded within Compose using `AndroidView`.

#### 1. Dependency

```groovy
implementation("com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")

```

#### 2. Compose Wrapper

```kotlin
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

@Composable
fun SubsamplingImageViewer(
    imageUri: Uri,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SubsamplingScaleImageView(context).apply {
                setDoubleTapZoomScale(2.5f)
                setMinimumTileDpi(160)
            }
        },
        update = { view ->
            view.setImage(ImageSource.uri(imageUri))
        },
        modifier = modifier.fillMaxSize()
    )
}

```

---

## 5. Multi-Page Swipeable Gallery (ViewPager in Compose)

A swipeable multi-page gallery integrates Compose's `HorizontalPager` with zoomable viewers.

### Gesture Conflict Resolution

1. **Zoomed Out:** Horizontal drag must navigate pages.
2. **Zoomed In:** Horizontal drag must pan across the image canvas without triggering page changes.
3. **Edge Panning:** When panning reaches the horizontal boundary of a zoomed-in image, continued dragging must transition to the adjacent page smoothly.

> Telephoto integrates directly with `HorizontalPager` to automate this drag-lock boundary handling.

### Multi-Page Gallery Implementation

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

@Composable
fun MultiPageGallery(imageUrls: List<String>) {
    val pagerState = rememberPagerState(pageCount = { imageUrls.size })

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { page ->
        val zoomState = rememberZoomableImageState()

        ZoomableAsyncImage(
            model = imageUrls[page],
            contentDescription = "Photo $page",
            state = zoomState,
            modifier = Modifier.fillMaxSize()
        )
    }
}

```

---

## 6. Comparison of Android Image Zooming Approaches

| Approach | Max Clarity on Zoom | Memory Usage (RAM) | Gesture / Pager Handling | Compose Native? | Best For |
| --- | --- | --- | --- | --- | --- |
| **Standard Compose `Image` + `Modifier.transformable` / `graphicsLayer**` | ❌ **Poor (Blurry)** (Scales downsampled preview) | High (if uncompressed) / Low (if downsampled) | Requires manual gesture implementation | ✔️ Yes | Basic icons, small static images, avatars |
| **`SubsamplingScaleImageView` (via `AndroidView`)** | ✔️ **Crystal Clear** (Dynamic regional tiles) | Minimal (5–15 MB active buffer) | Built-in gestures; requires custom bridge for pagers | ❌ No (Legacy Android View) | Legacy View-based applications |
| **Telephoto (`ZoomableAsyncImage`)** | ✔️ **Crystal Clear** (Native Compose subsampling) | Minimal (Visible tiles only) | Built-in gesture locking with `HorizontalPager` | ✔️ Yes (Pure Compose) | Modern Jetpack Compose apps, camera galleries |
| **Pre-sliced Tile Pyramid (Deep Zoom / DZI / OpenSeadragon)** | ✔️ **Crystal Clear** (Pre-rendered web tiles) | Minimal | Requires custom canvas/stitching logic | ❌ Custom Canvas / WebView | Gigapixel assets (>100MP–10GP+), complex interactive maps |

---

## 7. Decision Guide: Selecting the Right Solution

* **Standard High-Res Photos (Up to ~100MP):** Use **Telephoto `ZoomableAsyncImage**` (for Jetpack Compose) or **`SubsamplingScaleImageView`** (for classic Views). Both deliver 1:1 pixel clarity upon zoom while maintaining a 5–15 MB RAM footprint without requiring pre-processed assets.
* **Ultra-Gigapixel & Large Map Assets (>200MP, Blueprints, GIS):** Use **Pre-sliced Tile Pyramids (Deep Zoom / DZI)**. Decoding massive raw files on the fly overwhelms mobile CPU/decoders; serving pre-tiled $256 \times 256\text{ px}$ image chunks delivers significantly better performance.