package com.temrun_finalprojects.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.LineBackgroundSpan
import androidx.core.content.ContextCompat
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.temrun_finalprojects.R

class SingleDistanceDecorator(
    private val context: Context,
    private val targetDate: CalendarDay,
    private val text: String
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean {
        return day == targetDate
    }

    override fun decorate(view: DayViewFacade) {
        view.addSpan(object : LineBackgroundSpan {
            override fun drawBackground(
                canvas: Canvas, paint: Paint,
                left: Int, right: Int,
                top: Int, baseline: Int, bottom: Int,
                charSequence: CharSequence, start: Int, end: Int, lineNum: Int
            ) {
                paint.color = ContextCompat.getColor(context, R.color.black)
                paint.textSize = 34f  // 날짜보다 작게!
                paint.isAntiAlias = true

                val textWidth = paint.measureText(text)
                val x = ((left + right) / 2f) - (textWidth / 2f)
                val y = bottom + 38f

                canvas.drawText(text, x, y, paint)
            }
        })
    }
}
