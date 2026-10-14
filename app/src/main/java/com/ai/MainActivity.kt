package com.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var webSocketManager: KtorWebSocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        audioRecorder = AudioRecorder(this)
        
        // Ganti dengan API Key dan model yang sesuai
        val apiKey = "YOUR_API_KEY"
        val selectedModel = "gemini-2.0-flash-exp" // atau model live yang didukung
        val selectedEndpoint = "v1alpha"

        setContent {
            var statusText by remember { mutableStateOf("Siap untuk mulai") }
            var isConnected by remember { mutableStateOf(false) }

            val coroutineScope = rememberCoroutineScope()

            webSocketManager = remember {
                KtorWebSocketManager(
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    selectedEndpoint = selectedEndpoint,
                    onMessageReceived = { msg ->
                        statusText = msg
                    }
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    statusText = "Izin diberikan, menghubungkan..."
                    webSocketManager.connect()
                    isConnected = true
                    coroutineScope.launch {
                        audioRecorder.startRecording { audioChunk ->
                            webSocketManager.sendAudioChunk(audioChunk)
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
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(text = statusText)
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (!isConnected) {
                                val permission = Manifest.permission.RECORD_AUDIO
                                if (ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_GRANTED) {
                                    statusText = "Menghubungkan..."
                                    webSocketManager.connect()
                                    isConnected = true
                                    coroutineScope.launch {
                                        audioRecorder.startRecording { audioChunk ->
                                            webSocketManager.sendAudioChunk(audioChunk)
                                        }
                                    }
                                } else {
                                    permissionLauncher.launch(permission)
                                }
                            } else {
                                audioRecorder.stopRecording()
                                webSocketManager.disconnect()
                                isConnected = false
                                statusText = "Berhenti Bicara"
                            }
                        }
                    ) {
                        Text(if (isConnected) "Berhenti Bicara" else "Mulai Bicara")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.stopRecording()
        webSocketManager.disconnect()
    }
}