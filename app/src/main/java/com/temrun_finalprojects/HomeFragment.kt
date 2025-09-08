package com.temrun_finalprojects


import com.temrun_finalprojects.util.Metronome

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.temrun_finalprojects.breathing.audio.AudioRecorder

class HomeFragment : Fragment() {

    private lateinit var btns_breath: List<Button>
    private lateinit var btns_mode: List<Button>
    private var cadenceValue = 150
    private var total = 0

    private var recorder: AudioRecorder? = null
    // 멤버로 보관하면 나중에 stop() 가능
    private var metronome: Metronome? = null

    private fun getVibrator(): Vibrator? {
        val ctx = context ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: VibratorManager 권장
            val vm = ctx.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            // 구버전
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java) ?: ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * 현재 cadence(BPM)에 맞춰 일정 박자만큼 진동을 내보낸다.
     * - beats: 총 박자 수 (기본 8박)
     * - pulseMs: 한 박자 진동 길이 (기본 35ms) — 너무 길면 박자 구분이 무너짐
     */
    private fun vibrateToBpm(bpm: Int, beats: Int = 8, pulseMs: Long = 35L) {
        if (bpm <= 0) return
        val vib = getVibrator() ?: return

        // 한 박 간격(ms) = 60,000 / BPM
        val interval = (60000f / bpm).toLong().coerceAtLeast(pulseMs + 1)

        // Waveform: [대기, 진동, 대기, 진동, ...]
        // 첫 요소는 "대기" 시간이므로 0으로 시작해 바로 첫 박 진동
        val steps = beats * 2
        val timings = LongArray(steps)
        val amplitudes = IntArray(steps)

        for (i in 0 until steps) {
            if (i % 2 == 0) {
                // 대기 구간
                timings[i] = if (i == 0) 0L else (interval - pulseMs)
                amplitudes[i] = 0
            } else {
                // 진동 구간
                timings[i] = pulseMs
                amplitudes[i] = 255 // 최대 진동 세기
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // createWaveform(timings, amplitudes, no repeat)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vib.cancel() // 이전 패턴 중지
            vib.vibrate(effect)
        } else {
            // 구버전: 진폭 제어 불가
            @Suppress("DEPRECATION")
            vib.vibrate(timings, -1)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val tvCadence = view.findViewById<TextView>(R.id.tv_cadence_value)
        val btnPlus = view.findViewById<Button>(R.id.btn_cadence_plus)
        val btnMinus = view.findViewById<Button>(R.id.btn_cadence_minus)

        tvCadence.text = cadenceValue.toString()

        btnPlus.setOnClickListener {
            cadenceValue += 5
            tvCadence.text = cadenceValue.toString()
        }

        btnMinus.setOnClickListener {
            cadenceValue -= 5
            tvCadence.text = cadenceValue.toString()
        }

        val pickerHour = view.findViewById<NumberPicker>(R.id.picker_hour)
        val pickerMinute = view.findViewById<NumberPicker>(R.id.picker_minute)
        val pickerSecond = view.findViewById<NumberPicker>(R.id.picker_second)

        // NumberPicker 초기 설정
        listOf(pickerHour to 23, pickerMinute to 59, pickerSecond to 59).forEach { (picker, max) ->
            picker.minValue = 0
            picker.maxValue = max
            styleSelectedTextOnly(picker)
        }
        pickerMinute.value = 15

        // 변경 리스너
        val logTime = {
            total = pickerHour.value * 3600 + pickerMinute.value * 60 + pickerSecond.value
            Log.d("선택한 시간", "${pickerHour.value}:${pickerMinute.value}:${pickerSecond.value} → 총 $total 초")
        }

        pickerHour.setOnValueChangedListener { _, _, _ -> styleSelectedTextOnly(pickerHour); logTime() }
        pickerMinute.setOnValueChangedListener { _, _, _ -> styleSelectedTextOnly(pickerMinute); logTime() }
        pickerSecond.setOnValueChangedListener { _, _, _ -> styleSelectedTextOnly(pickerSecond); logTime() }

        // 버튼 그룹 처리
        btns_breath = listOf(
            view.findViewById(R.id.btn_breath_1_1),
            view.findViewById(R.id.btn_breath_2_1),
            view.findViewById(R.id.btn_breath_2_2)
        )
        btns_mode = listOf(
            view.findViewById(R.id.btn_normal),
            view.findViewById(R.id.btn_beginner)
        )

        // 호흡 버튼 클릭 설정
        for (button in btns_breath) {
            button.setOnClickListener {
                //버튼 클릭했을때 호출 할 이벤트 정의
                highlightGroup(button, btns_breath)
            }
        }

        // 모드 버튼 클릭 설정
        for (button in btns_mode) {
            button.setOnClickListener {
                //버튼 클릭했을때 호출 할 이벤트 정의
                highlightGroup(button, btns_mode)
            }
        }

        recorder = AudioRecorder(requireContext())

        val btnBpmVibe = view.findViewById<Button>(R.id.btn_bpm_vibe)
        btnBpmVibe?.setOnClickListener {
            // 현재 선택된 cadenceValue 기준으로 8박, 박자당 35ms 진동
            startFeedback(cadenceValue, beats = 8, pulseMs = 35L)
        }

        val btnConfirm = view.findViewById<Button>(R.id.btn_start_running)
        btnConfirm.setOnClickListener {
            val cadence = cadenceValue

            val selectedMode = when {
                btns_mode[0].currentTextColor == Color.WHITE -> "normal"
                btns_mode[1].currentTextColor == Color.WHITE -> "beginner"
                else -> "normal" // 기본값
            }

            val selectedBreath = when {
                btns_breath[0].currentTextColor == Color.WHITE -> "1_1"
                btns_breath[1].currentTextColor == Color.WHITE -> "2_1"
                btns_breath[2].currentTextColor == Color.WHITE -> "2_2"
                else -> "1_1" // 기본값
            }

            // 오디오 리코딩 시작
            recorder?.startRecording()

            // MainActivity로 이동 (bpm 값,모드,시간만 넘김)
            val intent = Intent(this.context, MainActivity::class.java)
            intent.putExtra("cadence", cadence)
            intent.putExtra("mode", selectedMode)
            intent.putExtra("time", total)
            intent.putExtra("breath", selectedBreath)

            intent.putExtra("cadence", cadenceValue)
            startActivity(intent)
        }

        return view
    }

    // 기존 startFeedback() 치환 (내용만 교체)
    private fun startFeedback(bpm: Int, beats: Int = 8, pulseMs: Long = 35L) {
        vibrateToBpm(bpm, beats, pulseMs) // 기존 진동 그대로 병행

        // 애니메이션 콜백 없이 소리만
        metronome = Metronome(
            context = requireContext(),
            onFinished = {
                // 필요 시 완료 처리 로직 (없으면 비워둬도 됨)
            }
        ).also {
            it.start(bpm = bpm, beats = beats, pulseMs = pulseMs)
        }
    }

    private fun stopFeedback() {
        metronome?.stop()
        metronome = null
        try { getVibrator()?.cancel() } catch (_: Throwable) {}
    }


    private fun styleSelectedTextOnly(picker: NumberPicker) {
        picker.post {
            for (i in 0 until picker.childCount) {
                val child = picker.getChildAt(i)
                if (child is EditText) {
                    child.setTextColor(Color.BLACK)
                    child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
                    child.setTypeface(null, Typeface.BOLD)
                }
            }
        }
    }

    private fun highlightGroup(selected: Button, group: List<Button>) {
        group.forEach {
            if (it == selected) {
                it.setBackgroundColor(Color.parseColor("#8CC97F"))
                it.setTextColor(Color.WHITE)
            } else {
                it.setBackgroundColor(Color.parseColor("#D9D9D9"))
                it.setTextColor(Color.BLACK)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 화면 떠날 때 남아있는 진동이 있으면 중지
        stopFeedback()
    }
}
