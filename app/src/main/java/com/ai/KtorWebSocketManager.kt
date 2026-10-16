package com.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.json.JSONObject

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
    private var lastImageSendTime: Long = 0

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

                    val setupMessage = """
                        {
                            "setup": {
                                "model": "models/$selectedModel",
                                "generationConfig": {
                                    "responseModalities": ["AUDIO"]
                                },
                                "speechConfig": {
                                    "voiceConfig": {
                                        "prebuiltVoiceConfig": {
                                            "voiceName": "$selectedVoice"
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    send(Frame.Text(setupMessage))
                    
                    onMessageReceived("Siap! Silakan bicara...")

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
                                                val audioBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                                                audioTrack?.write(audioBytes, 0, audioBytes.size)
                                                onMessageReceived("Memutar suara AI...")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                onMessageReceived("Parse Error: ${e.localizedMessage}")
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
                if (session == null || !session!!.isActive) return@launch
                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                // Format resmi Gemini Live API untuk realtime input audio
                val audioMessage = """
                    {
                        "realtimeInput": {
                            "audio": {
                                "data": "$base64Audio",
                                "mimeType": "audio/pcm;rate=24000"
                            }
                        }
                    }
                """.trimIndent()
                session?.send(Frame.Text(audioMessage))
            } catch (e: Exception) {
                if (e.localizedMessage?.contains("cancelled") != true) {
                    onMessageReceived("Gagal kirim audio: ${e.localizedMessage}")
                }
            }
        }
    }

    fun sendImageChunk(imageBytes: ByteArray) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastImageSendTime < 1000) {
            return
        }
        lastImageSendTime = currentTime

        scope?.launch(Dispatchers.IO) {
            try {
                if (session == null || !session!!.isActive) return@launch
                
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                // Format resmi Gemini Live API untuk realtime input video/gambar
                val imageMessage = """
                    {
                        "realtimeInput": {
                            "video": {
                                "data": "$base64Image",
                                "mimeType": "image/jpeg"
                            }
                        }
                    }
                """.trimIndent()
                
                session?.send(Frame.Text(imageMessage))
            } catch (e: Exception) {
                if (e.localizedMessage?.contains("cancelled") != true) {
                    onMessageReceived("Gagal kirim gambar: ${e.localizedMessage}")
                }
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
            try {
                session?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        session = null
    }
}