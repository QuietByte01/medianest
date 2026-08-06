# Implementation Plan - Tablet Audio Player & Landscape Refinement

This plan addresses the UI layout for tablets and landscape mode in the Audio Player, as well as fixing video features and refining the image viewer.

## Proposed Changes

### [Component] UI - Audio Player (Tablet & Landscape)

#### [MODIFY] [AudioPlayerScreen.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/ui/audioplayer/AudioPlayerScreen.kt)
- **Album Art Size:**
    - Increase `baseArtSize` for tablets to be larger and more immersive.
    - Set the landscape weight to `0.4f` for the artwork side.
- **Landscape Split Panel:**
    - Redesign the right side (`0.6f` weight) to be a vertical split:
        - **Top (50%):** Synced Lyrics view in a glass card.
        - **Bottom (50%):** Up Next / Album Songs queue in a glass card.
    - Ensure the seek bar and transport controls are visible and accessible (either as a shared footer or integrated into the right-side flow).
- **Responsive Sizing:**
    - Use screen width percentages to ensure the art doesn't look too small on ultra-wide tablet screens.

---

### [Component] UI - Video Player & Image Viewer (Carry-over fixes)

#### [MODIFY] [VideoPlayerScreen.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/ui/videoplayer/VideoPlayerScreen.kt)
- Fix Samsung Smart View and Edit Video intents (targeted Samsung apps).
- Ensure Background Play and Auto-Repeat are correctly synchronized.

#### [MODIFY] [QuickViewScreen.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/ui/quickview/QuickViewScreen.kt)
- Fix double-tap zoom detection.
- Add strict panning constraints to prevent images from floating into black bars.

## Verification Plan

### Manual Verification
- **Tablet Landscape:** Verify the "Artwork | (Lyrics / Queue)" split layout.
- **Artwork Size:** Compare art size on phone vs tablet; should be significantly larger on tablet.
- **Video Intents:** Verify Samsung Studio and Smart View picker open correctly.
- **Image Zoom:** Verify double-tap and panning limits.
