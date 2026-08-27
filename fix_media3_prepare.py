import re

with open("app/src/main/java/com/medianest/player/Media3PlaybackEngine.kt", "r") as f:
    content = f.read()

pattern = r"    override fun prepare\(uri: Uri, playWhenReady: Boolean\) \{.*?(?=    override fun play\(\))"

replacement = """    override fun prepare(uri: Uri, playWhenReady: Boolean) {
        val currentItem = player.currentMediaItem
        val mediaItem = if (currentItem?.localConfiguration?.uri == uri) {
            currentItem // Reuse the item which might have subtitles injected
        } else {
            Media3Item.fromUri(uri)
        }
        player.setMediaItem(mediaItem, true)
        player.prepare()
        player.playWhenReady = playWhenReady
        Logger.i("Media3PlaybackEngine", "Player prepared for URI: $uri, playWhenReady=$playWhenReady")
    }

"""

new_content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open("app/src/main/java/com/medianest/player/Media3PlaybackEngine.kt", "w") as f:
    f.write(new_content)
