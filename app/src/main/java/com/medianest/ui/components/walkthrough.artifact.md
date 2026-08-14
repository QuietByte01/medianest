# Walkthrough - Album Art Hue Tinted Glass

I have enhanced the app's glassmorphic UI by integrating "Album Art Hue Tinting." Instead of a generic frosted look, glass surfaces (bottom sheets and dropdown menus) now adopt a subtle color tint derived from the currently viewed media item.

## Changes Made

### 1. Hue-Aware Glass Surface
- **Modified**: [GlassSurface.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/components/GlassSurface.kt)
- **Fix**: Added a `hue` parameter. When provided, the component calculates a tinted "Obsidian" (dark) or "Frosted" (light) background color using HSV. This creates a more vibrant and contextually aware glass effect.

### 2. Adaptive Bottom Sheets & Info Modals
- **Modified**: [AdaptiveBottomSheet.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/components/AdaptiveBottomSheet.kt), [MediaInfoBottomSheet.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/components/MediaInfoBottomSheet.kt), and [NativeAudioDspSheet.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/components/NativeAudioDspSheet.kt)
- **Fix**: Propagated the `hue` parameter through the bottom sheet components. This ensures that when you open track details or DSP settings, the entire sheet is tinted to match the album art.

### 3. Integrated Hue Extraction
- **Modified**: [AudioPlayerScreen.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/audioplayer/AudioPlayerScreen.kt), [MediaGridItem.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/components/MediaGridItem.kt), and [QuickViewScreen.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/quickview/QuickViewScreen.kt)
- **Fix**:
    - **Audio Player**: Uses the existing `albumArtHue` from the player state.
    - **Quick View**: Uses the calculated `imageHue` from the viewer.
    - **Media Grid**: Added an asynchronous hue extraction triggered when a menu is opened to ensure a consistent experience across the library.

## Verification
- **Visual Harmony**: Verified that opening the overflow menu or info sheet for a "blue" album results in a subtle blue-tinted glass surface, while a "red" album yields a red tint.
- **Dark/Light Mode**: Confirmed that the tinting logic adapts correctly to system themes, maintaining readability and the "frosted" aesthetic.
- **Performance**: Asynchronous extraction in the media grid ensures that the UI remains responsive.

> [!TIP]
> The hue tinting works best with media that has a strong dominant color. For items with neutral or very dark covers, the glass defaults to a sophisticated gray-blue "Obsidian" tint in dark mode.
