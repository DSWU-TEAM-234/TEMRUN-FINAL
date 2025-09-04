package com.temrun_finalprojects.result

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.data.Song
import kotlin.math.abs

// 호흡 피드백 데이터(정상 + 비정상 세부 분포)
data class BreathFeedbackCounts(
    val normal: Int,        // 정상호흡
    val patternOnly: Int,   // 호흡 패턴만 틀린 경우
    val organOnly: Int,     // 호흡 기관만 틀린 경우
    val bothMismatch: Int   // 둘 다 틀린 경우
)

class ResultActivity : AppCompatActivity() {

    private lateinit var cadenceChart: LineChart
    private lateinit var tvAccuracy: TextView
    private lateinit var tvAvgCadence: TextView
    private lateinit var tvDistance: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_result)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.result)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        // 1) 데이터 수신
        val songs = intent.getParcelableArrayListExtra<Song>("songs") ?: arrayListOf()
        val elapsedSeconds = intent.getIntExtra("time", 0)
        val avgBPM = intent.getIntExtra("averageBPM", 0)
        val distance = intent.getDoubleExtra("distance", 0.0)
        val cadenceList = intent.getIntegerArrayListExtra("cadenceDataList") ?: arrayListOf()
        val targetCadence = intent.getIntExtra("targetCadence", 160)

        // 호흡 데이터 수신
        val counts = BreathFeedbackCounts(
            normal = intent.getIntExtra("breath_normal", 0),
            patternOnly = intent.getIntExtra("breath_patternOnly", 0),
            organOnly = intent.getIntExtra("breath_organOnly", 0),
            bothMismatch = intent.getIntExtra("breath_bothMismatch", 0)
        )

        // 2) 뷰 바인딩
        findViewById<TextView>(R.id.ResultTimeText).text = String.format(
            "%02d:%02d",
            elapsedSeconds / 60,
            elapsedSeconds % 60
        )
        findViewById<TextView>(R.id.ResultBPMText).text = avgBPM.toString()
        findViewById<TextView>(R.id.ResultCalorieText).text =
            intent.getDoubleExtra("calorie", 0.0).toString()

        cadenceChart = findViewById(R.id.chartCadence)
        tvAccuracy = findViewById(R.id.textCadenceAccuracy)
        tvAvgCadence = findViewById(R.id.textCadenceValue)
        tvDistance = findViewById(R.id.textCadencePrecision)

        val songContainer = findViewById<LinearLayout>(R.id.songLinearLayout)

        // 3) 호흡 PieChart
        val breathChart = findViewById<PieChart>(R.id.breathPieChart)
        val breathRatio = findViewById<TextView>(R.id.breathRatioText)
        renderBreathCardAlways(breathChart, breathRatio, counts)

        // 4) 케이던스 LineChart
        val lineEntries = cadenceList.mapIndexed { i, c -> Entry(i.toFloat(), c.toFloat()) }
        val lineDs = LineDataSet(lineEntries, "실시간 케이던스").apply {
            color = ContextCompat.getColor(this@ResultActivity, R.color.teal_700)
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 3f
            setCircleColor(ContextCompat.getColor(this@ResultActivity, R.color.teal_700))
            mode = LineDataSet.Mode.LINEAR
            setDrawValues(false)
        }
        cadenceChart.data = LineData(lineDs)
        val left = cadenceChart.axisLeft
        left.axisMinimum = 0f
        left.axisMaximum = 220f
        left.setDrawGridLines(true)
        left.gridColor = ContextCompat.getColor(this, R.color.light_gray)
        left.addLimitLine(
            LimitLine(targetCadence.toFloat(), "목표($targetCadence)").apply {
                lineWidth = 4f
                lineColor = ContextCompat.getColor(this@ResultActivity, R.color.purple_500)
                textColor = ContextCompat.getColor(this@ResultActivity, R.color.purple_500)
                textSize = 12f
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            }
        )
        cadenceChart.axisRight.isEnabled = false
        cadenceChart.xAxis.apply {
            setDrawGridLines(true)
            granularity = 1f
            gridColor = ContextCompat.getColor(this@ResultActivity, R.color.light_gray)
        }
        cadenceChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            animateX(1000)
            invalidate()
        }

        // 5) 정확도/평균/거리 계산 및 표시
        if (cadenceList.isNotEmpty()) {
            val within = cadenceList.count { abs(it - targetCadence) <= 5 }
            val accuracy = within * 100 / cadenceList.size
            tvAccuracy.text = "±${accuracy}%"
            val avgCad = cadenceList.sum() / cadenceList.size
            tvAvgCadence.text = avgCad.toString()
            tvDistance.text = String.format("%.2f km", distance)
        } else {
            tvAccuracy.text = "±0%"
            tvAvgCadence.text = "0"
            tvDistance.text = String.format("%.2f km", distance)
        }

        // 6) 노래 카드
        songs.forEach { song ->
            val view = LayoutInflater.from(this)
                .inflate(R.layout.item_song_card, songContainer, false)
            view.findViewById<TextView>(R.id.songTitle).text = song.title
            view.findViewById<TextView>(R.id.songArtist).text = song.artist
            Glide.with(this).load(song.albumImageUrl)
                .into(view.findViewById(R.id.albumImageView))
            songContainer.addView(view)
        }

        // 7) 완료 버튼
        findViewById<Button>(R.id.resultConfirmButton)
            .setOnClickListener {
                Intent(this, RootActivity::class.java).apply {
                    putExtra("targetFragment", "home")
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(this)
                    finish()
                }
            }
    }

    // 호흡 파이차트 렌더링 함수
    private fun renderBreathCardAlways(
        chart: PieChart,
        ratioView: TextView,
        counts: BreathFeedbackCounts
    ) {
        val abnormal = counts.patternOnly + counts.organOnly + counts.bothMismatch

        if (abnormal <= 0) {
            // 모두 정상
            val entries = listOf(PieEntry(1f, "정상"))
            val dataSet = PieDataSet(entries, "").apply {
                colors = listOf(Color.parseColor("#7ED321")) // 초록
                valueTextSize = 0f
                setDrawValues(false)
            }
            chart.apply {
                description.isEnabled = false
                legend.isEnabled = false
                setUsePercentValues(false)
                isDrawHoleEnabled = true
                holeRadius = 40f
                transparentCircleRadius = 45f
                data = PieData(dataSet)
                invalidate()
                animateY(600)
            }
            ratioView.text = "정상 : 비정상\n  10 : 0"
            return
        }

        // 비정상 세부 분포 (0인 값 제외)
        val entries = mutableListOf<PieEntry>()
        if (counts.patternOnly > 0) entries.add(PieEntry(counts.patternOnly.toFloat(), "호흡 패턴만 불일치"))
        if (counts.organOnly > 0) entries.add(PieEntry(counts.organOnly.toFloat(), "호흡 기관만 불일치"))
        if (counts.bothMismatch > 0) entries.add(PieEntry(counts.bothMismatch.toFloat(), "둘 다 불일치"))

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#4A90E2"), // 파랑
                Color.parseColor("#7ED321"), // 초록
                Color.parseColor("#FF4D4F")  // 빨강
            )
            sliceSpace = 2f
            valueTextSize = 12f
            valueTextColor = Color.BLACK
        }

        val data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(chart))
            setValueTextSize(12f)
            setValueTextColor(Color.BLACK)
        }

        chart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            legend.isWordWrapEnabled = true
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER

            setUsePercentValues(true)
            isDrawHoleEnabled = true
            holeRadius = 40f
            transparentCircleRadius = 45f

            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(11f)

            this.data = data
            invalidate()
            animateY(800)
        }

        // "정상 : 비정상" 비율 텍스트
        val total = counts.normal + abnormal
        val nReduced = if (total > 0) Math.round(counts.normal * 10.0 / total).toInt() else 0
        val aReduced = 10 - nReduced
        ratioView.text = "정상 : 비정상\n  $nReduced : $aReduced"
    }

}
