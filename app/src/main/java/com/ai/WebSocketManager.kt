package com.ai

import okhttp3.*
import okio.ByteString
import java.util.Base64

class WebSocketManager(private val url: String, private val onMessageReceived: (String) -> Unit) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connect() {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Berhasil terhubung ke server
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageReceived(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessageReceived(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onMessageReceived("Koneksi gagal: ${t.localizedMessage}")
            }
        })
    }

    fun sendAudioChunk(audioBytes: ByteArray) {
        // Mengubah ByteArray audio PCM ke format teks Base64 yang aman untuk WebSocket
        val base64Audio = Base64.getEncoder().encodeToString(audioBytes)
        webSocket?.send(base64Audio)
    }

    fun disconnect() {
        webSocket?.close(1000, "Selesai")
        webSocket = null
    }
}