package com.temrun_finalprojects.result

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.PercentFormatter
import com.temrun_finalprojects.BuildConfig
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.config.ApiConfig
import com.temrun_finalprojects.data.Preference
import com.temrun_finalprojects.data.Song
import com.temrun_finalprojects.util.Constants.BASE_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.math.abs

// 호흡 피드백 데이터
data class BreathFeedbackCounts(
    val normal: Int,
    val patternOnly: Int,
    val organOnly: Int,
    val bothMismatch: Int
)

class ResultActivity : AppCompatActivity() {

    private val client by lazy { OkHttpClient() }
    private val mediaJson by lazy { "application/json; charset=utf-8".toMediaType() }
//    private val BASE_URL = "https://4a0bc02d836c.ngrok-free.app"
    private fun getFeedbackUrl() = ApiConfig.getFeedbackUrl()

    private lateinit var cadenceChart: LineChart
    private lateinit var tvAccuracy: TextView
    private lateinit var tvAvgCadence: TextView
    private lateinit var tvDistance: TextView

    companion object {
        const val EXTRA_DEBUG_STUB = "debug_stub"
    }

    private fun loadUserIdOrNull(): String? =
        getSharedPreferences("AppUser", MODE_PRIVATE)
            .getString("user_id", null)?.takeIf { it.isNotBlank() }

    private fun loadRunIdOrNull(): String? =
        getSharedPreferences("AppUser", MODE_PRIVATE)
            .getString("run_id", null)?.takeIf { it.isNotBlank() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_result)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.result)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        // 데이터 수신
        val debugStub = intent.getBooleanExtra(EXTRA_DEBUG_STUB, false)
        val receivedSongs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("songs", Song::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Song>("songs")
        } ?: arrayListOf()

        val songsForUi = when {
            receivedSongs.isNotEmpty() -> receivedSongs
            debugStub || BuildConfig.DEBUG -> makeStubSongs()
            else -> arrayListOf()
        }

        val elapsedSeconds = intent.getIntExtra("time", 0)
        val avgBPM = intent.getIntExtra("averageBPM", 0)
        val distance = intent.getDoubleExtra("distance", 0.0)
        val calorie = intent.getDoubleExtra("calorie", 0.0)
        val cadenceList = intent.getIntegerArrayListExtra("cadenceDataList") ?: arrayListOf()
        val targetCadence = intent.getIntExtra("targetCadence", 160)

        val counts = BreathFeedbackCounts(
            normal = intent.getIntExtra("breath_normal", 0),
            patternOnly = intent.getIntExtra("breath_patternOnly", 0),
            organOnly = intent.getIntExtra("breath_organOnly", 0),
            bothMismatch = intent.getIntExtra("breath_bothMismatch", 0)
        )

        // 뷰 바인딩
        findViewById<TextView>(R.id.ResultTimeText).text = String.format(
            "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60
        )
        findViewById<TextView>(R.id.ResultBPMText).text = avgBPM.toString()
        findViewById<TextView>(R.id.ResultCalorieText).text = calorie.toString()

        val songContainer = findViewById<LinearLayout>(R.id.songLinearLayout)

        // 호흡 PieChart
        val breathChart = findViewById<PieChart>(R.id.breathPieChart)
        val breathRatio = findViewById<TextView>(R.id.breathRatioText)
        renderBreathCardAlways(breathChart, breathRatio, counts)

        // 케이던스 LineChart
        cadenceChart = findViewById(R.id.chartCadence)
        tvAccuracy = findViewById(R.id.textCadenceAccuracy)
        tvAvgCadence = findViewById(R.id.textCadenceValue)
        tvDistance = findViewById(R.id.textCadencePrecision)

        if (cadenceList.isNotEmpty()) {
            val entries = cadenceList.mapIndexed { idx, value ->
                Entry(idx.toFloat(), value.toFloat())
            }
            val dataSet = LineDataSet(entries, "케이던스").apply {
                color = ContextCompat.getColor(this@ResultActivity, R.color.teal_700)
                setDrawCircles(true); circleRadius = 3f
                setDrawValues(false); lineWidth = 2f
            }
            cadenceChart.data = LineData(dataSet)
            // 2) 목표 케이던스 기준선 추가
            cadenceChart.axisLeft.apply {

                axisMinimum = 0f
                axisMaximum = (targetCadence + 20).toFloat()
                val limitLine = LimitLine(targetCadence.toFloat(), "목표($targetCadence)").apply {
                    lineWidth = 2f
                    lineColor = ContextCompat.getColor(this@ResultActivity, R.color.purple_500)
                    textColor = ContextCompat.getColor(this@ResultActivity, R.color.purple_500)
                    textSize = 12f
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                }
                addLimitLine(limitLine)
            }
            cadenceChart.invalidate()
        } else {
            // 데이터 없을 때 처리
            findViewById<TextView>(R.id.textCadenceValue).text = "0"
            findViewById<TextView>(R.id.textCadenceAccuracy).text = "±0%"
        }

        Log.d("ResultActivity", "cadenceList size=${cadenceList.size}")

        val chart = findViewById<LineChart>(R.id.chartCadence)
        chart.setNoDataText("케이던스 데이터가 없어요")

        // 정확도/평균 케이던스/거리
        if (cadenceList.isNotEmpty()) {
            // 1) Entry 생성 및 LineDataSet 생성
            val entries = cadenceList.mapIndexed { idx, value -> Entry(idx.toFloat(), value.toFloat()) }
            val dataSet = LineDataSet(entries, "케이던스").apply {
                color = ContextCompat.getColor(this@ResultActivity, R.color.teal_700)
                setDrawCircles(true)
                circleRadius = 3f
                setDrawValues(false)
                lineWidth = 2f
            }

            // 2) 차트에 데이터 할당
            cadenceChart.data = LineData(dataSet)

            // 3) 축 및 레전드 설정 (생략)

            // 4) 데이터 변경, 차트 갱신
            cadenceChart.data?.notifyDataChanged()
            cadenceChart.notifyDataSetChanged()
            cadenceChart.invalidate()
        } else {
            tvAccuracy.text = "±0%"
            tvAvgCadence.text = "0"
            tvDistance.text = String.format("%.2f km", distance)
        }

        // 노래 카드 + 좋아요/싫어요
        val preferences = MutableList(songsForUi.size) { Preference.NONE }

        fun applyIcons(btnLike: ImageView, btnDislike: ImageView, pref: Preference) {
            val likeIcon =
                if (pref == Preference.LIKE) R.drawable.thumb_up_green else R.drawable.thumb_up_line
            val dislikeIcon =
                if (pref == Preference.DISLIKE) R.drawable.thumb_down_green else R.drawable.thumb_down_line
            btnLike.setImageResource(likeIcon)
            btnDislike.setImageResource(dislikeIcon)
        }

        songsForUi.forEachIndexed { index, song ->
            val view = LayoutInflater.from(this)
                .inflate(R.layout.item_song_card, songContainer, false)
            view.findViewById<TextView>(R.id.songTitle).text = song.title
            view.findViewById<TextView>(R.id.songArtist).text = song.artist
            Glide.with(this).load(song.albumImageUrl)
                .into(view.findViewById(R.id.albumImageView))

            val btnLike = view.findViewById<ImageView>(R.id.btnLike)
            val btnDislike = view.findViewById<ImageView>(R.id.btnDislike)

            applyIcons(btnLike, btnDislike, preferences[index])

            btnLike.setOnClickListener {
                preferences[index] =
                    if (preferences[index] == Preference.LIKE) Preference.NONE else Preference.LIKE
                applyIcons(btnLike, btnDislike, preferences[index])
            }
            btnDislike.setOnClickListener {
                preferences[index] =
                    if (preferences[index] == Preference.DISLIKE) Preference.NONE else Preference.DISLIKE
                applyIcons(btnLike, btnDislike, preferences[index])
            }

            songContainer.addView(view)
        }

        // 완료 버튼 (서버 전송)
        val resultConfirmButton: Button = findViewById(R.id.resultConfirmButton)
        resultConfirmButton.setOnClickListener {
            val userId = loadUserIdOrNull()
            val runId = loadRunIdOrNull()
            if (userId == null || runId == null) {
                Toast.makeText(this, "로그인 정보 또는 실행 세션(runId)이 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val items = songsForUi.mapIndexedNotNull { i, s ->
                val rating = when (preferences[i]) {
                    Preference.LIKE -> 5
                    Preference.DISLIKE -> 1
                    Preference.NONE -> 0
                }
                if (rating == 0 || s.trackId.isNullOrBlank()) null
                else {
                    JSONObject().apply {
                        put("userId", userId)
                        put("trackId", s.trackId.substringAfterLast(":"))
                        put("rating", rating)
                    }
                }
            }

            if (items.isEmpty()) {
                Toast.makeText(this, "보낼 피드백이 없습니다.", Toast.LENGTH_SHORT).show()
                goHome()
                return@setOnClickListener
            }

            val url = "$BASE_URL/api/runs/feedback"
            setLoading(true, resultConfirmButton)

            lifecycleScope.launch(Dispatchers.IO) {
                var failed = false
                items.forEachIndexed { idx, obj ->
                    val ok = postOne(url, obj)
                    if (!ok) failed = true
                }
                withContext(Dispatchers.Main) {
                    setLoading(false, resultConfirmButton)
                    if (!failed) {
                        Toast.makeText(this@ResultActivity, "피드백 전송 완료", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ResultActivity, "일부 전송 실패", Toast.LENGTH_LONG).show()
                    }
                    goHome()
                }
            }
        }
    }

    private fun postOne(url: String, obj: JSONObject): Boolean {
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .post(obj.toString().toRequestBody(mediaJson))
                .build()
            client.newCall(req).execute().use { resp ->
                Log.d("SongFeedbackDebug", ">>> POST $url\nREQ=$obj\n<<< ${resp.code}")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SongFeedbackDebug", "요청 오류", e)
            false
        }
    }

    private fun setLoading(loading: Boolean, button: Button) {
        button.isEnabled = !loading
    }

    private fun goHome() {
        val intent = Intent(this, RootActivity::class.java).apply {
            putExtra("targetFragment", "home")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun makeStubSongs(): ArrayList<Song> {
        return arrayListOf(
            Song("spotify:track:TEST_1", "테스트 곡 1", "아티스트 A", "https://picsum.photos/seed/1/200/200"),
            Song("spotify:track:TEST_2", "테스트 곡 2", "아티스트 B", "https://picsum.photos/seed/2/200/200"),
            Song("spotify:track:TEST_3", "테스트 곡 3", "아티스트 C", "https://picsum.photos/seed/3/200/200")
        )
    }

    private fun renderBreathCardAlways(chart: PieChart, ratioView: TextView, counts: BreathFeedbackCounts) {
        val abnormal = counts.patternOnly + counts.organOnly + counts.bothMismatch
        if (abnormal <= 0) {
            val entries = listOf(PieEntry(1f, "정상"))
            val dataSet = PieDataSet(entries, "").apply {
                colors = listOf(Color.parseColor("#7ED321"))
                setDrawValues(false)
            }
            chart.data = PieData(dataSet)
            chart.invalidate()
            ratioView.text = "정상 : 비정상\n  10 : 0"
            return
        }
        val entries = mutableListOf<PieEntry>()
        if (counts.patternOnly > 0) entries.add(PieEntry(counts.patternOnly.toFloat(), "호흡 패턴만 불일치"))
        if (counts.organOnly > 0) entries.add(PieEntry(counts.organOnly.toFloat(), "호흡 기관만 불일치"))
        if (counts.bothMismatch > 0) entries.add(PieEntry(counts.bothMismatch.toFloat(), "둘 다 불일치"))

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(Color.parseColor("#4A90E2"), Color.parseColor("#7ED321"), Color.parseColor("#FF4D4F"))
            sliceSpace = 2f
            valueTextSize = 12f
        }
        val data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(chart)) }
        chart.data = data
        chart.invalidate()

        val total = counts.normal + abnormal
        val nReduced = if (total > 0) Math.round(counts.normal * 10.0 / total).toInt() else 0
        val aReduced = 10 - nReduced
        ratioView.text = "정상 : 비정상\n  $nReduced : $aReduced"
    }
}
