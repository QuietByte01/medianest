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

## 🚀 Hardware Optimization

MediaNest includes a custom **Hardware Acceleration Engine** that detects device capabilities at runtime:
- **Zero-Copy Pipeline:** Direct surface binding from decoder buffers to VRAM.
- **Adaptive Caching:** Memory tiers (2GB to 64GB+) determine cache sizes dynamically.
- **GPU Rendering:** UI effects, blur, and scaling filters (Lanczos/CAS) are processed entirely on the GPU.

For detailed specifications, see [HARDWARE_ACCELERATION.md](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/HARDWARE_ACCELERATION.md).

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
