package com.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var audioRecorder: AudioRecorder
    private var webSocketManager: KtorWebSocketManager? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        audioRecorder = AudioRecorder(this)
        val sharedPrefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)

        setContent {
            var apiKey by remember { mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") }
            var selectedModel by remember { mutableStateOf(sharedPrefs.getString("model", "gemini-2.0-flash-exp") ?: "gemini-2.0-flash-exp") }
            var selectedEndpoint by remember { mutableStateOf(sharedPrefs.getString("endpoint", "v1alpha") ?: "v1alpha") }
            var selectedVoice by remember { mutableStateOf(sharedPrefs.getString("voice", "Puck") ?: "Puck") }
            
            var statusText by remember { mutableStateOf("Silakan masukkan API Key & Pengaturan") }
            var isConnected by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(apiKey.isEmpty()) }

            val lifecycleOwner = LocalLifecycleOwner.current

            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions: Map<String, Boolean> ->
                val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
                val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

                if (audioGranted && cameraGranted) {
                    statusText = "Izin diberikan, menghubungkan..."
                    startLiveSession(
                        apiKey, selectedModel, selectedEndpoint, selectedVoice,
                        lifecycleOwner,
                        onStatusChange = { statusText = it },
                        onConnected = { isConnected = true }
                    )
                } else {
                    statusText = "Izin Kamera dan Mikrofon diperlukan!"
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Navigasi AI Live Voice & Vision",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (showSettings) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (apiKey.isNotBlank()) {
                                    sharedPrefs.edit().putString("api_key", apiKey).apply()
                                    showSettings = false
                                    statusText = "Pengaturan disimpan"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Simpan Pengaturan")
                        }
                    } else {
                        Text(text = statusText)
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (!isConnected) {
                                    val hasAudio = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    val hasCamera = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

                                    if (hasAudio && hasCamera) {
                                        startLiveSession(
                                            apiKey, selectedModel, selectedEndpoint, selectedVoice,
                                            lifecycleOwner,
                                            onStatusChange = { statusText = it },
                                            onConnected = { isConnected = true }
                                        )
                                    } else {
                                        permissionsLauncher.launch(
                                            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                                        )
                                    }
                                } else {
                                    stopLiveSession()
                                    isConnected = false
                                    statusText = "Sesi Dimatikan"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isConnected) "Berhenti Navigasi" else "Mulai Navigasi Live")
                        }
                    }
                }
            }
        }
    }

    private fun startLiveSession(
        apiKey: String,
        model: String,
        endpoint: String,
        voice: String,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        onStatusChange: (String) -> Unit,
        onConnected: () -> Unit
    ) {
        webSocketManager = KtorWebSocketManager(
            apiKey = apiKey,
            selectedModel = model,
            selectedEndpoint = endpoint,
            selectedVoice = voice,
            onMessageReceived = { msg -> onStatusChange(msg) }
        )
        webSocketManager?.connect()
        onConnected()

        audioRecorder.startRecording { audioChunk ->
            webSocketManager?.sendAudioChunk(audioChunk)
        }

        startCameraStream(lifecycleOwner)
    }

    private fun startCameraStream(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val imageBytes = imageProxyToJpegByteArray(imageProxy)
                if (imageBytes != null) {
                    webSocketManager?.sendImageChunk(imageBytes)
                }
                imageProxy.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun imageProxyToJpegByteArray(image: ImageProxy): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 75, out)
        return out.toByteArray()
    }

    private fun stopLiveSession() {
        audioRecorder.stopRecording()
        webSocketManager?.disconnect()
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLiveSession()
        cameraExecutor.shutdown()
    }
}