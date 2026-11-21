package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_vip_prefs")

data class JarvisSettings(
    val backendBaseUrl: String = "",
    val backendSharedToken: String = "",
    val openAiModel: String = "gpt-4o-mini",
    val preferredLanguage: String = "auto", // "auto", "en-IN", "hi-IN"
    val ttsEnabled: Boolean = true,
    val ttsPitch: Float = 1.08f,
    val ttsRate: Float = 1.0f,
    val handsFreeWakeEnabled: Boolean = false,
    val confirmBeforeComms: Boolean = true,
    val saveConversationLogs: Boolean = true,
    val forceDarkHud: Boolean = true
)

class JarvisPreferencesRepository(private val context: Context) {

    private object Keys {
        val BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        val BACKEND_SHARED_TOKEN = stringPreferencesKey("backend_shared_token")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val HANDS_FREE_WAKE = booleanPreferencesKey("hands_free_wake")
        val CONFIRM_BEFORE_COMMS = booleanPreferencesKey("confirm_before_comms")
        val SAVE_CONVERSATION_LOGS = booleanPreferencesKey("save_conversation_logs")
        val FORCE_DARK_HUD = booleanPreferencesKey("force_dark_hud")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        JarvisSettings(
            backendBaseUrl = prefs[Keys.BACKEND_BASE_URL] ?: "",
            backendSharedToken = prefs[Keys.BACKEND_SHARED_TOKEN] ?: "",
            openAiModel = prefs[Keys.OPENAI_MODEL] ?: "gpt-4o-mini",
            preferredLanguage = prefs[Keys.PREFERRED_LANGUAGE] ?: "auto",
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.08f,
            ttsRate = prefs[Keys.TTS_RATE] ?: 1.0f,
            handsFreeWakeEnabled = prefs[Keys.HANDS_FREE_WAKE] ?: false,
            confirmBeforeComms = prefs[Keys.CONFIRM_BEFORE_COMMS] ?: true,
            saveConversationLogs = prefs[Keys.SAVE_CONVERSATION_LOGS] ?: true,
            forceDarkHud = prefs[Keys.FORCE_DARK_HUD] ?: true
        )
    }

    suspend fun updateBackendConfig(url: String, token: String, model: String) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.BACKEND_BASE_URL] = url.trim().trimEnd('/')
            prefs[Keys.BACKEND_SHARED_TOKEN] = token.trim()
            prefs[Keys.OPENAI_MODEL] = model.trim().ifBlank { "gpt-4o-mini" }
        }
    }

    suspend fun setPreferredLanguage(lang: String) {
        context.jarvisDataStore.edit { it[Keys.PREFERRED_LANGUAGE] = lang }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun setTtsVoiceTuning(pitch: Float, rate: Float) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.TTS_PITCH] = pitch.coerceIn(0.75f, 1.35f)
            prefs[Keys.TTS_RATE] = rate.coerceIn(0.75f, 1.35f)
        }
    }

    suspend fun setHandsFreeWakeEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.HANDS_FREE_WAKE] = enabled }
    }

    suspend fun setConfirmBeforeComms(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.CONFIRM_BEFORE_COMMS] = enabled }
    }

    suspend fun setSaveConversationLogs(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.SAVE_CONVERSATION_LOGS] = enabled }
    }

    suspend fun setForceDarkHud(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.FORCE_DARK_HUD] = enabled }
    }
}
