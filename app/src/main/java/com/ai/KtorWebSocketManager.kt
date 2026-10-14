package com.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.Base64

class KtorWebSocketManager(
    private val apiKey: String,
    private val selectedModel: String,
    private val selectedEndpoint: String,
    private val selectedVoice: String,
    private val onMessageReceived: (String) -> Unit
) {
    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 20_000
        }
    }
    
    private var scope: CoroutineScope? = null
    private var audioTrack: AudioTrack? = null
    private var session: DefaultClientWebSocketSession? = null

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
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope?.launch {
            try {
                onMessageReceived("Menghubungkan ke Gemini Live...")
                
                val host = "generativelanguage.googleapis.com"
                val path = "/ws/google.ai.generativelanguage.$selectedEndpoint.GenerativeService.BidiGenerateContent"
                
                client.wss(
                    host = host,
                    path = "$path?key=$apiKey"
                ) {
                    session = this
                    onMessageReceived("Terhubung! Mengirim setup...")

                    // Menggunakan model, endpoint, dan pilihan karakter suara dinamis dari dropdown
                    val setupMessage = """
                        {
                            "setup": {
                                "model": "models/$selectedModel",
                                "generationConfig": {
                                    "responseModalities": ["AUDIO"]
                                },
                                "voiceConfig": {
                                    "prebuiltVoiceConfig": {
                                        "voiceName": "$selectedVoice"
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    send(Frame.Text(setupMessage))
                    
                    onMessageReceived("Menunggu respons server suara...")

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val json = JSONObject(text)
                                
                                if (json.has("error")) {
                                    val errObj = json.getJSONObject("error")
                                    val errMsg = errObj.optString("message", "Unknown error")
                                    onMessageReceived("Server Error: $errMsg")
                                    continue
                                }
                                
                                if (json.has("serverContent")) {
                                    val serverContent = json.getJSONObject("serverContent")
                                    
                                    if (serverContent.has("modelTurn")) {
                                        val modelTurn = serverContent.getJSONObject("modelTurn")
                                        val parts = modelTurn.getJSONArray("parts")
                                        for (i in 0 until parts.length()) {
                                            val part = parts.getJSONObject(i)
                                            if (part.has("text")) {
                                                onMessageReceived("AI: ${part.getString("text")}")
                                            }
                                            if (part.has("inlineData")) {
                                                val inlineData = part.getJSONObject("inlineData")
                                                val base64Data = inlineData.getString("data")
                                                val audioBytes = Base64.getDecoder().decode(base64Data)
                                                audioTrack?.write(audioBytes, 0, audioBytes.size)
                                                onMessageReceived("Memutar suara AI...")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Abaikan parsing jika format lain
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                onMessageReceived("Koneksi gagal: ${e.localizedMessage}")
            }
        }
    }

    fun sendAudioChunk(audioBytes: ByteArray) {
        scope?.launch {
            try {
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
                session?.send(Frame.Text(audioMessage))
            } catch (e: Exception) {
                // Abaikan
            }
        }
    }

    fun disconnect() {
        scope?.cancel()
        scope = null
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        runBlocking {
            session?.close()
        }
        session = null
    }
}