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
                onMessageReceived("Terhubung ke Server. Mengirim setup...")
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageReceived("Pesan masuk: ${text.take(40)}")
                try {
                    val json = JSONObject(text)
                    if (json.has("error")) {
                        val errorObj = json.getJSONObject("error")
                        val message = errorObj.optString("message", "Error")
                        onMessageReceived("API Error: $message")
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
                                    val inlineData = part.getJSONObject("inlineData")
                                    val base64Data = inlineData.getString("data")
                                    val audioBytes = Base64.getDecoder().decode(base64Data)
                                    audioTrack?.write(audioBytes, 0, audioBytes.size)
                                    onMessageReceived("Memutar suara AI...")
                                    return
                                }
                            }
                        }
                    } else if (json.has("setupComplete")) {
                        onMessageReceived("Siap! Silakan berbicara.")
                    }
                } catch (e: Exception) {
                    onMessageReceived("Error Parse: ${e.localizedMessage}")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val audioBytes = bytes.toByteArray()
                audioTrack?.write(audioBytes, 0, audioBytes.size)
                onMessageReceived("Memutar audio biner...")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onMessageReceived("Koneksi ditutup: $reason (Kode: $code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code ?: 0
                val message = response?.message ?: t.localizedMessage ?: "Tidak diketahui"
                onMessageReceived("Gagal Terhubung! HTTP $code: $message")
            }
        })
    }

    private fun sendSetupMessage() {
        val setupJson = """
        {
            "setup": {
                "model": "$selectedModel",
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