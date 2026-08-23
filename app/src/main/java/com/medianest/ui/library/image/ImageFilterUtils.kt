package com.medianest.ui.library.image

import com.medianest.data.model.MediaItem

fun filterImageList(
    imagesList: List<MediaItem>,
    activeFilterTab: String,
    favoriteUris: Set<String> = emptySet()
): List<MediaItem> {
    val result = when (activeFilterTab) {
        "HIDDEN" -> imagesList.filter { it.isHidden && !it.isExcluded && !com.medianest.util.FolderHiddenUtils.isItemHidden(it) }
        "EXCLUDED" -> imagesList.filter { it.isExcluded || com.medianest.util.FolderHiddenUtils.isItemHidden(it) }
        "ALL" -> imagesList
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
            val isWallpaper = full.contains("wallpaper") || full.contains("wallpapers") || full.contains("wallhaven") || full.contains("zedge") || full.contains("backdrops") || full.contains("live wallpaper")
            !isWallpaper && editKeywords.any { full.contains(it) }
        }
        "AI_GENERATED" -> imagesList.filter { item ->
            val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()

            val aiKeywords = listOf(
                "ai_generated", "midjourney", "dall-e", "stable diffusion",
                "chatgpt", "gemini", "bing image creator", "leonardo",
                "civitai", "flux", "imagen", "generative fill",
                "openai", "anthropic", "claude", "perplexity", "firefly", 
                "stablediffusion", "dreamstudio", "nightcafe", "wombo", 
                "craiyon", "artbreeder", "runwayml", "pika", "sora", "deepai"
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
            val animeKeywords = listOf(
                "anime", "manga", "otaku", "crunchyroll", "goku", "naruto", "Itachi", "shakura", "hinata", "kakashi", "pain", "luffy",
                "waifu", "husbando", "kawaii", "one piece", "bleach", "dragon ball", "aot", "suzume", "asta", "rustage", "cartoon",
                "attack on titan", "demon slayer", "jujutsu kaisen", "my hero academia", 
                "death note", "evangelion", "ghibli", "cosplay", "pixiv"
            )
            animeKeywords.any { full.contains(it) }
        }
        "COOKING" -> imagesList.filter { item ->
            val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "")).lowercase()
            val title = item.title.lowercase()
            
            // Exclude wallpapers directories and keywords
            val excludedFolders = setOf("wallpaper", "wallpapers", "wallhaven", "zedge", "background", "backgrounds", "lockscreen")
            val pathSegments = path.split(Regex("[/_\\s\\.\\-]+")).filter { it.isNotBlank() }
            val isExcluded = pathSegments.any { it in excludedFolders } ||
                    path.contains("wallpaper") || path.contains("wallpapers") ||
                    path.contains("wallhaven") || path.contains("zedge") ||
                    title.contains("wallpaper") || title.contains("wallpapers")

            if (isExcluded) return@filter false

            val cookingFolders = setOf("cooking", "veg", "non-veg", "nonveg", "recipe", "recipes", "food", "kitchen", "bakery")
            val folderMatch = pathSegments.any { it in cookingFolders }
            
            val cookingKeywords = listOf(
                "recipe", "food", "kitchen", "cooking", "bake", "cake", "meal", 
                "dinner", "breakfast", "lunch", "chef", "cook", "dish", 
                "ingredients", "menu", "restaurant", "bakery", "delicious", 
                "yummy", "cuisine", "veg", "non-veg", "nonveg", "meat", "vegetable", "curry"
            )
            val titleMatch = cookingKeywords.any { title.contains(it) }
            
            folderMatch || titleMatch
        }
        "GARDENING" -> imagesList.filter { item ->
            val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "")).lowercase()
            val title = item.title.lowercase()

            // 1. Exclude Documents, Notes, Screenshots, Quick Share, WhatsApp, Telegram directories
            val excludedFolders = setOf(
                "screenshot", "screenshots", "quick share", "quickshare", "quick", "share",
                "whatsapp", "telegram", "documents", "document", "notes", "note", "doc", "docs"
            )
            val pathSegments = path.split(Regex("[/_\\s\\.\\-]+")).filter { it.isNotBlank() }

            val isExcluded = pathSegments.any { it in excludedFolders } ||
                    path.contains("document") || path.contains("documents") ||
                    path.contains("note") || path.contains("notes") ||
                    path.contains("quick share") || path.contains("screenshot")

            if (isExcluded) return@filter false

            // 2. Gardening matching logic
            val gardeningFolders = setOf("gardening", "garden", "flowers", "flower", "nature", "farm", "park", "botany", "botanical", "plants")
            val folderMatch = pathSegments.any { it in gardeningFolders }

            val gardeningKeywords = listOf(
                "garden", "gardening", "plant", "plants", "flower", "flowers", "leaf", "tree", "nature",
                "seeds", "soil", "farm", "forest", "bloom", "blossom", "botanical", "botany", "organic",
                "agriculture", "park", "yard", "grass", "environment", "landscape"
            )
            val titleMatch = gardeningKeywords.any { title.contains(it) }

            folderMatch || titleMatch
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
        "EXCLUDED" -> imagesList.filter { it.isExcluded }
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

    return result
}
