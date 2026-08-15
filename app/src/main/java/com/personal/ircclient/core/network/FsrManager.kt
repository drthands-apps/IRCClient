package com.personal.ircclient.core.network

import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fenix Stream Relay (FSR) Manager
 * Handles P2P-like file streaming through a RAM-only relay server.
 */
class FsrManager(private val serverUrl: String) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) 
        .build()
    
    private var webSocket: WebSocket? = null
    
    interface FsrCallback {
        fun onProgress(percent: Float)
        fun onComplete(file: File?)
        fun onError(error: String)
        fun onReady()
    }

    fun startTransfer(sessionId: String, file: File, role: String, callback: FsrCallback) {
        val request = Request.Builder().url(serverUrl).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val auth = JSONObject().apply {
                    put("type", "IDENTIFY")
                    put("sessionId", sessionId)
                    put("role", role)
                }
                webSocket.send(auth.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val data = JSONObject(text)
                when (data.getString("type")) {
                    "READY" -> {
                        if (role == "SENDER") {
                            sendStreaming(file, sessionId, callback)
                        } else {
                            callback.onReady()
                        }
                    }
                    "DATA" -> {
                        val payload = data.getString("payload")
                        val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
                        val tempFile = File(file.parent, "fsr_received_${System.currentTimeMillis()}")
                        tempFile.writeBytes(bytes)
                        callback.onComplete(tempFile)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                callback.onError(t.message ?: "Connection failed")
            }
        })
    }

    private fun sendStreaming(file: File, sessionId: String, callback: FsrCallback) {
        Thread {
            try {
                val bytes = file.readBytes()
                val json = JSONObject().apply {
                    put("type", "STREAM")
                    put("sessionId", sessionId)
                    put("payload", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                }
                webSocket?.send(json.toString())
                callback.onComplete(file)
            } catch (e: Exception) {
                callback.onError(e.message ?: "Streaming error")
            }
        }.start()
    }

    fun stop() {
        webSocket?.close(1000, "Transfer ended")
    }
}
