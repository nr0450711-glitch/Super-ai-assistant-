package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.system.PendingActionConfirmation
import com.example.ui.JarvisViewModel
import com.example.ui.components.ActionConfirmationCard
import com.example.ui.components.HudCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.HudEmerald
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PlannerScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val birthdays by viewModel.birthdays.collectAsStateWithLifecycle()
    val auditTrail by viewModel.auditTrail.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        PlannerAddDialog(
            tabIndex = selectedTab,
            onDismiss = { showAddDialog = false },
            onAddTask = { title, due, isReminder ->
                viewModel.addTask(title, "", due, "VIP", isReminder)
                showAddDialog = false
            },
            onAddSchedule = { slot, title, category ->
                viewModel.addSchedule(slot, title, category)
                showAddDialog = false
            },
            onAddBirthday = { name, month, day, contact ->
                viewModel.addBirthday(name, month, day, contact, "")
                showAddDialog = false
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (selectedTab in 0..2) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("planner_add_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add planner entry")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tasks") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Schedule") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Birthdays") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Audit") })
            }

            pendingConfirmation?.let { pending ->
                ActionConfirmationCard(
                    confirmation = pending,
                    onConfirm = { viewModel.executeConfirmation(pending) },
                    onDismiss = { viewModel.dismissConfirmation() },
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        if (tasks.isEmpty()) {
                            item {
                                EmptyPlannerCard(
                                    icon = Icons.Default.Notifications,
                                    title = "No tasks or smart reminders yet",
                                    body = "Tap + to add a task or smart reminder, or say \"Add task ...\" / \"Remind me to ...\" in Core."
                                )
                            }
                        } else {
                            items(tasks, key = { it.id }) { task ->
                                HudCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { viewModel.toggleTask(task) }) {
                                            Icon(
                                                imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = "Toggle completion",
                                                tint = if (task.completed) HudEmerald else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                StatusPill(
                                                    label = if (task.isReminder) "REMINDER" else "TASK",
                                                    color = if (task.isReminder) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                                )
                                                if (task.dueLabel.isNotBlank()) {
                                                    StatusPill(label = task.dueLabel, color = MaterialTheme.colorScheme.tertiary)
                                                }
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete task")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (schedules.isEmpty()) {
                            item {
                                EmptyPlannerCard(
                                    icon = Icons.Default.EventNote,
                                    title = "No daily schedule blocks yet",
                                    body = "Tap + to plan your time blocks for today."
                                )
                            }
                        } else {
                            items(schedules, key = { it.id }) { item ->
                                HudCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = item.completed,
                                            onCheckedChange = { viewModel.toggleSchedule(item) }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${item.timeSlot} • ${item.title}",
                                                style = MaterialTheme.typography.titleSmall,
                                                textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                            Text(
                                                text = item.category,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteSchedule(item.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete schedule")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (birthdays.isEmpty()) {
                            item {
                                EmptyPlannerCard(
                                    icon = Icons.Default.Cake,
                                    title = "No birthday reminders saved",
                                    body = "Tap + to save family and VIP birthdays with one-tap WhatsApp wishes."
                                )
                            }
                        } else {
                            items(birthdays, key = { it.id }) { bday ->
                                val daysLeft = daysUntilBirthday(bday.month, bday.day)
                                HudCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cake,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(bday.personName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = "%02d/%02d • %s".format(
                                                    bday.day,
                                                    bday.month,
                                                    if (daysLeft == 0) "Today!" else "In $daysLeft days"
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (daysLeft == 0) HudEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.requestConfirmation(
                                                    PendingActionConfirmation.WhatsAppDraft(
                                                        name = bday.personName,
                                                        phoneNumber = bday.contactInfo,
                                                        body = "Happy Birthday ${bday.personName}! Wishing you an amazing year ahead!"
                                                    )
                                                )
                                            }
                                        ) {
                                            Text("Wish")
                                        }
                                        IconButton(onClick = { viewModel.deleteBirthday(bday.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete birthday")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        if (auditTrail.isEmpty()) {
                            item {
                                EmptyPlannerCard(
                                    icon = Icons.Default.History,
                                    title = "Automation audit trail is empty",
                                    body = "Confirmed calls, SMS/WhatsApp drafts, alarms, shares, and AI queries appear here."
                                )
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { viewModel.clearAuditTrail() }) {
                                        Text("Clear Audit Log")
                                    }
                                }
                            }
                            items(auditTrail, key = { it.id }) { entry ->
                                val timeStr = remember(entry.timestamp) {
                                    SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                                }
                                HudCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StatusPill(label = entry.actionType.uppercase(), color = MaterialTheme.colorScheme.primary)
                                        StatusPill(
                                            label = entry.status.uppercase(),
                                            color = if (entry.status == "Executed") HudEmerald else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlannerCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    HudCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlannerAddDialog(
    tabIndex: Int,
    onDismiss: () -> Unit,
    onAddTask: (String, String, Boolean) -> Unit,
    onAddSchedule: (String, String, String) -> Unit,
    onAddBirthday: (String, Int, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var extra2 by remember { mutableStateOf("") }
    var isReminder by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (tabIndex) {
                    0 -> "New Task / Smart Reminder"
                    1 -> "New Daily Schedule"
                    else -> "New Birthday Reminder"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tabIndex) {
                    0 -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = extra,
                            onValueChange = { extra = it },
                            label = { Text("Due time (optional, e.g. 6:00 PM)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !isReminder,
                                onClick = { isReminder = false },
                                label = { Text("Task") }
                            )
                            FilterChip(
                                selected = isReminder,
                                onClick = { isReminder = true },
                                label = { Text("Smart Reminder") }
                            )
                        }
                    }

                    1 -> {
                        OutlinedTextField(
                            value = extra,
                            onValueChange = { extra = it },
                            label = { Text("Time slot (e.g. 09:30 AM)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Activity title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = extra2,
                            onValueChange = { extra2 = it },
                            label = { Text("Category (Work / Health / VIP)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    else -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Person name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = extra,
                            onValueChange = { extra = it },
                            label = { Text("Date DD/MM (e.g. 15/08)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = extra2,
                            onValueChange = { extra2 = it },
                            label = { Text("Phone number (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (tabIndex) {
                        0 -> onAddTask(title, extra, isReminder)
                        1 -> onAddSchedule(extra.ifBlank { "09:00" }, title, extra2.ifBlank { "Focus" })
                        else -> {
                            val parts = extra.split("/", "-")
                            val day = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
                            val month = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                            onAddBirthday(title, month, day, extra2)
                        }
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun daysUntilBirthday(month: Int, day: Int): Int {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val next = Calendar.getInstance().apply {
        set(Calendar.YEAR, today.get(Calendar.YEAR))
        set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
        set(Calendar.DAY_OF_MONTH, day.coerceIn(1, 28))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (next.before(today)) {
        next.add(Calendar.YEAR, 1)
    }
    val diffMs = next.timeInMillis - today.timeInMillis
    return (diffMs / (24L * 60L * 60L * 1000L)).toInt()
}
