import re

with open("app/src/main/java/com/medianest/player/ExoPlayerManager.kt", "r") as f:
    content = f.read()

pattern = r"(ff\.release\(\)\n\s*Logger\.d\(\"ExoPlayerManager\", \"Engines released successfully in background\"\))"
replacement = r"\1\n                synchronized(brokenEngines) { brokenEngines.forEach { try { it.release() } catch(e: Exception) {} }; brokenEngines.clear() }"

new_content = re.sub(pattern, replacement, content)

with open("app/src/main/java/com/medianest/player/ExoPlayerManager.kt", "w") as f:
    f.write(new_content)
