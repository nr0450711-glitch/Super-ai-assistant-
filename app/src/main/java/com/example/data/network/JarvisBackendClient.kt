package com.example.data.network

import com.example.system.DeviceTelemetry
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ChatHistoryTurn(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class DeviceContextPayload(
    val batteryPct: Int,
    val isCharging: Boolean,
    val ramFreeMb: Long
)

@JsonClass(generateAdapter = true)
data class JarvisChatRequest(
    val message: String,
    val history: List<ChatHistoryTurn>,
    val language: String,
    val deviceContext: DeviceContextPayload
)

@JsonClass(generateAdapter = true)
data class JarvisChatResponse(
    val reply: String? = null,
    val model: String? = null,
    val language: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendHealthResponse(
    val ok: Boolean = false,
    val service: String? = null,
    val openAiConfigured: Boolean = false,
    val firebaseAuthEnabled: Boolean = false,
    val sharedTokenEnabled: Boolean = false
)

sealed interface BackendCheckResult {
    data class Ready(val details: String) : BackendCheckResult
    data class Failed(val reason: String) : BackendCheckResult
}

class JarvisBackendClient {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val requestAdapter = moshi.adapter(JarvisChatRequest::class.java)
    private val responseAdapter = moshi.adapter(JarvisChatResponse::class.java)
    private val healthAdapter = moshi.adapter(BackendHealthResponse::class.java)

    fun isValidHttpsUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        return trimmed.startsWith("https://", ignoreCase = true) && trimmed.length > 10
    }

    suspend fun checkHealth(baseUrl: String): BackendCheckResult = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        if (!isValidHttpsUrl(cleanUrl)) {
            return@withContext BackendCheckResult.Failed("Enter a valid HTTPS backend URL (https://...).")
        }
        return@withContext try {
            val request = Request.Builder()
                .url("$cleanUrl/health")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    BackendCheckResult.Failed("HTTP ${response.code}: Unable to reach /health")
                } else {
                    val parsed = runCatching { healthAdapter.fromJson(bodyStr) }.getOrNull()
                    if (parsed?.ok == true) {
                        val aiFlag = if (parsed.openAiConfigured) "OpenAI ready" else "OPENAI_API_KEY unset"
                        BackendCheckResult.Ready("Connected (${parsed.service ?: "backend"}, $aiFlag)")
                    } else {
                        BackendCheckResult.Failed("Unexpected /health response from server.")
                    }
                }
            }
        } catch (e: Exception) {
            BackendCheckResult.Failed(e.localizedMessage ?: "Network error contacting backend.")
        }
    }

    suspend fun sendChat(
        baseUrl: String,
        bearerOrSharedToken: String,
        message: String,
        history: List<ChatHistoryTurn>,
        language: String,
        telemetry: DeviceTelemetry
    ): Result<JarvisChatResponse> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        if (!isValidHttpsUrl(cleanUrl)) {
            return@withContext Result.failure(IllegalArgumentException("HTTPS backend URL is not configured."))
        }
        return@withContext try {
            val payload = JarvisChatRequest(
                message = message,
                history = history.takeLast(10),
                language = language,
                deviceContext = DeviceContextPayload(
                    batteryPct = telemetry.batteryPercent,
                    isCharging = telemetry.isCharging,
                    ramFreeMb = telemetry.ramFreeMb
                )
            )
            val json = requestAdapter.toJson(payload)
            val builder = Request.Builder()
                .url("$cleanUrl/api/jarvis/chat")
                .post(json.toRequestBody(jsonMediaType))
                .addHeader("Content-Type", "application/json")

            val cleanToken = bearerOrSharedToken.trim()
            if (cleanToken.isNotEmpty()) {
                builder.addHeader("Authorization", "Bearer $cleanToken")
                builder.addHeader("X-Jarvis-Token", cleanToken)
            }

            client.newCall(builder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                val parsed = runCatching { responseAdapter.fromJson(rawBody) }.getOrNull()
                if (response.isSuccessful && parsed?.reply != null) {
                    Result.success(parsed)
                } else {
                    val msg = parsed?.error ?: "Backend returned HTTP ${response.code}"
                    Result.failure(IllegalStateException(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
