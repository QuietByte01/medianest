sed -i '' -e 's/it.player.volume = 0f/try { it.player.volume = 0f } catch(e: Exception) {}/g' app/src/main/java/com/medianest/player/ExoPlayerManager.kt
sed -i '' -e 's/it.player.playWhenReady = false/try { it.player.playWhenReady = false } catch(e: Exception) {}/g' app/src/main/java/com/medianest/player/ExoPlayerManager.kt
sed -i '' -e 's/it.stop()/try { it.stop() } catch(e: Exception) {}/g' app/src/main/java/com/medianest/player/ExoPlayerManager.kt
