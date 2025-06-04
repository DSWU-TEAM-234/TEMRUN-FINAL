package com.temrun_finalprojects.breathing.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale


class FeedbackTTS(context: Context?, listener: OnInitListener?) :
    TextToSpeech(context, listener) {
    init {
        this.setPitch(1.0f) // 음성의 높낮이 설정
        this.setSpeechRate(1.0f) // 음성의 속도 설정
        this.setLanguage(Locale.KOREA)
    }

    fun speak(text: CharSequence?) {
        this.speak(text, QUEUE_FLUSH, null, "id1")
    }

    fun destroy() {
        this.stop()
        this.shutdown()
    }
}