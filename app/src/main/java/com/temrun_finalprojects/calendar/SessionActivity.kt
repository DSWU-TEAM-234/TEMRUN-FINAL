package com.temrun_finalprojects.calendar

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.temrun_finalprojects.R
import com.temrun_finalprojects.config.ApiConfig
import com.temrun_finalprojects.result.RunningResultBottomSheet
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// 1. Data model matching the API response
data class RunSession(
    val runId: String,
    val totalDuration: Long,
    val averageBpm: Int,
    val totalCalories: Int,
    val startTime: String
)

// 2. Retrofit service interface
interface RunsApiService {
    @GET("/api/users/{user_id}/runs/date/{date}")
    suspend fun getRunsByDate(
        @Path("user_id") userId: String,
        @Path("date") date: String
    ): List<RunSession>
}

class SessionActivity : AppCompatActivity(), SessionAdapter.OnSessionClickListener {

    companion object {
        const val EXTRA_DATE = "extra_date"
        // 교체
//        private const val BASE_URL = "https://339cdd456ce9.ngrok-free.app"
        private const val USER_ID = "user123"
        private const val DATE = "2025-02-09"

    }

    private lateinit var tvSelectedDate: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var progress: ProgressBar
    private lateinit var rvSessions: RecyclerView
    private lateinit var adapter: SessionAdapter
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)

        // 액션바 뒤로가기(홈) 아이콘은 사용하지 않습니다.
        // supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvEmpty = findViewById(R.id.tvEmpty)
        progress = findViewById(R.id.progress)
        rvSessions = findViewById(R.id.rvSessions)
        btnBack = findViewById(R.id.btnBack)

        // 커스텀 백버튼 클릭 시 시스템 뒤로가기와 동일하게 처리
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            // 또는 finish()
        }

        // RecyclerView setup
        adapter = SessionAdapter(this)
        rvSessions.layoutManager = LinearLayoutManager(this)
        rvSessions.adapter = adapter

        val dateText = intent.getStringExtra(EXTRA_DATE)
        if (dateText.isNullOrBlank()) {
            Toast.makeText(this, "날짜 정보가 없어 목록을 표시할 수 없어요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 액션바 제목 대신 상단 TextView에 날짜 노출
        // supportActionBar?.title = dateText
        tvSelectedDate.text = dateText

        loadSessions(dateText)
    }

    private fun loadSessions(date: String) {
        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        // Configure Retrofit
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.getBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(RunsApiService::class.java)
        val userId = getSharedPreferences("AppUser", MODE_PRIVATE)
            .getString("user_id", null) ?: "u12345"

        lifecycleScope.launch {
            try {
                val sessions = api.getRunsByDate(userId, date)
                progress.visibility = View.GONE

                if (sessions.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    adapter.submitList(sessions)
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                Toast.makeText(
                    this@SessionActivity,
                    "러닝 기록을 불러오는 중 오류가 발생했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                e.printStackTrace()
            }
        }
    }

    override fun onSessionClick(session: RunSession) {
        val bottomSheet = RunningResultBottomSheet().apply {
            arguments = Bundle().apply {
                putString("runId", session.runId)
                putLong("totalDuration", session.totalDuration)
                putInt("averageBpm", session.averageBpm)
                putInt("totalCalories", session.totalCalories)
                putString("startTime", session.startTime)
            }
        }
        bottomSheet.show(supportFragmentManager, "RunningResult")
    }
}