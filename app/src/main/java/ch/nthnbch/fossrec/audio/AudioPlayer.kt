package ch.nthnbch.fossrec.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.core.net.toUri
import java.io.File

class AudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    
    var onCompletionListener: (() -> Unit)? = null

    fun playFile(file: File) {
        // Stop current if playing
        stop()
        
        MediaPlayer.create(context, file.toUri())?.apply {
            player = this
            setOnCompletionListener {
                onCompletionListener?.invoke()
            }
            start()
        }
    }

    fun stop() {
        player?.stop()
        player?.release()
        player = null
    }

    fun pause() {
        player?.pause()
    }
    
    fun resume() {
        player?.start()
    }

    fun seekTo(positionMs: Int) {
        player?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Int = player?.currentPosition ?: 0

    fun getDuration(): Int = player?.duration ?: 0
}
