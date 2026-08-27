import re

with open("app/src/main/java/com/medianest/player/Media3PlaybackEngine.kt", "r") as f:
    content = f.read()

# We want to replace the whole prepare method
pattern = r"    override fun prepare\(uri: Uri, playWhenReady: Boolean\) \{.*?(?=    override fun play\(\))"
replacement = """    override fun prepare(uri: Uri, playWhenReady: Boolean) {
        val currentUri = player.currentMediaItem?.localConfiguration?.uri
        if (currentUri != uri) {
            val mediaItem = Media3Item.fromUri(uri)
            player.setMediaItem(mediaItem, true)
        }
        player.prepare()
        player.playWhenReady = playWhenReady
        Logger.i("Media3PlaybackEngine", "Player prepared for URI: $uri, playWhenReady=$playWhenReady")
    }

"""
new_content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open("app/src/main/java/com/medianest/player/Media3PlaybackEngine.kt", "w") as f:
    f.write(new_content)
