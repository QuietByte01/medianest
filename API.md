
### **📱 Application Overview**

- **App Name:** MediaNest
- **Type:** Universal Media Viewer, Audio/Video Player & Gallery Manager
- **Architecture:** Kotlin, Jetpack Compose, Room Database, ExoPlayer

---

### **✨ Key Features & Capabilities**

1. **Library & Media Management**
    - **Videos Tab:** Custom video categories, quick video previews, and metadata management.
    - **Audio Tab:** High-fidelity audio playback with interactive wavy seekbar, audio visualizers, lyrics display, and background notification cards.
    - **Images Tab:** Fast image grid and detail viewer.
    - **Hidden Folders:** Secure, locked private folders with PIN/biometric dialog protection.
2. **Playback Engines**
    - **Dedicated Video & Audio Players:** ExoPlayer-powered native playback screens with subtitle support and picture-in-picture modes.
    - **Floating Player Service:** Floating overlay picture-in-picture widget for multitasking across apps.
    - **Quick View Mode:** Instant media preview without leaving current view context.
3. **Analytics & Preferences**
    - **Media Analytics:** Visual breakdown and drill-down reports of media consumption and local storage usage.
    - **Settings Manager:** App lock, theme preferences, and media folder filtering.

---

Here is the list of APIs, SDKs, and development kits used in **MediaNest**:

### **1. Core Development Kit & UI**

- **Kotlin** (v2.2.10) – Primary programming language.
- **Jetpack Compose** (Compose BOM 2024.09.00) – Declarative UI framework with **Material Design 3 (M3)** components and Material Extended Icons.
- **Navigation Compose** (v2.8.9) – Type-safe screen navigation and backstack management.
- **AndroidX Lifecycle & ViewModel** (v2.8.7) – Architecture components for state handling and coroutine scoping (collectAsStateWithLifecycle).

---

### **2. Media Playback & Android System APIs**

- **AndroidX Media3 ExoPlayer** (v1.5.1) – Modern audio and video player engine (exoplayer, media3-ui, media3-session, media3-common).
- **Android MediaStore API** – Native system Content Provider used to index local audio, video, and image files.
- **Android MediaSession & Foreground Services** – Background audio playback, system media controls, and floating player support.
- **Coil** (v2.7.0) – Asynchronous image loading and video thumbnail extraction (coil-compose, coil-video).

---

### **3. Data Storage & Persistence**

- **Room Database** (v2.7.0 via Kotlin KSP) – Local SQLite database for media metadata, hidden folders, playlists, and analytics.
- **Jetpack DataStore Preferences** (v1.1.7) – Asynchronous key-value storage for application preferences and settings.

---

### **4. Networking & Data Parsing**

- **Retrofit** (v2.12.0) & **OkHttp** (v4.10.0) – HTTP client and REST network library.
- **Moshi** (v1.15.2 with KSP Codegen) – JSON parser and model serializer.

---

### **5. Cloud, AI & Security**

- **Firebase BOM** (v34.15.0) – Firebase suite, including firebase-ai and firebase-appcheck-recaptcha.
- **Secrets Gradle Plugin** – Tooling to inject environment variables and API keys from .env into BuildConfig.
- **Accompanist Permissions** (v0.37.3) – Jetpack Compose runtime permission handlers.

---

### **6. Testing & Tooling Frameworks**

- **Robolectric** (v4.16.1) – Local JVM Android runner for testing logic without an emulator.
- **Roborazzi** (v1.59.0) – Screenshot testing engine for UI verification.
- **JUnit 4** & **Espresso Core** – Standard testing frameworks.

---

In **MediaNest**, the following APIs, web services, and local framework sources are used for fetching subtitles, lyrics, metadata/info, album art, and artist imagery:

---

### **1. Lyrics (Synced .lrc & Plain Lyrics)**

- **LRCLIB API** (https://lrclib.net/api/get & /search)
    - Searches and fetches synchronized .lrc lyrics with millisecond timestamps and plain text lyrics using track title and artist name.
- **Local Fallback**
    - Parses embedded .lrc text or generates responsive timed lyric displays for offline listening.

---

### **2. Subtitles**

- **Wyzie Subtitles API** (https://sub.wyzie.ru/search)
    - Searches online video subtitle database streams based on video file titles.
- **Online Sample Mirror**
    - Hosted GitHub subtitle resources for multi-language SRT tracks (English, English SDH, Spanish, French, Hindi).
- **Local Parsing**
    - Supports local .srt and .vtt files alongside ExoPlayer embedded subtitle streams.

---

### **3. Track Info & Metadata (Title, Artist, Album, Genre, Release Year)**

- **iTunes Search API** (https://itunes.apple.com/search?media=music)
    - Fetches clean metadata, track name, album title, genre, and release year.
- **MusicBrainz Web API** (https://musicbrainz.org/ws/2/recording/)
    - Secondary fallback query for open music library track lookup.
- **Android MediaMetadataRetriever**
    - Reads local ID3 tags directly from on-device media files.

---

### **4. Album Art**

- **iTunes High-Res Artwork API**
    - Retrieves high-resolution 600x600 cover art images derived from iTunes catalog URLs.
- **Embedded ID3 Cover Art**
    - Extracts embedded cover images from audio files via MediaMetadataRetriever.embeddedPicture into local app cache.

---

### **5. Artist Imagery & Portraits**

- **Deezer API** (https://api.deezer.com/search/artist)
    - Primary API used to query and display high-definition artist profile photos (picture_big / picture_xl).
- **iTunes Artist Catalog**
    - Backup source for artist and group visuals.
- **Unsplash Celled Photography**
    - Curated high-quality fallback visuals for unknown artists, composers, and movie soundtracks.