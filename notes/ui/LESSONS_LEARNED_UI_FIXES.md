# UI Best Practices & Architecture Lessons Learned

This document summarizes the key architectural learnings, layout mechanics, and UI best practices derived from fixing dialogs, backdrop blur, immersive modes, and event consumption across MediaNest.

---

## 1. Dialogs vs. In-Tree Overlays vs. Window Management

### The Problem
- Rendering full-screen overlay `Box(modifier = Modifier.fillMaxSize())` directly as in-tree composable nodes inside nested layouts (e.g., inside `HorizontalPager`, `LazyColumn`, or `StaggeredGrid`) causes severe layout measurement crashes.
- When an in-tree overlay contains a scrollable container (`.verticalScroll()`) with a `FlowRow` or `FlexGrid`, the scrollable parent passes infinite vertical constraints (`Constraints.Height = Infinity`).
- In Compose, measuring `FlowRow` with infinite height constraints throws:
  ```
  java.lang.IllegalStateException: FlowRow measured with infinite max height
  ```

### The Solution
- **Always wrap floating modal dialogs in Compose `Dialog(...)`**:
  ```kotlin
  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
      GlassSurface(
          shape = RoundedCornerShape(24.dp),
          backgroundColor = if (isDark) Color(0xF012131A) else Color(0xF0FFFFFF),
          borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x18000000),
          borderWidth = 1.dp,
          modifier = Modifier.fillMaxWidth(0.90f)
      ) {
          // Dialog Content
      }
  }
  ```
- **Why `Dialog` Works**:
  1. **Window-Level Isolation**: `Dialog` creates a separate native Android `Window` (`DialogWindowProvider`) managed directly by Android's `WindowManager`.
  2. **Fixed Constraints**: The `Dialog` window root receives fixed, safe screen constraints (`Constraints.fixed(screenWidth, screenHeight)`), protecting inner scrollables and `FlowRow`s from infinite constraint measurement crashes.
  3. **Automatic System Gesture Integration**: `Dialog` automatically intercepts hardware Back button presses and outside taps to trigger `onDismissRequest`.

---

## 2. Backdrop Blur Mechanics (`backdropReceiver` & `LocalBackdropState`)

### Coordinate Space & Separate Windows
- `Modifier.backdropReceiver` calculates the background crop slice using screen coordinates:
  ```
  relativeOffset = receiverCoordinates.positionOnScreen() - sourceCoordinates.positionOnScreen()
  ```
- **Same-Window Composables**: `backdropReceiver` works seamlessly for composables in the **SAME** window coordinate space as `backdropSource` (e.g., `GlassDropdownMenu`, `LibraryBatchActionBar`, and `ImageInfoOverlay`).
- **Native Dialog Windows**: A native `Dialog(...)` creates a separate native Window where `receiverCoordinates.positionOnScreen()` evaluates to `(0, 0)` relative to the dialog window's top-left origin. Passing a main-window `BackdropBlurState` to a native `Dialog` window causes `-offset` to evaluate to `(0, 0)`, which crops the top-left corner of the main Activity window (e.g., "MediaNest Gallery" title bar) as a screenshot artifact.

### Glass Surface Tint vs. Backdrop Blur
- `GlassSurface` draws an internal background brush (`finalBgBrush`) based on `backgroundColor`.
- If `GlassSurface` is assigned a heavy opaque `backgroundColor` (e.g. `Color(0xDC0F1015)`), the solid background brush covers the hardware GPU blurred background content drawn by `backdropReceiver`.
- **Best Practice for Glass Blur**: Use `backgroundColor = Color(0x330F1015)` (20% obsidian glass) or `Color.Transparent` with `borderWidth = 0.5.dp` when `backdropReceiver` is attached, allowing the real-time blurred background content to shine through crystal-clear.

---

## 3. Full-Screen Immersive Mode & System Bars

### Immersive Mode (`QuickViewScreen`, `AudioPlayerScreen`)
- Full-screen media viewers require hiding the Android status bar and navigation bar:
  ```kotlin
  val view = LocalView.current
  DisposableEffect(Unit) {
      val window = (view.context as? Activity)?.window
      val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }

      controller?.let {
          it.hide(WindowInsetsCompat.Type.systemBars())
          it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }

      onDispose {
          controller?.show(WindowInsetsCompat.Type.systemBars())
      }
  }
  ```
- **Transient Swipe Behavior**: Setting `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` allows the user to temporarily swipe down to check notifications without permanently exiting full-screen mode.
- **Do Not Force Bars On During Menus**: Avoid calling `controller.show(Type.statusBars())` when opening internal side panels, `...` dropdown menus, or sheets in full-screen mode, as it breaks the full-screen immersive experience.

---

## 4. Lambda Event Handler Invocation Pitfall

### The Double Lambda Bug (`{ { onClick() } }`)
- Writing `onClick = if (condition != null) { { onClick(item) } } else null` creates a **lambda returning a lambda** (`() -> () -> Unit`).
- When the event handler is invoked with `onClick()`, only the outer lambda executes and returns the unexecuted inner lambda `() -> Unit`, causing clicks (such as "Move to Filter", "Rename", "Move", "Copy") to silently do nothing.
- **Correct Syntax**:
  ```kotlin
  onClick = if (onAction != null) ({ onAction(item) }) else null
  ```

---

## 5. Touch Event Consumption in Overlays

### Preventing Click Pass-Through
- In Jetpack Compose, transparent or semi-transparent overlay containers do **NOT** consume touch events by default.
- Clicks on empty space pass through to underlying views (e.g., clicking below the `...` menu in `AudioPlayerScreen` triggering the `LibraryTopBar` Settings button behind it).
- **Solution**: Intercept and consume all touch gestures on full-screen overlay root containers:
  ```kotlin
  Box(
      modifier = Modifier
          .fillMaxSize()
          .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
          ) {}
  )
  ```

---

## Quick Reference Summary

| Requirement | Implementation Pattern |
| :--- | :--- |
| **Modal Dialogs & Pickers** | `Dialog(onDismissRequest = ..., properties = DialogProperties(usePlatformDefaultWidth = false))` + `GlassSurface` |
| **Dropdown Menus** | `GlassDropdownMenu` with `Modifier.width(200.dp)`, `shape = RoundedCornerShape(20.dp)`, top Title Header + `HorizontalDivider` |
| **Full-Screen Viewers** | `WindowInsetsControllerCompat.hide(Type.systemBars())` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` |
| **Overlay Touch Interception** | `Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}` |
| **Lambda Callbacks** | `onClick = if (callback != null) ({ callback(item) }) else null` |
