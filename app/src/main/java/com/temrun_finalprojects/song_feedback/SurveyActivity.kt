package com.temrun_finalprojects.song_feedback

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.data.Preference
import com.temrun_finalprojects.data.SongFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SurveyActivity : AppCompatActivity() {

    // 샘플 목록 (Spotify ID/URI 유지)
    private val songs = mutableListOf(
        SongFeedback(id = "spotify:track:04HGddGSz9qgxiFJkR7oMy", title = "너와의 모든 지금", artist = "JAESSBEE"),
        SongFeedback(id = "spotify:track:4ytyLpIwUXbdFsNOvgNnmP", title = "사계", artist = "태연"),
        SongFeedback(id = "spotify:track:0hqj5JBnFt1BHEz2UCFwrl", title = "어떻게 이별까지 사랑하겠어, 널 사랑하는 거지", artist = "AKMU"),
        SongFeedback(id = "spotify:track:16FsCP14q4NnOmygfIy3WP", title = "Colors", artist = "스텔라장"),
        SongFeedback(id = "spotify:track:24ntZeyCrVePmN3nUYhfLx", title = "불가행력", artist = "Vaundy")
    )

    // SpotifyID → YouTube 전체 링크(하이라이트 URL)
    private val youtubeBySpotifyId = mapOf(
        "spotify:track:04HGddGSz9qgxiFJkR7oMy" to "https://youtu.be/XV0lSvr0huU?feature=shared&t=44",
        "spotify:track:4ytyLpIwUXbdFsNOvgNnmP"  to "https://youtu.be/4HG_CJzyX6A?feature=shared&t=72",
        "spotify:track:0hqj5JBnFt1BHEz2UCFwrl"   to "https://youtu.be/m3DZsBw5bnE?feature=shared&t=82",
        "spotify:track:16FsCP14q4NnOmygfIy3WP"  to "https://youtu.be/LMLdOpwHtIg?feature=shared&t=34",
        "spotify:track:24ntZeyCrVePmN3nUYhfLx"  to "https://youtu.be/Gbz2C2gQREI?feature=shared&t=41"
    )

    private var idx = 0

    // UI
    private lateinit var tvTitle: TextView
    private lateinit var tvMeta: TextView
    private lateinit var btnLike: Button
    private lateinit var btnDislike: Button
    private lateinit var btnSkip: Button
    private lateinit var btnSubmitAll: Button
    private lateinit var progress: ProgressBar
    private lateinit var webPlayer: WebView

    // 네트워크
    private val client by lazy { OkHttpClient() }
    private val mediaJson by lazy { "application/json; charset=utf-8".toMediaType() }

    // 디버그: 전송 생략하고 다음 화면으로
    private val DEBUG_PREVIEW = false

    // 환경
    private var userId: String? = null
    // TODO: 서버 재기동 시 교체
    private val BASE_URL = "https://339cdd456ce9.ngrok-free.app"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 설문 완료 여부 체크 (AppPrefs.survey_done)
        val appPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        if (appPrefs.getBoolean("survey_done", false)) {
            // 이미 설문을 마쳤다면 설문 없이 Root로 이동
            startActivity(Intent(this, RootActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_survey)

        // userId 로드 (키명 주의: "user_id")
        val prefs = getSharedPreferences("AppUser", MODE_PRIVATE)
        userId = prefs.getString("user_id", null) ?: run {
            Toast.makeText(this, "userId가 없습니다. 로그인/회원가입 필요.", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        bindViews()
        bindClicks()

        // WebView 설정(한 번만)
        with(webPlayer.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
        }
        webPlayer.webViewClient = object : android.webkit.WebViewClient() {}
        webPlayer.webChromeClient = object : android.webkit.WebChromeClient() {}

        showSong(idx)
    }

    private fun bindViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvMeta = findViewById(R.id.tvMeta)
        btnLike = findViewById(R.id.btnLike)
        btnDislike = findViewById(R.id.btnDislike)
        btnSkip = findViewById(R.id.btnSkip)
        btnSubmitAll = findViewById(R.id.btnSubmitAll)
        progress = findViewById(R.id.progress)
        webPlayer = findViewById(R.id.webPlayer)
    }

    private fun bindClicks() {
        btnLike.setOnClickListener { markAndNext(Preference.LIKE) }
        btnDislike.setOnClickListener { markAndNext(Preference.DISLIKE) }
        btnSkip.setOnClickListener { markAndNext(Preference.NONE) }
        btnSubmitAll.setOnClickListener { sendAllFeedbacks() }
    }

    private fun showSong(i: Int) {
        if (i in songs.indices) {
            val s = songs[i]
            tvTitle.text = "선호 조사 ${i + 1}/${songs.size}"
            tvMeta.text = "${s.title} - ${s.artist}"

            // Spotify ID → YouTube 전체 링크 → 임베드 URL 로드
            youtubeBySpotifyId[s.id]?.let { loadYouTube(it) }
                ?: run {
                    Toast.makeText(this, "YouTube 링크가 없습니다.", Toast.LENGTH_SHORT).show()
                    webPlayer.loadUrl("about:blank")
                }
        }
        btnSubmitAll.isEnabled = songs.all { it.preference != Preference.NONE } || idx >= songs.lastIndex
    }

    // youtu.be/<id>?t=44 → https://www.youtube.com/embed/<id>?playsinline=1&start=44
    private fun toEmbedUrl(youtuBeUrl: String): String {
        val u = android.net.Uri.parse(youtuBeUrl)
        val id = u.lastPathSegment ?: return youtuBeUrl
        val start = (u.getQueryParameter("t") ?: "0").filter { it.isDigit() }.ifEmpty { "0" }
        return "https://www.youtube.com/embed/$id?playsinline=1&autoplay=0&modestbranding=1&start=$start"
    }
    private fun loadYouTube(fullYtUrl: String) {
        webPlayer.loadUrl(toEmbedUrl(fullYtUrl))
    }

    private fun markAndNext(pref: Preference) {
        songs[idx].preference = pref
        if (idx < songs.lastIndex) {
            idx++
            showSong(idx)
        } else {
            Toast.makeText(this, "제출 버튼을 눌러 전송하세요.", Toast.LENGTH_SHORT).show()
            btnSubmitAll.isEnabled = true
        }
    }

    private fun sendAllFeedbacks() {
        val uid = userId ?: run {
            Toast.makeText(this, "userId가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 전송할 항목(스킵 제외)
        val items = songs.mapNotNull { s ->
            val rating = when (s.preference) {
                Preference.LIKE -> 5
                Preference.DISLIKE -> 1
                Preference.NONE -> 0
            }
            if (rating == 0 || s.id.isBlank()) null
            else {
                // 서버는 단건 DTO: {userId, trackId, rating}
                // trackId는 순수 Spotify ID로 보냄
                JSONObject().apply {
                    put("userId", uid)
                    put("trackId", s.id.substringAfterLast(":"))
                    put("rating", rating)
                }
            }
        }

        if (items.isEmpty()) {
            Toast.makeText(this, "보낼 피드백이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "$BASE_URL/api/runs/feedback"

        if (DEBUG_PREVIEW) {
            val sample = items.first().toString(2)
            android.util.Log.d("SurveyDebug", "[REQUEST_URL] $url")
            android.util.Log.d("SurveyDebug", "[REQUEST_BODY_SAMPLE]\n$sample")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("전송 예정 (단건 샘플)")
                .setMessage("URL:\n$url\n\nBody:\n$sample\n\n총 ${items.size}건이 순차 전송됩니다.")
                .setPositiveButton("다음 화면") { _, _ ->
                    Toast.makeText(this, "디버그 모드: 서버 호출 생략", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SurveyActivity, RootActivity::class.java))
                    finish()
                }
                .setNegativeButton("취소", null)
                .show()
            return
        }

        // 순차 전송
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { setLoading(true) }

            var failed = false
            items.forEachIndexed { index, obj ->
                val ok = postOne(url, obj)
                if (!ok) {
                    failed = true
                    android.util.Log.e("SurveyDebug", "전송 실패 index=$index body=$obj")
                    // 계속 보낼지 중단할지는 정책에 따라 결정.
                    // 여기선 계속 진행 (부분 성공 허용).
                }
            }

            withContext(Dispatchers.Main) {
                setLoading(false)
                if (!failed) {
                    // 설문 완료로 기록
                    getSharedPreferences("AppPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("survey_done", true)
                        .apply()

                    Toast.makeText(this@SurveyActivity, "전송 완료", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SurveyActivity, RootActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@SurveyActivity, "일부 전송 실패(로그 확인)", Toast.LENGTH_LONG).show()
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
                val body = resp.body?.string().orEmpty()
                android.util.Log.d("SurveyDebug", ">>> POST $url\nREQ=$obj\n<<< ${resp.code} ${resp.message}\n$body")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("SurveyDebug", "요청 오류", e)
            false
        }
    }

    // 메인 보장형
    private fun setLoading(loading: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { setLoading(loading) }
            return
        }
        progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        btnLike.isEnabled = !loading
        btnDislike.isEnabled = !loading
        btnSkip.isEnabled = !loading
        btnSubmitAll.isEnabled = !loading
    }

    override fun onResume() { super.onResume(); webPlayer.onResume() }
    override fun onPause() { webPlayer.onPause(); super.onPause() }
    override fun onDestroy() {
        try {
            webPlayer.loadUrl("about:blank")
            webPlayer.stopLoading()
            webPlayer.clearHistory()
            webPlayer.removeAllViews()
            webPlayer.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
