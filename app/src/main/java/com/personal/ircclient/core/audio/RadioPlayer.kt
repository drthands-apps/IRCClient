package com.personal.ircclient.core.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RadioPlayer {
    private var exoPlayer: ExoPlayer? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl

    fun play(context: Context, url: String) {
        if (_currentUrl.value == url && _isPlaying.value) {
            stop()
            return
        }

        stop()
        
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            // Added support for potentially problematic streams by ensuring basic URI parsing
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _isPlaying.value = false
                    _currentUrl.value = null
                    android.util.Log.e("RadioPlayer", "Error playing stream: ${error.message}", error)
                }
            })
        }
        
        _currentUrl.value = url
        _isPlaying.value = true
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        _isPlaying.value = false
        _currentUrl.value = null
    }
}
