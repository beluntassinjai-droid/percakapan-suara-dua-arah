package com.ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.isActive
import java.util.Base64

class WebSocketManager(private val url: String) {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    private var session: DefaultClientWebSocketSession? = null

    suspend fun connect(onMessageReceived: (String) -> Unit) {
        try {
            session = client.webSocketSession(urlString = url)
            session?.let { s ->
                for (frame in s.incoming) {
                    if (frame is Frame.Text) {
                        onMessageReceived(frame.readText())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun sendAudioChunk(audioBytes: ByteArray) {
        session?.let { s ->
            if (s.isActive) {
                // Mengubah byte audio ke Base64 agar mudah dikirim melalui teks WebSocket
                val base64Audio = Base64.getEncoder().encodeToString(audioBytes)
                s.send(Frame.Text(base64Audio))
            }
        }
    }

    suspend fun disconnect() {
        session?.close()
        client.close()
    }
}