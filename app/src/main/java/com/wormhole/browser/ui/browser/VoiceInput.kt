package com.wormhole.browser.ui.browser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wormhole.browser.ui.theme.bouncyClickable

@Composable
fun rememberVoiceInput(
    prompt: String = "Speak now",
    onResult: (String) -> Unit,
): () -> Unit {
    val handle = rememberVoiceSearch(prompt = prompt, onResult = onResult)
    return { handle.start() }
}

class VoiceSearchHandle(
    val start: () -> Unit,
    val cancel: () -> Unit,
    val listening: Boolean,
)

@Composable
fun rememberVoiceSearch(
    prompt: String = "Speak now",
    onResult: (String) -> Unit,
): VoiceSearchHandle {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestResult = remember { mutableStateOf(onResult) }
    latestResult.value = onResult
    val cancelledByUser = remember { AtomicBoolean(false) }

    DisposableEffect(recognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
            }
            override fun onBeginningOfSpeech() {
                listening = true
            }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                listening = false
                if (cancelledByUser.getAndSet(false)) return
                val message = speechErrorMessage(error) ?: return
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!spoken.isNullOrBlank()) latestResult.value(spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        recognizer?.setRecognitionListener(listener)
        onDispose {
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            listening = false
        }
    }

    fun startListening() {
        val engine = recognizer
        if (engine == null) {
            Toast.makeText(context, "Voice input isn’t available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        cancelledByUser.set(false)
        listening = true
        mainHandler.post {
            runCatching { engine.startListening(intent) }
        }
    }

    fun cancelListening() {
        cancelledByUser.set(true)
        listening = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.stopListening() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening()
        else Toast.makeText(context, "Microphone permission is needed for voice search", Toast.LENGTH_SHORT).show()
    }

    val start = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    return VoiceSearchHandle(start = start, cancel = { cancelListening() }, listening = listening)
}

@Composable
fun VoiceMicButton(
    onResult: (String) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
) {
    val handle = rememberVoiceSearch(onResult = onResult)
    VoiceMicButton(handle = handle, tint = tint, modifier = modifier, iconSize = iconSize)
}

@Composable
fun VoiceMicButton(
    handle: VoiceSearchHandle,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
) {
    val listening = handle.listening
    val pulse = rememberInfiniteTransition(label = "voicePulse")
    val ringScale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voiceRing",
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voiceRingAlpha",
    )
    val activeTint = tint

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize + 10.dp)
                .bouncyClickable(
                    contentDescription = if (listening) "Listening" else "Voice search",
                    onClick = { if (listening) handle.cancel() else handle.start() },
                ),
        ) {
            if (listening) {
                Box(
                    modifier = Modifier
                        .size(iconSize + 10.dp)
                        .scale(ringScale)
                        .border(1.5.dp, activeTint.copy(alpha = ringAlpha), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(iconSize + 2.dp)
                        .background(activeTint.copy(alpha = 0.16f), CircleShape),
                )
            }
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = activeTint,
                modifier = Modifier.size(iconSize),
            )
        }
        AnimatedVisibility(
            visible = listening,
            enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.7f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.7f),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Cancel voice search",
                tint = activeTint,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(iconSize)
                    .bouncyClickable(contentDescription = "Cancel voice search", onClick = handle.cancel),
            )
        }
    }
}

private fun speechErrorMessage(error: Int): String? = when (error) {
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "Microphone permission is needed for voice search"
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "Voice search needs a network connection"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
        "Voice search is busy. Try again."
    SpeechRecognizer.ERROR_AUDIO ->
        "Couldn’t use the microphone. Try again."
    SpeechRecognizer.ERROR_SERVER ->
        "Voice service is unavailable. Try again."
    else -> "Couldn’t hear that. Try again."
}
