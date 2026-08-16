package com.medianest.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.medianest.data.db.MediaType
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.getFilePathFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class FolderInfo(
    val name: String,
    val count: Int,
    val previewUri: Uri?
)

class MediaStoreRepository(private val context: Context) {

    suspend fun getImages(
        hiddenFolders: Set<String> = emptySet(),
        showHidden: Boolean = false,
        limit: Int = -1,
        offset: Int = 0
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val imagesList = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            "datetaken",
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val queryBundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.os.Bundle().apply {
                    if (limit > 0) {
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                    }
                    if (showHidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    }
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                }
            } else null

            val cursor = if (queryBundle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.contentResolver.query(collection, projection, queryBundle, null)
            } else {
                context.contentResolver.query(collection, projection, null, null, sortOrder)
            }

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthColumn = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightColumn = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedColumn = c.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val dateTakenColumn = c.getColumnIndex("datetaken")
                val bucketIdColumn = c.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameColumn = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH) else -1

                while (c.moveToNext()) {
                    val relativePath = if (relPathColumn >= 0) c.getString(relPathColumn) else null
                    val bucketName = if (bucketNameColumn >= 0) c.getString(bucketNameColumn) else null
                    val bucketId = if (bucketIdColumn >= 0) c.getString(bucketIdColumn) else null
                    
                    val folderName = bucketName ?: relativePath?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Pictures"
                    val id = c.getLong(idColumn)
                    val name = c.getString(nameColumn) ?: "Image_$id"

                    val isFolderDisabled = hiddenFolders.isNotEmpty() && hiddenFolders.any { hidden ->
                        hidden.equals(folderName, ignoreCase = true) ||
                        (bucketName != null && hidden.equals(bucketName, ignoreCase = true)) ||
                        (bucketId != null && hidden.equals(bucketId, ignoreCase = true)) ||
                        (relativePath != null && relativePath.split("/").any { part -> part.isNotBlank() && part.equals(hidden, ignoreCase = true) })
                    }
                    if (!showHidden && isFolderDisabled) {
                        continue
                    }

                    if (!showHidden) {
                        if (name.startsWith(".") || folderName.startsWith(".") || (relativePath != null && relativePath.split("/").any { it.startsWith(".") })) {
                            continue
                        }
                    }

                    val mimeType = c.getString(mimeColumn) ?: "image/*"
                    val width = if (widthColumn >= 0) c.getInt(widthColumn) else 0
                    val height = if (heightColumn >= 0) c.getInt(heightColumn) else 0
                    val size = c.getLong(sizeColumn)
                    val dateAdded = c.getLong(dateColumn)
                    val dateModified = if (dateModifiedColumn >= 0) c.getLong(dateModifiedColumn) else dateAdded
                    val dateTaken = if (dateTakenColumn >= 0) c.getLong(dateTakenColumn) / 1000 else dateModified

                    val contentUri = ContentUris.withAppendedId(collection, id)

                    imagesList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            title = name,
                            mimeType = mimeType,
                            type = MediaType.IMAGE,
                            width = width,
                            height = height,
                            size = size,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            dateCreated = dateTaken,
                            bucketId = bucketId,
                            bucketName = folderName,
                            relativePath = relativePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val hasAllFilesAccess = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()
        if (showHidden || hasAllFilesAccess) {
            val hiddenFromFileSystem = scanFileSystemHiddenMedia(MediaType.IMAGE, hiddenFolders, showHidden = showHidden)
            val existingUris = imagesList.map { it.uri.toString() }.toSet()
            val existingNameSize = imagesList.map { "${it.title.substringAfterLast('/')}_${it.size}" }.toSet()

            hiddenFromFileSystem.forEach { item ->
                val nameSizeKey = "${item.title.substringAfterLast('/')}_${item.size}"
                if (!existingUris.contains(item.uri.toString()) && !existingNameSize.contains(nameSizeKey)) {
                    imagesList.add(item)
                }
            }
        }

        imagesList
    }

    suspend fun getVideos(
        hiddenFolders: Set<String> = emptySet(),
        showHidden: Boolean = false,
        limit: Int = -1,
        offset: Int = 0
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val videosList = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            "datetaken",
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
                add(MediaStore.Video.Media.ORIENTATION)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            val queryBundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.os.Bundle().apply {
                    if (limit > 0) {
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                    }
                    if (showHidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    }
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                }
            } else null

            val cursor = if (queryBundle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.contentResolver.query(collection, projection, queryBundle, null)
            } else {
                context.contentResolver.query(collection, projection, null, null, sortOrder)
            }

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeColumn = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthColumn = c.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightColumn = c.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val durationColumn = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedColumn = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val dateTakenColumn = c.getColumnIndex("datetaken")
                val bucketIdColumn = c.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameColumn = c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1
                val orientationColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex(MediaStore.Video.Media.ORIENTATION) else -1

                while (c.moveToNext()) {
                    val relativePath = if (relPathColumn >= 0) c.getString(relPathColumn) else null
                    val orientation = if (orientationColumn >= 0) c.getInt(orientationColumn) else 0
                    val bucketName = if (bucketNameColumn >= 0) c.getString(bucketNameColumn) else null
                    val bucketId = if (bucketIdColumn >= 0) c.getString(bucketIdColumn) else null
                    
                    val folderName = bucketName ?: relativePath?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Movies"
                    val id = c.getLong(idColumn)
                    val name = c.getString(nameColumn) ?: "Video_$id"

                    val isFolderDisabled = hiddenFolders.isNotEmpty() && hiddenFolders.any { hidden ->
                        hidden.equals(folderName, ignoreCase = true) ||
                        (bucketName != null && hidden.equals(bucketName, ignoreCase = true)) ||
                        (bucketId != null && hidden.equals(bucketId, ignoreCase = true)) ||
                        (relativePath != null && relativePath.split("/").any { part -> part.isNotBlank() && part.equals(hidden, ignoreCase = true) })
                    }
                    if (!showHidden && isFolderDisabled) {
                        continue
                    }

                    if (!showHidden) {
                        if (name.startsWith(".") || folderName.startsWith(".") || (relativePath != null && relativePath.split("/").any { it.startsWith(".") })) {
                            continue
                        }
                    }

                    val mimeType = c.getString(mimeColumn) ?: "video/*"
                    val rawWidth = if (widthColumn >= 0) c.getInt(widthColumn) else 0
                    val rawHeight = if (heightColumn >= 0) c.getInt(heightColumn) else 0
                    val (width, height) = if (orientation == 90 || orientation == 270) {
                        rawHeight to rawWidth
                    } else {
                        rawWidth to rawHeight
                    }
                    val duration = if (durationColumn >= 0) c.getLong(durationColumn) else 0L
                    val size = c.getLong(sizeColumn)
                    val dateAdded = c.getLong(dateColumn)
                    val dateModified = if (dateModifiedColumn >= 0) c.getLong(dateModifiedColumn) else dateAdded
                    val dateTaken = if (dateTakenColumn >= 0) c.getLong(dateTakenColumn) / 1000 else dateModified

                    val contentUri = ContentUris.withAppendedId(collection, id)

                    videosList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            title = name,
                            mimeType = mimeType,
                            type = MediaType.VIDEO,
                            width = width,
                            height = height,
                            durationMs = duration,
                            size = size,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            dateCreated = dateTaken,
                            bucketId = bucketId,
                            bucketName = folderName,
                            relativePath = relativePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val hasAllFilesAccess = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()
        if (showHidden || hasAllFilesAccess) {
            val hiddenFromFileSystem = scanFileSystemHiddenMedia(MediaType.VIDEO, hiddenFolders, showHidden = showHidden)
            val existingUris = videosList.map { it.uri.toString() }.toSet()
            val existingNameSize = videosList.map { "${it.title.substringAfterLast('/')}_${it.size}" }.toSet()

            hiddenFromFileSystem.forEach { item ->
                val nameSizeKey = "${item.title.substringAfterLast('/')}_${item.size}"
                if (!existingUris.contains(item.uri.toString()) && !existingNameSize.contains(nameSizeKey)) {
                    videosList.add(item)
                }
            }
        }

        videosList
    }

    suspend fun getAudio(
        hiddenFolders: Set<String> = emptySet(),
        showHidden: Boolean = false,
        limit: Int = -1,
        offset: Int = 0
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<MediaItem>()
        val mediaStoreFileNames = mutableSetOf<String>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.BUCKET_ID)
                add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            val queryBundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.os.Bundle().apply {
                    if (limit > 0) {
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                    }
                    if (showHidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    }
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                }
            } else null

            val cursor = if (queryBundle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.contentResolver.query(collection, projection, queryBundle, null)
            } else {
                context.contentResolver.query(collection, projection, null, null, sortOrder)
            }

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val nameColumn = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val durationColumn = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val artistColumn = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumColumn = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val bucketIdColumn = c.getColumnIndex("bucket_id")
                val bucketNameColumn = c.getColumnIndex("bucket_display_name")
                val relPathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex("relative_path") else -1

                while (c.moveToNext()) {
                    val relativePath = if (relPathColumn >= 0) c.getString(relPathColumn) else null
                    val bucketName = if (bucketNameColumn >= 0) c.getString(bucketNameColumn) else null
                    val bucketId = if (bucketIdColumn >= 0) c.getString(bucketIdColumn) else null
                    
                    val folderName = bucketName ?: relativePath?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Music"
                    val id = c.getLong(idColumn)
                    val title = if (titleColumn >= 0) c.getString(titleColumn) else null
                    val name = if (nameColumn >= 0) c.getString(nameColumn) else "Audio_$id"

                    val isFolderDisabled = hiddenFolders.isNotEmpty() && hiddenFolders.any { hidden ->
                        hidden.equals(folderName, ignoreCase = true) ||
                        (bucketName != null && hidden.equals(bucketName, ignoreCase = true)) ||
                        (bucketId != null && hidden.equals(bucketId, ignoreCase = true)) ||
                        (relativePath != null && relativePath.split("/").any { part -> part.isNotBlank() && part.equals(hidden, ignoreCase = true) })
                    }
                    if (!showHidden && isFolderDisabled) {
                        continue
                    }

                    if (!showHidden) {
                        if (name.startsWith(".") || folderName.startsWith(".") || (relativePath != null && relativePath.split("/").any { it.startsWith(".") })) {
                            continue
                        }
                    }
                    val mimeType = c.getString(mimeColumn) ?: "audio/*"
                    val duration = if (durationColumn >= 0) c.getLong(durationColumn) else 0L
                    val size = c.getLong(sizeColumn)
                    val dateAdded = c.getLong(dateColumn)
                    val dateModified = if (dateModifiedColumn >= 0) c.getLong(dateModifiedColumn) else dateAdded

                    val artist = if (artistColumn >= 0) c.getString(artistColumn) else null
                    val album = if (albumColumn >= 0) c.getString(albumColumn) else null
                    val albumId = if (albumIdColumn >= 0) c.getLong(albumIdColumn) else -1L

                    val rawTitle = if (titleColumn >= 0) c.getString(titleColumn) else null
                    val displayName = if (nameColumn >= 0) c.getString(nameColumn) else null
                    val cleanedTitle = when {
                        !rawTitle.isNullOrBlank() && !rawTitle.all { it.isDigit() } -> rawTitle
                        !displayName.isNullOrBlank() && !displayName.all { it.isDigit() } -> displayName.substringBeforeLast('.')
                        else -> "Track $id"
                    }

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val artworkUri = if (albumId != -1L) {
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                    } else null

                    audioList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            title = cleanedTitle,
                            mimeType = mimeType,
                            type = MediaType.AUDIO,
                            durationMs = duration,
                            size = size,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            dateCreated = dateModified, // Audio usually doesn't have datetaken, use modified
                            artist = artist?.takeIf { it != "<unknown>" },
                            album = album?.takeIf { it != "<unknown>" },
                            bucketId = bucketId,
                            bucketName = folderName,
                            albumArtUri = artworkUri
                        )
                    )
                    
                    if (!displayName.isNullOrBlank()) {
                        mediaStoreFileNames.add("${displayName.substringBeforeLast('.').lowercase().trim()}_${size}")
                        mediaStoreFileNames.add("${displayName.lowercase().trim()}_${size}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val hasAllFilesAccess = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()
        if (showHidden || hasAllFilesAccess) {
            val hiddenFromFileSystem = scanFileSystemHiddenMedia(MediaType.AUDIO, hiddenFolders, showHidden = showHidden)
            val existingUris = audioList.map { it.uri.toString() }.toSet()
            val existingPaths = audioList.mapNotNull { item ->
                item.uri.path?.lowercase()?.trim('/') ?: item.relativePath?.lowercase()?.trim('/')
            }.toSet()

            val existingKeys = mutableSetOf<String>()
            audioList.forEach { item ->
                val normTitle = item.title.substringBeforeLast('.').lowercase().trim()
                if (normTitle.isNotBlank()) {
                    existingKeys.add("${normTitle}_${item.size}")
                }
                existingKeys.add("${item.title.lowercase().trim()}_${item.size}")
            }

            hiddenFromFileSystem.forEach { item ->
                val normTitle = item.title.substringBeforeLast('.').lowercase().trim()
                val titleSizeKey = "${normTitle}_${item.size}"
                val rawTitleSizeKey = "${item.title.lowercase().trim()}_${item.size}"
                val itemPath = item.uri.path?.lowercase()?.trim('/')

                val isDuplicate = existingUris.contains(item.uri.toString()) ||
                        existingKeys.contains(titleSizeKey) ||
                        existingKeys.contains(rawTitleSizeKey) ||
                        mediaStoreFileNames.contains(titleSizeKey) ||
                        mediaStoreFileNames.contains(rawTitleSizeKey) ||
                        (itemPath != null && existingPaths.any { path -> path.endsWith(normTitle) || itemPath.contains(path) || path.contains(itemPath) })

                if (!isDuplicate) {
                    val cleanTitle = if (item.title.contains('.')) item.title.substringBeforeLast('.') else item.title
                    audioList.add(item.copy(title = cleanTitle))
                    existingKeys.add(titleSizeKey)
                }
            }
        }

        val deduplicatedAudioList = mutableListOf<MediaItem>()
        val seenKeys = mutableSetOf<String>()
        for (item in audioList) {
            val normTitle = item.title.substringBeforeLast('.').lowercase().trim()
            val key = if (item.size > 0 && normTitle.isNotBlank()) "${normTitle}_${item.size}" else item.uri.toString()
            if (seenKeys.add(key)) {
                deduplicatedAudioList.add(item)
            }
        }

        try {
            val cacheList = com.medianest.MediaNestApp.instance.database.metadataCacheDao().getAllCache()
            if (cacheList.isNotEmpty()) {
                val cacheMap = cacheList.associateBy { it.audioUri }
                for (i in deduplicatedAudioList.indices) {
                    val item = deduplicatedAudioList[i]
                    val cached = cacheMap[item.uri.toString()]
                    if (cached != null) {
                        val cachedTitle = cached.title?.takeIf { it.isNotBlank() }
                        val cleanTitle = cachedTitle?.let { if (it.contains('.')) it.substringBeforeLast('.') else it } ?: item.title
                        deduplicatedAudioList[i] = item.copy(
                            title = cleanTitle,
                            artist = cached.artist?.takeIf { it.isNotBlank() } ?: item.artist,
                            album = cached.album?.takeIf { it.isNotBlank() } ?: item.album
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        deduplicatedAudioList
    }

    /**
     * Resolves sibling media items when an external Intent sends a content URI.
     * Queries MediaStore for files with matching MIME family in the same parent directory.
     */
    suspend fun resolveSiblingsForUri(targetUri: Uri, mimeType: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val resolvedMime = mimeType ?: context.contentResolver.getType(targetUri) ?: ""
        val isVideo = resolvedMime.startsWith("video")
        val isAudio = resolvedMime.startsWith("audio")
        
        val fullList = when {
            isVideo -> getVideos()
            isAudio -> getAudio()
            else -> getImages()
        }

        if (fullList.isEmpty()) {
            val single = MediaItem(
                id = 1L,
                uri = targetUri,
                title = targetUri.lastPathSegment ?: "Media",
                mimeType = resolvedMime.ifEmpty { "media/*" },
                type = if (isVideo) MediaType.VIDEO else if (isAudio) MediaType.AUDIO else MediaType.IMAGE
            )
            return@withContext listOf(single)
        }

        // Return full list so user can swipe through all items in library
        val foundIndex = fullList.indexOfFirst { it.uri.toString() == targetUri.toString() || it.uri == targetUri }
        if (foundIndex != -1) {
            return@withContext fullList
        }

        // If not directly found in MediaStore query (e.g. custom provider URI), append targetUri
        val targetItem = MediaItem(
            id = System.currentTimeMillis(),
            uri = targetUri,
            title = targetUri.lastPathSegment ?: "Media",
            mimeType = resolvedMime.ifEmpty { "media/*" },
            type = if (isVideo) MediaType.VIDEO else if (isAudio) MediaType.AUDIO else MediaType.IMAGE
        )
        return@withContext listOf(targetItem) + fullList
    }

    fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer
        )

        trySend(Unit)

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    private val commonDotFolders = listOf(
        ".secret", ".hidden", ".pictures", ".videos", ".audio", ".photos",
        ".vault", ".gallery", ".private", ".nomedia", ".trash", ".backup",
        ".data", ".myfiles", ".secure", ".media", ".download", ".downloads",
        ".Telegram", ".WhatsApp", ".facebook", ".instagram", ".status", ".Statuses",
        ".RecycleBin", ".stfolder", ".camera", ".DCIM", ".Images", ".Videos",
        ".Audio", ".Music", ".Documents", ".Files", ".thumbnails", ".recycle_bin", "recycle.bin"
    )

    private fun scanFileSystemHiddenMedia(mediaType: MediaType, hiddenFolders: Set<String> = emptySet(), showHidden: Boolean = true): List<MediaItem> {
        val hiddenItems = mutableListOf<MediaItem>()
        try {
            val rootDir = android.os.Environment.getExternalStorageDirectory() ?: return emptyList()
            val extensions = when (mediaType) {
                MediaType.IMAGE -> setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "svg")
                MediaType.VIDEO -> setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "flv")
                MediaType.AUDIO -> setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma")
            }

            val visitedPaths = mutableSetOf<String>()

            // 1. SAF persisted tree URIs scanning via DocumentFile
            try {
                val persistedPermissions = context.contentResolver.persistedUriPermissions
                for (perm in persistedPermissions) {
                    if (perm.isReadPermission && perm.uri != null) {
                        val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, perm.uri)
                        if (rootDoc != null && rootDoc.exists() && rootDoc.isDirectory) {
                            scanDocumentFileDir(rootDoc, mediaType, extensions, hiddenFolders, showHidden, hiddenItems, visitedPaths, 0)
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore SAF access errors
            }

            // 2. File system scanning (with DocumentFile fallback and dot-folder probing)
            fun scanDir(dir: java.io.File, depth: Int, parentIsHidden: Boolean = false) {
                if (depth > 10 || !dir.exists() || !dir.isDirectory) return
                val canonicalPath = try { dir.canonicalPath } catch (e: Exception) { dir.absolutePath }
                if (visitedPaths.contains(canonicalPath)) return
                visitedPaths.add(canonicalPath)

                val dirName = dir.name
                if (dirName == "Android" && dir.parentFile?.absolutePath == rootDir.absolutePath) return

                val isSelectivelyDisabledFolder = hiddenFolders.isNotEmpty() && hiddenFolders.any { hidden ->
                    dirName.equals(hidden, ignoreCase = true) || dir.absolutePath.contains(hidden, ignoreCase = true)
                }

                if (!showHidden && isSelectivelyDisabledFolder) return

                var rawFiles: Array<java.io.File>? = dir.listFiles()
                if (rawFiles == null) {
                    try {
                        val docDir = androidx.documentfile.provider.DocumentFile.fromFile(dir)
                        if (docDir.exists() && docDir.isDirectory) {
                            val docFiles = docDir.listFiles()
                            rawFiles = docFiles.mapNotNull { doc ->
                                val p = doc.uri.path
                                if (p != null) java.io.File(p) else null
                            }.toTypedArray()
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                val filesList = (rawFiles ?: emptyArray()).toMutableList()

                val hasNoMedia = filesList.any { it.name.equals(".nomedia", ignoreCase = true) }
                val isHiddenFolder = parentIsHidden || dirName.startsWith(".") || dir.absolutePath.contains("/.") || hasNoMedia || isSelectivelyDisabledFolder

                for (file in filesList) {
                    if (file.isDirectory) {
                        scanDir(file, depth + 1, isHiddenFolder)
                    } else if (file.isFile) {
                        val ext = file.extension.lowercase()
                        val isHiddenFile = file.name.startsWith(".") || isHiddenFolder
                        if (extensions.contains(ext) && (showHidden || !isHiddenFile)) {
                            val fileUri = Uri.fromFile(file)
                            val folderName = if (dirName.isNotBlank()) dirName else (file.parentFile?.name ?: "Hidden")
                            val relPath = file.parentFile?.absolutePath?.removePrefix(rootDir.absolutePath)?.trim('/')?.let { "$it/" } ?: "$folderName/"
                            
                            val meta = com.medianest.util.MediaMetadataUtils.extractBasicMetadata(context, fileUri)

                            hiddenItems.add(
                                MediaItem(
                                    id = file.absolutePath.hashCode().toLong(),
                                    uri = fileUri,
                                    title = if (mediaType == MediaType.AUDIO && file.name.contains('.')) file.name.substringBeforeLast('.') else file.name,
                                    mimeType = when (mediaType) {
                                        MediaType.IMAGE -> "image/$ext"
                                        MediaType.VIDEO -> "video/$ext"
                                        MediaType.AUDIO -> "audio/$ext"
                                    },
                                    type = mediaType,
                                    width = meta.width,
                                    height = meta.height,
                                    durationMs = meta.durationMs,
                                    size = file.length(),
                                    dateAdded = if (meta.dateCreated > 0) meta.dateCreated else (file.lastModified() / 1000),
                                    dateModified = meta.dateModified,
                                    dateCreated = meta.dateCreated,
                                    bucketName = folderName,
                                    relativePath = relPath,
                                    artist = meta.artist,
                                    album = meta.album
                                )
                            )
                        }
                    }
                }
            }

            val scanRoots = mutableListOf<java.io.File>()
            scanRoots.add(rootDir)
            val publicDirs = listOf(
                android.os.Environment.DIRECTORY_PICTURES,
                android.os.Environment.DIRECTORY_DCIM,
                android.os.Environment.DIRECTORY_DOWNLOADS,
                android.os.Environment.DIRECTORY_MOVIES,
                android.os.Environment.DIRECTORY_MUSIC,
                android.os.Environment.DIRECTORY_DOCUMENTS,
                android.os.Environment.DIRECTORY_PODCASTS
            )
            for (pDir in publicDirs) {
                try {
                    val f = android.os.Environment.getExternalStoragePublicDirectory(pDir)
                    if (f != null && f.exists()) scanRoots.add(f)
                } catch (e: Exception) {
                    // ignore
                }
            }

            for (root in scanRoots) {
                scanDir(root, 0, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return hiddenItems
    }

    private fun scanDocumentFileDir(
        docDir: androidx.documentfile.provider.DocumentFile,
        mediaType: MediaType,
        extensions: Set<String>,
        hiddenFolders: Set<String>,
        showHidden: Boolean,
        hiddenItems: MutableList<MediaItem>,
        visitedPaths: MutableSet<String>,
        depth: Int,
        parentRelativePath: String = ""
    ) {
        if (depth > 10 || !docDir.isDirectory) return
        val dirName = docDir.name ?: ""
        val uriStr = docDir.uri.toString()
        if (visitedPaths.contains(uriStr)) return
        visitedPaths.add(uriStr)

        val currentRelativePath = if (parentRelativePath.isBlank()) {
            if (dirName.isNotBlank()) "$dirName/" else ""
        } else {
            "$parentRelativePath$dirName/"
        }

        val children = try { docDir.listFiles() } catch (e: Exception) { emptyArray() }
        val hasNoMedia = children.any { (it.name ?: "").equals(".nomedia", ignoreCase = true) }
        val isHiddenFolder = dirName.startsWith(".") ||
                currentRelativePath.startsWith(".") ||
                currentRelativePath.split("/").any { it.startsWith(".") } ||
                hasNoMedia ||
                (hiddenFolders.isNotEmpty() && hiddenFolders.any { hidden -> dirName.equals(hidden, ignoreCase = true) || currentRelativePath.contains(hidden, ignoreCase = true) })

        if (!showHidden && isHiddenFolder) return

        for (child in children) {
            if (child.isDirectory) {
                scanDocumentFileDir(child, mediaType, extensions, hiddenFolders, showHidden, hiddenItems, visitedPaths, depth + 1, currentRelativePath)
            } else if (child.isFile) {
                val name = child.name ?: ""
                val ext = name.substringAfterLast('.', "").lowercase()
                val isHiddenFile = name.startsWith(".") || isHiddenFolder
                if (extensions.contains(ext) && (showHidden || !isHiddenFile)) {
                    val folderName = if (dirName.isNotBlank()) dirName else "Hidden"
                    val relPath = currentRelativePath.ifBlank { "$folderName/" }
                    
                    val meta = com.medianest.util.MediaMetadataUtils.extractBasicMetadata(context, child.uri)
                    
                    hiddenItems.add(
                        MediaItem(
                            id = child.uri.toString().hashCode().toLong(),
                            uri = child.uri,
                            title = name,
                            mimeType = when (mediaType) {
                                MediaType.IMAGE -> "image/$ext"
                                MediaType.VIDEO -> "video/$ext"
                                MediaType.AUDIO -> "audio/$ext"
                            },
                            type = mediaType,
                            width = meta.width,
                            height = meta.height,
                            durationMs = meta.durationMs,
                            size = child.length(),
                            dateAdded = child.lastModified() / 1000,
                            dateModified = child.lastModified() / 1000,
                            dateCreated = if (meta.dateCreated > 0) meta.dateCreated else (child.lastModified() / 1000),
                            bucketName = folderName,
                            relativePath = relPath,
                            artist = meta.artist,
                            album = meta.album
                        )
                    )
                }
            }
        }
    }
}
