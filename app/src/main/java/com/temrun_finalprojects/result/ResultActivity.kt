package com.temrun_finalprojects.result

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.data.Song

class ResultActivity: AppCompatActivity() {

    private lateinit var cadenceChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_result)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.result)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val songContainer = findViewById<LinearLayout>(R.id.songLinearLayout) // 내부 LinearLayout ID

//        val songList = listOf(
//            Song("노래1", "가수1"),
//            Song("노래2", "가수2"),
//            Song("노래3", "가수3")
//        )

        val receivedSongs = intent.getParcelableArrayListExtra<Song>("songs") ?: arrayListOf()

        for (song in receivedSongs)
        {
            val itemView =
                LayoutInflater.from(this).inflate(R.layout.item_song_card, songContainer, false)

            val title = itemView.findViewById<TextView>(R.id.songTitle)
            val artist = itemView.findViewById<TextView>(R.id.songArtist)
            val image = itemView.findViewById<ImageView>(R.id.albumImageView)

            title.text = song.title
            artist.text = song.artist
            Glide.with(itemView.context)
                .load(song.albumImageUrl)
                .into(image)

            songContainer.addView(itemView)
        }

        val time = intent.getIntExtra("time", 0)
        val calorie = intent.getDoubleExtra("calorie", 0.0)
        val distance = intent.getFloatExtra("distance", 0f)
        val averageBPM = intent.getIntExtra("averageBPM", 0)

        // 러닝 시작 시 선택한 목표 케이던스와 실시간 케이던스 데이터 받기
        val targetCadence = intent.getIntExtra("targetCadence", 160)
        val cadenceDataList = intent.getIntegerArrayListExtra("cadenceDataList") ?: arrayListOf()

        val hours = time / 3600
        val minutes = (time % 3600) / 60
        val seconds = time % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

        findViewById<TextView>(R.id.ResultTimeText).text = timeFormatted
        findViewById<TextView>(R.id.ResultBPMText).text =  averageBPM.toString()
        findViewById<TextView>(R.id.ResultCalorieText).text = calorie.toString()

        // 케이던스 차트 초기화 및 설정
        setupCadenceChart(targetCadence, cadenceDataList)

        val resultConfirmButton : Button = findViewById(R.id.resultConfirmButton)

        resultConfirmButton.setOnClickListener {
            val intent = Intent(this, RootActivity::class.java)
            intent.putExtra("targetFragment", "home")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun setupCadenceChart(targetCadence: Int, cadenceDataList: List<Int>) {
        cadenceChart = findViewById(R.id.chartCadence)

        // 실시간 케이던스 데이터를 그래프 엔트리로 변환
        val entries = mutableListOf<Entry>()
        cadenceDataList.forEachIndexed { index, cadence ->
            entries.add(Entry(index.toFloat(), cadence.toFloat()))
        }

        // 실시간 케이던스 라인 데이터셋 생성
        val dataSet = LineDataSet(entries, "실시간 케이던스").apply {
            color = ContextCompat.getColor(this@ResultActivity, R.color.teal_700)
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 3f
            setCircleColor(ContextCompat.getColor(this@ResultActivity, R.color.teal_700))
            mode = LineDataSet.Mode.LINEAR
            setDrawValues(false)
        }

        // 차트에 데이터 설정
        cadenceChart.data = LineData(dataSet)

        // Y축 설정
        val leftAxis: YAxis = cadenceChart.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 220f
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = ContextCompat.getColor(this, R.color.light_gray)

        // 목표 케이던스 직선 추가
        val targetLimitLine = LimitLine(targetCadence.toFloat(), "목표 케이던스 ($targetCadence)").apply {
            lineWidth = 4f
            lineColor = ContextCompat.getColor(this@ResultActivity, R.color.purple_500)
            textColor = ContextCompat.getColor(this@ResultActivity, R.color.purple_500)
            textSize = 12f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        leftAxis.addLimitLine(targetLimitLine)

        // 오른쪽 Y축 비활성화
        cadenceChart.axisRight.isEnabled = false

        // X축 설정
        cadenceChart.xAxis.apply {
            setDrawGridLines(true)
            setAvoidFirstLastClipping(true)
            granularity = 1f
            gridColor = ContextCompat.getColor(this@ResultActivity, R.color.light_gray)
        }

        // 차트 전체 설정
        cadenceChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            animateX(1000)
        }

        // 차트 새로고침
        cadenceChart.invalidate()
    }
}