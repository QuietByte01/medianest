# MediaNest ▶▶
### Universal Media Viewer & Gallery for Android

MediaNest is a high-performance, feature-rich media management application designed for seamless local media playback and organization. Built with a "Hardware-First" philosophy, it leverages modern Android APIs to provide a zero-copy, privacy-focused experience.

View your app in AI Studio: [https://ai.studio/apps/17f7222d-22c3-467f-ace8-cb1f8b9d9cba](https://ai.studio/apps/17f7222d-22c3-467f-ace8-cb1f8b9d9cba)

---

## 🌟 Key Features

- **Unified Library:** A single hub for all your Images, Videos, and Audio files.
- **Quick View:** Rapid media previewing without leaving your context.
- **Custom Categorization:** Organize videos and images into custom collections and categories.
- **Advanced Playback:**
  - **Video:** Supports H.264, HEVC, AV1, and VP9 with hardware SoC DSP decoding.
  - **Audio:** High-fidelity playback using AAudio and OpenSL ES with lyrics and metadata support.
- **Security & Privacy:**
  - **App Lock:** Secure your library with a PIN.
  - **Hidden Folders:** Mask specific directories from the media scanner.
  - **Stealth Mode:** Content protection in the "Recent Apps" screen using `FLAG_SECURE`.
- **Media Analytics:** Deep insights into your media formats, storage usage, and playback history.
- **Hardware Acceleration Engine:** Proprietary subsystem for zero-copy rendering, adaptive memory management, and GPU-accelerated UI.

---

## 🛠️ Tech Stack

- **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/)
- **Media Engine:** [Android Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3)
- **Database:** [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- **Preferences:** [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Image Loading:** [Coil](https://coil-kt.github.io/coil/) with Hardware Bitmaps enabled
- **Language:** 100% [Kotlin](https://kotlinlang.org/)
- **AI Integration:** [Gemini AI](https://ai.google.dev/) via Firebase AI SDK

---

## 🚀 Hardware Optimization & Architecture

MediaNest implements a sophisticated, multi-engine pipeline to ensure high-performance playback and broad format compatibility.

### Media Flow Architecture

```mermaid
graph TD
    Input[Media File / Content URI] --> Inspector{Media Inspector}
    
    %% Image Path
    Inspector -->|Image: JPG/PNG/WebP/GIF| Coil[Coil Image Engine]
    Coil --> HW_Bitmap[Hardware Bitmaps]
    HW_Bitmap --> GPU_Comp[GPU Composition]

    %% Routing Decisions
    Inspector -->|Standard: MP4/MKV/WebM| Media3[Media3 / ExoPlayer]
    
    %% Fallback Trigger
    Inspector -->|Non-Android: AVI/FLV/WMV| FFmpegTrigger{FFmpeg Fallback}
    Inspector -->|Corrupted: Broken Headers/PTS| FFmpegTrigger
    Inspector -->|Hi-Fi: FLAC/ALAC/Lossless| FFmpegTrigger

    %% Hardware Path
    subgraph HW_SoC_Path [Hardware Accelerated Path]
        Media3 --> MediaCodec[Android MediaCodec HW]
        MediaCodec -->|H.264 / HEVC / AV1 / VP9| Surface[Direct Surface Binding]
    end

    %% Software/Native Path
    subgraph Native_Path [Native FFmpeg & Oboe Path]
        FFmpegTrigger --> Demux[Native Demuxer libavformat]
        Demux -->|Handles AVI Indexing / Proc FD| Dec[Native Decoder libavcodec]
        
        %% Video SW Fallback
        Dec -->|MPEG-4 / Xvid / Sorenson / WMV| SWS[swscale YUV to RGB]
        SWS --> NativeWin[ANativeWindow Rendering]
        
        %% Audio Oboe Path
        Dec -->|AC-3 / DTS / FLAC / MP3| Filters[libavfilter DSP]
        Filters -->|Bass Boost / EQ / Vol Boost| Resample[libswresample]
        Resample --> Oboe[Oboe / AAudio Direct]
    end

    %% Fault Tolerance Mechanism
    subgraph Resilience_Layer [Fault Tolerance & Recovery]
        Resilience[Native Resilience Loop]
        Resilience -->|+discardcorrupt| Dec
        Resilience -->|ignore_err| Demux
        Resilience -->|Clock Resync| NativeWin
    end

    FFmpegTrigger -.->|Unstable Input| Resilience

    Surface --> Display[Screen]
    NativeWin --> Display
    Oboe --> AudioHW[Audio Hardware]
```

### Engine Selection Matrix

| Media Type | Formats / Codecs | Why Fallback? | Decoding Engine | Rendering Path |
| :--- | :--- | :--- | :--- | :--- |
| **Standard Video** | MP4, MKV, WebM (H.264, HEVC, AV1) | Native Support | **Media3 (HW)** | MediaCodec -> Surface |
| **Non-Native Video**| **AVI, FLV, WMV** (Xvid, DivX, Sorenson) | Missing HW Decoders | **FFmpeg (SW)** | swscale -> NativeWindow |
| **Corrupted Video** | **Damaged Headers, Invalid PTS** | Media3 Stalls/Crashes | **FFmpeg (Resilient)** | Fault-Tolerant Loop |
| **Lossless Audio** | **FLAC, ALAC, WAV, DTS, AC-3** | Bit-Perfect / DSP | **FFmpeg (Native)** | Oboe (Direct HW Access) |
| **Standard Audio** | MP3, AAC | System Default | **Media3** | System Mixer |
| **Images** | JPG, PNG, WEBP, SVG, GIF | Zero-Copy | **Coil** | Hardware Bitmaps |

### Fault Tolerance & Resilience
- **Native Recovery**: The FFmpeg loop uses `+discardcorrupt` and `ignore_err` flags to bypass damaged frames.
- **Clock Synchronization**: The master clock is slaved to Oboe's hardware clock. If video timestamps drift by more than 500ms, a sub-millisecond resync occurs to prevent hangs.
- **Memory Safety**: Uses direct File Descriptors (`/proc/self/fd/`) to avoid memory-heavy file copying during native probing.

---

## 🚀 Hardware Features Details

---

## 📂 Project Structure

- `app/src/main/java/com/example/ui/`: Compose screens and components.
- `app/src/main/java/com/example/data/`: Repositories and Room DB definitions.
- `app/src/main/java/com/example/player/`: ExoPlayer lifecycle and state management.
- `app/src/main/java/com/example/hardware/`: Hardware capability detection and optimization logic.

---

## 🛠️ Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. **Clone & Open:** Open the project directory in Android Studio.
2. **API Key Setup:** Create a `.env` file in the root directory and add:
   ```env
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
   (See `.env.example` for reference).
3. **Build Configuration:** Remove the following line from `app/build.gradle.kts`:
   ```kotlin
   signingConfig = signingConfigs.getByName("debugConfig")
   ```
4. **Deploy:** Run the app on an emulator or physical device.
5. **Key Reset:** If published in AI Studio, [request an upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in the Play Console if needed.

---

## 🛡️ Privacy & Security

**100% Local Processing.** All media decoding, indexing, and UI rendering happen strictly on your device. MediaNest does not upload your personal media to any cloud services.

---

## 📱 Device Support & Build Variants

MediaNest uses **ABI Splits** to keep the application size small while providing native FFmpeg support. When building or downloading, choose the variant that matches your device:

- **`arm64-v8a` (Modern Standard):** Recommended for almost all Android devices released in the last 5-7 years (e.g., Pixel 4-9, Samsung S10-S24). Offers the best performance for native decoding.
- **`armeabi-v7a` (Legacy):** Suitability for older 32-bit ARM devices or budget entry-level phones (pre-2017 hardware).
- **`x86_64` (Emulators/Laptops):** Optimized for Android Emulators (running on Mac/Windows) and Intel-based Chromebooks or tablets.
- **`Universal APK` (One-Size-Fits-All):** Contains native code for all architectures. Larger file size (~90MB vs ~25MB), but compatible with any device.

