package com.example.system

import java.util.Locale

sealed interface PendingActionConfirmation {
    data class CallContact(val name: String, val phoneNumber: String) : PendingActionConfirmation
    data class SmsDraft(val name: String, val phoneNumber: String, val body: String) : PendingActionConfirmation
    data class WhatsAppDraft(val name: String, val phoneNumber: String, val body: String) : PendingActionConfirmation
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : PendingActionConfirmation
    data class SetTimer(val seconds: Int, val label: String) : PendingActionConfirmation
    data class CalendarEvent(val title: String) : PendingActionConfirmation
    data class LaunchApp(val label: String, val packageName: String) : PendingActionConfirmation
}

sealed interface LocalRouteOutcome {
    data class Handled(
        val reply: String,
        val confirmation: PendingActionConfirmation? = null,
        val createdTaskTitle: String? = null,
        val isReminder: Boolean = false
    ) : LocalRouteOutcome

    data object ForwardToBackend : LocalRouteOutcome
}

object LocalCommandRouter {

    fun detectHindiIntent(text: String): Boolean {
        if (text.any { it in '\u0900'..'\u097F' }) return true
        val lower = text.lowercase(Locale.ROOT)
        val hindiKeywords = listOf(
            "mera", "meri", "dikhao", "kholo", "batao", "karo", "jodo",
            "lagao", "bhej", "samay", "battery kitni", "namaste", "shukriya"
        )
        return hindiKeywords.any { lower.contains(it) }
    }

    fun route(
        input: String,
        telemetry: DeviceTelemetry,
        telemetryHelper: DeviceTelemetryHelper,
        contacts: List<DeviceContact>,
        apps: List<InstalledAppInfo>,
        preferredLanguage: String
    ): LocalRouteOutcome {
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        val hindi = preferredLanguage == "hi-IN" || detectHindiIntent(trimmed)

        // 1. Live System Telemetry
        if (lower.contains("battery") || lower.contains("ram") || lower.contains("storage") ||
            lower.contains("telemetry") || lower.contains("बैटरी") || lower.contains("स्टोरेज")
        ) {
            return LocalRouteOutcome.Handled(
                reply = telemetryHelper.formatTelemetrySummary(telemetry, hindi)
            )
        }

        // 2. Add Task / Smart Reminder
        val taskPrefixes = listOf("add task ", "remind me to ", "remind me ", "reminder ", "टास्क ", "रिमाइंडर ")
        for (prefix in taskPrefixes) {
            if (lower.startsWith(prefix)) {
                val title = trimmed.substring(prefix.length).trim()
                if (title.isNotEmpty()) {
                    val isReminder = prefix.contains("remind") || prefix.contains("रिमाइंडर")
                    val reply = if (hindi) {
                        "मैंने \"$title\" को आपके लोकल प्लानर में सेव कर दिया है।"
                    } else {
                        "Saved \"$title\" to your Planner ${if (isReminder) "reminders" else "tasks"}."
                    }
                    return LocalRouteOutcome.Handled(
                        reply = reply,
                        createdTaskTitle = title,
                        isReminder = isReminder
                    )
                }
            }
        }

        // 3. Alarm
        if (lower.contains("alarm") || lower.contains("अलार्म")) {
            val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(lower)
            var hour = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 7
            val minute = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            val amPm = timeMatch?.groupValues?.getOrNull(3)?.lowercase(Locale.ROOT).orEmpty()
            if (amPm == "pm" && hour in 1..11) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            val label = "Jarvis VIP Alarm"
            val formatted = String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            val reply = if (hindi) {
                "$formatted का अलार्म सेट करने के लिए नीचे कन्फर्म करें।"
            } else {
                "Prepared alarm for $formatted. Please confirm below to open the Clock alarm."
            }
            return LocalRouteOutcome.Handled(
                reply = reply,
                confirmation = PendingActionConfirmation.SetAlarm(hour.coerceIn(0, 23), minute.coerceIn(0, 59), label)
            )
        }

        // 4. Timer
        if (lower.contains("timer") || lower.contains("टाइमर")) {
            val numMatch = Regex("(\\d+)\\s*(min|minute|minutes|sec|second|seconds|मिनट|सेकंड)?", RegexOption.IGNORE_CASE).find(lower)
            val count = numMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            val unit = numMatch?.groupValues?.getOrNull(2)?.lowercase(Locale.ROOT).orEmpty()
            val seconds = if (unit.startsWith("sec") || unit.contains("सेकंड")) count else count * 60
            val reply = if (hindi) {
                "$seconds सेकंड का टाइमर तैयार है। शुरू करने के लिए कन्फर्म करें।"
            } else {
                "Prepared a timer for ${if (seconds >= 60) "${seconds / 60} min" else "$seconds sec"}. Confirm below to start."
            }
            return LocalRouteOutcome.Handled(
                reply = reply,
                confirmation = PendingActionConfirmation.SetTimer(seconds, "Jarvis VIP Timer")
            )
        }

        // 5. Calendar Event
        if (lower.startsWith("schedule ") || lower.contains("calendar") || lower.contains("कैलेंडर")) {
            val title = trimmed
                .replace(Regex("^(schedule|add calendar event|calendar)\\s*", RegexOption.IGNORE_CASE), "")
                .ifBlank { "Jarvis VIP Meeting" }
            val reply = if (hindi) {
                "कैलेंडर इवेंट \"$title\" जोड़ने के लिए कन्फर्म करें।"
            } else {
                "Ready to create calendar event \"$title\". Confirm below to open Calendar."
            }
            return LocalRouteOutcome.Handled(
                reply = reply,
                confirmation = PendingActionConfirmation.CalendarEvent(title)
            )
        }

        // 6. Confirmed Phone Call
        if (lower.startsWith("call ") || lower.startsWith("कॉल ")) {
            val target = trimmed.substringAfter(" ").trim()
            val match = contacts.firstOrNull {
                it.displayName.contains(target, ignoreCase = true) ||
                    it.phoneNumber.replace(" ", "").contains(target)
            }
            return if (match != null) {
                val reply = if (hindi) {
                    "${match.displayName} (${match.phoneNumber}) को कॉल करने की पुष्टि करें।"
                } else {
                    "Found ${match.displayName} (${match.phoneNumber}). Confirm below to place the call."
                }
                LocalRouteOutcome.Handled(
                    reply = reply,
                    confirmation = PendingActionConfirmation.CallContact(match.displayName, match.phoneNumber)
                )
            } else if (target.any { it.isDigit() }) {
                LocalRouteOutcome.Handled(
                    reply = if (hindi) "$target पर कॉल करने के लिए कन्फर्म करें।" else "Confirm below to dial $target.",
                    confirmation = PendingActionConfirmation.CallContact(target, target)
                )
            } else {
                LocalRouteOutcome.Handled(
                    reply = if (hindi) {
                        "\"$target\" कॉन्टैक्ट्स में नहीं मिला। कृपया Control टैब में Contacts अनुमति दें या नंबर लिखें।"
                    } else {
                        "I couldn't find \"$target\" in your contacts. Grant Contacts access in Control or include the phone number."
                    }
                )
            }
        }

        // 7. Confirmed WhatsApp / SMS Draft
        if (lower.startsWith("whatsapp ") || lower.startsWith("sms ") || lower.startsWith("text ")) {
            val isWa = lower.startsWith("whatsapp ")
            val rest = trimmed.substringAfter(" ").trim()
            val parts = rest.split(":", "-", limit = 2)
            val targetName = parts[0].trim()
            val body = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "Hi from Jarvis VIP!" }
            val match = contacts.firstOrNull { it.displayName.contains(targetName, ignoreCase = true) }
            val number = match?.phoneNumber ?: targetName.filter { it.isDigit() || it == '+' }
            val label = match?.displayName ?: targetName
            val confirmation = if (isWa) {
                PendingActionConfirmation.WhatsAppDraft(label, number, body)
            } else {
                PendingActionConfirmation.SmsDraft(label, number, body)
            }
            val channel = if (isWa) "WhatsApp" else "SMS"
            return LocalRouteOutcome.Handled(
                reply = if (hindi) {
                    "$label के लिए $channel ड्राफ्ट तैयार है। भेजने से पहले कन्फर्म करें।"
                } else {
                    "Prepared $channel draft for $label. Confirm below to open the draft."
                },
                confirmation = confirmation
            )
        }

        // 8. Open App
        if (lower.startsWith("open ") || lower.endsWith("खोलो")) {
            val query = trimmed
                .removePrefix("open ").removePrefix("Open ")
                .removeSuffix("खोलो").trim()
            val app = apps.firstOrNull { it.label.contains(query, ignoreCase = true) }
            if (app != null) {
                return LocalRouteOutcome.Handled(
                    reply = if (hindi) "${app.label} खोलने के लिए कन्फर्म करें।" else "Ready to launch ${app.label}. Confirm below.",
                    confirmation = PendingActionConfirmation.LaunchApp(app.label, app.packageName)
                )
            }
        }

        return LocalRouteOutcome.ForwardToBackend
    }
}
