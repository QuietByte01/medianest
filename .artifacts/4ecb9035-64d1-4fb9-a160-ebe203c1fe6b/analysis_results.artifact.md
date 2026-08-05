# Performance Analysis Report - MediaNest

This report summarizes the identified performance bottlenecks, slow startup causes, and lag issues in the MediaNest project. No code changes have been made.

## 1. Startup Performance (Slow App Launch)

> [!WARNING]
> Several synchronous operations in `MediaNestApp.onCreate` block the main thread during initialization.

- **Synchronous Database/Settings Init**: `AppDatabase.getDatabase(this)` and `SettingsManager(this)` are called on the main thread in `onCreate`. This performs disk I/O before the first frame can be drawn.
- **Hardware Capability Detection**: `AndroidHardwareEngine.detectCapabilities(context)` queries the `MediaCodecList` synchronously in `onCreate`. Querying all system codecs can be slow on older devices.
- **Coil Initialization**: The `ImageLoader` is configured in `onCreate` with several components, which adds to the work done before the app is ready.

## 2. Media Scanning & I/O Bottlenecks

> [!CAUTION]
> The `MediaStoreRepository` contains the most critical performance issues, specifically regarding hidden file scanning.

- **Exhaustive File System Walk**: `scanFileSystemHiddenMedia` recursively walks the entire external storage (up to depth 10) to find hidden files.
- **Heavy Metadata Extraction during Scan**: For *every* hidden file found, the app uses `BitmapFactory.decodeFile` (for images) or `MediaMetadataRetriever` (for videos) to extract width and height. This is extremely I/O and CPU intensive.
- **Probing Hidden Folders**: The scanner explicitly checks for 30+ "common dot folders" in every directory it visits, leading to thousands of `File.exists()` calls.
- **Redundant Scans**: `resolveSiblingsForUri` (used when opening a file from an external Intent) triggers a full scan of the entire media library.

## 3. Memory & State Management Issues

- **Lack of Pagination**: `getImages`, `getVideos`, and `getAudio` return full lists of items. For users with large libraries (5,000+ items), this causes high memory pressure and potential `OutOfMemoryError` or severe UI jank.
- **State Bloat in MainActivity**: `MainActivity` holds massive lists in memory as `MutableState`. Updating these lists triggers re-composition across the entire library UI.
- **Analytics Overhead**: `AnalyticsRepository.scanAndSaveAnalytics` iterates through the entire media library multiple times to calculate sizes and group by extension. This is triggered automatically whenever the library changes.

## 4. UI Rendering & Compose Performance

- **Visualizer 60 FPS Loop**: `AudioVisualizer` uses a `while(true)` loop with `withFrameNanos` to trigger re-composition on every frame. This is extremely power-hungry and can cause jank if the Canvas drawing (which involves complex gradients and paths) takes too long.
- **Ambient Aura Calculations**: The `LibraryScreen` calculates "Ambient Auras" by extracting dominant hues from album art. `extractBaseHueFromArt` decodes bitmaps and iterates through pixels. While it uses `Dispatchers.IO`, rapid track skipping can queue up many heavy background tasks.
- **Search Filtering**: Filtering large lists in `LibraryScreen` (`filteredImages = remember(imagesList, searchQuery)`) happens on the main thread. As the list grows, typing in the search bar will become laggy.

## 5. Build & Configuration Issues

> [!IMPORTANT]
> The release build is not optimized for performance.

- **Minification Disabled**: `isMinifyEnabled = false` in `build.gradle.kts`. This means R8 optimizations are not applied, resulting in larger code and slower execution.
- **Resource Optimization Disabled**: `isCrunchPngs = false` prevents PNG optimization.
- **Unused Dependencies**: Several libraries are included but not used (e.g., `Accompanist Permissions`, `Moshi`, `Retrofit`), increasing APK size and initialization time.

## 6. ExoPlayer & Service Overhead

- **Notification Updates**: `FloatingPlayerService` decodes the app icon and artwork bitmaps repeatedly in `buildNotification`.
- **MediaMetadataRetriever in Service**: Artwork is loaded using `MediaMetadataRetriever` on every track change, which is slower than using standard `ContentResolver` thumbnails.

---

### Suggested Priorities for Fixes

1.  **Refactor Media Scanning**: Replace the recursive file walk with a more targeted approach or move it to a truly background task with heavy caching.
2.  **Implement Pagination/Paging**: Use the Jetpack Paging library or simple windowed loading for media lists.
3.  **Optimize Release Build**: Enable R8 and PNG crunching.
4.  **Defer Startup Tasks**: Move DB/Hardware detection to a background thread or initialize them lazily.
5.  **Throttle Analytics**: Run analytics scans less frequently or use a Worker.
