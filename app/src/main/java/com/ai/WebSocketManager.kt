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
        val sampleRate = 24000 // Standar output audio dari Gemini Live
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

                    // 1. Konfigurasi setup lengkap dengan voiceConfig (Puck) dan responseModalities (AUDIO)
                    val setupMessage = """
                        {
                            "setup": {
                                "model": "$selectedModel",
                                "generationConfig": {
                                    "responseModalities": ["AUDIO"]
                                },
                                "voiceConfig": {
                                    "prebuiltVoiceConfig": {
                                        "voiceName": "Puck"
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    send(Frame.Text(setupMessage))

                    // 2. Komponen pemicu (trigger) teks awal agar AI langsung menyapa dan merespons suara
                    val triggerMessage = """
                        {
                            "clientContent": {
                                "turns": [
                                    {
                                        "role": "user",
                                        "parts": [
                                            {
                                                "text": "Halo, sapa saya dengan suara singkat."
                                            }
                                        ]
                                    }
                                ],
                                "turnComplete": true
                            }
                        }
                    """.trimIndent()
                    send(Frame.Text(triggerMessage))
                    
                    onMessageReceived("Terhubung! Meminta respons AI...")

                    // Menerima balasan dari server secara real-time
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val json = JSONObject(text)
                                if (json.has("error")) {
                                    val err = json.getJSONObject("error").optString("message", "Error")
                                    onMessageReceived("API Error: $err")
                                    continue
                                }
                                
                                // Memeriksa isi respons server yang membawa audio balik
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
                                                onMessageReceived("Gemini sedang berbicara...")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Abaikan
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