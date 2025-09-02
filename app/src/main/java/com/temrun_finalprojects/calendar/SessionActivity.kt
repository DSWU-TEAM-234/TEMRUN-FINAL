package com.temrun_finalprojects.calendar

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.temrun_finalprojects.R

class SessionActivity : AppCompatActivity() {

    companion object {
        // CalendarFragment에서 putExtra에 사용한 키와 동일하게 유지
        const val EXTRA_DATE = "extra_date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // [중요] 반드시 activity_session.xml을 가리키도록 합니다.
        setContentView(R.layout.activity_session)

        // 인텐트로부터 날짜 받기
        val dateText = intent.getStringExtra(EXTRA_DATE)
        if (dateText.isNullOrBlank()) {
            Toast.makeText(this, "날짜 정보가 없어 목록을 표시할 수 없어요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 액션바 타이틀로 날짜 노출
        supportActionBar?.title = dateText

        // [핵심] 레이아웃(activity_session.xml)에 반드시 tvSelectedDate가 존재해야 합니다.
        findViewById<TextView>(R.id.tvSelectedDate)?.text = dateText

        // TODO: 여기서 Repository/DAO/ViewModel을 통해 dateText(yyyy-MM-dd)에 해당하는
        // 러닝 기록 목록을 로드하고 RecyclerView에 표시하세요.
        // 예시:
        // viewModel.loadSessionsForDate(dateText)
        // viewModel.uiState.observe(this) { state -> adapter.submitList(state.items) }
    }
}