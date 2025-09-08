// app/src/main/java/com/temrun_finalprojects/util/Metronome.kt
package com.temrun_finalprojects.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * 애니메이션 없이 "비프 소리"만 내는 경량 메트로놈.
 * - ToneGenerator 로 짧은 beep
 * - 드리프트 보정: (start + n*interval) 기준으로 예약
 */
class Metronome(
    private val context: Context,
    private val onFinished: (() -> Unit)? = null
) {
    private val running = AtomicBoolean(false)
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private var tone: ToneGenerator? = null

    fun start(
        bpm: Int,
        beats: Int = 8,
        pulseMs: Long = 35L
    ) {
        if (bpm <= 0 || beats <= 0) return
        if (running.getAndSet(true)) return

        worker = HandlerThread("MetronomeThread").also { it.start() }
        handler = Handler(worker!!.looper)

        tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

        val interval = max(1L, (60000f / bpm).roundToLong())
        val startAt = SystemClock.elapsedRealtime()
        val totalBeats = beats

        handler?.post(object : Runnable {
            var count = 0
            override fun run() {
                if (!running.get()) return

                // 비프 재생
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, pulseMs.toInt())

                count++
                if (count >= totalBeats) {
                    stop()
                    onFinished?.invoke()
                    return
                }

                // 드리프트 보정
                val nextAt = startAt + count * interval
                val delay = max(0L, nextAt - SystemClock.elapsedRealtime())
                handler?.postDelayed(this, delay)
            }
        })
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try { tone?.release() } catch (_: Throwable) {}
        tone = null
        handler?.removeCallbacksAndMessages(null)
        worker?.quitSafely()
        worker = null
        handler = null
    }
}
