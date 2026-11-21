package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ChatMessageEntity
import com.example.ui.JarvisViewModel
import com.example.ui.components.ActionConfirmationCard
import com.example.ui.components.CoreVisualState
import com.example.ui.components.HolographicCoreVisualizer
import com.example.ui.components.HudCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.HudEmerald

@Composable
fun JarvisCoreScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val isHandsFreeRunning by viewModel.isHandsFreeRunning.collectAsStateWithLifecycle()
    val wakeStatus by viewModel.wakeStatus.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceCapture()
        } else {
            viewModel.showBanner("Microphone permission is needed for bilingual voice input.")
        }
    }

    val handsFreePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            viewModel.controlHelper.hasPermission(Manifest.permission.RECORD_AUDIO)
        if (micOk) {
            viewModel.toggleHandsFreeService(true)
        } else {
            viewModel.showBanner("Grant Microphone permission to enable Hey JARVIS hands-free mode.")
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val visualState = when {
        isListening -> CoreVisualState.LISTENING
        isThinking -> CoreVisualState.THINKING
        isSpeaking -> CoreVisualState.SPEAKING
        else -> CoreVisualState.IDLE
    }

    val statusText = when {
        isListening -> "Listening (${settings.preferredLanguage})..."
        isThinking -> "Querying secure backend..."
        isSpeaking -> "Speaking (Female AI Voice)..."
        isHandsFreeRunning -> wakeStatus
        else -> "Battery ${telemetry.batteryPercent}% • RAM ${telemetry.ramFreeMb} MB free"
    }

    val quickPrompts = remember {
        listOf(
            "Battery & RAM status",
            "Add task Review notes",
            "Remind me to drink water",
            "Set alarm 7 AM",
            "Timer 5 min",
            "मेरा बैटरी स्टेटस बताओ"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top HUD Status Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    label = if (settings.backendBaseUrl.startsWith("https://")) "PROXY READY" else "LOCAL CORE",
                    color = if (settings.backendBaseUrl.startsWith("https://")) HudEmerald else MaterialTheme.colorScheme.primary
                )
                StatusPill(
                    label = settings.preferredLanguage.uppercase(),
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.setTtsEnabled(!settings.ttsEnabled) },
                    modifier = Modifier.testTag("tts_toggle_button")
                ) {
                    Icon(
                        imageVector = if (settings.ttsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (settings.ttsEnabled) "Mute female voice" else "Enable female voice",
                        tint = if (settings.ttsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (messages.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearConversation() },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear conversation history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Language & Hands-free quick bar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = isHandsFreeRunning,
                    onClick = {
                        if (isHandsFreeRunning) {
                            viewModel.toggleHandsFreeService(false)
                        } else {
                            val needed = buildList {
                                add(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }.toTypedArray()
                            handsFreePermissionsLauncher.launch(needed)
                        }
                    },
                    label = { Text(if (isHandsFreeRunning) "Hey JARVIS ON" else "Hey JARVIS") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("hands_free_chip")
                )
            }
            listOf("auto" to "Auto", "en-IN" to "English (IN)", "hi-IN" to "हिन्दी").forEach { (code, label) ->
                item {
                    FilterChip(
                        selected = settings.preferredLanguage == code,
                        onClick = { viewModel.setPreferredLanguage(code) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("lang_chip_$code")
                    )
                }
            }
        }

        // Animated Holographic Core + Live Waveform
        HolographicCoreVisualizer(
            state = visualState,
            rmsLevel = rmsLevel,
            statusLabel = statusText,
            onCoreClick = {
                if (isListening) {
                    viewModel.stopVoiceCapture()
                } else if (isSpeaking) {
                    viewModel.stopSpeaking()
                } else if (viewModel.controlHelper.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    viewModel.startVoiceCapture()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.padding(vertical = 4.dp)
        )

        AnimatedVisibility(visible = partialTranscript.isNotBlank()) {
            HudCard(
                accentBorder = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = partialTranscript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        AnimatedVisibility(visible = pendingConfirmation != null) {
            pendingConfirmation?.let { pending ->
                ActionConfirmationCard(
                    confirmation = pending,
                    onConfirm = { viewModel.executeConfirmation(pending) },
                    onDismiss = { viewModel.dismissConfirmation() },
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        // Quick bilingual command chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(quickPrompts) { prompt ->
                AssistChip(
                    onClick = { viewModel.submitCommand(prompt) },
                    label = { Text(prompt, style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                )
            }
        }

        // Conversation Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    HudCard {
                        Text(
                            text = "JARVIS VIP Ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the glowing core for Hindi or English voice commands, or type below. Try \"Battery & RAM status\", \"Call Mom\", \"Set alarm 7 AM\", or connect your HTTPS /backend proxy in Settings for full ChatGPT intelligence.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
            }
        }

        // Bottom Command Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask Jarvis (Hindi / English)...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.submitCommand(inputText)
                            inputText = ""
                        }
                    }
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input")
            )

            FilledIconButton(
                onClick = {
                    if (isListening) {
                        viewModel.stopVoiceCapture()
                    } else if (viewModel.controlHelper.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                        viewModel.startVoiceCapture()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isListening) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("voice_mic_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.cd_voice_mic)
                )
            }

            FilledIconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.submitCommand(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !isThinking,
                modifier = Modifier
                    .size(50.dp)
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.cd_send_command)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isUser) "YOU" else "JARVIS • ${message.sourceLabel.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
