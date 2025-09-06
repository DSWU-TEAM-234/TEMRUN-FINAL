package com.temrun_finalprojects.calendar

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.CalendarMode
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.temrun_finalprojects.databinding.FragmentCalendarBinding
import com.temrun_finalprojects.util.DefaultDateStyleDecorator
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import com.temrun_finalprojects.config.ApiConfig
import com.temrun_finalprojects.util.Constants.BASE_URL
import android.util.Log

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val runningDays = mutableSetOf<CalendarDay>()

    companion object {
        private const val EXTRA_DATE = "extra_date"
        // 교체
//        private const val BASE_URL = "https://4382a6a5c3d2.ngrok-free.app"
        //private const val USER_ID = "user123"

        private val client = OkHttpClient()

        private fun formatDate(day: CalendarDay): String {
            return String.format("%04d-%02d-%02d", day.year, day.month, day.day)
        }
    }

    // MaterialCalendarView.month는 0~11이므로 +1 보정
    private fun normalizeMonth(monthZeroBased: Int): Int = monthZeroBased + 1

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

        binding.calendarView.setTopbarVisible(false)
        binding.calendarView.state().edit()
            .setFirstDayOfWeek(java.util.Calendar.SUNDAY)
            .setCalendarDisplayMode(CalendarMode.MONTHS)
            .commit()
        binding.calendarView.setShowOtherDates(MaterialCalendarView.SHOW_NONE)

        // 1) 초기 헤더/데이터 로드
        val current = binding.calendarView.currentDate
        val initYear = current.year
        val initMonth = normalizeMonth(current.month)
        updateHeaderDate(LocalDate.of(initYear, initMonth, 1))
        fetchCalendarData(initYear, initMonth)

        // 2) 월 변경 시 헤더 및 데이터 갱신
        binding.calendarView.setOnMonthChangedListener { _, date ->
            val year = date.year
            val month = normalizeMonth(date.month)
            updateHeaderDate(LocalDate.of(year, month, 1))
            fetchCalendarData(year, month)
        }

        binding.calendarView.addDecorator(DefaultDateStyleDecorator(requireContext()))

        // 3) 날짜 클릭 시 선택 해제하고 기록 여부에 따라 처리
        binding.calendarView.setOnDateChangedListener { _, date, _ ->
            binding.calendarView.clearSelection()

            val y = date.year
            val m = normalizeMonth(date.month)
            val d = date.day
            val clickedDate = CalendarDay.from(y, m, d)

            if (clickedDate in runningDays) {
                startActivity(
                    Intent(requireContext(), SessionActivity::class.java).apply {
                        putExtra(EXTRA_DATE, formatDate(clickedDate))
                    }
                )
            } else {
                Toast.makeText(requireContext(), "선택한 날짜의 러닝 기록이 없어요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateHeaderDate(date: LocalDate) {
        binding.textViewYear.text = date.year.toString()
        binding.textViewMonth.text = date.monthValue.toString().padStart(2, '0')
    }

    // 수정된 fetchCalendarData 함수
    private fun fetchCalendarData(year: Int, month: Int) {
        // Fragment에서는 Context를 통해 접근
        val prefs = requireContext().getSharedPreferences("AppUser", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null) ?: "u12345"

        val monthParam = String.format("%04d-%02d", year, month)
        val url = ApiConfig.getCalendarUrl(userId, monthParam)

        // 약간의 지연 추가 (서버 저장 완료 대기)
        binding.root.postDelayed({
            val request = Request.Builder()
                .url(url)
                .addHeader("Cache-Control", "no-cache") // 캐시 무효화
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "캘린더 데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let { body ->
                        val json = JSONObject(body)
                        val dates = json.optJSONArray("runningDates") ?: return@let
                        runningDays.clear()
                        for (i in 0 until dates.length()) {
                            val dayInt = dates.getString(i).toInt()
                            runningDays.add(CalendarDay.from(year, month, dayInt))
                        }
                        Log.d("CalendarFragment", "JSON 전체: $body")
                        val totalSec = json.optLong("totalDuration", 0L)
                        val avgBpm   = json.optInt("averageBpm", 0)
                        val totalCal = json.optInt("totalCalories", 0)
                        Log.d("CalendarFragment", "parsed totalSec=$totalSec, avgBpm=$avgBpm, totalCal=$totalCal")

                        val hours = totalSec / 3600
                        val minutes = (totalSec % 3600) / 60
                        val durationStr = String.format("%d:%02d", hours, minutes)


                        requireActivity().runOnUiThread {

                            updateMonthSummary(durationStr, avgBpm, totalCal)
                            // 캘린더 뷰 새로고침
                            binding.calendarView.invalidateDecorators()
                        }
                    }
                }
            })
        }, 500) // 500ms 지연
    }

    private fun updateMonthSummary(
        totalDuration: String,
        averageBpm: Int,
        totalCalories: Int
    ) {
        binding.textViewSummary.text =
            "$totalDuration / ${averageBpm}BPM / ${totalCalories}kcal"
    }

    // 새로 추가: onResume() 함수
    override fun onResume() {
        super.onResume()
        // 프래그먼트가 다시 보여질 때마다 현재 월 데이터 새로고침
        val current = binding.calendarView.currentDate
        val currentYear = current.year
        val currentMonth = normalizeMonth(current.month)
        fetchCalendarData(currentYear, currentMonth)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}