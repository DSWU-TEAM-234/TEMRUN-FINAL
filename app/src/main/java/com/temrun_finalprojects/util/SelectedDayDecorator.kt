package com.temrun_finalprojects.util

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.text.style.ForegroundColorSpan
import android.text.style.TextAppearanceSpan
import androidx.core.content.ContextCompat
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.temrun_finalprojects.R

class SelectedDayDecorator(
    private val context: Context,
    private val selectedDate: CalendarDay
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean {
        return day == selectedDate
    }

    override fun decorate(view: DayViewFacade) {
        val bgColor = ContextCompat.getColor(context, R.color.green_selected)
        val textColor = ContextCompat.getColor(context, R.color.white)

        view.setBackgroundDrawable(ColorDrawable(bgColor))
        view.addSpan(ForegroundColorSpan(textColor))

        // ✅ 날짜 숫자 크기 고정 (선택된 날짜도 강제 고정)
        view.addSpan(TextAppearanceSpan(context, android.R.style.TextAppearance_Material_Body1))
    }
}
