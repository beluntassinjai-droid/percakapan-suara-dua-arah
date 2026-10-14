package com.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var audioRecorder: AudioRecorder
    private var webSocketManager: KtorWebSocketManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        audioRecorder = AudioRecorder(this)
        val sharedPrefs = getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)

        setContent {
            var apiKey by remember { mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") }
            
            // Variabel Pengaturan
            var selectedModel by remember { mutableStateOf(sharedPrefs.getString("model", "gemini-3.8-live") ?: "gemini-3.8-live") }
            var selectedEndpoint by remember { mutableStateOf(sharedPrefs.getString("endpoint", "v1alpha") ?: "v1alpha") }
            var selectedVoice by remember { mutableStateOf(sharedPrefs.getString("voice", "Puck") ?: "Puck") }
            
            var statusText by remember { mutableStateOf("Silakan masukkan API Key & Pengaturan") }
            var isConnected by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(apiKey.isEmpty()) }

            val coroutineScope = rememberCoroutineScope()

            // 1. Daftar Pilihan Model Versi 3
            val modelList = listOf(
                "gemini-3.8-live",
                "gemini-3.8-live-extended-thinking",
                "gemini-3.5-transcribe-live",
                "gemini-3.5-live-translate-preview"
            )
            var expandedModel by remember { mutableStateOf(false) }

            // 2. Daftar Pilihan Endpoint
            val endpointList = listOf(
                "v1alpha",
                "v1beta",
                "v1"
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

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    statusText = "Izin diberikan, menghubungkan..."
                    webSocketManager = KtorWebSocketManager(
                        apiKey = apiKey,
                        selectedModel = selectedModel,
                        selectedEndpoint = selectedEndpoint,
                        selectedVoice = selectedVoice,
                        onMessageReceived = { msg -> statusText = msg }
                    )
                    webSocketManager?.connect()
                    isConnected = true
                    coroutineScope.launch {
                        audioRecorder.startRecording { audioChunk ->
                            webSocketManager?.sendAudioChunk(audioChunk)
                        }
                    }
                } else {
                    statusText = "Izin mikrofon ditolak"
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
                        text = "Percakapan Suara Dua Arah",
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
                        
                        // Dropdown 1: Model Versi 3
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

                        // Dropdown 3: Karakter Suara (Voice Config)
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
                                    val permission = Manifest.permission.RECORD_AUDIO
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_GRANTED) {
                                        statusText = "Menghubungkan..."
                                        webSocketManager = KtorWebSocketManager(
                                            apiKey = apiKey,
                                            selectedModel = selectedModel,
                                            selectedEndpoint = selectedEndpoint,
                                            selectedVoice = selectedVoice,
                                            onMessageReceived = { msg -> statusText = msg }
                                        )
                                        webSocketManager?.connect()
                                        isConnected = true
                                        coroutineScope.launch {
                                            audioRecorder.startRecording { audioChunk ->
                                                webSocketManager?.sendAudioChunk(audioChunk)
                                            }
                                        }
                                    } else {
                                        permissionLauncher.launch(permission)
                                    }
                                } else {
                                    audioRecorder.stopRecording()
                                    webSocketManager?.disconnect()
                                    isConnected = false
                                    statusText = "Berhenti Bicara"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isConnected) "Berhenti Bicara" else "Mulai Bicara")
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

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.stopRecording()
        webSocketManager?.disconnect()
    }
}