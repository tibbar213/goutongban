package cn.goutongban.aac.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

enum class VoiceStatus { CHECKING, READY, MISSING, ERROR }
data class SpeechState(
    val status: VoiceStatus = VoiceStatus.CHECKING,
    val activeId: String? = null,
    val completed: Int = 0,
    val enginePackage: String? = null
)

class BoardSpeech(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(SpeechState())
    val state = mutable.asStateFlow()
    private var engine: TextToSpeech? = null
    private var probeFile: File? = null
    private var deadline: Runnable? = null
    private var attempt = 0
    private var utterance = 0
    private var closed = false
    private var defaultEngine: String? = null

    init { recheck() }

    fun recheck() {
        if (closed) return
        defaultEngine = null
        mutable.value = SpeechState()
        val installed = context.packageManager
            .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { it.serviceInfo.packageName }
            .distinct()
        tryEngine(listOf(null) + installed)
    }

    private fun tryEngine(candidates: List<String?>) {
        val token = ++attempt
        clearDeadline()
        probeFile?.delete()
        probeFile = null
        engine?.shutdown()
        engine = null
        if (candidates.isEmpty()) {
            mutable.value = SpeechState(VoiceStatus.MISSING)
            return
        }
        val candidate = candidates.first()
        val rest = candidates.drop(1)
        if (candidate != null && candidate == defaultEngine) {
            tryEngine(rest)
            return
        }
        val moveOn = { if (token == attempt && !closed) tryEngine(rest) }
        try {
            engine = TextToSpeech(context, { status ->
                main.post {
                    if (token != attempt || closed) return@post
                    val tts = engine ?: return@post
                    if (candidate == null) defaultEngine = tts.defaultEngine
                    if (status == TextToSpeech.SUCCESS) {
                        probe(tts, candidate ?: defaultEngine, token, moveOn)
                    } else moveOn()
                }
            }, candidate)
            setDeadline(6_000, moveOn)
        } catch (_: RuntimeException) {
            moveOn()
        }
    }

    private fun probe(tts: TextToSpeech, enginePackage: String?, token: Int, moveOn: () -> Unit) {
        val probeId = "voice-probe-$token"
        try {
            if (enginePackage == OplusEngine && tts.voice?.isOfflineMandarin() != true) {
                tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            }
            val current = tts.voice?.takeIf { it.isOfflineMandarin() }
            val voice = tts.voices.orEmpty().asSequence()
                .filter { it.isOfflineMandarin() }
                .sortedWith(compareByDescending<Voice> { mandarinRank(it.locale) }
                    .thenByDescending { it.quality })
                .firstOrNull { it == current || tts.setVoice(it) == TextToSpeech.SUCCESS } ?: current
            if (voice == null) {
                moveOn()
                return
            }
            tts.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    main.post {
                        if (token != attempt || closed) return@post
                        if (utteranceId == probeId) {
                            val valid = probeFile?.let { it.length() > 44L } == true
                            probeFile?.delete()
                            probeFile = null
                            clearDeadline()
                            if (valid) mutable.value = SpeechState(VoiceStatus.READY, enginePackage = enginePackage)
                            else moveOn()
                        } else if (mutable.value.status == VoiceStatus.READY && mutable.value.activeId != null) {
                            val state = mutable.value
                            mutable.value = state.copy(
                                activeId = state.activeId.takeUnless { it == utteranceId },
                                completed = state.completed + 1
                            )
                        }
                    }
                }

                @Deprecated("Used by older TTS engines")
                override fun onError(utteranceId: String) = onError(utteranceId, TextToSpeech.ERROR)

                override fun onError(utteranceId: String, errorCode: Int) {
                    main.post {
                        if (token != attempt || closed) return@post
                        if (utteranceId == probeId) moveOn()
                        else if (utteranceId == mutable.value.activeId) {
                            val unavailable = errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET ||
                                errorCode == TextToSpeech.ERROR_NETWORK ||
                                errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT
                            mutable.value = SpeechState(if (unavailable) VoiceStatus.MISSING else VoiceStatus.ERROR)
                        }
                    }
                }
            })
            val file = File.createTempFile("voice-probe-", ".wav", context.cacheDir)
            probeFile = file
            setDeadline(8_000, moveOn)
            if (tts.synthesizeToFile("沟通板", Bundle(), file, probeId) != TextToSpeech.SUCCESS) moveOn()
        } catch (_: Exception) {
            moveOn()
        }
    }

    fun speak(text: String, speed: Float, volume: Float, enqueue: Boolean = false) {
        val tts = engine ?: return
        if (mutable.value.status != VoiceStatus.READY) return
        val id = "board-${++utterance}"
        mutable.value = mutable.value.copy(activeId = id)
        try {
            tts.setSpeechRate(speed)
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
            val queue = if (enqueue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
            if (tts.speak(text, queue, params, id) != TextToSpeech.SUCCESS) {
                mutable.value = SpeechState(VoiceStatus.ERROR)
            }
        } catch (_: RuntimeException) {
            mutable.value = SpeechState(VoiceStatus.ERROR)
        }
    }

    fun stop() {
        if (mutable.value.activeId != null) engine?.stop()
        mutable.value = mutable.value.copy(activeId = null)
    }

    fun close() {
        closed = true
        ++attempt
        clearDeadline()
        engine?.shutdown()
        engine = null
        probeFile?.delete()
    }

    private fun setDeadline(millis: Long, block: () -> Unit) {
        clearDeadline()
        deadline = Runnable(block).also { main.postDelayed(it, millis) }
    }

    private fun clearDeadline() {
        deadline?.let(main::removeCallbacks)
        deadline = null
    }
}

internal fun mandarinRank(locale: Locale): Int {
    // ColorOS advertises its embedded Mandarin voice as "chn".
    if (locale.language == "chn" && (locale.country.isEmpty() || locale.country == "CN")) return 5
    if (locale.language !in setOf("zh", "cmn")) return 0
    return when (locale.country) {
        "CN" -> 5
        "SG" -> 4
        "" -> if (locale.script == "Hant") 2 else 3
        "TW" -> 2
        else -> 0
    }
}

private fun Voice.isOfflineMandarin(): Boolean =
    !isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty() &&
        mandarinRank(locale) > 0

private const val OplusEngine = "com.oplus.ttsaccessibilityengine"
