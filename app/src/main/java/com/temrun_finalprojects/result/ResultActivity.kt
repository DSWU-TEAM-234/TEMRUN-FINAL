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
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
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

class ResultActivity : AppCompatActivity() {

    private lateinit var cadenceChart: LineChart
    private lateinit var pieChartBreathing: PieChart
    private lateinit var tvPatternNormal: TextView
    private lateinit var tvPatternAbnormal: TextView
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
        val cadenceList = intent.getIntegerArrayListExtra("cadenceDataList") ?: arrayListOf()
        val targetCadence = intent.getIntExtra("targetCadence", 160)

        val breathNormalAcc = intent.getIntExtra("breathNormalAcc", 0)
        val breathAbnormalAcc = intent.getIntExtra("breathAbnormalAcc", 0)
        val breathAbnl1 = intent.getIntExtra("breathAbnormalType1", 0)
        val breathAbnl2 = intent.getIntExtra("breathAbnormalType2", 0)
        val breathAbnl3 = intent.getIntExtra("breathAbnormalType3", 0)

        // 2) 뷰 바인딩
        findViewById<TextView>(R.id.ResultTimeText).text = String.format(
            "%02d:%02d",
            elapsedSeconds / 60,
            elapsedSeconds % 60
        )
        findViewById<TextView>(R.id.ResultBPMText).text = avgBPM.toString()
        findViewById<TextView>(R.id.ResultCalorieText).text =
            intent.getDoubleExtra("calorie", 0.0).toString()

        pieChartBreathing = findViewById(R.id.pieChartBreathing)
        tvPatternNormal = findViewById(R.id.textPatterNormal)
        tvPatternAbnormal = findViewById(R.id.textPatternAbnormal)

        tvAccuracy = findViewById(R.id.textCadenceAccuracy)
        tvAvgCadence = findViewById(R.id.textCadenceValue)
        tvDistance = findViewById(R.id.textCadencePrecision)

        cadenceChart = findViewById(R.id.chartCadence)
        val songContainer = findViewById<LinearLayout>(R.id.songLinearLayout)

        // 3) 호흡 PieChart
        val pieEntries = listOf(
            PieEntry(breathAbnl1.toFloat(), "타입1"),
            PieEntry(breathAbnl2.toFloat(), "타입2"),
            PieEntry(breathAbnl3.toFloat(), "타입3")
        )
        val pieDs = PieDataSet(pieEntries, "").apply {
            setColors(
                ContextCompat.getColor(this@ResultActivity, R.color.teal_700),
                ContextCompat.getColor(this@ResultActivity, R.color.purple_500),
                ContextCompat.getColor(this@ResultActivity, R.color.orange_500)
            )
            valueFormatter = PercentFormatter(pieChartBreathing)
            valueTextSize = 12f
        }
        pieChartBreathing.apply {
            data = PieData(pieDs)
            setUsePercentValues(true)
            description.isEnabled = false
            legend.isEnabled = true
            animateY(600)
            invalidate()
        }
        tvPatternNormal.text = breathNormalAcc.toString()
        tvPatternAbnormal.text = breathAbnormalAcc.toString()

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
            val meters = avgCad / 60.0 * elapsedSeconds * 0.8
            tvDistance.text = String.format("%.2f km", meters / 1000)
        } else {
            tvAccuracy.text = "±0%"
            tvAvgCadence.text = "0"
            tvDistance.text = "0 km"
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
}
