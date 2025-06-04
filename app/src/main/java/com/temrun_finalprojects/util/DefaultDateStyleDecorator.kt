package com.temrun_finalprojects.util

import android.content.Context
import android.text.style.TextAppearanceSpan
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade

class DefaultDateStyleDecorator(private val context: Context) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean = true

    override fun decorate(view: DayViewFacade) {
        // 날짜 숫자 크기 고정 (Material 기본 스타일)
        view.addSpan(TextAppearanceSpan(context, android.R.style.TextAppearance_Material_Body1))
    }
}
