package com.personal.ircclient.core.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object RadioPlayer {
    private var exoPlayer: ExoPlayer? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl = _currentUrl.asStateFlow()

    fun play(context: Context, url: String) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
        
        if (_currentUrl.value == url && _isPlaying.value) {
            stop()
            return
        }

        _currentUrl.value = url
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
        _isPlaying.value = true
    }

    fun stop() {
        exoPlayer?.stop()
        _isPlaying.value = false
        _currentUrl.value = null
    }
    
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
