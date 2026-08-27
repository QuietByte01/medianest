import re

with open("app/src/main/java/com/medianest/player/ExoPlayerManager.kt", "r") as f:
    content = f.read()

pattern = r"""val isStuckAtStart = isMedia3 && state\.isPlaying && state\.currentPositionMs == 0L
\s*val isBufferingForever = isMedia3 && !state\.isPlaying && media3Engine\?\.player\?\.playWhenReady == true
\s*
\s*if \(isStuckAtStart \|\| isBufferingForever\) \{
\s*val reason = if \(isStuckAtStart\) \"Black screen stuck at 00:00\" else \"Buffering deadlock\""""

replacement = r"""val isBufferingForever = isMedia3 && !state.isPlaying && media3Engine?.player?.playWhenReady == true
            
            if (isBufferingForever) {
                val reason = "Buffering deadlock\""""

new_content = re.sub(pattern, replacement, content)

with open("app/src/main/java/com/medianest/player/ExoPlayerManager.kt", "w") as f:
    f.write(new_content)
