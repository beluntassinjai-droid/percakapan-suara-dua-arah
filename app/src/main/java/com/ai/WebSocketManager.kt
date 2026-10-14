package com.ai

import okhttp3.*
import okio.ByteString
import java.util.Base64

class WebSocketManager(private val apiKey: String, private val onMessageReceived: (String) -> Unit) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connect() {
        // Menggunakan endpoint Gemini Live BidiGenerateContent dengan model yang ditentukan
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onMessageReceived("Terhubung ke Gemini Live!")
                // Kirim pesan setup awal untuk konfigurasi model (opsional/standar)
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageReceived(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessageReceived("Menerima data biner dari server")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onMessageReceived("Koneksi gagal: ${t.localizedMessage}")
            }
        })
    }

    private fun sendSetupMessage() {
        val setupJson = """
        {
            "setup": {
                "model": "models/gemini-3.8-live",
                "generationConfig": {
                    "responseModalities": ["AUDIO"]
                }
            }
        }
        """.trimIndent()
        webSocket?.send(setupJson)
    }

    fun sendAudioChunk(audioBytes: ByteArray) {
        val base64Audio = Base64.getEncoder().encodeToString(audioBytes)
        // Membungkus chunk audio PCM ke dalam format real-time input Gemini API
        val audioJson = """
        {
            "realtimeInput": {
                "mediaChunks": [
                    {
                        "mimeType": "audio/pcm",
                        "data": "$base64Audio"
                    }
                ]
            }
        }
        """.trimIndent()
        webSocket?.send(audioJson)
    }

    fun disconnect() {
        webSocket?.close(1000, "Selesai")
        webSocket = null
    }
}