# Local Database Schema

MediaNest uses the **Room Persistence Library** to manage its 100% local database. This schema stores everything from user preferences and custom categories to media metadata caches and analytics snapshots.

---

## 1. Core Entities

These tables handle the primary organization and playback state of the application.

### `media_categories`
Stores user-defined collections like playlists or video folders.
| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | Long (PK) | Auto-generated unique identifier. |
| `name` | String | User-defined name (e.g., "Anime", "Summer 2024"). |
| `type` | String | Category type: `VIDEO` or `AUDIO`. |
| `coverUri` | String? | Optional custom thumbnail URI. |
| `iconName` | String? | Optional custom icon name for UI display. |
| `createdAt` | Long | Timestamp of creation. |
| `sortOrder` | Int | Manual sorting position. |

### `category_media_cross_ref`
A junction table for many-to-many relationships between categories and media items.
| Column | Type | Description |
| :--- | :--- | :--- |
| `categoryId` | Long (PK) | Reference to `media_categories.id`. |
| `mediaUri` | String (PK) | The Content URI or MediaStore ID of the item. |
| `positionInCategory` | Int | Order of the item within this specific category. |

### `playback_states`
Persists the current state of media items for "Resume Playback" and custom settings.
| Column | Type | Description |
| :--- | :--- | :--- |
| `mediaUri` | String (PK) | Unique URI of the media item. |
| `mediaType` | String | `AUDIO` or `VIDEO`. |
| `positionMs` | Long | Last playback position in milliseconds. |
| `durationMs` | Long | Total duration of the media. |
| `playbackSpeed` | Float | User-selected playback speed (e.g., 1.5f). |
| `savedBrightness` | Float? | Per-video brightness level memory. |
| `cropMode` | String | Display mode: `FIT`, `CROP`, or `STRETCH`. |
| `subtitleUri` | String? | Path to the last used subtitle file. |
| `playCount` | Int | Total number of times the item has been played. |

---

## 2. Metadata & Enrichment Caches

These tables store data fetched from external APIs or extracted from files to enable offline browsing.

### `audio_metadata_cache`
Stores enriched ID3 tags, lyrics, and high-res album art.
| Column | Type | Description |
| :--- | :--- | :--- |
| `audioUri` | String (PK) | Unique URI of the audio track. |
| `title` | String? | Cleaned track title. |
| `artist` | String? | Artist name. |
| `album` | String? | Album name. |
| `albumArtUri` | String? | URI to the cached local copy of fetched artwork. |
| `lyricsPlain` | String? | Plain text lyrics. |
| `lyricsSyncedLrc` | String? | LRC format synchronized lyrics. |
| `genre` | String? | Track genre. |
| `composer` | String? | Track composer. |

### `artist_metadata`
Stores deep insights for artists fetched from cloud providers (Deezer/iTunes).
| Column | Type | Description |
| :--- | :--- | :--- |
| `artistName` | String (PK) | Name of the artist. |
| `about` | String? | Biography/Description. |
| `imageUrl` | String? | High-definition profile photo URL. |
| `popularAlbumsJson` | String? | JSON-encoded list of top albums. |
| `socialLinksJson` | String? | JSON-encoded artist social media links. |
| `lastUpdated` | Long | Timestamp of the last metadata sync. |

### `subtitle_cache`
Stores search results from online subtitle providers.
| Column | Type | Description |
| :--- | :--- | :--- |
| `query` | String (PK) | The search term used (usually file title). |
| `provider` | String (PK) | The API source (e.g., "Wyzie"). |
| `jsonResults` | String | JSON-encoded list of available subtitle tracks. |
| `timestamp` | Long | Timestamp for cache expiration management. |

---

## 3. Privacy & Analytics

Tables used for managing hidden media and tracking usage statistics.

### `selective_hidden_folders`
Stores path-based exclusion rules for the media scanner.
| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | Long (PK) | Auto-generated ID. |
| `folderPath` | String | Absolute path or name of the excluded directory. |
| `mediaType` | String | The type of media to hide (`IMAGE`, `VIDEO`, `AUDIO`). |
| `isHidden` | Boolean | Whether the rule is currently active. |

### `format_stats`
Provides data for the Media Analytics dashboard.
| Column | Type | Description |
| :--- | :--- | :--- |
| `extension` | String (PK) | File extension (e.g., "mp4", "flac"). |
| `category` | String (PK) | Parent media type (`VIDEO`, `AUDIO`, `IMAGE`). |
| `fileCount` | Int | Number of files with this extension. |
| `sizeBytes` | Long | Total storage consumed by this format. |
