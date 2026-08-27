import androidx.media3.exoplayer.ExoPlayer
import android.os.Looper
fun test(builder: ExoPlayer.Builder) {
    builder.setApplicationLooper(Looper.getMainLooper())
}
