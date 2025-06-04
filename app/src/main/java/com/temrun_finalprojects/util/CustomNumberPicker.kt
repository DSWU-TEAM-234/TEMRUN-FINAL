package com.temrun_finalprojects.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.EditText
import android.widget.NumberPicker

//넘버피커 스타일 전체적으로 바꾸는 코드.
//따로따로는 안되는 모양...
class CustomNumberPicker(context: Context, attrs: AttributeSet?) : NumberPicker(context, attrs) {

    override fun addView(child: android.view.View?) {
        super.addView(child)
        updateView(child)
    }

    override fun addView(child: android.view.View?, index: Int, params: android.view.ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        updateView(child)
    }

    override fun addView(child: android.view.View?, width: Int, height: Int) {
        super.addView(child, width, height)
        updateView(child)
    }

    private fun updateView(view: android.view.View?) {
        if (view is EditText) {
            view.setTextColor(Color.BLACK)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            view.setTypeface(null, Typeface.BOLD)
        }
    }
}
