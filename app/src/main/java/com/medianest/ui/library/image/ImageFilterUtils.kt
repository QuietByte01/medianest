package com.medianest.ui.library.image

import com.medianest.MediaNestApp
import com.medianest.data.model.MediaItem
import com.medianest.util.ImageExclusionManager
import com.medianest.util.ImageTagManager

fun filterImageList(
    imagesList: List<MediaItem>,
    activeFilterTab: String,
    favoriteUris: Set<String> = emptySet()
): List<MediaItem> {
    val context = MediaNestApp.instance

    // Pre-filter items explicitly excluded by the user from this filter tab
    val candidateList = if (activeFilterTab in listOf("ALL", "FOLDERS", "HIDDEN", "EXCLUDED")) {
        imagesList
    } else {
        imagesList.filter { item ->
            !ImageExclusionManager.isExcludedFromFilter(context, item.uri.toString(), activeFilterTab)
        }
    }

    val result = when (activeFilterTab) {
        "HIDDEN" -> candidateList.filter { com.medianest.util.FolderHiddenUtils.isItemHidden(it) && !com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
        "EXCLUDED" -> candidateList.filter { com.medianest.util.FolderHiddenUtils.isItemExcluded(it) }
        "ALL" -> candidateList
        "CAMERA" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "CAMERA")) return@filter true

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
        "FAVORITES" -> candidateList.filter { favoriteUris.contains(it.uri.toString()) }
        "SOCIAL" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "SOCIAL")) return@filter true

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
        "GIFS" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "GIFS")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            full.contains("gif")
        }
        "PNG_SVG" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "PNG_SVG")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            full.endsWith(".png") || full.endsWith(".svg") || full.contains("png") || full.contains("svg")
        }
        "EDITED" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "EDITED")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            val editKeywords = listOf("edited", "snapseed", "lightroom", "picsart", "vsco", "photoshop", "canva", "enhance", "remini")
            val isWallpaper = full.contains("wallpaper") || full.contains("wallpapers") || full.contains("wallhaven") || full.contains("zedge") || full.contains("backdrops") || full.contains("live wallpaper")
            !isWallpaper && editKeywords.any { full.contains(it) }
        }
        "AI_GENERATED" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "AI_GENERATED")) return@filter true
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
        "ANIME" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "ANIME")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            val animeKeywords = listOf(
                "anime", "manga", "otaku", "crunchyroll", "goku", "naruto", "Itachi", "shakura", "hinata", "kakashi", "pain", "luffy",
                "waifu", "husbando", "kawaii", "one piece", "bleach", "dragon ball", "aot", "suzume", "asta", "rustage", "cartoon",
                "attack on titan", "demon slayer", "jujutsu kaisen", "my hero academia", 
                "death note", "evangelion", "ghibli", "cosplay", "pixiv"
            )
            animeKeywords.any { full.contains(it) }
        }
        "COOKING" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "COOKING")) return@filter true
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
        "TRAVEL" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "TRAVEL")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            val travelKeywords = listOf(
                "travel", "trip", "vacation", "tour", "beach", "mountain", "flight", "hotel", 
                "resort", "passport", "airport", "holiday", "location", "geocoded", "tourist", 
                "country", "city", "place", "sightseeing", "nature"
            )
            travelKeywords.any { full.contains(it) }
        }
        "GARDENING" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "GARDENING")) return@filter true
            val path = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "")).lowercase()
            val title = item.title.lowercase()

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
        "PETS" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "PETS")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title).lowercase()
            val keywords = listOf("pet", "pets", "dog", "dogs", "cat", "cats", "puppy", "kitten", "animal", "animals")
            keywords.any { full.contains(it) }
        }
        "FAMILY" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "FAMILY")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title).lowercase()
            val keywords = listOf("family", "people", "friend", "friends", "wedding", "party", "birthday", "baby", "portrait")
            keywords.any { full.contains(it) }
        }
        "DOCUMENTS" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "DOCUMENTS")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title).lowercase()
            val keywords = listOf("document", "doc", "docs", "receipt", "bill", "invoice", "card", "passport", "form", "ticket", "scan")
            keywords.any { full.contains(it) }
        }
        "MEMES" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "MEMES")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title).lowercase()
            val keywords = listOf("meme", "memes", "funny", "humor", "joke", "dank")
            keywords.any { full.contains(it) }
        }
        "WALLPAPERS" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "WALLPAPERS")) return@filter true
            val full = ((item.relativePath ?: "") + "/" + (item.bucketName ?: "") + "/" + item.title + "/" + item.uri.toString()).lowercase()
            val wallpaperKeywords = listOf("wallpaper", "wallpapers", "background", "lockscreen", "zedge", "unsplash")
            wallpaperKeywords.any { full.contains(it) }
        }
        "SCREENSHOTS" -> candidateList.filter { item ->
            if (ImageTagManager.hasTag(context, item.uri.toString(), "SCREENSHOTS")) return@filter true
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

            candidateList.filter { item ->
                if (ImageTagManager.hasTag(context, item.uri.toString(), "NOTES")) return@filter true

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
        else -> candidateList
    }

    return result
}
