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

@OptIn(ExperimentalMaterial3Api::class)
@Composable 
fun LayarUtama() {
    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorder(context) }
    
    val sharedPreferences = remember {
        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    }
    
    var apiKey by remember {
        mutableStateOf(sharedPreferences.getString("GEMINI_API_KEY", "") ?: "")
    }
    
    // Pilihan Model & Endpoint
    val daftarModel = listOf(
        "models/gemini-2.0-flash-exp",
        "models/gemini-2.0-flash",
        "models/gemini-2.5-flash",
        "models/gemini-3.0-flash",
        "models/gemini-3.8-live",
        "Kustom (Ketik Sendiri)"
    )
    
    val daftarEndpoint = listOf("v1beta", "v1alpha")

    var selectedModel by remember { mutableStateOf(sharedPreferences.getString("SELECTED_MODEL", daftarModel[0]) ?: daftarModel[0]) }
    var selectedEndpoint by remember { mutableStateOf(sharedPreferences.getString("SELECTED_ENDPOINT", daftarEndpoint[0]) ?: daftarEndpoint[0]) }
    
    var customModelInput by remember { mutableStateOf(sharedPreferences.getString("CUSTOM_MODEL", "") ?: "") }
    
    var inputKeySementara by remember { mutableStateOf(apiKey) }
    var tampilkanMenuPengaturan by remember { mutableStateOf(apiKey.isEmpty()) }
    
    var statusTeks by remember { mutableStateOf(if (apiKey.isEmpty()) "Silakan masukkan API Key terlebih dahulu." else "Siap merekam suara.") }
    var isRecording by remember { mutableStateOf(false) }

    var webSocketManager by remember { mutableStateOf<WebSocketManager?>(null) }

    // Dropdown state
    var modelExpanded by remember { mutableStateOf(false) }
    var endpointExpanded by remember { mutableStateOf(false) }

    val modelAktif = if (selectedModel == "Kustom (Ketik Sendiri)") customModelInput else selectedModel

    LaunchedEffect(apiKey, modelAktif, selectedEndpoint) {
        if (apiKey.isNotEmpty() && modelAktif.isNotEmpty()) {
            webSocketManager = WebSocketManager(apiKey, modelAktif, selectedEndpoint) { response ->
                statusTeks = if (response.length > 50) "Respon: ${response.take(50)}..." else "Respon: $response"
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
            // Input API Key
            OutlinedTextField(
                value = inputKeySementara,
                onValueChange = { inputKeySementara = it },
                label = { Text("Masukkan Gemini API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Dropdown Pilih Endpoint (v1beta / v1alpha)
            ExposedDropdownMenuBox(
                expanded = endpointExpanded,
                onExpandedChange = { endpointExpanded = !endpointExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedEndpoint,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pilih Endpoint API") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endpointExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = endpointExpanded,
                    onBinding = {},
                    onDismissRequest = { endpointExpanded = false }
                ) {
                    daftarEndpoint.forEach { endpoint ->
                        DropdownMenuItem(
                            text = { Text(endpoint) },
                            onClick = {
                                selectedEndpoint = endpoint
                                endpointExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dropdown Pilih Model
            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = !modelExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pilih Versi Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    daftarModel.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                selectedModel = model
                                modelExpanded = false
                            }
                        )
                    }
                }
            }

            // Jika memilih Kustom, tampilkan kolom teks tambahan
            if (selectedModel == "Kustom (Ketik Sendiri)") {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customModelInput,
                    onValueChange = { customModelInput = it },
                    label = { Text("Ketik Nama Model (cth: models/gemini-2.0-flash)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                apiKey = inputKeySementara
                sharedPreferences.edit()
                    .putString("GEMINI_API_KEY", apiKey)
                    .putString("SELECTED_MODEL", selectedModel)
                    .putString("SELECTED_ENDPOINT", selectedEndpoint)
                    .putString("CUSTOM_MODEL", customModelInput)
                    .apply()
                
                tampilkanMenuPengaturan = false
                statusTeks = "Pengaturan disimpan. Siap merekam."
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Simpan Pengaturan")
            }
        } else {
            Button(onClick = {
                if (apiKey.isEmpty()) {
                    tampilkanMenuPengaturan = true
                    return@Button
                }

                if (!isRecording) {
                    isRecording = true
                    statusTeks = "Menghubungkan ke Gemini..."
                    
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
            
            TextButton(onClick = { tampilkanMenuPengaturan = true }) {
                Text("Ubah Pengaturan & API Key")
            }
        }
    }
}