# Implementation Plan - Video Player Features & Image Viewer Refinement

This plan addresses the fixes for Video Player's Background Play, Auto-Repeat, Editing, and Smart View, as well as refining the Image Viewer's zoom and panning behavior.

## Proposed Changes

### [Component] UI - Video Player

#### [MODIFY] [VideoPlayerScreen.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/ui/videoplayer/VideoPlayerScreen.kt)
- **Edit Video:** Update "Edit Video" menu logic to try Samsung-specific editor intent (`com.sec.android.app.editor.intent.action.EDIT`) before falling back to generic `ACTION_EDIT`.
- **Samsung Smart View:** Change the Intent from `ACTION_CAST_SETTINGS` to `android.settings.WIFI_DISPLAY_SETTINGS` and try Samsung-specific action `com.samsung.wfd.LAUNCH_WFD_PICKER_DLG` to prioritize Smart View over Google Cast.
- **Auto Repeat:** Ensure the `isAutoRepeatEnabled` state is correctly synchronized with `ExoPlayer`'s repeat mode and persists correctly within the session.

#### [MODIFY] [VideoPlayerActivity.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/ui/videoplayer/VideoPlayerActivity.kt)
- **Background Play:** Update `onStop` to ensure the player does NOT automatically pause if "Background Play" was enabled in the UI.

---

### [Component] UI - Image Viewer (QuickView)

#### [MODIFY] [QuickViewScreen.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/ui/quickview/QuickViewScreen.kt)
- **Double Tap Zoom:** Fix the one-finger double-tap detection logic. Use a more robust timer and gesture listener within the `pointerInput` block to ensure it triggers every time.
- **Strict Panning Bounds:** Recalculate the `maxOffsetX` and `maxOffsetY` more precisely based on the current `scale` and the image's "fit" dimensions to ensure the image never pans into the black bar areas.

## Verification Plan

### Manual Verification
- **Background Play:** Start a video, enable "Background Play" in the menu, then press the Home button. Verify audio continues.
- **Edit Video:** Tap "Edit Video" and verify it opens the device's default editor (Samsung Studio on Samsung devices).
- **Smart View:** Tap "Samsung Smart View" and verify it opens the screen mirroring menu, not Google Cast.
- **Image Zoom:**
    - Verify one-finger double-tap toggles zoom reliably.
    - Zoom in and try to pan to the extreme edges; verify the image stops exactly at its borders and doesn't "float" over the background.
