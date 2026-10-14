package com.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.Base64

class WebSocketManager(private val apiKey: String, private val onMessageReceived: (String) -> Unit) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private var audioTrack: AudioTrack? = null

    init {
        // Inisialisasi AudioTrack untuk output suara 24kHz PCM mono dari Gemini Live
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
        // Menggunakan endpoint v1beta dan model gemini-3.8-live
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onMessageReceived("Terhubung ke Gemini Live!")
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    
                    // Memeriksa apakah server mengirimkan data audio balasan (ServerContent)
                    if (json.has("serverContent")) {
                        val serverContent = json.getJSONObject("serverContent")
                        if (serverContent.has("modelTurn")) {
                            val modelTurn = serverContent.getJSONObject("modelTurn")
                            val parts = modelTurn.getJSONArray("parts")
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (part.has("inlineData")) {
                                    val inlineData = part.getJSONObject("inlineData")
                                    val mimeType = inlineData.optString("mimeType", "")
                                    if (mimeType.startsWith("audio/")) {
                                        val base64Data = inlineData.getString("data")
                                        val audioBytes = Base64.getDecoder().decode(base64Data)
                                        
                                        // Memutar data audio ke speaker
                                        audioTrack?.write(audioBytes, 0, audioBytes.size)
                                        onMessageReceived("Memutar suara dari Gemini...")
                                        return
                                    }
                                }
                            }
                        }
                    }
                    
                    onMessageReceived("Respon diterima dari server")
                } catch (e: Exception) {
                    Log.e("WebSocket", "Gagal memproses pesan: ${e.localizedMessage}")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val audioBytes = bytes.toByteArray()
                audioTrack?.write(audioBytes, 0, audioBytes.size)
                onMessageReceived("Memutar data biner...")
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
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        webSocket?.close(1000, "Selesai")
        webSocket = null
    }
}