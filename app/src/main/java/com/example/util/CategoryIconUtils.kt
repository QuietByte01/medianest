package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconUtils {
    val AVAILABLE_ICONS = listOf(
        "Category" to Icons.Default.Category,
        "Movie" to Icons.Default.Movie,
        "Music" to Icons.Default.MusicNote,
        "Star" to Icons.Default.Star,
        "Bookmark" to Icons.Default.Bookmark,
        "Folder" to Icons.Default.Folder,
        "Work" to Icons.Default.Work,
        "School" to Icons.Default.School,
        "Fitness" to Icons.Default.FitnessCenter,
        "Shows" to Icons.Default.Tv,
        "Home" to Icons.Default.Home,
        "Lock" to Icons.Default.Lock
    )

    val AUDIO_ICONS = AVAILABLE_ICONS.filter { (key, _) -> key != "Movie" && key != "Shows" }

    fun getCategoryIcon(iconName: String?): ImageVector {
        return when (iconName) {
            "Movie" -> Icons.Default.Movie
            "Folder" -> Icons.Default.Folder
            "Star" -> Icons.Default.Star
            "Bookmark" -> Icons.Default.Bookmark
            "Music" -> Icons.Default.MusicNote
            "Work" -> Icons.Default.Work
            "School", "Learning" -> Icons.Default.School
            "Fitness" -> Icons.Default.FitnessCenter
            "Shows", "Tv" -> Icons.Default.Tv
            "Home" -> Icons.Default.Home
            "Lock" -> Icons.Default.Lock
            else -> Icons.Default.Category
        }
    }
}
