package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.util.Locale

data class DeviceTelemetry(
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val batteryTempC: Float = 0f,
    val ramTotalMb: Long = 0L,
    val ramFreeMb: Long = 0L,
    val ramUsedPercent: Int = 0,
    val storageTotalGb: Float = 0f,
    val storageFreeGb: Float = 0f,
    val storageUsedPercent: Int = 0,
    val networkLabel: String = "Offline",
    val mediaVolume: Int = 0,
    val mediaMaxVolume: Int = 15,
    val ringVolume: Int = 0,
    val ringMaxVolume: Int = 15,
    val alarmVolume: Int = 0,
    val alarmMaxVolume: Int = 15
)

class DeviceTelemetryHelper(private val context: Context) {

    fun readTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val ramTotalMb = memInfo.totalMem / (1024L * 1024L)
        val ramFreeMb = memInfo.availMem / (1024L * 1024L)
        val ramUsedPct = if (ramTotalMb > 0L) (((ramTotalMb - ramFreeMb) * 100L) / ramTotalMb).toInt() else 0

        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.totalBytes
        val freeBytes = statFs.availableBytes
        val gbDivisor = 1024f * 1024f * 1024f
        val totalGb = totalBytes / gbDivisor
        val freeGb = freeBytes / gbDivisor
        val storageUsedPct = if (totalBytes > 0L) (((totalBytes - freeBytes) * 100L) / totalBytes).toInt() else 0

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        val networkLabel = when {
            capabilities == null -> "Offline"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mediaVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val mediaMax = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
        val ringVol = audioManager?.getStreamVolume(AudioManager.STREAM_RING) ?: 0
        val ringMax = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_RING) ?: 7).coerceAtLeast(1)
        val alarmVol = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 0
        val alarmMax = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7).coerceAtLeast(1)

        return DeviceTelemetry(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempC = tempC,
            ramTotalMb = ramTotalMb,
            ramFreeMb = ramFreeMb,
            ramUsedPercent = ramUsedPct,
            storageTotalGb = totalGb,
            storageFreeGb = freeGb,
            storageUsedPercent = storageUsedPct,
            networkLabel = networkLabel,
            mediaVolume = mediaVol,
            mediaMaxVolume = mediaMax,
            ringVolume = ringVol,
            ringMaxVolume = ringMax,
            alarmVolume = alarmVol,
            alarmMaxVolume = alarmMax
        )
    }

    fun setStreamVolume(streamType: Int, value: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = audioManager.getStreamMaxVolume(streamType)
        audioManager.setStreamVolume(streamType, value.coerceIn(0, max), 0)
    }

    fun formatTelemetrySummary(t: DeviceTelemetry, hindi: Boolean): String {
        val freeStorageStr = String.format(Locale.US, "%.1f", t.storageFreeGb)
        return if (hindi) {
            "सिस्टम टेलीमेट्री: बैटरी ${t.batteryPercent}% (${if (t.isCharging) "चार्ज हो रही है" else "बैटरी पर"}), खाली RAM ${t.ramFreeMb} MB, खाली स्टोरेज ${freeStorageStr} GB, नेटवर्क ${t.networkLabel}।"
        } else {
            "Telemetry status: Battery at ${t.batteryPercent}% (${if (t.isCharging) "Charging" else "Discharging"}), ${t.ramFreeMb} MB RAM available, $freeStorageStr GB storage free, Network: ${t.networkLabel}."
        }
    }
}
