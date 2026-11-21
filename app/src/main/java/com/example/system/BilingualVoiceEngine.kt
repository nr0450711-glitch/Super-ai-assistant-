package com.example.system

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class BilingualVoiceEngine(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _activeVoiceLabel = MutableStateFlow("Initializing female TTS...")
    val activeVoiceLabel: StateFlow<String> = _activeVoiceLabel.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                selectFemaleVoiceForLocale(Locale("en", "IN"))
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                _activeVoiceLabel.value = "TTS unavailable on device"
            }
        }
    }

    fun speak(text: String, preferredLanguage: String, pitch: Float, rate: Float) {
        if (!ttsReady || text.isBlank()) return
        val locale = resolveSpeechLocale(text, preferredLanguage)
        selectFemaleVoiceForLocale(locale)
        tts?.setPitch(pitch.coerceIn(0.75f, 1.35f))
        tts?.setSpeechRate(rate.coerceIn(0.75f, 1.35f))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    private fun resolveSpeechLocale(text: String, preferredLanguage: String): Locale {
        val containsDevanagari = text.any { it in '\u0900'..'\u097F' }
        return when {
            preferredLanguage == "hi-IN" || containsDevanagari -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }
    }

    private fun selectFemaleVoiceForLocale(locale: Locale) {
        val engine = tts ?: return
        engine.language = locale
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        val matchingLocaleVoices = voices.filter { voice ->
            voice.locale.language.equals(locale.language, ignoreCase = true)
        }
        val bestFemaleVoice: Voice? = matchingLocaleVoices.firstOrNull { voice ->
            val name = voice.name.lowercase(Locale.ROOT)
            val femaleHint = name.contains("female") ||
                name.contains("-f-") ||
                name.contains("hif") ||
                name.contains("inf") ||
                name.contains("sfg") ||
                name.contains("wavenet-a") ||
                name.contains("wavenet-d") ||
                name.contains("neural2-a") ||
                name.contains("in-x-ene")
            femaleHint && !voice.isNetworkConnectionRequired
        } ?: matchingLocaleVoices.firstOrNull { voice ->
            val name = voice.name.lowercase(Locale.ROOT)
            name.contains("female") || name.contains("hif") || name.contains("inf")
        } ?: matchingLocaleVoices.firstOrNull()

        if (bestFemaleVoice != null) {
            engine.voice = bestFemaleVoice
            _activeVoiceLabel.value = "${bestFemaleVoice.locale.toLanguageTag()} • ${bestFemaleVoice.name.take(24)}"
        } else {
            _activeVoiceLabel.value = "${locale.toLanguageTag()} • Female pitch tuned"
        }
    }

    fun startListening(
        languageTag: String,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service is unavailable on this device.")
            return
        }
        stopSpeaking()
        stopListening()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        val resolvedTag = when (languageTag) {
            "hi-IN" -> "hi-IN"
            else -> "en-IN"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolvedTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, resolvedTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _rmsLevel.value = 0.2f
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                _rmsLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _rmsLevel.value = 0f
            }

            override fun onError(error: Int) {
                _isListening.value = false
                _rmsLevel.value = 0f
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Could not catch that—please try again"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech engine busy"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    else -> "Voice recognition interrupted ($error)"
                }
                onError(msg)
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                _rmsLevel.value = 0f
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    onFinalResult(spoken)
                } else {
                    onError("No voice command recognized.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                    onPartialResult(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        _isListening.value = true
        recognizer.startListening(intent)
    }

    fun stopListening() {
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
        _isListening.value = false
        _rmsLevel.value = 0f
    }

    fun release() {
        stopListening()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
    }
}
