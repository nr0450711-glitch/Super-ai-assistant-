package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class JarvisVoiceService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var active = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            JarvisServiceBus.setHandsFreeRunning(false, "Microphone permission required")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!active) {
            active = true
            JarvisServiceBus.setHandsFreeRunning(true, "Listening for \"Hey Jarvis\"...")
            scheduleListenCycle(300L)
        }
        return START_STICKY
    }

    private fun scheduleListenCycle(delayMs: Long) {
        if (!active) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ startWakeRecognizer() }, delayMs)
    }

    private fun startWakeRecognizer() {
        if (!active || !SpeechRecognizer.isRecognitionAvailable(this)) {
            JarvisServiceBus.setHandsFreeRunning(false, "Speech recognition unavailable")
            stopSelf()
            return
        }

        runCatching { speechRecognizer?.destroy() }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                JarvisServiceBus.setHandsFreeRunning(true, "Hey Jarvis wake guard active")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (!active) return
                val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 650L
                scheduleListenCycle(delay)
            }

            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (spoken.isNotEmpty()) {
                    handleHeardPhrase(spoken)
                }
                scheduleListenCycle(500L)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        runCatching {
            recognizer.startListening(intent)
        }.onFailure {
            scheduleListenCycle(1500L)
        }
    }

    private fun handleHeardPhrase(phrase: String) {
        val lower = phrase.lowercase()
        val wakePrefixes = listOf("hey jarvis", "ok jarvis", "jarvis", "हे जार्विस", "जार्विस")
        val matchedPrefix = wakePrefixes.firstOrNull { lower.contains(it) }
        if (matchedPrefix != null) {
            val idx = lower.indexOf(matchedPrefix)
            val afterWake = phrase.substring((idx + matchedPrefix.length).coerceAtMost(phrase.length)).trim()
            val command = afterWake.ifBlank { "Jarvis wake ready" }
            JarvisServiceBus.setHandsFreeRunning(true, "Wake detected: \"$command\"")
            JarvisServiceBus.emitWakeCommand(command)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.voice_service_channel_description)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, JarvisVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.voice_service_notification_title))
            .setContentText(getString(R.string.voice_service_notification_text))
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    override fun onDestroy() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
        JarvisServiceBus.setHandsFreeRunning(false, "Hands-free standby")
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_voice_channel"
        private const val NOTIFICATION_ID = 1007
        const val ACTION_STOP = "com.example.action.STOP_VOICE_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, JarvisVoiceService::class.java))
        }
    }
}
