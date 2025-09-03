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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.data.Song
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter


// 호흡 피드백 데이터(정상 + 비정상 세부 분포)
data class BreathFeedbackCounts(
    val normal: Int,        //정상호흡
    val patternOnly: Int,   //호흡 패턴만 틀린 경우
    val organOnly: Int,    //호흡 기관만 틀린 경우
    val bothMismatch: Int   //둘 다 틀린 경우
)

class ResultActivity: AppCompatActivity() {
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

        val hours = time / 3600
        val minutes = (time % 3600) / 60
        val seconds = time % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

        findViewById<TextView>(R.id.ResultTimeText).text = timeFormatted
        findViewById<TextView>(R.id.ResultBPMText).text =  averageBPM.toString()
        findViewById<TextView>(R.id.ResultCalorieText).text = calorie.toString()

        val resultConfirmButton : Button = findViewById(R.id.resultConfirmButton)

        resultConfirmButton.setOnClickListener {
            val intent = Intent(this, RootActivity::class.java)
            intent.putExtra("targetFragment", "home")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        val breathChart = findViewById<PieChart>(R.id.breathPieChart)
        val breathRatio = findViewById<TextView>(R.id.breathRatioText)

        //더미 데이터 값
        /*
        val dummyCounts = BreathFeedbackCounts(
            normal = 12,
            patternOnly = 3,
            organOnly = 4,
            bothMismatch = 1
        )

        renderBreathCardAlways(breathChart, breathRatio, dummyCounts)
        */
        // Intent에서 받아온 실제 예측 결과 사용
        val counts = BreathFeedbackCounts(
            normal = intent.getIntExtra("breath_normal", 0),
            patternOnly = intent.getIntExtra("breath_patternOnly", 0),
            organOnly = intent.getIntExtra("breath_organOnly", 0),
            bothMismatch = intent.getIntExtra("breath_bothMismatch", 0)
        )
        renderBreathCardAlways(breathChart, breathRatio, counts)

    }

    //파이차트 그리는 함수
    //비정상 합계 0이면 "모두 정상"
    private fun renderBreathCardAlways(
        chart: PieChart,
        ratioView: TextView,
        counts: BreathFeedbackCounts
    ) {

        val abnormal = counts.patternOnly + counts.organOnly + counts.bothMismatch

        // 전부 정상일 때
        if (abnormal <= 0) {
            val entries = listOf(PieEntry(1f, "정상"))
            val dataSet = PieDataSet(entries, "").apply {
                colors = listOf(Color.parseColor("#7ED321")) // 초록
                valueTextSize = 0f
                setDrawValues(false)
                sliceSpace = 0f
            }
            chart.apply {
                description.isEnabled = false
                legend.isEnabled = false
                setUsePercentValues(false)
                isDrawHoleEnabled = true //true이면 도넛차트
                //도넛차트와 관련된 속성

                holeRadius = 40f
                transparentCircleRadius = 45f
                /*
                centerText = "모두 정상"
                setCenterTextSize(12f)
                 */
                setEntryLabelTextSize(0f)
                data = PieData(dataSet)
                highlightValues(null)
                invalidate()
                animateY(600)
            }
            ratioView.text = "정상 : 비정상\n  10 : 0\n "
            return
        }

        // 비정상 3분류의 내부 분포를 파이로 생성
        val entries = listOf(
            PieEntry(counts.patternOnly.toFloat(), "호흡 패턴만 불일치 "),
            PieEntry(counts.organOnly.toFloat(),  "호흡 기관만 불일치 "),
            PieEntry(counts.bothMismatch.toFloat(),"둘 다 불일치")
        )

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#4A90E2"), // 파랑: 패턴만
                Color.parseColor("#7ED321"), // 초록: 기간만
                Color.parseColor("#FF4D4F")  // 빨강: 둘 다
            )
            sliceSpace = 2f
            valueTextSize = 12f
            valueTextColor = Color.BLACK
        }

        val data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(chart)) // 내부 합계를 100%로 환산
            setValueTextSize(12f)
            setValueTextColor(Color.BLACK) //파이 차트 위 글자 색
        }

        chart.apply {
            description.isEnabled = false
            legend.isEnabled = true //범례
            legend.isWordWrapEnabled = true
            legend.orientation = Legend.LegendOrientation.HORIZONTAL  //가로 방향
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER //중앙 정렬

            setUsePercentValues(true) //파이 값은 비정상, 백분율(%)로 표시
            isDrawHoleEnabled = true //false는 파이차트

            //도넛 차트로 만들 시 활용할 속성
            holeRadius = 40f
            transparentCircleRadius = 45f
            /*
            centerText = "호흡 불일치 분포"
            setCenterTextSize(12f)
             */

            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(11f)

            this.data = data
            highlightValues(null)
            invalidate()
            animateY(800)
        }

        // "정상 : 비정상"을 출력
        val total = counts.normal + abnormal
        val nReduced = if (total > 0) Math.round(counts.normal * 10.0 / total).toInt() else 0
        val aReduced = 10 - nReduced  // 항상 합이 10이 되도록
        ratioView.text = "정상 : 비정상\n  $nReduced : $aReduced"
    }

}