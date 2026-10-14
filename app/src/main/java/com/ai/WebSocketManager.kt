package com.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.Base64

class WebSocketManager(
    private val apiKey: String,
    private val selectedModel: String,
    private val selectedEndpoint: String,
    private val onMessageReceived: (String) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private var audioTrack: AudioTrack? = null

    init {
        val sampleRate = 24000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(audioFormat)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
    }

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.$selectedEndpoint.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onMessageReceived("Terhubung! Menginisialisasi model...")
                
                // Format setup standar untuk model versi 3+
                val setupMessage = """
                {
                    "setup": {
                        "model": "$selectedModel"
                    }
                }
                """.trimIndent()
                
                webSocket.send(setupMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("error")) {
                        val err = json.getJSONObject("error").optString("message", "Terjadi kesalahan")
                        onMessageReceived("Error: $err")
                        return
                    }
                    
                    if (json.has("serverContent")) {
                        val serverContent = json.getJSONObject("serverContent")
                        if (serverContent.has("modelTurn")) {
                            val modelTurn = serverContent.getJSONObject("modelTurn")
                            val parts = modelTurn.getJSONArray("parts")
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (part.has("inlineData")) {
                                    val base64Data = part.getJSONObject("inlineData").getString("data")
                                    val audioBytes = Base64.getDecoder().decode(base64Data)
                                    audioTrack?.write(audioBytes, 0, audioBytes.size)
                                    onMessageReceived("Gemini sedang berbicara...")
                                    return
                                }
                            }
                        }
                    } else {
                        onMessageReceived("Siap! Silakan berbicara.")
                    }
                } catch (e: Exception) {
                    onMessageReceived("Siap! Silakan berbicara.")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val audioBytes = bytes.toByteArray()
                audioTrack?.write(audioBytes, 0, audioBytes.size)
                onMessageReceived("Memutar audio...")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onMessageReceived("Koneksi ditutup: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onMessageReceived("Koneksi gagal: ${t.localizedMessage}")
            }
        })
    }

    fun sendAudioChunk(audioBytes: ByteArray) {
        val base64Audio = Base64.getEncoder().encodeToString(audioBytes)
        val audioMessage = """
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
        webSocket?.send(audioMessage)
    }

    fun disconnect() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        webSocket?.close(1000, "Selesai")
        webSocket = null
    }
}