// cz/lastaapps/languagetool/ui/features/home/HomeDest.kt
package cz.lastaapps.languagetool.ui.features.home


import android.Manifest
import android.util.Log
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.os.trace
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import cz.lastaapps.languagetool.core.error.getMessage
import cz.lastaapps.languagetool.ui.features.home.components.ActionChips
import cz.lastaapps.languagetool.ui.features.home.components.ErrorSuggestionRow
import cz.lastaapps.languagetool.ui.features.home.components.HomeBottomAppBar
import cz.lastaapps.languagetool.ui.features.home.components.TextCorrectionField
import kotlinx.coroutines.launch


@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun HomeDest(
    viewModel: HomeViewModel,
    toAbout: () -> Unit,
    toHelp: () -> Unit,
    toLanguage: () -> Unit,
    toSettings: () -> Unit,
    toSpellCheck: () -> Unit,
    modifier: Modifier = Modifier,
    hostState: SnackbarHostState =
        remember { SnackbarHostState() },
) {
    val state by viewModel.flowState
    var cursorPosition by remember { mutableStateOf(0) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(viewModel) {
        viewModel.onAppear()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            scope.launch { hostState.showSnackbar(it.getMessage()) }
            viewModel.dismissError()
        }
    }

    val textBlock: @Composable (Modifier) -> Unit = { localModifier ->
        TextCorrectionField(
            progress = state.progress,
            matched = state.matched,
            onText = viewModel::onTextChanged,
            onCursor = { cursorPosition = it },
            charLimit = state.maxChars,
            modifier = localModifier,
        )
    }
    val errorSuggestions: @Composable () -> Unit = {
        ErrorSuggestionRow(
            progress = state.progress,
            cursorPosition = cursorPosition,
            matched = state.matched,
            onApplySuggestion = viewModel::applySuggestion,
            onDetail = { viewModel.selectMatchError(it) },
            onSkip = viewModel::skipSuggestion,
        )
    }
    val chipsBlock: @Composable () -> Unit = {
        ActionChips(
            matched = state.matched,
            onPasteText = {
                viewModel.onTextChanged(it)
                viewModel.onCheckRequest()
            },
            onClear = { viewModel.onTextChanged("") },
            onError = {
                scope.launch { hostState.showSnackbar(it.getMessage()) }
            },
            isPicky = state.isPicky,
            onPickyClick = { viewModel.setIsPicky(!state.isPicky) },
            selectedLanguage = state.language?.name,
            onLanguageClick = toLanguage,
            hasPremium = false,
            onPremiumClick = {
                uriHandler.openUri("https://languagetool.org/premium_new")
            },
        )
    }

    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.initializeVosk(context)
    }

    val appBarBlock: @Composable () -> Unit = {
        HomeBottomAppBar(
            progress = state.progress,
            onCheck = { viewModel.onCheckRequest() },



// File: cz/lastaapps/languagetool/ui/features/home/HomeDest.kt

        onRecord = {
            // permissionState.status.isGranted
            if (permissionState.status.isGranted) {
//                Log.d("VOSK_DEBUG", "onRecord 121")
                viewModel.toggleRecognition()
            } else {
//                Log.d("VOSK_DEBUG", "onRecord 125")
                permissionState.launchPermissionRequest()
            }
        },

            onSystemSpellCheck = toSpellCheck,
            onHelpClick = toHelp,
            onSettings = toSettings,
            onAbout = toAbout,
        )
    }

    HomeScreenScaffold(
        snackbarHostState = hostState,
        text = textBlock,
        actionChips = chipsBlock,
        errorSuggestions = errorSuggestions,
        appBar = appBarBlock,
        modifier = modifier,
    )

    state.selectedMatch?.let { error ->
        val dismiss = { viewModel.selectMatchError(null) }
        MatchDetailDialog(
            error = error,
            onApplySuggestion = {
                viewModel.applySuggestion(error, it)
                dismiss()
            },
            onSkip = {
                viewModel.skipSuggestion(error)
                dismiss()
            },
            onDismiss = dismiss,
        )
    }
}

