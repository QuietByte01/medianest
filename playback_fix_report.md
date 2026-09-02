# MediaNest Playback Engine & Stability Report

## 1. Overview & Context

This document details the issues, root causes, and solutions implemented to resolve playback instability, surface attachment race conditions, engine switching loops, and playback stalls across the dual-engine architecture (**Media3 ExoPlayer** and **Native FFmpeg with AAudio/Oboe**).

---

## 2. Summary of Issues Identified

### Issue 1: Broken AC3 / Corrupted Frame Crash in AVI Files
* **Symptoms**: Playing legacy AVI files (e.g., `Bin Tere.avi` with AC3 audio or MPEG-4 Part 2/Xvid video) caused Media3 to crash with `ArrayIndexOutOfBoundsException` in `Ac3Util.parseAc3SyncframeInfo` (`ERROR_CODE_IO_UNSPECIFIED`).
* **Root Cause**: Android Media3's `AviExtractor` and `Ac3Util` struggle with fixed-chunk AVI audio syncframes lacking explicit sample durations.

### Issue 2: Engine Flip-Flop / Infinite Switching Loop
* **Symptoms**: After an AVI failed in Media3 and fell back to FFmpeg, the player kept switching between Media3 and FFmpeg, resulting in flashing black screens and muted audio.
* **Root Cause**: The background watchdog/rebirth mechanism (`rebuildEngineDueToDeadlock`) was triggered by Media3 timeout events during teardown. It unconditionally recreated `Media3` and called `prepare()` on the current item even when playback had **already successfully switched to and was actively running on FFmpeg**.

### Issue 3: Black Screen on Emergency Engine Fallback
* **Symptoms**: When falling back from Media3 to FFmpeg, audio would play but the video screen remained pitch black.
* **Root Cause**: During Compose recomposition when `activeEngineName` changed from `"Media3"` to `"FFmpeg"`, the disposal callback (`onRelease`) of the old `PlayerView` executed *after* the new `TextureView` had already attached its surface, wiping out `playerManager.lastSurface` with `null`.

### Issue 4: Global Hardware Fault Latching
* **Symptoms**: Once any single corrupted video triggered an emergency fallback, subsequent normal MP4/MKV videos failed to use hardware acceleration and malfunctioned.
* **Root Cause**: `switchToFFmpegFallback` and decoder error callbacks latched a global singleton flag `isHardwareFaulty = true`, which was never reset for new tracks.

### Issue 5: Subsequent MP4 Videos Stuck at 00:00 (Black Screen / Buffering)
* **Symptoms**: After playing an AVI video on FFmpeg and navigating to a standard MP4 video, the MP4 remained stuck at `00:00` in `STATE_BUFFERING` on a black screen.
* **Root Causes**:
  1. **Deadlocked Media3 Instance Reuse**: When Media3 was stopped during the switch to FFmpeg, it encountered `ExoTimeoutException: Detaching surface timed out`. The singleton manager kept a reference to the deadlocked `ExoPlayerImpl` instance instead of invalidating it. Returning to Media3 attempted to prepare the deadlocked instance.
  2. **TextureView Surface Generation Mismatch**: `player_view_texture.xml` forced a `TextureView` surface for Media3. On Qualcomm Codec2 (`c2.qti.avc.decoder`), attaching the surface asynchronously caused a surface generation ID mismatch (`setOutputSurface failed`), forcing the hardware decoder to shut down (`CCodec: state->set(RELEASING)`).
  3. **Main-Thread Dead Engine Release**: Destroyed engines were being released synchronously on Android's Main Looper inside a while loop, causing Looper message queue stalls.

---

## 3. Architecture & Technical Fixes Implemented

```
                       [ Incoming Media Item ]
                                  │
                   MediaCapabilityInspector.inspect()
                                  │
                 ┌────────────────┴────────────────┐
                 ▼                                 ▼
   [ Fast Path / Normal Formats ]     [ Legacy / Fallback Formats ]
       (MP4, MKV, WebM, MOV)              (AVI, WMV, FLV, DivX, Xvid)
                 │                                 │
                 ▼                                 ▼
       Media3 ExoPlayer Engine           Native FFmpeg Engine
        (GPU Hardware Direct)              (Software Decoder + Oboe)
                 │                                 │
                 ▼                                 ▼
          PlayerView (Surface)             TextureView (Surface)
```

### 1. Pre-emptive Direct Engine Routing
* **File**: `com/medianest/player/MediaCapabilityInspector.kt`
* Added direct routing for `.avi`, `.wmv`, `.flv`, `.divx`, `.rmvb`, `.xvid`, and `.asf` to FFmpeg immediately upon metadata inspection.
* Avoids attempting Media3 on known problematic containers, eliminating initial black screen delays and codec crashes.

### 2. Isolated Engine Rebirth & Deadlock Guard
* **File**: `com/medianest/player/ExoPlayerManager.kt`
* `rebuildEngineDueToDeadlock` now checks `if (activeEngine == ffmpegEngine) return`. If FFmpeg is already playing, background Media3 events are forbidden from hijacking or interrupting the active session.
* Media3 timeout errors on `onPlayerError` route directly to `switchToFFmpegFallback` instead of repeating rebirth loops.

### 3. Safe Engine Swapping & Invalidation
* **File**: `com/medianest/player/ExoPlayerManager.kt`
* When swapping from Media3 to FFmpeg, the old Media3 instance is orphaned, silenced, and `media3Engine` is explicitly set to `null`.
* When returning to Media3 from FFmpeg, a brand-new, healthy `ExoPlayer` instance is instantiated and `media3InstanceId` is incremented.
* Removed global `isHardwareFaulty` latching so individual file issues never disable hardware acceleration for subsequent healthy files.

### 4. Background Engine Release Queue
* **File**: `com/medianest/player/ExoPlayerManager.kt`
* Orphaned and dead engines are added to `brokenEngines` and released asynchronously on `Dispatchers.IO`, preventing main thread UI freezes and surface detachment timeouts.

### 5. Resilient Surface Lifecycle in Compose
* **File**: `com/medianest/ui/videoplayer/VideoPlayerScreen.kt`
* Updated Media3 container to use clean `androidx.media3.ui.PlayerView(ctx)` with native hardware `SurfaceView`, avoiding TextureView buffer queue deadlocks on Snapdragon Codec2.
* Updated FFmpeg `TextureView` listener to verify `playerManager.lastSurface == activeSurface` before clearing surfaces on destruction.
* Added `update` block validation in `TextureView` to re-bind the surface if already available.

### 6. Throttled UI & Non-Blocking Debug Overlays
* **Files**: `com/medianest/ui/components/debug/DebugOverlays.kt`, `ExoPlayerManager.kt`
* Added collapsible state (`▲` / `▼`) to all diagnostics overlays (`PlayerDebugOverlay`, `AudioDebugOverlay`, `ImageDebugOverlay`), allowing background touch interactions and controls access.
* Always visible real-time counters for `Dropped Frames`, `Audio Missing`, `Corrupted Frames`, and `Sync Recoveries`.
* Throttled notification IPC updates with `.distinctUntilChanged()` to eliminate main thread churn during 200ms player progress loops.

---

## 4. Verification & Validation Summary

| Test Case | Scenario | Result |
| :--- | :--- | :--- |
| **Normal MP4 Playback** | Play standard H.264/HEVC MP4 file | **PASSED** (Media3 GPU Direct) |
| **Corrupted AVI Playback** | Play `Bin Tere.avi` (MPEG-4 + AC3) | **PASSED** (Instant FFmpeg launch, no crash) |
| **Multi-Step Engine Swap** | Media3 Video $\rightarrow$ Back $\rightarrow$ Corrupted AVI $\rightarrow$ Back $\rightarrow$ Media3 Video | **PASSED** (Smooth transition, zero stalls at 00:00) |
| **Engine Recovery Loop** | Trigger fallback on damaged container | **PASSED** (No infinite switching, volume at 1.0f) |
| **Debug Overlay Interaction** | Collapse overlay & use player scrubbing/gestures | **PASSED** (Controls fully clickable through overlay) |
