package com.wormhole.browser.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wormhole.browser.R
import com.wormhole.browser.core.ai.ChatHistoryRepository
import com.wormhole.browser.core.ai.agent.AgentAction
import com.wormhole.browser.core.ai.agent.AgentObservation
import com.wormhole.browser.core.ai.agent.IdentifiedObservation
import com.wormhole.browser.core.ai.agent.AgentRunResult
import com.wormhole.browser.core.ai.agent.BrowserAgent
import com.wormhole.browser.core.browser.BrowserViewModel
import com.wormhole.browser.core.browser.Tab
import com.wormhole.browser.ui.theme.WormHoleMotion
import com.wormhole.browser.ui.theme.WormHoleSurface
import com.wormhole.browser.ui.theme.bouncyClickable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class ChatMessage(val id: Long, val role: String, val text: String)

@Composable
fun AiSheet(
    apiKey: String,
    activeTab: Tab?,
    viewModel: BrowserViewModel,
    geckoSessionPool: com.wormhole.browser.core.gecko.GeckoSessionPool,
    onSummarise: () -> Unit,
    onTranslate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ChatHistoryRepository(context) }
    val scope = rememberCoroutineScope()
    val agent = remember(viewModel, geckoSessionPool) { BrowserAgent(viewModel, geckoSessionPool) }

    var conversationId by remember { mutableStateOf<Long?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val actions = remember { mutableStateListOf<IdentifiedObservation>() }
    // Monotonic source for IdentifiedObservation ids -- see that class's doc
    // for why AgentObservation.hashCode() alone isn't safe to key a
    // LazyColumn item on.
    val nextObservationId = remember { java.util.concurrent.atomic.AtomicLong(0L) }

    var input by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var requestJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                input = if (input.isBlank()) spokenText else "$input $spokenText"
            }
        }

    }

    fun launchSpeechRecognizer() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Ask anything…")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {

            android.widget.Toast.makeText(
                context,
                "No speech recognition app found on this device",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchSpeechRecognizer()

    }

    val navigationTools = remember { setOf("open_url", "back", "forward", "reload", "switch_tab") }

    // Bridges BrowserAgent's suspend confirmation callback to a real Compose
    // dialog: the agent coroutine suspends on this deferred until the user
    // taps Allow or Deny. Without this, sensitive tools (execute_js,
    // clear_history) either ran completely unattended or -- with only the
    // fail-safe default -- were silently skipped with no way for the user to
    // ever approve them.
    var pendingConfirmation by remember {
        mutableStateOf<Pair<AgentAction, kotlinx.coroutines.CompletableDeferred<Boolean>>?>(null)
    }
    val onConfirmationNeeded: suspend (AgentAction) -> Boolean = { action ->
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        pendingConfirmation = action to deferred
        deferred.await()
    }

    LaunchedEffect(messages.size, actions.size, working) {
        val last = messages.size + actions.size + if (working) 1 else 0
        if (last > 0) {
            runCatching { listState.animateScrollToItem(last - 1) }
        }
    }

    LaunchedEffect(conversationId) {
        val id = conversationId
        if (id == null) {
            messages.clear()
            return@LaunchedEffect
        }
        repository.messages(id).collect { stored ->
            messages.clear()
            messages += stored.map { ChatMessage(it.id, it.role, it.text) }
        }
    }

    suspend fun ensureConversation(firstMessage: String): Long {
        conversationId?.let { return it }
        val title = firstMessage.take(48).ifBlank { "New conversation" }
        val id = repository.createConversation(title)
        conversationId = id
        return id
    }

    fun send() {
        val text = input.trim()
        if (working) {
            requestJob?.cancel()
            requestJob = null
            working = false
            // Cancelling mid-run can leave a confirmation dialog waiting on a
            // deferred nobody will ever resolve now (the coroutine that was
            // going to await it is gone) -- clear it so "stop" actually
            // dismisses everything instead of leaving a dead dialog on screen.
            pendingConfirmation?.second?.cancel()
            pendingConfirmation = null
            return
        }
        if (text.isEmpty()) return
        input = ""
        working = true
        requestJob = scope.launch {
            try {
                val id = ensureConversation(text)
                repository.addMessage(id, "user", text)
                var closedForNavigation = false
                val result = try {
                    agent.run(
                        apiKey,
                        text,
                        onObservation = { observation ->
                            try {
                                actions += IdentifiedObservation(
                                    id = nextObservationId.getAndIncrement(),
                                    observation = observation,
                                )
                                if (!closedForNavigation &&
                                    observation.result.success &&
                                    observation.action.tool in navigationTools
                                ) {
                                    closedForNavigation = true
                                    onDismiss()
                                }
                            } catch (_: Throwable) {
                            }
                        },
                        onConfirmationNeeded = onConfirmationNeeded,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    AgentRunResult("Something went wrong: ${e.message ?: "unknown error"}")
                }
                val reply = result.answer.ifBlank {
                    if (actions.isNotEmpty()) "Done." else ""
                }
                if (reply.isNotBlank()) {
                    repository.addMessage(id, "assistant", reply)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Keep the sheet alive even if chat persistence fails.
            } finally {
                working = false
                requestJob = null
            }
        }
    }

    var sheetHeightPx by remember { mutableStateOf(0) }
    val offsetFraction = remember { androidx.compose.animation.core.Animatable(1f) }
    val scrimAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val dismissScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, animationSpec = WormHoleMotion.overlay()) }
        offsetFraction.animateTo(0f, animationSpec = WormHoleMotion.chrome())
    }

    fun dismissAnimated() {
        dismissScope.launch {
            launch { scrimAlpha.animateTo(0f, animationSpec = WormHoleMotion.fadeOut()) }
            offsetFraction.animateTo(1f, animationSpec = WormHoleMotion.chrome())
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f * scrimAlpha.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissAnimated() },
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .onGloballyPositioned { sheetHeightPx = it.size.height }
                .offset { IntOffset(x = 0, y = (offsetFraction.value * sheetHeightPx).toInt()) }
                .clip(WormHoleSurface.SheetShape)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, WormHoleSurface.HairlineBorder, WormHoleSurface.SheetShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                    )
                }

                val (siteName, siteHost) = siteIdentity(activeTab)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(siteName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                        Text(siteHost, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (messages.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(WormHoleSurface.Fill)
                                .border(1.dp, WormHoleSurface.HairlineBorder, CircleShape)
                                .bouncyClickable(
                                    contentDescription = "New chat",
                                    onClick = {
                                        requestJob?.cancel()
                                        requestJob = null
                                        working = false
                                        conversationId = null
                                        actions.clear()
                                        input = ""
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.AddComment,
                                contentDescription = "New chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickActionChip(
                        icon = Icons.AutoMirrored.Filled.Notes,
                        label = "Summarise",
                        modifier = Modifier.weight(1f),
                        onClick = onSummarise,
                    )
                    QuickActionChip(
                        icon = Icons.Default.Translate,
                        label = "Translate",
                        modifier = Modifier.weight(1f),
                        onClick = onTranslate,
                    )
                }

                if (messages.isNotEmpty() || actions.isNotEmpty() || working) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(messages, key = { "m${it.id}" }) { message ->
                            val user = message.role == "user"
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (user) MaterialTheme.colorScheme.primary else WormHoleSurface.Fill)
                                        .then(
                                            if (user) Modifier else Modifier.border(1.dp, WormHoleSurface.HairlineBorder, RoundedCornerShape(18.dp)),
                                        ),
                                ) {
                                    Text(
                                        message.text,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        color = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        items(actions, key = { it.id }) { identified ->
                            AgentActionChip(identified.observation)
                        }
                        if (working) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "Working…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.padding(top = 8.dp))
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(WormHoleSurface.Fill)
                        .border(1.dp, WormHoleSurface.HairlineBorder, RoundedCornerShape(26.dp))
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        if (input.isEmpty()) {
                            Text(
                                "Ask anything…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .bouncyClickable(
                                contentDescription = "Voice input",
                                onClick = {
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO,
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        launchSpeechRecognizer()
                                    } else {
                                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Mic, "Voice input", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    val canSend = working || input.isNotBlank()
                    val actionBg = if (working) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val actionFg = if (working) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(actionBg)
                            .then(
                                if (canSend) {
                                    Modifier.bouncyClickable(
                                        contentDescription = if (working) "Stop" else "Send",
                                        onClick = { send() },
                                    )
                                } else {
                                    Modifier.alpha(0.38f)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (working) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (working) "Stop" else "Send",
                            modifier = Modifier.size(if (working) 18.dp else 20.dp),
                            tint = actionFg,
                        )
                    }
                }
            }
        }

        pendingConfirmation?.let { (action, deferred) ->
            AlertDialog(
                onDismissRequest = {
                    deferred.complete(false)
                    pendingConfirmation = null
                },
                title = { Text("Allow this action?") },
                text = {
                    Text(
                        buildString {
                            append("The assistant wants to run \"${action.tool}\"")
                            if (action.arguments.isNotEmpty()) {
                                append(" with:\n")
                                action.arguments.forEach { (k, v) -> append("$k: $v\n") }
                            } else {
                                append(".")
                            }
                        }.trim(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        deferred.complete(true)
                        pendingConfirmation = null
                    }) { Text("Allow") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        deferred.complete(false)
                        pendingConfirmation = null
                    }) { Text("Deny") }
                },
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(percent = 50))
            .bouncyClickable(onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AgentActionChip(observation: AgentObservation) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(WormHoleSurface.Fill)
            .border(1.dp, WormHoleSurface.HairlineBorder, RoundedCornerShape(percent = 50)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    observation.action.tool,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val summary = observation.result.output.ifBlank { observation.result.error.orEmpty() }
                if (summary.isNotBlank()) {
                    Text(
                        summary.take(140),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun siteIdentity(tab: Tab?): Pair<String, String> {
    if (tab == null || tab.url.isBlank()) return "WormHole AI" to "New conversation"
    val name = tab.title.ifBlank { tab.url }
    val host = runCatching { android.net.Uri.parse(tab.url).host }.getOrNull()?.removePrefix("www.")
        ?: tab.url
    return name to host
}
