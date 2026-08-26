package com.wormhole.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wormhole.browser.R
import com.wormhole.browser.core.browser.AiRequestState
import com.wormhole.browser.ui.theme.WormHoleSurface
import com.wormhole.browser.ui.theme.bouncyClickable

@Composable
fun AiAnswerScreen(
    query: String,
    state: AiRequestState,
    onBack: () -> Unit,
    onAskFollowUp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Surface(
                    shape = WormHoleSurface.PillShape,
                    color = WormHoleSurface.Fill,
                    border = WormHoleSurface.border(),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAskFollowUp(query) },
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_wormhole_glyph),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            query,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            androidx.compose.animation.Crossfade(
                targetState = state,
                animationSpec = androidx.compose.animation.core.tween(180),
                label = "aiAnswerState",
            ) { s ->
                when (s) {
                    is AiRequestState.Idle -> Unit
                    is AiRequestState.Loading -> AiAnswerLoading()
                    is AiRequestState.Error -> AiAnswerError(s.message)
                    is AiRequestState.Success -> AiAnswerContent(query = query, rawText = s.text)
                }
            }
        }
    }
}

@Composable
private fun AiAnswerLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Text(
                "Thinking…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AiAnswerError(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AiAnswerContent(query: String, rawText: String) {
    val parsed = remember(rawText) { parseAiAnswer(rawText) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = parsed.heading.ifBlank { query },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(20.dp))

        parsed.sections.forEach { section ->
            AiAnswerSectionRow(section)
            Spacer(Modifier.height(18.dp))
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = WormHoleSurface.Fill,
            border = WormHoleSurface.border(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Generated by WormHole AI, not grounded in live search results -- double-check anything important.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AiAnswerSectionRow(section: AiAnswerSection) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(WormHoleSurface.Fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                section.label.firstOrNull()?.uppercaseChar()?.toString() ?: "•",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    append(section.label)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (section.description.isNotBlank()) {
                Text(
                    section.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private data class AiAnswerSection(val label: String, val description: String)
private data class AiAnswer(val heading: String, val sections: List<AiAnswerSection>)

private fun parseAiAnswer(raw: String): AiAnswer {
    val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return AiAnswer(heading = "", sections = emptyList())

    val heading = lines.first().trimStart('#', ' ').trim()
    val bodyLines = lines.drop(1)

    val sections = bodyLines.map { line ->
        val cleaned = line.trimStart('-', '*', '•', ' ')
        val separatorIndex = cleaned.indexOf(" - ").takeIf { it >= 0 }
            ?: cleaned.indexOf(" – ").takeIf { it >= 0 }
            ?: cleaned.indexOf(": ").takeIf { it >= 0 }
        if (separatorIndex != null) {
            val label = cleaned.substring(0, separatorIndex).trim()
            val description = cleaned.substring(separatorIndex + 3).trim()
            AiAnswerSection(label = label, description = description)
        } else {
            AiAnswerSection(label = cleaned, description = "")
        }
    }

    return AiAnswer(heading = heading, sections = sections)
}
