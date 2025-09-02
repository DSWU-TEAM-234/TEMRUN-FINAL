package com.temrun_finalprojects.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.temrun_finalprojects.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Random

class SessionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATE = "extra_date"
    }

    private lateinit var tvSelectedDate: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvSessions: RecyclerView
    private lateinit var sessionAdapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)

        // 툴바 설정
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvEmpty = findViewById(R.id.tvEmpty)
        progressBar = findViewById(R.id.progress)
        rvSessions = findViewById(R.id.rvSessions)

        val dateText = intent.getStringExtra(EXTRA_DATE)
        if (dateText.isNullOrBlank()) {
            Toast.makeText(this, "날짜 정보가 없어 목록을 표시할 수 없어요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val parsedDate = LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE)
            val displayFormatter = DateTimeFormatter.ofPattern("M월 d일")
            tvSelectedDate.text = parsedDate.format(displayFormatter)
        } catch (e: Exception) {
            tvSelectedDate.text = dateText
            e.printStackTrace()
        }

        // RecyclerView 초기화
        sessionAdapter = SessionAdapter()
        rvSessions.layoutManager = LinearLayoutManager(this)
        rvSessions.adapter = sessionAdapter

        // 더미 데이터 로드
        loadDummySessions(dateText)
    }

    private fun loadDummySessions(date: String) {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        rvSessions.visibility = View.GONE

        rvSessions.postDelayed({
            val dummyRecords = generateDummyRecords(date)
            if (dummyRecords.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rvSessions.visibility = View.GONE
            } else {
                sessionAdapter.submitList(dummyRecords)
                tvEmpty.visibility = View.GONE
                rvSessions.visibility = View.VISIBLE
            }
            progressBar.visibility = View.GONE
        }, 500)
    }

    private fun generateDummyRecords(date: String): List<SessionRecord> {
        val list = mutableListOf<SessionRecord>()
        val selectedDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        val random = Random()

        fun rndDistance() = String.format("%.1fkm", random.nextDouble() * 10)
        fun rndTime() = "${random.nextInt(1) + 1}시간 ${random.nextInt(60)}분"
        fun rndBpm() = random.nextInt(50) + 120
        fun rndCal() = random.nextInt(300) + 50

        when (selectedDate.dayOfWeek) {
            DayOfWeek.MONDAY -> list.add(
                SessionRecord("오전 러닝", "07:00", "0:30:10", rndBpm(), rndCal())
            )
            DayOfWeek.TUESDAY -> list.add(
                SessionRecord("조깅", "18:00", "0:20:45", rndBpm(), rndCal())
            )
            // 나머지 요일 패턴 생략 가능...
            else -> { /* 랜덤 기록 생략 */ }
        }
        // 60% 확률로 랜덤 기록 추가
        if (random.nextInt(100) < 60) {
            list.add(
                SessionRecord(
                    "랜덤 기록",
                    "${random.nextInt(23).toString().padStart(2, '0')}:${random.nextInt(60).toString().padStart(2,'0')}",
                    rndTime(),
                    rndBpm(),
                    rndCal()
                )
            )
        }
        return list
    }

    private class SessionAdapter :
        RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

        private var records: List<SessionRecord> = emptyList()

        inner class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.textTitle)
            val time: TextView = view.findViewById(R.id.textStartTime)
            val duration: TextView = view.findViewById(R.id.textDuration)
            val avgBpm: TextView = view.findViewById(R.id.textAvgBpm)
            val calories: TextView = view.findViewById(R.id.textCalories)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session, parent, false)
            return SessionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
            val record = records[position]
            holder.title.text = record.title
            holder.time.text = record.startTime
            holder.duration.text = record.duration
            holder.avgBpm.text = record.avgBpm.toString()
            holder.calories.text = record.calories.toString()
        }

        override fun getItemCount(): Int = records.size

        fun submitList(newRecords: List<SessionRecord>) {
            records = newRecords
            notifyDataSetChanged()
        }
    }
}
