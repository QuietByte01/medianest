# FFmpeg Media Studio

The **FFmpeg Media Studio** is a powerhouse media laboratory integrated directly into MediaNest. It leverages the full capabilities of the native FFmpeg engine to provide high-performance, on-device media processing.

---

## 🚀 Key Capabilities

### 1. Advanced Conversion & Remuxing
Media Studio provides two paths for changing media formats:
- **Lossless Remuxing**: Instant container repackaging (e.g., MKV to MP4) with **zero quality loss** and minimal battery impact.
- **High-Fidelity Transcoding**: Convert media using modern codecs like **AV1** and **H.265 (HEVC)** with fine-grained control over CRF (Constant Rate Factor) and presets.

### 2. Smart Compression
Target-aware compression algorithms designed for modern sharing:
- **Auto-Optimized**: Automatically selects the best codec and quality profile to achieve ~60% size reduction while remaining perceptually lossless.
- **Target Size Limits**: Specify maximum file sizes for platforms like **WhatsApp (16MB)** or **Discord (25MB)**.
- **Batch Processing**: Compress entire collections of images or videos in a single operation using background execution.

### 3. Component Extraction
Extract specific streams from complex media files:
- **Audio Extraction**: Save audio tracks as FLAC (lossless), MP3 (320kbps), or AAC.
- **Cinema GIF**: Convert video segments into high-quality, optimized GIFs.
- **Frame Export**: Save high-resolution keyframes as JPEG/PNG.
- **Subtitle Extraction**: Extract embedded SRT or ASS subtitle tracks.

### 4. Bitstream Repair
A dedicated utility for recovering unplayable media:
- **Index Rebuilding**: Fixes files with missing MOOV atoms (common in unfinalized recordings).
- **Timestamp Recovery**: Corrects monotonic clock errors and A/V desync issues.
- **Deep Transcode Recovery**: Decodes every packet and regenerates a clean H.264 stream, bypassing unrecoverable corruption.

---

## 🛠️ Processing Engine

The Studio is powered by the **MediaProcessorEngine**, which manages:
- **Foreground Service Integration**: Ensures processing continues even if the app is minimized.
- **Real-Time HUD**: Provides live progress, processing speed (e.g., 2.5x), and recent log output.
- **Safety Policy**: "Output: Create New File" toggle ensures your original media is always untouched.

---

## 🎨 Social Framing & Cropping
Standardized presets for social media optimization:
- **9:16**: Reels, TikTok, YouTube Shorts.
- **1:1**: Square posts.
- **21:9**: Cinematic ultra-wide.
- **4:3**: Classic television framing.
