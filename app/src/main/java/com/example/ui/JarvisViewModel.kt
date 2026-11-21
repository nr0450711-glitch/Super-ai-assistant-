package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AuditTrailEntity
import com.example.data.local.BirthdayEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.JarvisPreferencesRepository
import com.example.data.local.JarvisRepository
import com.example.data.local.JarvisSettings
import com.example.data.local.ScheduleEntity
import com.example.data.local.TaskEntity
import com.example.data.network.BackendCheckResult
import com.example.data.network.ChatHistoryTurn
import com.example.data.network.JarvisBackendClient
import com.example.service.CapturedNotification
import com.example.service.JarvisServiceBus
import com.example.service.JarvisVoiceService
import com.example.system.BilingualVoiceEngine
import com.example.system.DeviceContact
import com.example.system.DeviceControlHelper
import com.example.system.DeviceTelemetry
import com.example.system.DeviceTelemetryHelper
import com.example.system.InstalledAppInfo
import com.example.system.LocalCommandRouter
import com.example.system.LocalRouteOutcome
import com.example.system.PendingActionConfirmation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = JarvisRepository(JarvisDatabase.getInstance(appContext).jarvisDao())
    private val prefs = JarvisPreferencesRepository(appContext)
    private val backendClient = JarvisBackendClient()
    val telemetryHelper = DeviceTelemetryHelper(appContext)
    val controlHelper = DeviceControlHelper(appContext)
    private val voiceEngine = BilingualVoiceEngine(appContext)

    val settings: StateFlow<JarvisSettings> = prefs.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = JarvisSettings()
    )

    val tasks: StateFlow<List<TaskEntity>> = repository.tasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val schedules: StateFlow<List<ScheduleEntity>> = repository.schedules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val birthdays: StateFlow<List<BirthdayEntity>> = repository.birthdays.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val messages: StateFlow<List<ChatMessageEntity>> = repository.messages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val auditTrail: StateFlow<List<AuditTrailEntity>> = repository.auditTrail.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val rmsLevel: StateFlow<Float> = voiceEngine.rmsLevel
    val activeVoiceLabel: StateFlow<String> = voiceEngine.activeVoiceLabel

    val isHandsFreeRunning: StateFlow<Boolean> = JarvisServiceBus.isHandsFreeRunning
    val wakeStatus: StateFlow<String> = JarvisServiceBus.wakeStatus
    val capturedNotifications: StateFlow<List<CapturedNotification>> = JarvisServiceBus.capturedNotifications
    val isAccessibilityConnected: StateFlow<Boolean> = JarvisServiceBus.isAccessibilityConnected

    private val _telemetry = MutableStateFlow(telemetryHelper.readTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingActionConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingActionConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _contacts = MutableStateFlow<List<DeviceContact>>(emptyList())
    val contacts: StateFlow<List<DeviceContact>> = _contacts.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _socialPhotoUri = MutableStateFlow<Uri?>(null)
    val socialPhotoUri: StateFlow<Uri?> = _socialPhotoUri.asStateFlow()

    private val _socialCaption = MutableStateFlow("")
    val socialCaption: StateFlow<String> = _socialCaption.asStateFlow()

    private val _isPolishingCaption = MutableStateFlow(false)
    val isPolishingCaption: StateFlow<Boolean> = _isPolishingCaption.asStateFlow()

    private val _backendCheckResult = MutableStateFlow<BackendCheckResult?>(null)
    val backendCheckResult: StateFlow<BackendCheckResult?> = _backendCheckResult.asStateFlow()

    private val _isTestingBackend = MutableStateFlow(false)
    val isTestingBackend: StateFlow<Boolean> = _isTestingBackend.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        refreshTelemetry()
        refreshInstalledApps()
        refreshContacts("")
        viewModelScope.launch {
            JarvisServiceBus.wakeCommands.collect { command ->
                if (command == "Jarvis wake ready") {
                    val hindi = settings.value.preferredLanguage == "hi-IN"
                    val greeting = if (hindi) "जी, मैं सुन रही हूँ। बताइए क्या करना है?" else "Yes, I am listening. How can I assist?"
                    speakIfEnabled(greeting)
                } else {
                    submitCommand(command)
                }
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun refreshTelemetry() {
        _telemetry.value = telemetryHelper.readTelemetry()
    }

    fun setStreamVolume(streamType: Int, value: Int) {
        telemetryHelper.setStreamVolume(streamType, value)
        refreshTelemetry()
    }

    fun refreshContacts(query: String) {
        viewModelScope.launch {
            _contacts.value = controlHelper.searchContacts(query)
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = controlHelper.getLaunchableApps()
        }
    }

    fun startVoiceCapture(languageOverride: String? = null) {
        val lang = languageOverride ?: when (settings.value.preferredLanguage) {
            "hi-IN" -> "hi-IN"
            else -> "en-IN"
        }
        _partialTranscript.value = ""
        voiceEngine.startListening(
            languageTag = lang,
            onPartialResult = { partial -> _partialTranscript.value = partial },
            onFinalResult = { finalText ->
                _partialTranscript.value = ""
                submitCommand(finalText)
            },
            onError = { errorMsg ->
                _partialTranscript.value = ""
                _statusBanner.value = errorMsg
            }
        )
    }

    fun stopVoiceCapture() {
        voiceEngine.stopListening()
        _partialTranscript.value = ""
    }

    fun toggleHandsFreeService(enable: Boolean) {
        viewModelScope.launch {
            prefs.setHandsFreeWakeEnabled(enable)
            if (enable) {
                JarvisVoiceService.start(appContext)
                repository.logAudit("Voice Wake", "Enabled Hey JARVIS foreground listener")
            } else {
                JarvisVoiceService.stop(appContext)
                repository.logAudit("Voice Wake", "Stopped Hey JARVIS foreground listener")
            }
        }
    }

    fun speakIfEnabled(text: String) {
        val current = settings.value
        if (current.ttsEnabled) {
            voiceEngine.speak(text, current.preferredLanguage, current.ttsPitch, current.ttsRate)
        }
    }

    fun testFemaleVoice(hindi: Boolean) {
        val current = settings.value
        val sample = if (hindi) {
            "नमस्ते, मैं जार्विस वीआईपी हूँ। आपकी आवाज़ और सभी डिवाइस कंट्रोल्स तैयार हैं।"
        } else {
            "Hello, I am Jarvis VIP. Your bilingual voice core and phone controls are online."
        }
        voiceEngine.speak(sample, if (hindi) "hi-IN" else "en-IN", current.ttsPitch, current.ttsRate)
    }

    fun stopSpeaking() = voiceEngine.stopSpeaking()

    fun submitCommand(rawInput: String) {
        val text = rawInput.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            refreshTelemetry()
            val currentSettings = settings.value
            val hindi = currentSettings.preferredLanguage == "hi-IN" || LocalCommandRouter.detectHindiIntent(text)
            val langTag = if (hindi) "hi-IN" else "en-IN"

            if (currentSettings.saveConversationLogs) {
                repository.appendMessage("user", text, langTag, "User")
            }

            // Ensure contacts loaded if user asks for call/message
            if (_contacts.value.isEmpty() &&
                (text.contains("call", true) || text.contains("whatsapp", true) || text.contains("sms", true))
            ) {
                _contacts.value = controlHelper.searchContacts("")
            }

            when (
                val outcome = LocalCommandRouter.route(
                    input = text,
                    telemetry = _telemetry.value,
                    telemetryHelper = telemetryHelper,
                    contacts = _contacts.value,
                    apps = _installedApps.value,
                    preferredLanguage = currentSettings.preferredLanguage
                )
            ) {
                is LocalRouteOutcome.Handled -> {
                    if (outcome.createdTaskTitle != null) {
                        repository.addTask(
                            title = outcome.createdTaskTitle,
                            isReminder = outcome.isReminder
                        )
                        repository.logAudit(
                            if (outcome.isReminder) "Reminder" else "Task",
                            "Added: ${outcome.createdTaskTitle}"
                        )
                    }
                    if (outcome.confirmation != null) {
                        if (currentSettings.confirmBeforeComms) {
                            _pendingConfirmation.value = outcome.confirmation
                        } else {
                            executeConfirmation(outcome.confirmation)
                        }
                    } else {
                        repository.logAudit("Voice/Text Core", text.take(80))
                    }
                    if (currentSettings.saveConversationLogs) {
                        repository.appendMessage("assistant", outcome.reply, langTag, "Local Core")
                    }
                    speakIfEnabled(outcome.reply)
                }

                LocalRouteOutcome.ForwardToBackend -> {
                    querySecureBackend(text, langTag, hindi, currentSettings)
                }
            }
        }
    }

    private suspend fun querySecureBackend(
        prompt: String,
        langTag: String,
        hindi: Boolean,
        currentSettings: JarvisSettings
    ) {
        if (!backendClient.isValidHttpsUrl(currentSettings.backendBaseUrl)) {
            val guidance = if (hindi) {
                "लोकल कमांड्स (कॉल, अलार्म, टाइमर, टास्क, बैटरी/RAM, ऐप लॉन्च) बिना सर्वर के काम करते हैं। ChatGPT AI उत्तरों के लिए Settings > OpenAI ChatGPT Secure Backend में अपना HTTPS /backend URL जोड़ें।"
            } else {
                "Local commands (telemetry, tasks, reminders, alarms, calls, SMS/WhatsApp, app launcher) work immediately on-device. For ChatGPT AI replies, paste your HTTPS /backend URL in Settings > OpenAI ChatGPT Secure Backend."
            }
            if (currentSettings.saveConversationLogs) {
                repository.appendMessage("assistant", guidance, langTag, "Local Core")
            }
            speakIfEnabled(guidance)
            return
        }

        _isThinking.value = true
        val history = messages.value.takeLast(8).map {
            ChatHistoryTurn(role = it.role, content = it.content)
        }
        val result = backendClient.sendChat(
            baseUrl = currentSettings.backendBaseUrl,
            bearerOrSharedToken = currentSettings.backendSharedToken,
            message = prompt,
            history = history,
            language = currentSettings.preferredLanguage,
            telemetry = _telemetry.value
        )
        _isThinking.value = false

        result.onSuccess { res ->
            val reply = res.reply.orEmpty().ifBlank { "Done." }
            if (currentSettings.saveConversationLogs) {
                repository.appendMessage("assistant", reply, langTag, "ChatGPT Proxy")
            }
            repository.logAudit("ChatGPT Proxy", "Answered query via ${res.model ?: "OpenAI"}")
            speakIfEnabled(reply)
        }.onFailure { err ->
            val errorText = "Backend proxy error: ${err.localizedMessage ?: "Unable to connect"}"
            if (currentSettings.saveConversationLogs) {
                repository.appendMessage("assistant", errorText, langTag, "Backend Error")
            }
            repository.logAudit("ChatGPT Proxy", errorText, status = "Failed")
            _statusBanner.value = errorText
        }
    }

    fun requestConfirmation(action: PendingActionConfirmation) {
        if (settings.value.confirmBeforeComms) {
            _pendingConfirmation.value = action
        } else {
            executeConfirmation(action)
        }
    }

    fun dismissConfirmation() {
        _pendingConfirmation.value = null
    }

    fun executeConfirmation(action: PendingActionConfirmation) {
        _pendingConfirmation.value = null
        viewModelScope.launch {
            val (type, summary, ok) = when (action) {
                is PendingActionConfirmation.CallContact -> Triple(
                    "Phone Call",
                    "Dialed ${action.name} (${action.phoneNumber})",
                    controlHelper.placeConfirmedCall(action.phoneNumber)
                )
                is PendingActionConfirmation.SmsDraft -> Triple(
                    "SMS Draft",
                    "Opened SMS draft to ${action.name}",
                    controlHelper.openConfirmedSmsDraft(action.phoneNumber, action.body)
                )
                is PendingActionConfirmation.WhatsAppDraft -> Triple(
                    "WhatsApp Draft",
                    "Opened WhatsApp draft to ${action.name}",
                    controlHelper.openConfirmedWhatsAppDraft(action.phoneNumber, action.body)
                )
                is PendingActionConfirmation.SetAlarm -> Triple(
                    "Alarm",
                    "Alarm ${action.hour}:${action.minute.toString().padStart(2, '0')} (${action.label})",
                    controlHelper.setAlarm(action.hour, action.minute, action.label)
                )
                is PendingActionConfirmation.SetTimer -> Triple(
                    "Timer",
                    "Timer ${action.seconds}s (${action.label})",
                    controlHelper.setTimer(action.seconds, action.label)
                )
                is PendingActionConfirmation.CalendarEvent -> Triple(
                    "Calendar",
                    "Calendar event: ${action.title}",
                    controlHelper.addCalendarEvent(action.title)
                )
                is PendingActionConfirmation.LaunchApp -> Triple(
                    "App Launch",
                    "Launched ${action.label}",
                    controlHelper.launchApp(action.packageName)
                )
            }
            repository.logAudit(type, summary, if (ok) "Executed" else "Unavailable")
            if (!ok) {
                _statusBanner.value = "No compatible system app found for $type."
            }
        }
    }

    // Social Studio
    fun setSocialPhoto(uri: Uri?) {
        _socialPhotoUri.value = uri
    }

    fun setSocialCaption(caption: String) {
        _socialCaption.value = caption
    }

    fun applySocialPreset(hindi: Boolean) {
        _socialCaption.value = if (hindi) {
            "नए विचार, नई ऊर्जा! ✨ आज के पल और आगे का सफर। #JarvisVIP #Motivation #India"
        } else {
            "Focused on what matters next. Building momentum every single day! ✨ #JarvisVIP #Productivity"
        }
    }

    fun polishSocialCaptionWithBackend() {
        val base = _socialCaption.value.trim().ifBlank { "Sharing a special update from my day!" }
        val currentSettings = settings.value
        if (!backendClient.isValidHttpsUrl(currentSettings.backendBaseUrl)) {
            _socialCaption.value = "$base\n\n#JarvisVIP #Highlights #DailyWins"
            _statusBanner.value = "Applied instant caption polish (configure HTTPS /backend in Settings for ChatGPT polishing)."
            return
        }
        viewModelScope.launch {
            _isPolishingCaption.value = true
            val result = backendClient.sendChat(
                baseUrl = currentSettings.backendBaseUrl,
                bearerOrSharedToken = currentSettings.backendSharedToken,
                message = "Rewrite this Facebook post caption to be engaging, warm, and crisp with 3 relevant hashtags. Return only the caption text: \"$base\"",
                history = emptyList(),
                language = currentSettings.preferredLanguage,
                telemetry = _telemetry.value
            )
            _isPolishingCaption.value = false
            result.onSuccess { res ->
                res.reply?.takeIf { it.isNotBlank() }?.let { _socialCaption.value = it }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Caption polish failed"
            }
        }
    }

    fun confirmSocialShare() {
        viewModelScope.launch {
            val ok = controlHelper.shareToFacebookOrSheet(_socialPhotoUri.value, _socialCaption.value)
            repository.logAudit(
                "Social Studio",
                "Shared Facebook Studio post (${if (_socialPhotoUri.value != null) "Photo + Caption" else "Caption"})",
                if (ok) "Executed" else "Failed"
            )
            if (!ok) _statusBanner.value = "Unable to open share sheet."
        }
    }

    // Planner CRUD
    fun addTask(title: String, notes: String, dueLabel: String, priority: String, isReminder: Boolean) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addTask(title, notes, dueLabel, priority, isReminder)
            repository.logAudit(if (isReminder) "Reminder" else "Task", "Created: ${title.trim()}")
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.setTaskCompleted(task.id, !task.completed)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch { repository.deleteTask(id) }
    }

    fun addSchedule(timeSlot: String, title: String, category: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addSchedule(timeSlot.ifBlank { "09:00" }, title, category.ifBlank { "Focus" })
            repository.logAudit("Schedule", "Added $timeSlot • ${title.trim()}")
        }
    }

    fun toggleSchedule(item: ScheduleEntity) {
        viewModelScope.launch { repository.setScheduleCompleted(item.id, !item.completed) }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch { repository.deleteSchedule(id) }
    }

    fun addBirthday(name: String, month: Int, day: Int, contactInfo: String, note: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addBirthday(name, month, day, contactInfo, note)
            repository.logAudit("Birthday", "Saved birthday for ${name.trim()} ($day/$month)")
        }
    }

    fun deleteBirthday(id: Long) {
        viewModelScope.launch { repository.deleteBirthday(id) }
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversation()
            repository.logAudit("Conversation", "Cleared conversation log")
        }
    }

    fun clearAuditTrail() {
        viewModelScope.launch { repository.clearAuditTrail() }
    }

    // Settings & Backend
    fun saveBackendConfig(url: String, token: String, model: String) {
        viewModelScope.launch {
            prefs.updateBackendConfig(url, token, model)
            _statusBanner.value = "Backend configuration saved."
            repository.logAudit("Settings", "Updated secure backend endpoint configuration")
        }
    }

    fun testBackendConnection(url: String) {
        viewModelScope.launch {
            _isTestingBackend.value = true
            _backendCheckResult.value = backendClient.checkHealth(url)
            _isTestingBackend.value = false
        }
    }

    fun setPreferredLanguage(lang: String) {
        viewModelScope.launch { prefs.setPreferredLanguage(lang) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setTtsEnabled(enabled)
            if (!enabled) voiceEngine.stopSpeaking()
        }
    }

    fun setTtsVoiceTuning(pitch: Float, rate: Float) {
        viewModelScope.launch { prefs.setTtsVoiceTuning(pitch, rate) }
    }

    fun setConfirmBeforeComms(confirm: Boolean) {
        viewModelScope.launch { prefs.setConfirmBeforeComms(confirm) }
    }

    fun setSaveConversationLogs(save: Boolean) {
        viewModelScope.launch { prefs.setSaveConversationLogs(save) }
    }

    fun setForceDarkHud(dark: Boolean) {
        viewModelScope.launch { prefs.setForceDarkHud(dark) }
    }

    fun triggerAccessibilityAction(action: Int) {
        if (!isAccessibilityConnected.value) {
            _statusBanner.value = "Enable Jarvis VIP Quick Navigation in Accessibility Settings first."
            return
        }
        JarvisServiceBus.requestGlobalAction(action)
        viewModelScope.launch {
            repository.logAudit("Accessibility", "Executed global action #$action")
        }
    }

    fun clearCapturedNotifications() {
        JarvisServiceBus.clearNotifications()
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
