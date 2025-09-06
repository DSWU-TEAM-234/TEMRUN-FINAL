package com.temrun_finalprojects.result

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.temrun_finalprojects.BuildConfig
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.data.Preference
import com.temrun_finalprojects.data.Song
import com.temrun_finalprojects.network.FeedbackSummary
import com.temrun_finalprojects.network.RunResultRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.google.gson.GsonBuilder
import com.temrun_finalprojects.config.ApiConfig
import com.temrun_finalprojects.util.Constants.BASE_URL
import kotlin.collections.forEachIndexed
import kotlin.collections.mapIndexedNotNull
import kotlin.jvm.java

// 호흡 피드백 데이터
data class BreathFeedbackCounts(
    val normal: Int,
    val patternOnly: Int,
    val organOnly: Int,
    val bothMismatch: Int
)

class ResultActivity : AppCompatActivity() {

    // OkHttp
    private val client: OkHttpClient = OkHttpClient()
    private val mediaJson = "application/json; charset=utf-8".toMediaType()
//  private val BASE_URL = "https://4382a6a5c3d2.ngrok-free.app"

    private fun getFeedbackUrl() = ApiConfig.getFeedbackUrl()

    // 저장(POST) 연동: ViewModel 주입
    private val vm: ResultViewModel by viewModels()

    // UI
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
        val accuracyDouble = intent.getDoubleExtra("cadenceAccuracy", 0.0)

        val counts = BreathFeedbackCounts(
            normal = intent.getIntExtra("breath_normal", 0),
            patternOnly = intent.getIntExtra("breath_patternOnly", 0),
            organOnly = intent.getIntExtra("breath_organOnly", 0),
            bothMismatch = intent.getIntExtra("breath_bothMismatch", 0)
        )

        // 뷰 바인딩
        findViewById<TextView>(R.id.ResultTimeText).text =
            String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
        findViewById<TextView>(R.id.ResultBPMText).text = avgBPM.toString()
        findViewById<TextView>(R.id.ResultCalorieText).text = String.format("%.1f", calorie)

        val songContainer = findViewById<LinearLayout>(R.id.songLinearLayout)

        // 호흡 PieChart
        val breathChart = findViewById<PieChart>(R.id.breathPieChart)
        val breathRatio = findViewById<TextView>(R.id.breathRatioText)
        renderBreathCardAlways(breathChart, breathRatio, counts)

        // 케이던스 LineChart
        cadenceChart = findViewById(R.id.chartCadence)
        cadenceChart.setNoDataText("케이던스 데이터가 없어요")
        tvAccuracy = findViewById(R.id.textCadenceAccuracy)
        tvAvgCadence = findViewById(R.id.textCadenceValue)
        tvDistance = findViewById(R.id.textDistance)
        tvDistance.text = String.format("%.01f km", distance)

        if (cadenceList.isNotEmpty()) {
            val entries = cadenceList.mapIndexed { idx, value ->
                Entry(idx.toFloat(), value.toFloat())
            }
            val dataSet = LineDataSet(entries, "케이던스").apply {
                color = ContextCompat.getColor(this@ResultActivity, R.color.teal_700)
                setDrawCircles(true)
                circleRadius = 3f
                setDrawValues(false)
                lineWidth = 2f
            }
            cadenceChart.data = LineData(dataSet)

            // 목표 케이던스 기준선
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
            cadenceChart.axisRight.isEnabled = false //
            cadenceChart.data?.notifyDataChanged()
            cadenceChart.notifyDataSetChanged()
            cadenceChart.invalidate()

            tvAvgCadence.text = cadenceList.average().toInt().toString()
            tvAccuracy.text = String.format("±%.0f", accuracyDouble)
        } else {
            tvAccuracy.text = "±0"
            tvAvgCadence.text = "0"
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

            // 호흡 비율(정상/비정상 %)
            val abnormalTotal = counts.patternOnly + counts.organOnly + counts.bothMismatch
            val totalBreath = counts.normal + abnormalTotal
            val breathNormalPercent =
                if (totalBreath > 0) ((counts.normal * 100.0) / totalBreath).toInt() else 0
            val breathAbnormalPercent = 100 - breathNormalPercent

            // 케이던스 통계
            val avgCadenceDouble = if (cadenceList.isNotEmpty()) cadenceList.average() else 0.0
            val maxCadence = cadenceList.maxOrNull() ?: 0
            val minCadence = cadenceList.minOrNull() ?: 0

            // 음악 BPM 리스트 (없으면 빈 리스트)
            val musicBpmList: List<Int> = emptyList()

            // 요청 바디 구성 (network 패키지 DTO 사용)
            val req = RunResultRequest(
                duration = elapsedSeconds,                                 // Int(초)
                distance = distance,                                       // Double(km)
                calories = calorie.toInt(),                                // Int(kcal)
                avgCadence = avgCadenceDouble,                             // Double(spm)
                maxCadence = maxCadence,
                minCadence = minCadence,
                abnormalCount = abnormalTotal,
                musicCount = songsForUi.size,
                cadenceAccuracy = accuracyDouble,                          // Double(%)
                breathNormalAcc = breathNormalPercent,                     // Int(%)
                breathAbnormalAcc = breathAbnormalPercent,                 // Int(%)
                musicBpmList = musicBpmList,
                feedbackSummary = FeedbackSummary(
                    type1 = counts.patternOnly,                            // 호흡 패턴만 불일치
                    type2 = counts.organOnly,                              // 호흡 기관만 불일치
                    type3 = counts.bothMismatch                            // 둘 다 불일치
                ),
                cadenceHistory = cadenceList.toList()
            )

            // 버튼 비활성화
            setLoading(true, resultConfirmButton)

            // 요청 바디 구성 (req 만든 직후)
            val gson = GsonBuilder().setPrettyPrinting().create()
            val runJson = gson.toJson(req)
            logLong("RunResultPayload", ">>> POST $BASE_URL/api/runs/$runId/results\n$runJson")

            // 저장 호출 (토큰 필요 시 token="Bearer ..." 전달)
            vm.save(runId, req, token = null)



            // 노래 피드백(있을 때만 전송)
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

            if (items.isNotEmpty()) {
                val url = ApiConfig.getFeedbackUrl()
                lifecycleScope.launch(Dispatchers.IO) {
                    var failed = false
                    items.forEach { obj ->
                        val ok = postOne(url, obj)
                        if (!ok) failed = true
                    }
                    withContext(Dispatchers.Main) {
                        if (!failed) {
                            Toast.makeText(this@ResultActivity, "피드백 전송 완료", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@ResultActivity, "일부 피드백 전송 실패", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        // 저장 상태 관찰 → 성공 시 홈 이동
        vm.saveState.observe(this) { state ->
            when (state) {
                ResultViewModel.SaveState.Success -> {
                    Toast.makeText(this, "결과가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    setLoading(false, findViewById(R.id.resultConfirmButton))
                    goHome()
                }
                ResultViewModel.SaveState.Error -> {
                    Toast.makeText(this, "저장 실패: 네트워크/서버를 확인하세요.", Toast.LENGTH_SHORT).show()
                    findViewById<Button>(R.id.resultConfirmButton).isEnabled = true
                }
                ResultViewModel.SaveState.Saving -> { /* 로딩표시 필요시 구현 */ }
                else -> Unit
            }
        }
    }

    //긴 로그가 잘리지 않게 도우미 함수 추가
    private fun logLong(tag: String, msg: String) {
        if (!BuildConfig.DEBUG) return
        val chunkSize = 3500
        var i = 0
        while (i < msg.length) {
            val end = (i + chunkSize).coerceAtMost(msg.length)
            Log.d(tag, msg.substring(i, end))
            i = end
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
                return resp.isSuccessful
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
        this.finish()
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
        chart.setUsePercentValues(true)

        if (abnormal <= 0) {
            val entries = listOf(PieEntry(1f, "정상"))
            val dataSet = PieDataSet(entries, "").apply {
                colors = listOf(Color.parseColor("#7ED321"))
                setDrawValues(false) // 값 숨김
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
            colors = listOf(
                Color.parseColor("#4A90E2"),
                Color.parseColor("#7ED321"),
                Color.parseColor("#FF4D4F")
            )
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
