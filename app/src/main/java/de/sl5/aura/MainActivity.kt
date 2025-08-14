package de.sl5.aura

import android.Manifest // Added for permission
import android.content.pm.PackageManager // Added for permission

import android.os.Bundle

import android.widget.Toast // Added for user feedback

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.activity.result.contract.ActivityResultContracts // Added for permission

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

import androidx.core.content.ContextCompat // Added for permission

import de.sl5.aura.ui.theme.SL5AuraTheme

class MainActivity : ComponentActivity() {

    // 1. ADDED: Create a launcher that knows how to request a permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            val message = if (isGranted) "Permission granted!" else "Permission denied."
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. ADDED: Check for microphone permission at startup
        checkMicrophonePermission()

        enableEdgeToEdge()
        setContent {
            SL5AuraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

    }
    // 3. ADDED: The function that handles the permission logic
    private fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                Toast.makeText(this, "Permission already granted.", Toast.LENGTH_SHORT).show()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SL5AuraTheme {
        Greeting("Aura")
    }
}