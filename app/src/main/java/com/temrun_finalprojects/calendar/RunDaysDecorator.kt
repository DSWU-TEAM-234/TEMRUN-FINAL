package com.temrun_finalprojects.calendar

import android.content.Context
import androidx.core.content.ContextCompat
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.temrun_finalprojects.R

class RunDaysDecorator(
    private val context: Context,
    private val days: Set<CalendarDay>
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean = day in days

    override fun decorate(view: DayViewFacade) {
        // ✅ 뛴 날만 라운드 사각형 배경 적용
        val bg = ContextCompat.getDrawable(context, R.drawable.bg_run_day_rect)
        view.setBackgroundDrawable(bg!!)
    }
}
