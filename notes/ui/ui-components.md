# MediaNest UI Component Library

MediaNest features a custom-built, hardware-accelerated UI library designed for high-performance media interaction. The library emphasizes glassmorphism, fluid animations, and high-fidelity media controls.

---

## 1. Foundational Surfaces & Effects

These components provide the consistent glassmorphic look-and-feel across the application.

| Component Name | Visual Design Characteristics | Example Usages |
| :--- | :--- | :--- |
| **[`GlassSurface`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/GlassSurface.kt)** | **Core Glass Panel**: Supports dynamic obsidian/frosted tints, background images, and software blur fallbacks. | Dashboard cards, dialog backgrounds, mini-player. |
| **[`AmbientGlassSurface`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/AmbientGlassSurface.kt)** | **Adaptive Glow Surface**: Picks up colors from underlying media to create an "ambient light" effect. | Audio player background, immersive video overlays. |
| **[`SolidGlossySurface`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/SolidGlossySurface.kt)** | **High-Contrast Glass**: Higher opacity and sharper borders for clear separation. | Top/Bottom bars, side panels, settings cards. |

### Usage: `GlassSurface`
```kotlin
GlassSurface(
    modifier = Modifier.fillMaxWidth(),
    backgroundImage = R.drawable.artwork_placeholder,
    enableBlur = true,
    blurRadius = 16.dp
) {
    // Content placed over the glass panel
}
```

### Color Palette Note: `0x330F1015` vs `0x221C1F2B`
MediaNest uses standardized glass tint hex codes across all glass surfaces:

| Hex Code | Alpha / Opacity | Tone & Color Family | Common Usage |
| :--- | :--- | :--- | :--- |
| **`0x330F1015`** | **20% Opacity** (Alpha `0x33` = 51/255) | **Ultra-Dark Obsidian / Midnight Slate Tint** (`#0F1015`) | Active hardware backdrop blur surface tint (`enableBlur = true` on `GlassSurface`, `GlassDropdownMenu`, `ImageInfoOverlay`). |
| **`0x221C1F2B`** | **13% Opacity** (Alpha `0x22` = 34/255) | **Dark Slate Glass** (`#1C1F2B`) | Default card background for song rows, album cards, photo/video folder items, and empty state cards. |
| **`0x3D181A24`** | **24% Opacity** (Alpha `0x3D` = 61/255) | **Dark Slate Obsidian** (`#181A24`) | Dashboard stat cards, storage charts, and media studio converter panels. |
| **`0xCC08090E`** | **80% Opacity** (Alpha `0xCC` = 204/255) | **Deep Obsidian Glass** (`#08090E`) | High-contrast modal cards, app lock, and heavy glass surfaces. |

---

## 2. Buttons & Interaction

A suite of buttons designed for different levels of hierarchy and action types.

| Component Name | Visual Design Characteristics | Example Usages |
| :--- | :--- | :--- |
| **[`AppButtons.kt`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/AppButtons.kt)** | **Triple Tier System**: Includes `Critical` (red/glossy), `Action` (tinted), and `Pill` (compact/selection) styles. | Clear cache, primary actions, tab selections. |
| **[`AppChips.kt`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/AppChips.kt)** | **Selection Suite**: Includes `GlossyChip`, `PaletteTagChip` (badging), and `WireframePreviewChip`. | Format badges (MP4, FLAC), Aspect ratio selectors. |
| **[`AppSlider`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/AppSlider.kt)** | **Custom Track Slider**: Sleek track with glowing thumb and haptic feedback support. | Volume, playback speed, DSP intensity. |
| **[`AppSwitch`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/AppSwitch.kt)** | **Glossy Toggle**: Smoothly animated switch with translucent housing. | App Lock, Stealth Mode, Hardware Accel toggles. |

---

## 3. Specialized Media Controls

High-fidelity controls optimized for media playback precision.

| Component Name | Visual Design Characteristics | Example Usages |
| :--- | :--- | :--- |
| **[`WavySeekBar`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/WavySeekBar.kt)** | **Liquid Progress**: Dual-layer wavy animation that responds to playback state (flattens when paused). | Audio player seeker, notification seekbar. |
| **[`ThinSeekBar`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/ThinSeekBar.kt)** | **Minimalist Seeker**: Ultra-thin progress line that expands on hover/drag for precision. | Mini-player seeker, video scrubber overlay. |
| **[`AudioVisualizer`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/AudioVisualizer.kt)** | **OpenGL ES 3.0 Rendering**: High-performance FFT visualization with multiple 3D particle and grid styles. | Now playing screen, audio preview cards. |
| **[`NativeAudioDspSheet`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/NativeAudioDspSheet.kt)** | **Pro-Audio Console**: Glassmorphic panel for controlling the native FFmpeg audio filter pipeline. | Advanced audio settings, equalizer. |
| **[`MediaTrimTimeline`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/media/MediaTrimTimeline.kt)** | **Precision Editor**: Multi-track timeline for frame-accurate video trimming. | Video Editor Studio. |

### Visualizer Styles
MediaNest includes several high-performance OpenGL-powered visualization modes:
- **Particle Globe**: A 3D sphere that pulsates and deforms based on bass energy.
- **Wavefield Terrain**: A 3D wireframe grid that flows like a liquid landscape.
- **Particle Tunnel**: A high-speed vortex that swirls with treble frequencies.
- **Neon Waveform**: Traditional oscilloscope-style lines with glow effects.

### DSP Controls (Native Equalizer)
The DSP subsystem provides professional-grade audio manipulation:
- **5-Band Equalizer**: Individual gain control for 60Hz, 230Hz, 910Hz, 3.6kHz, and 14kHz.
- **Super Volume Boost**: Up to 300% volume amplification (hardware-limited).
- **Pitch Shifter**: Adjust key in semitones (-12 to +12) without affecting tempo.
- **Vocal Mute**: Real-time center-channel cancellation for karaoke effects.

### Usage: `WavySeekBar`
```kotlin
WavySeekBar(
    value = currentPosition,
    onValueChange = { newPos -> seekTo(newPos) },
    isPlaying = playbackState.isPlaying,
    activeColor = MaterialTheme.colorScheme.primary,
    waveAmplitudeDp = 6.dp
)
```

---

## 4. Media Display & Information

Components for displaying media metadata and previews efficiently.

| Component Name | Visual Design Characteristics | Example Usages |
| :--- | :--- | :--- |
| **[`HybridImageViewer`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/image/hybrid/HybridImageViewer.kt)** | **Google Photos Physics + 2-Layer Subsampling**: Base image + 1024px regional tiles with bicubic scaling, pull-to-dismiss springs, and zero-flicker crossfade. | Quick View photo viewer, full-screen gallery image viewer. |
| **[`MediaGridItem`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/MediaGridItem.kt)** | **Smart Thumbnail**: Hardware bitmap preview with type badges and duration overlays. | Library grid, folder views. |
| **[`WideVideoCard`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/WideVideoCard.kt)** | **Cinematic Preview**: 16:9 thumbnail with title, metadata, and format chips. | Featured videos, search results. |
| **[`MusicNotificationCard`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/MusicNotificationCard.kt)** | **Rich Control Card**: Album art background, playback controls, and synced lyrics snippet. | Notification panel, lock screen (via FloatingService). |
| **[`MediaInfoBottomSheet`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/MediaInfoBottomSheet.kt)** | **Technical Diagnostic**: Detailed breakdown of codecs, bitrates, and hardware path status. | "Media Info" option in player/library. |

---

## 5. Navigation & Layout

Adaptive layout components that handle different screen sizes and orientations.

| Component Name | Visual Design Characteristics | Example Usages |
| :--- | :--- | :--- |
| **[`AlphabetScroller`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/AlphabetScroller.kt)** | **Quick Navigation**: Vertical alphabet rail with balloon indicator and haptics. | Songs list, artist grid. |
| **[`AdaptiveBottomSheet`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/AdaptiveBottomSheet.kt)** | **Contextual Glass Overlay**: Handles transitions between modal and non-modal states. | Filter menus, playlist additions. |
| **[`SortRow`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/SortRow.kt)** | **Compact Filter Bar**: Horizontal scrollable list of sort and filter options. | Library tab headers. |
| **[`GlossyScrollbar`](file:///Users/sachin/Lab/VibeCoded/medianest/app/src/main/java/com/medianest/ui/components/GlossyScrollbar.kt)** | **Interactive Scroll Rail**: Translucent scroll indicator with proximity-based visibility. | All long-scrolling grids and lists. |
