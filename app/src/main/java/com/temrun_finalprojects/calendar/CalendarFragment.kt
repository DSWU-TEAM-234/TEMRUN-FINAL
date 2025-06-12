package com.temrun_finalprojects.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.temrun_finalprojects.databinding.FragmentCalendarBinding
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.temrun_finalprojects.util.DefaultDateStyleDecorator
import com.temrun_finalprojects.result.RunningResultBottomSheet
import com.temrun_finalprojects.util.SelectedDayDecorator
import com.temrun_finalprojects.util.SingleDistanceDecorator

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: CalendarDay? = null

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

        for ((day, text) in dummyData) {
            binding.calendarView.addDecorator(
                SingleDistanceDecorator(requireContext(), day, text)
            )
        }

        binding.calendarView.setOnDateChangedListener { _, date, _ ->
            val clickedDate = CalendarDay.from(date.year, date.month, date.day)

            selectedDate = if (clickedDate == selectedDate) null else clickedDate

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

            val bottomSheet = RunningResultBottomSheet()
            bottomSheet.show(parentFragmentManager, bottomSheet.tag)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
