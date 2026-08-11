package com.example.ui.library.image

import com.example.data.model.MediaItem

fun filterImageList(
    imagesList: List<MediaItem>,
    activeFilterTab: String,
    favoriteUris: Set<String>,
    trashUris: Set<String>
): List<MediaItem> {
    val systemHiddenNames = listOf(".thumbnails", ".recycle_bin", "recycle.bin", "thumbnails")

    val result = when (activeFilterTab) {
        "HIDDEN" -> imagesList.filter { item ->
            val bucket = (item.bucketName ?: "").lowercase()
            val path = (item.relativePath ?: "").lowercase()
            val title = item.title.lowercase()
            bucket.startsWith(".") || path.contains("/.") || title.startsWith(".") ||
                    systemHiddenNames.any { it == bucket || path.split('/').any { p -> p == it } }
        }
        "CAMERA" -> imagesList.filter { item ->
            val bucket = (item.bucketName ?: "").lowercase()
            val path = (item.relativePath ?: "").lowercase()
            val title = item.title.lowercase()
            val uriStr = item.uri.toString().lowercase()
            val full = "$path/$bucket/$uriStr/$title"

            val isInDcim = full.contains("dcim") || bucket == "camera" || path.contains("camera") || title.startsWith("img_") || title.startsWith("pxl_")
            if (!isInDcim) return@filter false

            val excluded = listOf("game media", "gif", "snapchat", "instagram", "screenshots", "reddit", "twitter", "whatsapp", "facebook", "telegram", "tiktok", "pinterest", "snap", "insta", "fb")
            val isExcluded = excluded.any { exc -> full.contains(exc) }
            !isExcluded
        }
        "FAVORITES" -> imagesList.filter { favoriteUris.contains(it.uri.toString()) || it.title.lowercase().contains("fav") }
        "TRASH" -> imagesList.filter { trashUris.contains(it.uri.toString()) }
        "SOCIAL" -> imagesList.filter { item ->
            val title = (item.title ?: "").lowercase()
            val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

            val targetFolders = listOf(
                "whatsapp/media", "pictures/whatsapp images", "telegram",
                "pictures/instagram", "download/instagram", "dcim/instagram",
                "pictures/facebook", "pictures/messenger", "movies/tiktok",
                "pictures/tiktok", "dcim/tiktok", "pictures/snapchat",
                "dcim/snapchat", "pictures/pinterest", "pictures/twitter",
                "pictures/x", "pictures/reddit"
            )
            val matchesFolder = targetFolders.any { path.contains(it) }
            val socialPackages = listOf(
                "com.whatsapp", "org.telegram.messenger", "com.instagram.android",
                "com.facebook.katana", "com.zhiliaoapp.musically",
                "com.snapchat.android", "com.twitter.android"
            )
            val matchesAndroidDir = socialPackages.any { pkg ->
                path.contains("/android/data/$pkg") || path.contains("/android/media/$pkg")
            }

            val socialNames = listOf(
                "whatsapp", "telegram", "instagram", "insta",
                "facebook", "fb", "messenger", "tiktok",
                "snapchat", "snap", "pinterest", "twitter", "reddit"
            )
            val matchesTitle = socialNames.any { title.contains(it) }
            matchesFolder || matchesAndroidDir || matchesTitle
        }
        "GIFS" -> imagesList.filter { item ->
            val full = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            full.contains("gif")
        }
        "PNG_SVG" -> imagesList.filter { item ->
            val full = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            full.endsWith(".png") || full.endsWith(".svg") || full.contains("png") || full.contains("svg")
        }
        "EDITED" -> imagesList.filter { item ->
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            val editKeywords = listOf("edited", "snapseed", "lightroom", "picsart", "vsco", "photoshop", "canva", "enhance", "remini")
            editKeywords.any { full.contains(it) }
        }
        "AI_GENERATED" -> imagesList.filter { item ->
            val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

            val aiKeywords = listOf(
                "ai_generated", "midjourney", "dall-e", "stable diffusion",
                "chatgpt", "gemini", "bing image creator", "leonardo",
                "civitai", "flux", "imagen", "generative fill"
            )
            val matchesKeywords = aiKeywords.any { path.contains(it) }

            val aiFolders = listOf(
                "pictures/midjourney",
                "pictures/stablediffusion",
                "pictures/dall-e",
                "pictures/bing image creator",
                "pictures/leonardo",
                "pictures/generated"
            )
            val matchesFolder = aiFolders.any { path.contains(it) }

            matchesKeywords || matchesFolder
        }
        "ANIME" -> imagesList.filter { item ->
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            val animeKeywords = listOf("anime", "manga", "otaku", "crunchyroll", "goku", "naruto", "luffy")
            animeKeywords.any { full.contains(it) }
        }
        "WALLPAPERS" -> imagesList.filter { item ->
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            val wallpaperKeywords = listOf("wallpaper", "wallpapers", "background", "lockscreen", "zedge", "unsplash")
            wallpaperKeywords.any { full.contains(it) }
        }
        "SCREENSHOTS" -> imagesList.filter { item ->
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            full.contains("screenshot") || full.contains("screenshots")
        }
        "NOTES" -> {
            val notesKeywordsSet = setOf(
                "note", "notes", "document", "documents", "doc", "docs", "scan", "scanner", "receipt", "whiteboard",
                "pdf", "slides", "ppt", "pptx", "sheet", "page", "paper", "quiz", "test", "exam", "syllabus", "lecture",
                "assignment", "homework", "formula", "ch1", "chapter", "unit", "topic", "memo", "class", "course", "lab",
                "revision", "summary", "mindmap", "diagram", "handwritten", "notion", "obsidian", "evernote", "onenote",
                "camscanner", "goodnotes", "notability",
                "kotlin", "java", "python", "javascript", "typescript", "rust", "golang", "swift",
                "objc", "php", "ruby", "scala", "html", "css", "sql", "bash", "shell", "assembly",
                "programming", "coding", "developer", "development", "software", "flutter",
                "git", "github", "docker", "kubernetes", "linux", "ubuntu",
                "terminal", "database", "api", "backend", "frontend", "algorithm", "datastructure", "syntax",
                "architecture", "bug", "debug", "compile", "ide", "vscode", "androidstudio", "intellij",
                "gk", "discussion", "nutrients", "isro", "nasa", "communication", "rules", "science", "physics",
                "chemistry", "biology", "math", "mathematics", "geography", "economics", "civics", "study",
                "education", "tutorial"
            )

            imagesList.filter { item ->
                val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "")).lowercase()

                if (path.contains("whatsapp")) {
                    return@filter false
                }

                val full = (path + "/" + item.title).lowercase()
                if (full.contains("c++") || full.contains("c#")) {
                    return@filter true
                }

                val words = full.split(Regex("[/_\\s\\.\\-\\(\\)\\[\\]]+"))
                words.any { it in notesKeywordsSet }
            }
        }
        else -> imagesList
    }

    if (activeFilterTab == "HIDDEN") return result

    return result.filter { item ->
        val bucket = (item.bucketName ?: "").lowercase()
        val path = (item.relativePath ?: "").lowercase()
        !systemHiddenNames.any { it == bucket || path.split('/').any { p -> p == it } }
    }
}
