package com.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    // Konfigurasi standar untuk audio real-time (16kHz, Mono, 16-bit PCM)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    /**
     * Memulai proses perekaman suara dari mikrofon.
     * Mengembalikan data byte PCM secara kontinu melalui lambda [onAudioDataReady].
     */
    suspend fun startRecording(onAudioDataReady: (ByteArray) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Pastikan izin RECORD_AUDIO sudah diberikan sebelum memanggil fungsi ini
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        withContext(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readSize > 0) {
                    // Salin data audio yang terbaca untuk dikirim ke WebSocket/AI
                    val audioChunk = buffer.copyOfRange(0, readSize)
                    onAudioDataReady(audioChunk)
                }
            }
        }
    }

    /**
     * Menghentikan proses perekaman suara.
     */
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}