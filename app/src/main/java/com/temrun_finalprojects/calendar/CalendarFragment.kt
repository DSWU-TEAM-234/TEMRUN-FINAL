package com.temrun_finalprojects.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.temrun_finalprojects.databinding.FragmentCalendarBinding
import com.temrun_finalprojects.util.Constants
import com.temrun_finalprojects.util.DefaultDateStyleDecorator
import com.temrun_finalprojects.calendar.RunDaysDecorator
import com.temrun_finalprojects.util.SelectedDayDecorator

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val vm: CalendarViewModel by viewModels()
    private var selectedDate: CalendarDay? = null

    // 1-based 월(01~12) 유지
    private var visibleYear: Int = 0
    private var visibleMonth1: Int = 0

    private var lastRunDays: Set<CalendarDay> = emptySet()

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

        // 기본 설정
        binding.calendarView.state().edit()
            .setFirstDayOfWeek(java.util.Calendar.SUNDAY)
            .commit()
        binding.calendarView.setShowOtherDates(MaterialCalendarView.SHOW_NONE)

        // 현재 날짜로 초기 설정
        val current = binding.calendarView.currentDate
        visibleYear = current.year
        visibleMonth1 = current.month + 1  // ✅ 예시 코드 방식 차용: month + 1

        binding.textViewYear.text = visibleYear.toString()
        binding.textViewMonth.text = "%02d".format(visibleMonth1)

        // 달이 바뀔 때마다 연/월 텍스트 갱신 + API 호출
        binding.calendarView.setOnMonthChangedListener { _, date ->
            visibleYear = date.year
            visibleMonth1 = date.month + 1  // ✅ month + 1 유지

            binding.textViewYear.text = visibleYear.toString()
            binding.textViewMonth.text = "%02d".format(visibleMonth1)

            vm.fetchCalendar(
                Constants.DEFAULT_USER_ID,
                "%04d-%02d".format(visibleYear, visibleMonth1)
            )
        }

        // 초기 데코레이터
        binding.calendarView.removeDecorators()
        binding.calendarView.addDecorator(DefaultDateStyleDecorator(requireContext()))

        // ✅ ViewModel에서 달린 날짜 마킹
        vm.summary.observe(viewLifecycleOwner) { s ->
            s ?: return@observe

            // 상단 요약 박스
            binding.textViewSummary.text =
                "${secondsToHHMM(s.totalDuration)} / ${s.averageBpm}BPM / ${s.totalCalories}kcal"

            // ✅ CalendarDay 생성 시 month - 1 보정 (0-based)
            val runDays = s.runningDates.mapNotNull { dayStr ->
                dayStr.toIntOrNull()?.let { day ->
                    CalendarDay.from(visibleYear, visibleMonth1 - 1, day)
                }
            }.toSet()
            lastRunDays = runDays

            binding.calendarView.removeDecorators()
            binding.calendarView.addDecorator(DefaultDateStyleDecorator(requireContext()))
            binding.calendarView.addDecorator(RunDaysDecorator(requireContext(), runDays))

            selectedDate?.let {
                binding.calendarView.addDecorator(SelectedDayDecorator(requireContext(), it))
            }

            binding.calendarView.invalidateDecorators()
        }

        // 날짜 선택 시 강조
        binding.calendarView.setOnDateChangedListener { _, date, _ ->
            val clicked = CalendarDay.from(date.year, date.month, date.day) // ✅ date.month는 이미 0-based

            selectedDate = if (selectedDate == clicked) null else clicked

            binding.calendarView.removeDecorators()
            binding.calendarView.addDecorator(DefaultDateStyleDecorator(requireContext()))
            binding.calendarView.addDecorator(RunDaysDecorator(requireContext(), lastRunDays))
            selectedDate?.let {
                binding.calendarView.addDecorator(SelectedDayDecorator(requireContext(), it))
            }
            binding.calendarView.invalidateDecorators()
        }

        // 최초 로딩도 현재 보이는 달 기준으로
        vm.fetchCalendar(
            Constants.DEFAULT_USER_ID,
            "%04d-%02d".format(visibleYear, visibleMonth1)
        )
    }

    private fun secondsToHHMM(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return "%02d:%02d".format(h, m)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
