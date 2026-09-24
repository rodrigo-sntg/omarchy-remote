package com.sandevsystems.omarchyremote.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Speaking instead of typing (docs/PLANO-V2.md §8): the system's speech recognizer in Portuguese.
 * Its own UI asks for the microphone, so the app needs no permission. Returns a function that starts it.
 */
@Composable
fun rememberDictation(onUnavailable: () -> Unit, onText: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (I18n.lang == I18n.Lang.EN) "en-US" else "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, tr("Fale o texto para o PC", "Say the text for the PC"))
        try {
            launcher.launch(intent)
        } catch (error: ActivityNotFoundException) {
            onUnavailable()
        }
    }
}
