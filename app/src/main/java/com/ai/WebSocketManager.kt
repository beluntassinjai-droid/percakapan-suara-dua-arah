package com.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import okhttp3.*
import okio.ByteString
import java.util.Base64

class WebSocketManager(private val apiKey: String, private val onMessageReceived: (String) -> Unit) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private var audioTrack: AudioTrack? = null

    init {
        // Konfigurasi pemutar AudioTrack untuk mendengarkan suara dari AI
        val sampleRate = 24000 // Standar audio output Gemini Live
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

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
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onMessageReceived("Terhubung ke Gemini Live!")
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageReceived(text)
                // Jika server mengirim balasan dalam bentuk teks JSON yang berisi audio Base64,
                // kita bisa ekstrak dan tulis ke audioTrack di sini jika diperlukan.
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Menerima data biner audio langsung dari server dan memutarnya ke speaker
                val audioBytes = bytes.toByteArray()
                audioTrack?.write(audioBytes, 0, audioBytes.size)
                onMessageReceived("Memutar suara dari AI...")
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