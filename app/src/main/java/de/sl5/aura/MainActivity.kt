// app/src/main/java/de/sl5/aura/MainActivity.kt
package de.sl5.aura

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
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
import de.sl5.aura.ui.theme.SL5AuraTheme
import org.json.JSONObject

class MainActivity : ComponentActivity(), VoskListener {

    private lateinit var voskProcessor: VoskProcessor
    private var resultText by mutableStateOf("Requesting permission...")
    private var isListening by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                initializeProcessor()
            } else {
                resultText = "Permission denied. App cannot function."
                Toast.makeText(this, resultText, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkMicrophonePermission()
        setContent {
            SL5AuraTheme {
                AuraScreen(
                    resultText = resultText,
                    isListening = isListening,
                    onButtonClick = ::toggleRecognition
                )
            }
        }
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initializeProcessor()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun initializeProcessor() {
        resultText = "Initializing model..."
        voskProcessor = VoskProcessor(this, this)
    }

    private fun toggleRecognition() {
        if (isListening) {
            voskProcessor.stopListening()
        } else {
            resultText = "Listening..."
            voskProcessor.startListening()
        }
        isListening = !isListening
    }

    // --- Implementation of VoskListener ---
    override fun onResult(text: String) {
        val json = JSONObject(text)
        if (json.getString("text").isNotBlank()) {
            resultText = json.getString("text")
        }
    }

    override fun onFinalResult() {
        isListening = false
    }

    override fun onError(message: String) {
        resultText = "Error: $message"
        isListening = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::voskProcessor.isInitialized) {
            voskProcessor.shutdown()
        }
    }
}


@Composable
fun AuraScreen(resultText: String, isListening: Boolean, onButtonClick: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "SL5 Aura", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(40.dp))
            Text(text = resultText)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onButtonClick) {
                Text(if (isListening) "Stop Listening..." else "Start Recording")
            }
        }
    }
}