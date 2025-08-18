package cz.lastaapps.languagetool.ui.features.home

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.TextFieldValue
import arrow.core.Either
import cz.lastaapps.languagetool.core.StateViewModel
import cz.lastaapps.languagetool.core.VMState
import cz.lastaapps.languagetool.core.error.CommonErrors
import cz.lastaapps.languagetool.core.error.DomainError
import cz.lastaapps.languagetool.core.launchInVM
import cz.lastaapps.languagetool.core.launchVM
import cz.lastaapps.languagetool.core.launchVMJob
import cz.lastaapps.languagetool.data.AppPreferences
import cz.lastaapps.languagetool.data.LangToolRepository
import cz.lastaapps.languagetool.data.getApiCredentials
import cz.lastaapps.languagetool.data.model.toDomain
import cz.lastaapps.languagetool.domain.logic.replace
import cz.lastaapps.languagetool.domain.logic.textDiff
import cz.lastaapps.languagetool.domain.model.CheckProgress
import cz.lastaapps.languagetool.domain.model.Language
import cz.lastaapps.languagetool.domain.model.MatchedError
import cz.lastaapps.languagetool.domain.model.MatchedText
import cz.lastaapps.languagetool.ui.features.home.vosk.VoskProcessor
import cz.lastaapps.languagetool.ui.features.home.vosk.VoskListener

import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

private val _isListening = MutableStateFlow(false)
val isListening = _isListening.asStateFlow()

private const val MAX_CHARS_FREE = 20_000
private const val MAX_CHARS_PREMIUM = 60_000
private val TIMEOUT_FREE = 6.seconds
private val TIMEOUT_PREMIUM = 1.seconds

internal class HomeViewModel(
    private val repo: LangToolRepository,
    private val appPreferences: AppPreferences
) : StateViewModel<HomeState>(HomeState()), VoskListener {

    private lateinit var voskProcessor: VoskProcessor

    fun onAppear() = launchOnlyOnce {
        appPreferences.getPicky().onEach {
            updateState { copy(isPicky = it) }
        }.launchInVM()

        appPreferences.getLanguage().onEach {
            updateState { copy(language = it?.toDomain()) }
        }.launchInVM()

        combine(
            appPreferences.getApiUrl(),
            appPreferences.getApiCredentials(),
        ) { url, credentials ->
            val canUsePremium = url != null || credentials != null
            updateState {
                copy(
                    maxChars = if (canUsePremium) {
                        MAX_CHARS_PREMIUM
                    } else {
                        MAX_CHARS_FREE
                    },
                    timeout = if (canUsePremium) {
                        TIMEOUT_PREMIUM
                    } else {
                        TIMEOUT_FREE
                    },
                )
            }
        }.launchInVM()
    }

    fun onCheckRequest() = launchVM {
        val state = latestState()
        val text = state.matched.text
        val maxChars = state.maxChars

        if (state.progress != CheckProgress.Ready) {
            return@launchVM
        }
        if (text.length > maxChars) {
            updateState { copy(error = CommonErrors.TextToLong) }
            return@launchVM
        }

        updateState { copy(progress = CheckProgress.Processing) }
        when (val res = repo.correctText(text)) {
            is Either.Right -> {
                updateState {
                    val oldErrors = matched.errors
                    val newErrors = res.value.errors
                    val merged = mergeSkipped(oldErrors, newErrors).toPersistentList()
                    copy(matched = res.value.copy(errors = merged))
                }
                launchRateLimitJob()
            }

            is Either.Left -> updateState {
                copy(error = res.value, progress = CheckProgress.Ready)
            }
        }
    }

    private fun mergeSkipped(old: List<MatchedError>, new: List<MatchedError>): List<MatchedError> {
        var iO = 0
        var iN = 0
        val out = mutableListOf<MatchedError>()

        while (iO < old.size && iN < new.size) {
            val fO = old[iO]
            val fN = new[iN]

            when {
                fO.range.first < fN.range.first -> iO++
                fO.range.first > fN.range.first -> {
                    iN++
                    out += fN
                }

                fO.range.first == fN.range.first -> {
                    iO++
                    iN++

                    out += if (fO.isSkipped) {
                        fN.copy(isSkipped = true)
                    } else {
                        fN
                    }
                }
            }
        }
        while (iN < new.size) {
            out += new[iN++]
        }

        return out
    }

    fun onTextChanged(newText: String) {
        updateState {

            val currentMatched = matched
            val currentText = currentMatched.text

            val diff = textDiff(currentText, newText)

            copy(
                matched = currentMatched.replace(diff.first, diff.second),
            )
        }
    }

    fun applySuggestion(error: MatchedError, suggestion: String) {
        updateState {
            copy(
                matched = matched.replace(error.range, suggestion),
            )
        }
        latestState().matched.errors.takeIf { it.isEmpty() }?.let {
            onCheckRequest()
        }
    }

    fun skipSuggestion(matchedError: MatchedError) {
        updateState {
            matched.errors.indexOf(matchedError)
                .takeIf { it >= 0 }
                ?.let { errorIndex ->
                    copy(
                        matched = matched.copy(
                            errors = matched.errors
                                .set(errorIndex, matchedError.copy(isSkipped = true)),
                        ),
                    )
                } ?: this
        }
    }

    fun dismissError() {
        updateState { copy(error = null) }
    }

    fun setIsPicky(value: Boolean) = launchVM {
        updateState { copy(isPicky = value) }
        appPreferences.setPicky(value)
    }

    private var rateLimitJob: Job? = null
    private fun launchRateLimitJob() {
        rateLimitJob?.cancel()
        val state = latestState()

        rateLimitJob = launchVMJob {
            val timeout = state.timeout
            updateState { copy(progress = CheckProgress.RateLimit(timeout)) }
            delay(timeout)
            updateState { copy(progress = CheckProgress.Ready) }
        }
    }

    fun selectMatchError(error: MatchedError?) {
        updateState {
            copy(selectedMatch = error)
        }
    }

//    File: cz/lastaapps/languagetool/ui/features/home/HomeViewModel.kt:220
    fun toggleRecognition() {
        val currentText = latestState().matched.text
        Log.d("VOSK_DEBUG", "Vosk toggleRecognition called! Current state , currentText={currentText}")

        if (_isListening.value) {
            voskProcessor.stopListening()
        } else {
            voskProcessor.startListening()
        }
        _isListening.value = !_isListening.value


        Log.d("VOSK_DEBUG", latestState().toString())

    }

    fun initializeVosk(context: Context) {
        // Verhindert, dass wir es aus Versehen mehrfach tun
        if (::voskProcessor.isInitialized) return

        voskProcessor = VoskProcessor(context, this) // 'this' ist das ViewModel als Listener
    }

    override fun onResult(text: String) {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) { return }
        val newText = json.getString("text")
        Log.d("MainActivity", "Received result: $newText")

        if (newText.isNotBlank()) {
            launchVM { // Starte eine Coroutine im ViewModel
                // 1. Hole den aktuellen Text
                val currentText = latestState().matched.text
                val separator = if (currentText.isNotEmpty()) " " else ""

                // 2. Füge den neuen, unkorrigierten Text hinzu
                onTextChanged(currentText + separator + newText)

                // 3. Stoße sofort die eingebaute Korrekturprüfung an
                onCheckRequest()
            }
        }
    }

    override fun onError(message: String) {
        Log.e("VOSK_ERROR", message)
        _isListening.value = false
    }

    //    File: cz/lastaapps/languagetool/ui/features/home/HomeViewModel.kt:220
    override fun onFinalResult() {
        _isListening.value = false
    }



    fun onDestroy() {
        if (::voskProcessor.isInitialized) {
            voskProcessor.shutdown()
        }
    }




}

@Immutable
internal data class HomeState(
    val progress: CheckProgress = CheckProgress.Ready,
    val matched: MatchedText = MatchedText.empty,
    val selectedMatch: MatchedError? = null,
    val error: DomainError? = null,
    val maxChars: Int = MAX_CHARS_FREE,
    val timeout: Duration = TIMEOUT_FREE,
    val isPicky: Boolean = false,
    val language: Language? = null,
) : VMState
