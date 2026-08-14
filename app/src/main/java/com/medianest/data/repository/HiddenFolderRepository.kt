package com.medianest.data.repository

import android.util.Log
import com.medianest.data.db.MediaType
import com.medianest.data.db.SelectiveHiddenFolder
import com.medianest.data.db.SelectiveHiddenFolderDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HiddenFolderRepository(
    private val selectiveHiddenFolderDao: SelectiveHiddenFolderDao
) {
    fun getHiddenFolders(mediaType: MediaType): Flow<List<SelectiveHiddenFolder>> {
        return selectiveHiddenFolderDao.getHiddenFolders(mediaType.name)
    }

    fun getAllHiddenFolders(): Flow<List<SelectiveHiddenFolder>> {
        return selectiveHiddenFolderDao.getAllHiddenFolders()
    }

    fun getAllHiddenFolderPaths(mediaType: String): Flow<Set<String>> {
        return selectiveHiddenFolderDao.getHiddenFolders(mediaType).map { list ->
            val set = mutableSetOf<String>()
            list.forEach {
                if (it.folderPath.isNotBlank()) set.add(it.folderPath)
                if (it.folderName.isNotBlank()) set.add(it.folderName)
            }
            set
        }
    }

    suspend fun setFolderHidden(
        folderPath: String,
        folderName: String,
        mediaType: MediaType,
        hidden: Boolean
    ) {
        if (hidden) {
            selectiveHiddenFolderDao.insertOrUpdate(
                SelectiveHiddenFolder(
                    folderPath = folderPath,
                    folderName = folderName,
                    mediaType = mediaType.name,
                    isHidden = true
                )
            )
            Log.d("HIDE_LOGIC", "Folder hidden: path=$folderPath, name=$folderName, type=${mediaType.name}")
        } else {
            selectiveHiddenFolderDao.unhideFolder(folderPath, mediaType.name)
            selectiveHiddenFolderDao.unhideFolder(folderName, mediaType.name)
            Log.d("HIDE_LOGIC", "Folder unhidden: path=$folderPath, name=$folderName, type=${mediaType.name}")
        }
    }
}
