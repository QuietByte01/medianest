# Implementation Plan - Real-time Background Haze Blur

The user wants to transition from a manual "blurred background image" approach to a "real-time background screen blur" approach for all glass components. This will be achieved using the **Haze** library, which is already present in the project's dependencies but not yet utilized.

## User Review Required

> [!IMPORTANT]
> Real-time blur (Haze) is more computationally expensive than blurring a static image. However, the `haze` library is highly optimized for Jetpack Compose.
>
> [!NOTE]
> For `Dialog` and `ModalBottomSheet` (which are in separate Windows), Haze's real-time blur typically requires the `HazeEffect` API or specific platform handling (Android 12+). I will implement a global `HazeState` that works for all on-screen surfaces and explore optimal handling for popups.

## Proposed Changes

### 1. Theme & CompositionLocal [Component: Theme]

#### [MODIFY] [Theme.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/theme/Theme.kt)
- Define `LocalHazeState` using `staticCompositionLocalOf<HazeState?>`.
- Update `MediaNestTheme` to initialize and provide a `HazeState`.

### 2. Global Haze Source [Component: Main]

#### [MODIFY] [MainActivity.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/MainActivity.kt)
- Access the provided `HazeState` from the theme.
- Apply `Modifier.haze(hazeState)` to the root `Surface`. This designates the entire application UI as the blur source.

### 3. Glass Component Refactor [Component: UI Components]

#### [MODIFY] [GlassSurface.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/components/GlassSurface.kt)
- Use `LocalHazeState.current` to get the global state.
- If a `hazeState` is available and `glassEnabled` is true, apply `Modifier.hazeChild(hazeState)`.
- The `backgroundImage` parameter will remain as a fallback if `Haze` is not available or explicitly disabled.

### 4. Bottom Sheets & Menus [Component: UI Components]

#### [MODIFY] [AdaptiveBottomSheet.kt](file:///Users/sachin/medianest/app/src/main/java/com/medianest/ui/components/AdaptiveBottomSheet.kt)
- Ensure the content within the sheet also has access to the `HazeState`.
- Note: If real-time blur doesn't penetrate to the popup window on older Android versions, it will fall back to the tinted background color.

## Verification Plan

### Manual Verification
- **Real-time Blur**: Open the Audio Player panels and scroll content behind them. Verify that the "underlying" content blurs dynamically.
- **Performance**: Verify smooth scrolling and transitions while Haze is active.
- **Fallback**: Ensure the UI still looks good on devices/scenarios where Haze might be limited.
