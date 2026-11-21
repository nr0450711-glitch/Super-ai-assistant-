package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.network.BackendCheckResult
import com.example.ui.JarvisViewModel
import com.example.ui.components.HudCard
import com.example.ui.components.HudSectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.HudEmerald
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeVoiceLabel by viewModel.activeVoiceLabel.collectAsStateWithLifecycle()
    val backendCheck by viewModel.backendCheckResult.collectAsStateWithLifecycle()
    val isTestingBackend by viewModel.isTestingBackend.collectAsStateWithLifecycle()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsStateWithLifecycle()

    var backendUrlInput by remember(settings.backendBaseUrl) { mutableStateOf(settings.backendBaseUrl) }
    var sharedTokenInput by remember(settings.backendSharedToken) { mutableStateOf(settings.backendSharedToken) }
    var modelInput by remember(settings.openAiModel) { mutableStateOf(settings.openAiModel) }
    var pitchSlider by remember(settings.ttsPitch) { mutableFloatStateOf(settings.ttsPitch) }
    var rateSlider by remember(settings.ttsRate) { mutableFloatStateOf(settings.ttsRate) }

    LaunchedEffect(settings.backendBaseUrl) {
        if (backendUrlInput.isBlank() && settings.backendBaseUrl.isNotBlank()) {
            backendUrlInput = settings.backendBaseUrl
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.showBanner("Permissions updated.")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Storage & Security Notice
        item {
            HudCard(accentBorder = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("On-Device Persistence Active", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Firebase Cloud Sync was declined during setup, so tasks, schedules, birthdays, chat logs, and audit trails are stored privately on your device in Room. Zero OpenAI API keys are packaged inside this APK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. OpenAI ChatGPT Secure Backend
        item {
            HudSectionHeader(
                title = "OpenAI ChatGPT Secure Backend",
                subtitle = "Connects to /backend (POST /api/jarvis/chat) over HTTPS",
                icon = Icons.Default.SettingsEthernet
            )
            HudCard {
                OutlinedTextField(
                    value = backendUrlInput,
                    onValueChange = { backendUrlInput = it },
                    label = { Text("Deployed HTTPS Backend URL") },
                    placeholder = { Text("https://your-jarvis-backend.run.app") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backend_url_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sharedTokenInput,
                    onValueChange = { sharedTokenInput = it },
                    label = { Text("Bearer / Shared Token (JARVIS_SHARED_TOKEN)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backend_token_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("OpenAI Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveBackendConfig(backendUrlInput, sharedTokenInput, modelInput) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_backend_button")
                    ) {
                        Text("Save Config")
                    }
                    OutlinedButton(
                        onClick = { viewModel.testBackendConnection(backendUrlInput) },
                        enabled = !isTestingBackend,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_backend_button")
                    ) {
                        if (isTestingBackend) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Test /health")
                        }
                    }
                }
                backendCheck?.let { check ->
                    Spacer(Modifier.height(8.dp))
                    val (msg, color) = when (check) {
                        is BackendCheckResult.Ready -> check.details to HudEmerald
                        is BackendCheckResult.Failed -> check.reason to MaterialTheme.colorScheme.error
                    }
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }

        // 3. Bilingual Female AI Voice & Preferences
        item {
            HudSectionHeader(
                title = "Bilingual Female AI Voice",
                subtitle = activeVoiceLabel,
                icon = Icons.Default.RecordVoiceOver
            )
            HudCard {
                Text(
                    "Pitch: ${String.format(Locale.US, "%.2f", pitchSlider)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = pitchSlider,
                    onValueChange = { pitchSlider = it },
                    onValueChangeFinished = { viewModel.setTtsVoiceTuning(pitchSlider, rateSlider) },
                    valueRange = 0.8f..1.3f
                )
                Text(
                    "Speech Rate: ${String.format(Locale.US, "%.2f", rateSlider)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = rateSlider,
                    onValueChange = { rateSlider = it },
                    onValueChangeFinished = { viewModel.setTtsVoiceTuning(pitchSlider, rateSlider) },
                    valueRange = 0.8f..1.3f
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.testFemaleVoice(hindi = false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test EN-IN")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.testFemaleVoice(hindi = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test हिन्दी")
                    }
                }
                Spacer(Modifier.height(10.dp))
                PreferenceSwitchRow(
                    title = "Confirm before Call / SMS / WhatsApp",
                    checked = settings.confirmBeforeComms,
                    onCheckedChange = { viewModel.setConfirmBeforeComms(it) }
                )
                PreferenceSwitchRow(
                    title = "Save conversation history locally",
                    checked = settings.saveConversationLogs,
                    onCheckedChange = { viewModel.setSaveConversationLogs(it) }
                )
                PreferenceSwitchRow(
                    title = "Midnight Obsidian Dark HUD",
                    checked = settings.forceDarkHud,
                    onCheckedChange = { viewModel.setForceDarkHud(it) }
                )
            }
        }

        // 4. Samsung Galaxy A51 Optimization & Permissions
        item {
            HudSectionHeader(
                title = "Galaxy A51 Battery & Permissions",
                subtitle = "Keep Hey JARVIS reliable on Samsung One UI",
                icon = Icons.Default.BatterySaver
            )
            HudCard {
                Text(
                    text = "1. Tap Galaxy A51 Battery Optimization below and set Jarvis VIP to Unrestricted.\n2. In Samsung Device Care > Battery > Background usage limits, ensure Jarvis VIP is not in Sleeping apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.controlHelper.openBatteryOptimizationSettings() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("galaxy_battery_button")
                    ) {
                        Text("Battery Optimization")
                    }
                    OutlinedButton(
                        onClick = { viewModel.controlHelper.openAppDetailsSettings() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("App Info")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Opt-In Permission Status", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                PermissionRow("Microphone", viewModel.controlHelper.hasPermission(Manifest.permission.RECORD_AUDIO))
                PermissionRow("Contacts", viewModel.controlHelper.hasPermission(Manifest.permission.READ_CONTACTS))
                PermissionRow("Direct Call", viewModel.controlHelper.hasPermission(Manifest.permission.CALL_PHONE))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow("Notifications", viewModel.controlHelper.hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                }
                PermissionRow("Notification Reader", viewModel.controlHelper.isNotificationListenerEnabled())
                PermissionRow("Accessibility Nav", isAccessibilityConnected)

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val perms = buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            add(Manifest.permission.READ_CONTACTS)
                            add(Manifest.permission.CALL_PHONE)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.toTypedArray()
                        permLauncher.launch(perms)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request Runtime Permissions")
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        StatusPill(
            label = if (granted) "GRANTED" else "OPT-IN",
            color = if (granted) HudEmerald else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
