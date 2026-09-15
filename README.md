# MediaNest ▶▶

[![Deploy](https://github.com/QuietByte01/medianest/actions/workflows/deploy.yml/badge.svg?branch=main)](https://github.com/QuietByte01/medianest/actions/workflows/deploy.yml)
[![PR Checks](https://github.com/QuietByte01/medianest/actions/workflows/pr_checks.yml/badge.svg?branch=main)](https://github.com/QuietByte01/medianest/actions/workflows/pr_checks.yml)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target--SDK-35-blue.svg)](https://developer.android.com/about/versions/15)
[![Material 3](https://img.shields.io/badge/Design-Material_3-7C4DFF.svg)](https://m3.material.io/)
[![Privacy Policy](https://img.shields.io/badge/Privacy--Policy-Online-brightgreen.svg)](https://QuietByte01.github.io/medianest/)

### High-Performance Universal Media Gallery for Android

MediaNest is a hardware-optimized media management application designed for seamless local media playback and organization. Built with a **"Hardware-First"** philosophy, it leverages zero-copy rendering and a hybrid playback engine to deliver an elite media experience on any Android device.

---

## ✨ Feature Highlights

### 🖼️ High-Performance Image Viewer & QuickView
- **Gigapixel Sub-Region Tiling**: Smoothly view ultra-high-resolution images without out-of-memory crashes.
- **EXIF Metadata Inspector**: View detailed camera metadata (ISO, aperture, exposure, focal length, location coordinates).
- **QuickView Viewer**: Ultra-fast full-screen viewer for photos, GIFs, and SVGs with gesture zoom & pan.

### 🎬 Video Player & Real-Time Filters
- **Real-Time Video Filters**: Native C++ JNI/OpenGL picture modes including *Vivid*, *HDR Accent*, *Warm*, *Cold*, *Sepia*, and *High-Contrast B&W*.
- **Floating PiP Service**: Picture-in-Picture mode allows continuous video watching while multitasking in other apps.
- **Gesture Controls**: Intuitive swipe controls for volume, screen brightness, and precise double-tap seeking.

### 📂 Smart Media Categorization & Series Organization
- **Series & Episode Grouping**: Smart detection and auto-grouping of TV series, seasons, and consecutive episode files.
- **Custom Categories**: Organize videos into *Movies*, *Series*, *Clips*, or custom user categories with personalized icons.
- **Smart Filter Tabs**: Filter media instantaneously by *All*, *Favorites*, *Hidden/Vault*, or *Excluded*.

### 🔁 Playback Series & Queue Management
- **Series Auto-Play & Queuing**: Smart playback queue automatically plays consecutive episodes or tracks in a folder/series.
- **Resume Playback**: Remembers exact playback position and audio/subtitle preferences for every video.
- **Custom Playlists**: Create, reorder, and export custom M3U playlists across audio and video libraries.

### 💬 Subtitles & Multi-Audio Track Switching
- **Universal Subtitle Support**: Full support for embedded and external subtitle files (`.srt`, `.ass`, `.ssa`, `.vtt`).
- **Dual Audio / Stream Switching**: Seamlessly switch between multi-language audio streams and dual-audio tracks in real time.
- **Subtitle Delay Adjustment**: Fine-tune subtitle synchronization with manual time offset controls.

### 🎤 Synchronized LRC Lyrics & Pro Audio DSP
- **Synchronized LRC Lyrics**: Automatic LRC lyrics parser with smooth auto-scroll and manual lyric delay adjustment.
- **5-Band Equalizer & Visualizer**: Integrated Pro Audio DSP with bass boost, audio virtualizer, and real-time frequency spectrum visualizer.

---

## ⚡ Native Engine: FFmpeg & Oboe Architecture

MediaNest combines native Android framework APIs with C++ NDK performance libraries:

- **Google Oboe C++ Engine**: Low-latency audio processing pipeline built on AAudio / OpenSL ES for real-time 5-band equalizer DSP and audio spectrum visualizers.
- **Native FFmpeg C++ JNI Bridge**: Full C++ JNI wrapper around FFmpeg 6.1 (with 16KB page-alignment support for Android 15+). Handles deep metadata probing (codecs, bitrates, audio channels, color spaces) and decodes non-native formats (`MKV`, `AVI`, `FLV`, `FLAC`, `OPUS`, `DTS`, `AC3`, `TRUEHD`).

---

## 🌟 Core Pillars

### 🚀 Performance & Hardware
- **Hybrid Engine**: Native SoC DSP decoding (Media3) with high-fidelity FFmpeg fallback.
- **Zero-Copy Pipeline**: Direct surface binding avoids expensive CPU-side memory copies.
- **Adaptive Memory**: Dynamic RAM & VRAM scaling for devices ranging from 2GB to 64GB+.

### 🛡️ Security & Privacy
- **100% Local**: No cloud dependencies. All indexing and processing happen on-device.
- **Privacy Suite**: App Lock (PIN/Biometric), Stealth Mode, and Hidden Folder support (`.nomedia`, private vaults).
- **Privacy Policy**: [Online Privacy Policy](https://QuietByte01.github.io/medianest/)

### 🎨 Elite User Experience
- **Glassmorphic UI**: High-performance "Ambient" surfaces and fluid liquid animations.
- **Unified Library**: Seamlessly browse Images, Videos, and Audio in a single hub.

---

## 🛠️ Tech Stack

| Layer | Technology |
| :--- | :--- |
| **UI** | [Jetpack Compose](https://developer.android.com/jetpack/compose) + [Material 3](https://m3.material.io/) |
| **Playback** | [Android Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3) + [Oboe](https://github.com/google/oboe) + [FFmpeg](https://ffmpeg.org/) |
| **Data** | [Room](https://developer.android.com/training/data-storage/room) + [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |
| **Images** | [Coil](https://coil-kt.github.io/coil/) (Hardware Bitmaps Enabled) |

---

## 📂 Project Structure

```text
medianest/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── cpp/             # Native C++ FFmpeg & colorizer JNI libraries
│   │       ├── java/com/medianest/
│   │       │   ├── data/        # Repositories, Room DB, & DataStore
│   │       │   ├── player/      # Hybrid ExoPlayer & Floating Player Service
│   │       │   ├── ui/          # Jetpack Compose UI (Library, Players, Settings)
│   │       │   └── util/        # Permission & file utilities
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── docs/                        # Hosted GitHub Pages site & Play Store documentation
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🛠️ Run Locally

1. **Clone** the repository.
2. Open in **Android Studio** (Koala or newer recommended).
3. Ensure your Android device/emulator supports **API 30+** (Target SDK 35).
4. **Build & Run**.

---

## 🛡️ Privacy & Security

**Privacy is a feature, not an afterthought.** All media decoding, indexing, and UI rendering happen strictly on your device. MediaNest does not upload your personal media to any cloud services.
