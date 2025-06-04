package com.temrun_finalprojects.breathing.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.util.Log

class FeedbackTTS(context: Context, private val onReady: (() -> Unit)? = null) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var lastSpokenTime: Long = 0L
    private val cooldownMillis: Long = 30_000 // 30초 쿨타임

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREA)
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.0f)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("FeedbackTTS", "한국어 언어 지원 안됨")
            } else {
                isInitialized = true
                Log.d("FeedbackTTS", "TTS 초기화 완료")
                onReady?.invoke()
            }
        } else {
            Log.e("FeedbackTTS", "TTS 초기화 실패: status=$status")
        }
    }

    fun speak(text: String) {
        if (!isInitialized) {
            Log.w("FeedbackTTS", "TTS가 초기화되지 않았습니다.")
            return
        }
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSpokenTime < cooldownMillis) {
            val wait = (cooldownMillis - (currentTime - lastSpokenTime)) / 1000
            Log.w("FeedbackTTS", "TTS 쿨다운 중입니다. ${wait}초 후에 다시 시도할 수 있습니다.")
            return
        }
        if (text.isBlank()) {
            Log.w("FeedbackTTS", "재생할 텍스트가 없습니다.")
            return
        }
        lastSpokenTime = currentTime
        Log.d("FeedbackTTS", "음성 재생: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }
}
