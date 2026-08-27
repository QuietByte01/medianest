import re

with open("app/src/main/java/com/medianest/player/ExoPlayerManager.kt", "r") as f:
    content = f.read()

pattern = r"(Logger\.d\(\"ExoPlayerManager\", \"Preparing engine for URI: \$\{currentTarget\.uri\}\"\)\n\s*activeEngine\?\.prepare\(currentTarget\.uri, true\))"

replacement = r"""if (activeEngine == media3Engine) {
                        Logger.d("ExoPlayerManager", "Waiting for initial surface attachment...")
                        var surfaceWaitCount = 0
                        while (media3Engine?.isSurfaceReady?.value == false && surfaceWaitCount < 15) {
                            kotlinx.coroutines.delay(300)
                            surfaceWaitCount++
                        }
                    }
                    
                    \1"""

new_content = re.sub(pattern, replacement, content)

with open("app/src/main/java/com/medianest/player/ExoPlayerManager.kt", "w") as f:
    f.write(new_content)
