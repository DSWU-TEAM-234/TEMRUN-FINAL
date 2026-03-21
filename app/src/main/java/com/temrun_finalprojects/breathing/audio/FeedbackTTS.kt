package com.temrun_finalprojects.breathing.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.util.Log


/**
 * TTS(텍스트 음성 변환)를 통한 피드백 제공 클래스.
 *
 * @param context 애플리케이션 컨텍스트
 * @param onReady TTS 초기화 완료 시 호출되는 콜백 함수
 */

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

    /**
     * 텍스트 -> 음성 출력
     *
     * - TTS 초기화 ❌,
     * - 쿨타임 내에 호출,
     * - 텍스트 비어 있는 경우 출력 ❌
     *
     * @param text 출력할 텍스트
     */
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

    /**
     * TTS 리소스를 정리하고 종료.
     * 액티비티 or 앱 종료 시 호출 필요.
     */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }
}
