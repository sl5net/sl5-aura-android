// app/src/main/java/de/sl5/aura/VoskProcessor.kt

package de.sl5.aura

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log // WICHTIG: Import für das Logging
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService


interface VoskListener {
    fun onResult(text: String)
    fun onError(message: String)
    fun onFinalResult()
}



class VoskProcessor(private val context: Context, private val listener: VoskListener) {



    private var model: Model? = null
        private var isListening = false
        private val mainHandler = Handler(Looper.getMainLooper())
        private val LOG_TAG = "VoskDebug" // Unser Filter für die Logs

        init {
            Log.d(LOG_TAG, "VoskProcessor init")
            initVosk()
        }

        private fun initVosk() {
            // vosk-model-small-de-0.15 vosk-model-de-0.21
            StorageService.unpack(context, "vosk-model-de", "model",
                                  { model ->
                                      this.model = model
                                      Log.d(LOG_TAG, "Model unpacked successfully!")
                                  },
                                  { exception ->
                                      Log.e(LOG_TAG, "Failed to unpack model", exception)
                                      listener.onError("Failed to unpack model: ${exception.message}")
                                  }
            )
        }

        @SuppressLint("MissingPermission")
        fun startListening() {
            if (isListening || model == null) {
                Log.w(LOG_TAG, "StartListening called but already listening or model is null.")
                return
            }
            isListening = true
            Log.d(LOG_TAG, "Starting listening thread...")

            Thread {
                try {
                    val sampleRate = 16000
                    val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    Log.d(LOG_TAG, "AudioRecord buffer size: $bufferSize")

                    val audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                    val recognizer = Recognizer(model, sampleRate.toFloat())

                    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        mainHandler.post { listener.onError("AudioRecord init failed") }
                        return@Thread
                    }

                    audioRecord.startRecording()
                    Log.d(LOG_TAG, "AudioRecord started recording.")

                    val buffer = ByteArray(bufferSize)
                    var totalBytesRead = 0

                    while (isListening) {
                        val nread = audioRecord.read(buffer, 0, buffer.size)
                        if (nread > 0) {
                            totalBytesRead += nread
                            recognizer.acceptWaveForm(buffer, nread)
                        }
                    }
                    Log.d(LOG_TAG, "Loop finished. Total bytes read: $totalBytesRead")

                    audioRecord.stop()
                    audioRecord.release()

                    val finalResult = recognizer.finalResult
                    Log.d(LOG_TAG, "Final Result JSON: $finalResult")
                    mainHandler.post { listener.onResult(finalResult) }
                    mainHandler.post { listener.onFinalResult() }

                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Error in listening thread", e)
                    mainHandler.post { listener.onError(e.message ?: "Unknown error") }
                }
            }.start()
        }



        fun stopListening() {
            isListening = false
        }

        fun shutdown() {
            stopListening()
            model?.close()
        }
}
