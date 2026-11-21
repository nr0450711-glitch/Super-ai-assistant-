package com.example.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.service.JarvisNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Calendar

data class DeviceContact(
    val id: String,
    val displayName: String,
    val phoneNumber: String
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

class DeviceControlHelper(private val context: Context) {

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val flat = ComponentName(context, JarvisNotificationListenerService::class.java).flattenToString()
        return enabledListeners.contains(flat)
    }

    fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!enabled) return false
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return settingValue.contains(context.packageName)
    }

    suspend fun searchContacts(query: String, limit: Int = 20): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return@withContext emptyList()
        val contacts = mutableListOf<DeviceContact>()
        val seenNumbers = mutableSetOf<String>()
        val trimmed = query.trim()
        val selection = if (trimmed.isNotEmpty()) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        } else null
        val selectionArgs = if (trimmed.isNotEmpty()) arrayOf("%$trimmed%", "%$trimmed%") else null

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && contacts.size < limit) {
                val id = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: "Unknown"
                val number = cursor.getString(numIdx)?.trim() ?: continue
                val normalized = number.replace("[^0-9+]".toRegex(), "")
                if (normalized.isNotEmpty() && seenNumbers.add(normalized)) {
                    contacts.add(DeviceContact(id = id, displayName = name, phoneNumber = number))
                }
            }
        }
        contacts
    }

    suspend fun getLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
            InstalledAppInfo(label = label, packageName = pkg)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun placeConfirmedCall(phoneNumber: String): Boolean {
        val uri = Uri.parse("tel:${Uri.encode(phoneNumber.trim())}")
        val action = if (hasPermission(Manifest.permission.CALL_PHONE)) {
            Intent.ACTION_CALL
        } else {
            Intent.ACTION_DIAL
        }
        return startNewTaskIntent(Intent(action, uri))
    }

    fun openConfirmedSmsDraft(phoneNumber: String, messageBody: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(phoneNumber.trim())}")
            putExtra("sms_body", messageBody)
        }
        return startNewTaskIntent(intent)
    }

    fun openConfirmedWhatsAppDraft(phoneNumber: String, messageBody: String): Boolean {
        val digits = phoneNumber.replace("[^0-9]".toRegex(), "")
        val encodedText = URLEncoder.encode(messageBody, "UTF-8")
        val url = if (digits.isNotEmpty()) {
            "https://wa.me/$digits?text=$encodedText"
        } else {
            "https://wa.me/?text=$encodedText"
        }
        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.whatsapp")
        }
        if (startNewTaskIntent(waIntent)) return true
        return startNewTaskIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun shareToFacebookOrSheet(imageUri: Uri?, caption: String): Boolean {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            if (imageUri != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, caption)
        }
        val fbPackages = listOf("com.facebook.katana", "com.facebook.lite")
        for (pkg in fbPackages) {
            val targeted = Intent(shareIntent).setPackage(pkg)
            if (targeted.resolveActivity(context.packageManager) != null && startNewTaskIntent(targeted)) {
                return true
            }
        }
        val chooser = Intent.createChooser(shareIntent, "Share via Jarvis Social Studio")
        return startNewTaskIntent(chooser)
    }

    fun setAlarm(hour: Int, minute: Int, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis VIP Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return startNewTaskIntent(intent)
    }

    fun setTimer(seconds: Int, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis VIP Timer" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return startNewTaskIntent(intent)
    }

    fun addCalendarEvent(title: String, description: String = "", startOffsetHours: Int = 1): Boolean {
        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, startOffsetHours) }
        val startMillis = cal.timeInMillis
        val endMillis = startMillis + 60L * 60L * 1000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.ifBlank { "Jarvis VIP Event" })
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        }
        return startNewTaskIntent(intent)
    }

    fun openWifiPanel(): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        return startNewTaskIntent(intent)
    }

    fun openBluetoothSettings(): Boolean = startNewTaskIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openBatteryOptimizationSettings(): Boolean {
        return startNewTaskIntent(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            || openAppDetailsSettings()
    }

    fun openAppDetailsSettings(): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        return startNewTaskIntent(intent)
    }

    fun openNotificationListenerSettings(): Boolean {
        return startNewTaskIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openAccessibilitySettings(): Boolean {
        return startNewTaskIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startNewTaskIntent(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
