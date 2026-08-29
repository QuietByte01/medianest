# MediaNest Video Player Architecture, Playback Resolution & Aspect Ratio Diagnostics Report

## 1. Executive Summary

This report provides a comprehensive, post-mortem analysis of all video and audio playback failures, aspect ratio discrepancies, black-screen triggers, and surface life-cycle issues encountered during development. It documents the root causes discovered across comparative reviews with baseline implementations (`medianest-ai-dev`, git commit `e9ccc1e`, and git commit `f477fb6`), outlines the fixes implemented, and establishes critical architectural guidelines and checklists to prevent future regressions.

---

## 2. Complete Root Cause Analysis & Resolution Matrix

| # | Root Cause & Failure Mechanism | Manifestation / Symptoms | Implemented Solution | Reference Baseline |
|---|---|---|---|---|
| **1** | **`TextureView` + `media3-effect` GL Dimension Race**<br>`FinalShaderProgramWrapper` received unmeasured `-1x-1` dimensions during Compose initial layout, throwing `IllegalArgumentException: width -1 must be positive`. | Video player crashed into `STATE_IDLE` at `00:00`, producing audio with black screen. | Restored AndroidX Media3 `PlayerView` with internal `SurfaceView` managing OpenGL surface creation safely. | `medianest-ai-dev` |
| **2** | **Destructive `pause()` Stopping Active Engine**<br>`pause()` called `stopAllEnginesExcept(null)`, calling `exoPlayer.stop()` which unloaded the media item and cleared decoder state. | Pausing wiped the video frame to black; pressing play failed to resume playback. | Removed `stopAllEnginesExcept(null)` from `pause()`. `pause()` now cleanly halts frame advancement without unloading. | `medianest-ai-dev` |
| **3** | **Spurious Watchdog & Permanent `isHardwareFaulty` Flag**<br>`startWatchdog()` falsely diagnosed rapid skips as "Buffering Deadlocks", destroyed Media3, and permanently set `isHardwareFaulty = true`. | After skipping a few tracks, all subsequent videos were diverted to FFmpeg, playing audio against a black screen. | Removed artificial watchdog timer, fake deadlock rebirth routines, and the global `isHardwareFaulty` flag. | `medianest-ai-dev` |
| **4** | **Hardware Layer Compositor Buffer Overflow**<br>`PlayerView.setLayerType(LAYER_TYPE_HARDWARE, paint)` forced an off-screen intermediate texture buffer that crashed on orientation changes or high-res media. | Switching between portrait and landscape caused black screens with audio only. | Removed `setLayerType(LAYER_TYPE_HARDWARE)` from `PlayerView`, allowing direct hardware surface rendering. | `e9ccc1e` |
| **5** | **Post-Instantiation Reflection `setSurfaceType(2)`**<br>Invoking reflection to switch to `TextureView` after `PlayerView` constructor initialized `SurfaceView` desynchronized Media3's surface holder. | Video frames froze on pause/resume or orientation changes. | Removed reflection hacks; `PlayerView` manages its default hardware surface natively. | `medianest-ai-dev` |
| **6** | **Global Surface Re-use Across Diverse Aspect Ratios / Resolutions**<br>`PlayerView` was keyed only by engine name. When skipping from a 9:16 vertical video to a 16:9 4K video, `SurfaceView` buffer was not reconfigured. | Rapid skipping between vertical (9:16) reels and horizontal (16:9/4K) widescreen caused black screens. | Keyed `PlayerView` to `(activeEngineName, media3InstanceId, currentItem?.id)`, binding a fresh surface per track. | `f477fb6` |
| **7** | **Stale `activeVideoSize` Leaking Across Engine Transitions (Media3 ➔ FFmpeg)**<br>`activeVideoSize` held onto the previous Media3 video's 9:16 dimensions when switching to an FFmpeg 16:9 track. | 16:9 FFmpeg videos were rendered squeezed into the previous 9:16 aspect ratio box. | Keyed `activeVideoSize` per item & engine, resetting to `VideoSize.UNKNOWN` and calculating FFmpeg sizes from item metadata. | `f477fb6` |
| **8** | **Aspect Ratio Viewport Math & Viewport Geometry**<br>Nested manual box constraints caused letterbox clipping and distortion on non-standard ratios. | Aspect ratios (`FIT`, `CROP`, `16:9`, `4:3`, `1:1`, `21:9`) distorted or stretched on wide displays. | Restored exact `containBoxSize` and `surfaceModifier` viewport calculation from commit `f477fb6`. | `f477fb6` |

---

## 3. Real-Time Developer Diagnostics Overlay

A live diagnostic indicator was integrated into the **Dev Mode Debug Overlay** (`PlayerDebugOverlay`):

- **Native/Preserved Aspect Ratio (`FIT`, `ORIGINAL`)**:
  - Displays the video's true geometric aspect ratio (e.g. `Aspect Ratio: 16:9` or `Aspect Ratio: 9:16`) in green (`Color(0xFF34D399)`).
- **Altered Aspect Ratio (`CROP`, `16:9`, `4:3`, `1:1`, `21:9`, `STRETCH`)**:
  - Automatically compares `videoAspectRatio` against `playingAspectRatio`.
  - When a mismatch is detected, the overlay highlights the row in **bold red** (`Color(0xFFEF4444)`) with an arrow indicating the transformation:
    ```text
    Aspect Ratio: 16:9 -> 4:3
    Aspect Ratio: 16:9 -> 1:1
    Aspect Ratio: 9:16 -> 16:9 (CROP)
    ```

---

## 4. Important Points to Remember & Regression Prevention Checklist

If video playback, aspect ratio, or black screen issues occur in future releases, follow this structured checklist:

### A. Surface & Compose Lifecycle Rules
1. **Never nest `SurfaceView` inside complex clipped Compose modifiers**:
   - `SurfaceView` punches an independent hole through the Android window compositor. Modifiers like `Modifier.clipToBounds()` or `Modifier.graphicsLayer(alpha)` on parent boxes do not clip `SurfaceView` reliably and cause black screens or z-order occlusions when UI controls appear.
   - Always let `PlayerView` / `AspectRatioFrameLayout` perform the viewport scaling.
2. **Never call `setLayerType(LAYER_TYPE_HARDWARE, ...)` on `PlayerView`**:
   - Hardware layers force GPU intermediate texture allocation. High-resolution (1080p/4K) or rotated frames will exceed buffer memory or fail during orientation matrix updates, dropping frames to black.
3. **Always key `PlayerView` to `currentItem?.id`**:
   - When switching between videos of different resolutions (e.g. `360p` ➔ `4K`) or orientations (`9:16` ➔ `16:9`), MediaCodec and Android SurfaceFlinger require a clean surface buffer binding.

### B. Player State & Engine Management Rules
4. **`pause()` must NEVER call `stop()` or `stopAllEnginesExcept(null)`**:
   - Calling `stop()` puts ExoPlayer into `STATE_IDLE`, unloads the active media buffer, and turns the screen black. `pause()` should strictly call `player.pause()`.
5. **Never introduce artificial timers/watchdogs that set permanent hardware fault flags**:
   - Network buffering or rapid track skipping is normal player behavior. Timers that assume "stuck playback" and permanently divert traffic to software decoding (`isHardwareFaulty = true`) cause permanent black screens for all future tracks.
   - Rely solely on Media3's `Player.Listener.onPlayerError` for genuine, unrecoverable codec errors.
6. **Isolate engine metadata during transitions**:
   - When switching between Media3 and FFmpeg, never reuse cached `videoSize` from the inactive engine. Always reset `activeVideoSize` to `VideoSize.UNKNOWN` on track change so the new engine calculates its geometry from clean stream headers.

---

## 5. Automated Verification & Test Commands

To verify playback reliability and prevent regressions:

```bash
# 1. Run Playback Stress Test (Track switching, rapid pause/play, timeline seeks)
./gradlew testDebugUnitTest --tests "com.medianest.player.PlaybackStressTest"

# 2. Run ExoPlayer Manager Unit Tests
./gradlew testDebugUnitTest --tests "com.medianest.player.ExoPlayerManagerTest"

# 3. Build & Install Debug APK for on-device validation
./gradlew installDebug
```
