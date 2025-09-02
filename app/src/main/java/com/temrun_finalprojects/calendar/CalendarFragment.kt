package com.temrun_finalprojects.calendar

import android.content.Intent // [추가] SessionActivity로 이동하기 위해 필요
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast // [추가] 기록 없을 때 안내용
import androidx.fragment.app.Fragment
import com.temrun_finalprojects.databinding.FragmentCalendarBinding
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.temrun_finalprojects.util.DefaultDateStyleDecorator
import com.temrun_finalprojects.result.RunningResultBottomSheet
import com.temrun_finalprojects.util.SelectedDayDecorator
import com.temrun_finalprojects.util.SingleDistanceDecorator
import com.temrun_finalprojects.calendar.SessionActivity // [추가] 이동 대상 액티비티
import kotlin.jvm.java

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: CalendarDay? = null

    companion object {
        // [추가] 인텐트 키 상수화
        private const val EXTRA_DATE = "extra_date"
        // [추가] 날짜 포맷(yyyy-MM-dd)
        private fun formatDate(day: CalendarDay): String {
            // CalendarDay의 month는 실제 달 번호(1~12)로 들어오므로 그대로 사용
            return String.format("%04d-%02d-%02d", day.year, day.month, day.day)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.calendarView.state().edit()
            .setFirstDayOfWeek(java.util.Calendar.SUNDAY)
            .commit()

        // ✅ 날짜 텍스트 크기를 모든 날짜에 고정
        binding.calendarView.addDecorator(DefaultDateStyleDecorator(requireContext()))

        // [원본 유지] 데모용 거리 텍스트 데코레이터 데이터
        val dummyData = mapOf(
            CalendarDay.from(2025, 5, 5) to "2.8km",
            CalendarDay.from(2025, 5, 7) to "6.7km",
            CalendarDay.from(2025, 5, 9) to "6.9km",
            CalendarDay.from(2025, 5, 11) to "3.9km",
            CalendarDay.from(2025, 5, 16) to "6.8km",
            CalendarDay.from(2025, 5, 23) to "5.8km",
            CalendarDay.from(2025, 5, 25) to "3.7km",
            CalendarDay.from(2025, 5, 26) to "3.9km",
            CalendarDay.from(2025, 5, 29) to "2.9km"
        )

        // [원본 유지] 기록 표시 데코레이터 적용
        for ((day, text) in dummyData) {
            binding.calendarView.addDecorator(
                SingleDistanceDecorator(requireContext(), day, text)
            )
        }

        // [변경] 날짜 선택 리스너: 기록 존재 여부에 따라 SessionActivity로 이동
        binding.calendarView.setOnDateChangedListener { _, date, _ ->
            val clickedDate = CalendarDay.from(date.year, date.month, date.day)

            // [원본 유지] 선택 상태 토글
            selectedDate = if (clickedDate == selectedDate) null else clickedDate

            // [원본 유지] 기존 데코레이터 재적용 흐름
            binding.calendarView.removeDecorators()

            // 항상 날짜 숫자 크기 유지
            binding.calendarView.addDecorator(DefaultDateStyleDecorator(requireContext()))

            for ((day, text) in dummyData) {
                binding.calendarView.addDecorator(
                    SingleDistanceDecorator(requireContext(), day, text)
                )
            }

            selectedDate?.let {
                binding.calendarView.addDecorator(
                    SelectedDayDecorator(requireContext(), it)
                )
            }

            // [핵심 변경] 기록이 있는 날짜면 SessionActivity로 이동, 없으면 안내 토스트
            // CalendarDay equals 비교는 year/month/day 기준으로 동등성 체크 가능
            if (dummyData.keys.any { it == clickedDate }) {
                val dateText = formatDate(clickedDate)
                val intent = Intent(requireContext(), SessionActivity::class.java).apply {
                    putExtra(EXTRA_DATE, dateText) // yyyy-MM-dd
                }
                startActivity(intent)
                // [참고] 기존 BottomSheet는 기록이 있을 때 대신 SessionActivity로 이동합니다.
                // val bottomSheet = RunningResultBottomSheet()
                // bottomSheet.show(parentFragmentManager, bottomSheet.tag)
            } else {
                // 기록이 없는 날짜
                Toast.makeText(requireContext(), "선택한 날짜의 러닝 기록이 없어요.", Toast.LENGTH_SHORT).show()

                // [원본 유지 선택지] 기록이 없을 때만 BottomSheet를 띄우고 싶다면 아래 주석을 해제
                // val bottomSheet = RunningResultBottomSheet()
                // bottomSheet.show(parentFragmentManager, bottomSheet.tag)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}