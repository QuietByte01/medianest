sed -i '' -e 's/it.player.playWhenReady = false/it.player.playWhenReady = false\n                    it.stop()/g' app/src/main/java/com/medianest/player/ExoPlayerManager.kt
sed -i '' -e 's/delay(10000) \/\/ 10s timeout/delay(25000) \/\/ 25s timeout/g' app/src/main/java/com/medianest/player/ExoPlayerManager.kt
