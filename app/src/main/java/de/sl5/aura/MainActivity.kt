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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.sl5.aura.ui.theme.SL5AuraTheme
import org.json.JSONObject

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
private var textFieldValue by mutableStateOf(TextFieldValue("Initializing"))

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
                    textFieldValue = textFieldValue,
                    isListening = isListening,
                    onButtonClick = ::toggleRecognition,
                    onValueChange = { newTfv -> textFieldValue = newTfv }
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
        resultText = "Initializing"
        voskProcessor = VoskProcessor(this, this)
    }

    // In MainActivity.kt
    private fun toggleRecognition() {
        if (isListening) {
            voskProcessor.stopListening()
        } else {
            if (textFieldValue.text.startsWith("Initializing") || textFieldValue.text.startsWith("Error:")) {
                textFieldValue = TextFieldValue("") // Wir setzen es auf ein leeres TextFieldValue
            }
            voskProcessor.startListening()
        }
        isListening = !isListening
    }

    override fun onResult(text: String) {
        val json = JSONObject(text)
        val newText = json.getString("text")

        if (newText.isNotBlank()) {
            val currentText = if (textFieldValue.text.startsWith("Initializing")) "" else textFieldValue.text
            val separator = if (currentText.isNotEmpty()) " " else ""
            val fullText = currentText + separator + newText

            textFieldValue = TextFieldValue(
                text = fullText,
                selection = TextRange(fullText.length)
            )
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
fun AuraScreen(
    textFieldValue: TextFieldValue, // NEU
    isListening: Boolean,
    onButtonClick: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit // NEU
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(textFieldValue.text) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "SL5 Aura", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(20.dp))

            TextField(
                value = textFieldValue, // NEU
                onValueChange = onValueChange, // NEU
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = onButtonClick) { Text(if (isListening) "Stop" else "Record") }
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(textFieldValue.text)) // NEU
                    Toast.makeText(context, "Text copied!", Toast.LENGTH_SHORT).show()
                }) { Text("Copy All") }
            }
        }
    }
}

