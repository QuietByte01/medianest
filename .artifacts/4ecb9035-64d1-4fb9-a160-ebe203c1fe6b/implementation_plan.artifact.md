# Implementation Plan - Crash Fix & Settings Refinement

This plan addresses the "TransactionTooLarge" crash when clicking media items and refines the settings screen background.

## User Review Required

> [!IMPORTANT]
> To fix the crash, I am moving the media list transfer from `Intent` extras to a shared static memory structure. This is necessary because Android has a 1MB limit for Intents, which large media folders exceed.

## Proposed Changes

### [Component] Core - Media Sharing (Crash Fix)

#### [MODIFY] [QuickViewActivity.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/ui/quickview/QuickViewActivity.kt)
- Add a `companion object` to hold a static `activeList: List<MediaItem>?`.
- Use this list for the image pager if it's available.
- Clear the list on `onDestroy` if finishing.

#### [MODIFY] [VideoPlayerActivity.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/ui/videoplayer/VideoPlayerActivity.kt)
- Add a `companion object` to hold a static `activeList: List<MediaItem>?`.
- Use this list for the video playlist.
- Clear the list on `onDestroy` if finishing.

#### [MODIFY] [MainActivity.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/MainActivity.kt)
- Update `onOpenQuickView` and `onOpenVideoPlayer` to set the static `activeList` on the target activity before starting it.
- Remove large array extras from the Intents.

---

### [Component] UI - Settings

#### [MODIFY] [SettingsScreen.kt](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/java/com/example/ui/settings/SettingsScreen.kt)
- Update the `drawBehind` grid logic to use **16dp** spacing (was 30dp) for a denser, more refined look.
- Slightly reduce alpha of the grid lines for subtlety.

## Verification Plan

### Automated Tests
- Verify successful build via `./gradlew assembleDebug`.

### Manual Verification
- **Crash Test:** Open an image from a folder with thousands of files.
- **Visual Test:** Check that the Settings grid looks more compact and refined.
- **Context Test:** Ensure the filmstrip in QuickView correctly shows the contextual list.
