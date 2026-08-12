package com.personal.ircclient.core.audio

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

    fun startRecording() {
        try {
            audioFile = File(context.cacheDir, "temp_recording.m4a")
            
            recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(FileOutputStream(audioFile).fd)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            recorder = null
        }
    }

    fun stopRecording(): File? {
        try {
            recorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorder?.release()
            recorder = null
        }
        return audioFile
    }
}
