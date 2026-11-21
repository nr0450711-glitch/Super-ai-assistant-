package com.example.ui.screens

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.system.PendingActionConfirmation
import com.example.ui.JarvisViewModel
import com.example.ui.components.ActionConfirmationCard
import com.example.ui.components.HudCard
import com.example.ui.components.HudSectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.HudEmerald
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PhoneControlScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val capturedNotifications by viewModel.capturedNotifications.collectAsStateWithLifecycle()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()

    var contactQuery by remember { mutableStateOf("") }
    var appQuery by remember { mutableStateOf("") }
    var hasContactsPermission by remember {
        mutableStateOf(viewModel.controlHelper.hasPermission(Manifest.permission.READ_CONTACTS))
    }
    var notifListenerEnabled by remember {
        mutableStateOf(viewModel.controlHelper.isNotificationListenerEnabled())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTelemetry()
                hasContactsPermission = viewModel.controlHelper.hasPermission(Manifest.permission.READ_CONTACTS)
                notifListenerEnabled = viewModel.controlHelper.isNotificationListenerEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        if (granted) {
            viewModel.refreshContacts(contactQuery)
        } else {
            viewModel.showBanner("Contacts permission denied. You can enable it anytime in Settings.")
        }
    }

    val filteredApps = remember(installedApps, appQuery) {
        if (appQuery.isBlank()) installedApps.take(12)
        else installedApps.filter { it.label.contains(appQuery, ignoreCase = true) }.take(12)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        pendingConfirmation?.let { pending ->
            item {
                ActionConfirmationCard(
                    confirmation = pending,
                    onConfirm = { viewModel.executeConfirmation(pending) },
                    onDismiss = { viewModel.dismissConfirmation() }
                )
            }
        }

        // 1. Live System Telemetry
        item {
            HudSectionHeader(
                title = "Live System Telemetry",
                subtitle = "Network: ${telemetry.networkLabel} • Temp ${telemetry.batteryTempC}°C",
                icon = Icons.Default.Memory,
                trailing = {
                    IconButton(
                        onClick = { viewModel.refreshTelemetry() },
                        modifier = Modifier.testTag("refresh_telemetry_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh device telemetry")
                    }
                }
            )
            HudCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TelemetryGauge(
                        icon = Icons.Default.BatteryChargingFull,
                        label = "Battery",
                        value = "${telemetry.batteryPercent}%",
                        progress = telemetry.batteryPercent / 100f,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryGauge(
                        icon = Icons.Default.Memory,
                        label = "RAM Used",
                        value = "${telemetry.ramUsedPercent}%",
                        progress = telemetry.ramUsedPercent / 100f,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryGauge(
                        icon = Icons.Default.Storage,
                        label = "Storage",
                        value = "${String.format(Locale.US, "%.1f", telemetry.storageFreeGb)} GB free",
                        progress = telemetry.storageUsedPercent / 100f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Quick System Shortcuts & Volume Mixer
        item {
            HudSectionHeader(
                title = "System Controls & Shortcuts",
                subtitle = "Alarms, timers, calendar, connectivity & audio",
                icon = Icons.Default.Alarm
            )
            HudCard {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilledTonalButton(
                            onClick = {
                                viewModel.requestConfirmation(PendingActionConfirmation.SetAlarm(7, 0, "Jarvis Morning"))
                            },
                            modifier = Modifier.testTag("shortcut_alarm_button")
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Alarm")
                        }
                    }
                    item {
                        FilledTonalButton(
                            onClick = {
                                viewModel.requestConfirmation(PendingActionConfirmation.SetTimer(300, "Jarvis Focus"))
                            },
                            modifier = Modifier.testTag("shortcut_timer_button")
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("5m Timer")
                        }
                    }
                    item {
                        FilledTonalButton(
                            onClick = {
                                viewModel.requestConfirmation(PendingActionConfirmation.CalendarEvent("Jarvis VIP Event"))
                            },
                            modifier = Modifier.testTag("shortcut_calendar_button")
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Calendar")
                        }
                    }
                    item {
                        OutlinedButton(onClick = { viewModel.controlHelper.openWifiPanel() }) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Wi-Fi")
                        }
                    }
                    item {
                        OutlinedButton(onClick = { viewModel.controlHelper.openBluetoothSettings() }) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bluetooth")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                VolumeRow(
                    label = "Media (${telemetry.mediaVolume}/${telemetry.mediaMaxVolume})",
                    value = telemetry.mediaVolume,
                    max = telemetry.mediaMaxVolume,
                    onChange = { viewModel.setStreamVolume(AudioManager.STREAM_MUSIC, it) }
                )
                VolumeRow(
                    label = "Ring (${telemetry.ringVolume}/${telemetry.ringMaxVolume})",
                    value = telemetry.ringVolume,
                    max = telemetry.ringMaxVolume,
                    onChange = { viewModel.setStreamVolume(AudioManager.STREAM_RING, it) }
                )
                VolumeRow(
                    label = "Alarm (${telemetry.alarmVolume}/${telemetry.alarmMaxVolume})",
                    value = telemetry.alarmVolume,
                    max = telemetry.alarmMaxVolume,
                    onChange = { viewModel.setStreamVolume(AudioManager.STREAM_ALARM, it) }
                )
            }
        }

        // 3. Contacts & Confirmed Comms
        item {
            HudSectionHeader(
                title = "Contacts & Confirmed Comms",
                subtitle = "Calls, SMS & WhatsApp drafts require confirmation",
                icon = Icons.Default.Contacts
            )
            HudCard {
                if (!hasContactsPermission) {
                    Text(
                        text = "Grant opt-in Contacts permission to search your phonebook for confirmed calls, SMS, and WhatsApp drafts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.testTag("grant_contacts_button")
                    ) {
                        Text("Allow Contacts Access")
                    }
                } else {
                    OutlinedTextField(
                        value = contactQuery,
                        onValueChange = {
                            contactQuery = it
                            viewModel.refreshContacts(it)
                        },
                        placeholder = { Text("Search contacts by name or number...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contacts_search_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (contacts.isEmpty()) {
                        Text(
                            text = "No matching contacts found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        contacts.take(6).forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        contact.phoneNumber,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            viewModel.requestConfirmation(
                                                PendingActionConfirmation.CallContact(contact.displayName, contact.phoneNumber)
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "Call ${contact.displayName}", tint = HudEmerald)
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.requestConfirmation(
                                                PendingActionConfirmation.SmsDraft(contact.displayName, contact.phoneNumber, "Hi ${contact.displayName}!")
                                            )
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "SMS ${contact.displayName}", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.requestConfirmation(
                                                PendingActionConfirmation.WhatsAppDraft(contact.displayName, contact.phoneNumber, "Hi ${contact.displayName}!")
                                            )
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = "WhatsApp ${contact.displayName}", tint = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. App Launcher
        item {
            HudSectionHeader(
                title = "App Launcher",
                subtitle = "${installedApps.size} launchable apps indexed",
                icon = Icons.Default.Apps
            )
            HudCard {
                OutlinedTextField(
                    value = appQuery,
                    onValueChange = { appQuery = it },
                    placeholder = { Text("Filter installed apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_launcher_search")
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        FilledTonalButton(
                            onClick = {
                                viewModel.executeConfirmation(
                                    PendingActionConfirmation.LaunchApp(app.label, app.packageName)
                                )
                            }
                        ) {
                            Text(app.label)
                        }
                    }
                }
            }
        }

        // 5. Opt-in Notification Reader & Accessibility Navigation
        item {
            HudSectionHeader(
                title = "Opt-In System Bridges",
                subtitle = "Notification Reader & Accessibility Navigation",
                icon = Icons.Default.NotificationsActive
            )
            HudCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notification Reader", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (notifListenerEnabled) "${capturedNotifications.size} captured" else "Disabled (Opt-in)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        label = if (notifListenerEnabled) "ACTIVE" else "OFF",
                        color = if (notifListenerEnabled) HudEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.controlHelper.openNotificationListenerSettings() },
                        modifier = Modifier.testTag("open_notif_settings_button")
                    ) {
                        Text("Notification Access")
                    }
                    if (capturedNotifications.isNotEmpty()) {
                        OutlinedButton(onClick = { viewModel.clearCapturedNotifications() }) {
                            Text("Clear Feed")
                        }
                    }
                }

                capturedNotifications.take(4).forEach { notif ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${notif.appName}: ${notif.title}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (notif.text.isNotBlank()) {
                        Text(
                            text = notif.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Accessibility Navigation", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Back, Home, Recents & Notification Shade shortcuts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        label = if (isAccessibilityConnected) "CONNECTED" else "OFF",
                        color = if (isAccessibilityConnected) HudEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.controlHelper.openAccessibilitySettings() },
                            modifier = Modifier.testTag("open_accessibility_settings_button")
                        ) {
                            Icon(Icons.Default.AccessibilityNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enable")
                        }
                    }
                    item {
                        FilledTonalButton(
                            onClick = { viewModel.triggerAccessibilityAction(AccessibilityService.GLOBAL_ACTION_BACK) }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Back")
                        }
                    }
                    item {
                        FilledTonalButton(
                            onClick = { viewModel.triggerAccessibilityAction(AccessibilityService.GLOBAL_ACTION_HOME) }
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Home")
                        }
                    }
                    item {
                        FilledTonalButton(
                            onClick = { viewModel.triggerAccessibilityAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
                        ) {
                            Text("Shade")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryGauge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VolumeRow(
    label: String,
    value: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..max.toFloat()
        )
    }
}
