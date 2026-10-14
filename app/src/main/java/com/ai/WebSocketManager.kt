package com.ai

import okhttp3.*
import okio.ByteString

class WebSocketManager(private val url: String, private val onMessageReceived: (String) -> Unit) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connect() {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageReceived(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessageReceived(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
            }
        })
    }

    fun sendAudioChunk(audioBytes: ByteArray) {
        val byteString = ByteString.of(*audioBytes)
        webSocket?.send(byteString)
    }

    fun disconnect() {
        webSocket?.close(1000, "Selesai")
        webSocket = null
    }
}