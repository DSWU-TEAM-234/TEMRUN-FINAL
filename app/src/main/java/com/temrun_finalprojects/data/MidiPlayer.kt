package com.temrun_finalprojects.data
import android.content.Context
import android.media.MediaPlayer

class MidiPlayer(private val context: Context, private val resId: Int) {
    private var mediaPlayer: MediaPlayer? = null

    fun playLoop() {
        mediaPlayer = MediaPlayer.create(context, resId).apply {
            isLooping = true
            start()
        }
    }

    fun setVolume(volume: Float) {
        // volume: 0.0f ~ 1.0f
        mediaPlayer?.setVolume(volume, volume)
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
