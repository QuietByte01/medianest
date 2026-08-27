import re

with open("app/src/main/java/com/medianest/player/ExoPlayerManager.kt", "r") as f:
    content = f.read()

pattern = r"""if \(activeEngine == media3Engine\) \{
\s*Logger\.d\(\"ExoPlayerManager\", \"Waiting for initial surface attachment\.\.\.\"\)
\s*var surfaceWaitCount = 0
\s*while \(media3Engine\?\.isSurfaceReady\?\.value == false && surfaceWaitCount < 15\) \{
\s*kotlinx\.coroutines\.delay\(300\)
\s*surfaceWaitCount\+\+
\s*\}
\s*\}
\s*
\s*(Logger\.d\(\"ExoPlayerManager\", \"Preparing engine for URI: \$\{currentTarget\.uri\}\"\))"""

replacement = r"\1"

new_content = re.sub(pattern, replacement, content)

with open("app/src/main/java/com/medianest/player/ExoPlayerManager.kt", "w") as f:
    f.write(new_content)
