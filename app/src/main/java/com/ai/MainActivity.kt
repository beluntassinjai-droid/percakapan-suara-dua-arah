package com.ai

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent { 
            LayarUtama() 
        }
    }
}

@Composable 
fun LayarUtama() {
    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorder(context) }
    
    // Mengambil SharedPreferences untuk menyimpan API Key
    val sharedPreferences = remember {
        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    }
    
    var apiKey by remember {
        mutableStateOf(sharedPreferences.getString("GEMINI_API_KEY", "") ?: "")
    }
    
    var inputKeySementara by remember { mutableStateOf(apiKey) }
    var tampilkanMenuPengaturan by remember { mutableStateOf(apiKey.isEmpty()) }
    
    var statusTeks by remember { mutableStateOf(if (apiKey.isEmpty()) "Silakan masukkan API Key terlebih dahulu." else "Siap merekam suara.") }
    var isRecording by remember { mutableStateOf(false) }

    var webSocketManager by remember {
        mutableStateOf<WebSocketManager?>(null)
    }

    // Inisialisasi WebSocketManager jika API Key sudah ada
    LaunchedEffect(apiKey) {
        if (apiKey.isNotEmpty()) {
            webSocketManager = WebSocketManager(apiKey) { response ->
                statusTeks = if (response.length > 60) "Respon: ${response.take(60)}..." else "Respon: $response"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.Center
    ) {
        Text("percakapan suara dua arah", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(statusTeks, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(24.dp))

        if (tampilkanMenuPengaturan) {
            // Tampilan Menu Pengaturan API Key
            OutlinedTextField(
                value = inputKeySementara,
                onValueChange = { inputKeySementara = it },
                label = { Text("Masukkan Gemini API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = {
                apiKey = inputKeySementara
                sharedPreferences.edit().putString("GEMINI_API_KEY", apiKey).apply()
                tampilkanMenuPengaturan = false
                statusTeks = "API Key disimpan. Siap merekam."
            }) {
                Text("Simpan API Key")
            }
        } else {
            // Tombol Utama Merekam
            Button(onClick = {
                if (apiKey.isEmpty()) {
                    tampilkanMenuPengaturan = true
                    return@Button
                }

                if (!isRecording) {
                    isRecording = true
                    statusTeks = "Menghubungkan ke Gemini Live..."
                    
                    webSocketManager?.connect()

                    CoroutineScope(Dispatchers.IO).launch {
                        audioRecorder.startRecording { audioChunk ->
                            webSocketManager?.sendAudioChunk(audioChunk)
                        }
                    }
                } else {
                    isRecording = false
                    audioRecorder.stopRecording()
                    webSocketManager?.disconnect()
                    
                    statusTeks = "Perekaman dihentikan."
                }
            }) {
                Text(if (isRecording) "Berhenti Bicara" else "Mulai Bicara")
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Tombol untuk membuka menu pengaturan API Key kembali
            TextButton(onClick = { tampilkanMenuPengaturan = true }) {
                Text("Ubah API Key")
            }
        }
    }
}