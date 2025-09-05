package com.temrun_finalprojects.util

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.temrun_finalprojects.config.ApiConfig

class DebugSettingsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DebugSettings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "DebugSettingsActivity onCreate")

        val editText = EditText(this).apply {
            hint = "Base URL 입력"
            setText(ApiConfig.getBaseUrl())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val button = Button(this).apply {
            text = "URL 변경"
            setOnClickListener {
                val newUrl = editText.text.toString().trim()
                Log.d(TAG, "Change button clicked, newUrl=$newUrl")
                if (newUrl.isNotEmpty()) {
                    ApiConfig.updateBaseUrl(this@DebugSettingsActivity, newUrl)
                    Log.i(TAG, "ApiConfig baseUrl updated to $newUrl")
                    Toast.makeText(
                        this@DebugSettingsActivity,
                        "Base URL이 변경되었습니다:\n$newUrl",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.w(TAG, "Empty URL entered, ignoring")
                    Toast.makeText(
                        this@DebugSettingsActivity,
                        "빈 URL은 허용되지 않습니다",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(editText)
            addView(button)
        }

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "DebugSettingsActivity onResume, currentBaseUrl=${ApiConfig.getBaseUrl()}")
    }
}
