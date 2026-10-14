package com.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Launcher untuk meminta izin mikrofon secara runtime
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Anda bisa menangani status izin di sini jika diperlukan
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        
        // Meminta izin RECORD_AUDIO saat aplikasi dibuka
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
    var statusTeks by remember { mutableStateOf("Template Jetpack Compose siap dikembangkan.") }
    var isRecording by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.Center
    ) {
        Text("percakapan suara dua arah", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(statusTeks)
        Spacer(modifier = Modifier.height(24.dp))

        // Tombol interaktif uji coba rekam suara
        Button(onClick = {
            if (!isRecording) {
                isRecording = true
                statusTeks = "Merekam suara..."
                CoroutineScope(Dispatchers.IO).launch {
                    audioRecorder.startRecording { audioChunk ->
                        // Di sinilah nanti data chunk audio PCM dikirim ke WebSocket AI
                    }
                }
            } else {
                isRecording = false
                audioRecorder.stopRecording()
                statusTeks = "Perekaman dihentikan. Template siap dikembangkan."
            }
        }) {
            Text(if (isRecording) "Berhenti Bicara" ket { "Mulai Bicara" })
        }
    }
}