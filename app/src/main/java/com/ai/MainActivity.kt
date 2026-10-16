package com.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import kotlinx.coroutines.launch
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
            
            // Variabel Pengaturan (Disesuaikan dengan Model Live Gemini Resmi)
            var selectedModel by remember { mutableStateOf(sharedPrefs.getString("model", "gemini-2.0-flash-exp") ?: "gemini-2.0-flash-exp") }
            var selectedEndpoint by remember { mutableStateOf(sharedPrefs.getString("endpoint", "v1alpha") ?: "v1alpha") }
            var selectedVoice by remember { mutableStateOf(sharedPrefs.getString("voice", "Puck") ?: "Puck") }
            
            var statusText by remember { mutableStateOf("Silakan masukkan API Key & Pengaturan") }
            var isConnected by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(apiKey.isEmpty()) }

            val coroutineScope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            // 1. Daftar Pilihan Model Live Gemini Resmi
            val modelList = listOf(
                "gemini-2.0-flash-exp",
                "gemini-1.5-flash"
            )
            var expandedModel by remember { mutableStateOf(false) }

            // 2. Daftar Pilihan Endpoint
            val endpointList = listOf(
                "v1alpha",
                "v1beta"
            )
            var expandedEndpoint by remember { mutableStateOf(false) }

            // 3. Daftar Pilihan Karakter Suara (Voice Config)
            val voiceList = listOf(
                "Puck",
                "Charon",
                "Kore",
                "Fenrir",
                "Aoede"
            )
            var expandedVoice by remember { mutableStateOf(false) }

            // Handler Perizinan Kamera & Mikrofon Sekaligus
            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
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
                        // Input API Key
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Dropdown 1: Model
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedModel = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Model: $selectedModel")
                            }
                            DropdownMenu(
                                expanded = expandedModel,
                                onDismissRequest = { expandedModel = false }
                            ) {
                                modelList.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = {
                                            selectedModel = item
                                            expandedModel = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dropdown 2: Endpoint
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedEndpoint = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Endpoint: $selectedEndpoint")
                            }
                            DropdownMenu(
                                expanded = expandedEndpoint,
                                onDismissRequest = { expandedEndpoint = false }
                            ) {
                                endpointList.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = {
                                            selectedEndpoint = item
                                            expandedEndpoint = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dropdown 3: Karakter Suara
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedVoice = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Suara AI: $selectedVoice")
                            }
                            DropdownMenu(
                                expanded = expandedVoice,
                                onDismissRequest = { expandedVoice = false }
                            ) {
                                voiceList.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = {
                                            selectedVoice = item
                                            expandedVoice = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Tombol Simpan
                        Button(
                            onClick = {
                                if (apiKey.isNotBlank()) {
                                    sharedPrefs.edit()
                                        .putString("api_key", apiKey)
                                        .putString("model", selectedModel)
                                        .putString("endpoint", selectedEndpoint)
                                        .putString("voice", selectedVoice)
                                        .apply()
                                    showSettings = false
                                    statusText = "Pengaturan disimpan"
                                } else {
                                    statusText = "API Key tidak boleh kosong!"
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
                                    if (apiKey.isBlank()) {
                                        showSettings = true
                                        return@Button
                                    }
                                    val hasAudio = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    val hasCamera = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

                                    if (hasAudio && hasCamera) {
                                        statusText = "Menghubungkan..."
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

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { showSettings = true }
                        ) {
                            Text("Ubah Pengaturan & API Key")
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

        // 1. Jalankan Rekaman Suara
        audioRecorder.startRecording { audioChunk ->
            webSocketManager?.sendAudioChunk(audioChunk)
        }

        // 2. Jalankan Stream Kamera Belakang (Visual Live)
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
                // Mengonversi bingkai kamera ke JPEG / Base64 dan dikirim via WebSocket
                val imageBytes = imageProxy.toJpegByteArray()
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

// Extension Helper untuk konversi ImageProxy ke ByteArray JPEG
private fun ImageProxy.toJpegByteArray(): ByteArray? {
    val plane = planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}