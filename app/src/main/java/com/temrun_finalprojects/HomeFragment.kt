package com.temrun_finalprojects

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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
    private var cadenceValue = 180
    private var total = 0

    private var recorder: AudioRecorder? = null


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
            intent.putExtra("Breath", selectedBreath)

            intent.putExtra("cadence", cadenceValue)
            startActivity(intent)
        }

        return view
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
}
