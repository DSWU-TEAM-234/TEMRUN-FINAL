package com.temrun_finalprojects.result

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.temrun_finalprojects.BuildConfig
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.data.Preference
import com.temrun_finalprojects.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ResultActivity : AppCompatActivity() {

    private val client by lazy { OkHttpClient() }
    private val mediaJson by lazy { "application/json; charset=utf-8".toMediaType() }

    // 서버 베이스 URL
    // TODO: 서버 재기동 시 교체
    private val BASE_URL = "https://4a0bc02d836c.ngrok-free.app"

    // 디버그 플래그
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
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val songContainer = findViewById<LinearLayout>(R.id.songLinearLayout)

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

        // 각 곡의 선택 상태
        val preferences = MutableList(songsForUi.size) { Preference.NONE }

        fun applyIcons(btnLike: ImageView, btnDislike: ImageView, pref: Preference) {
            val likeIcon =
                if (pref == Preference.LIKE) R.drawable.thumb_up_green else R.drawable.thumb_up_line
            val dislikeIcon =
                if (pref == Preference.DISLIKE) R.drawable.thumb_down_green else R.drawable.thumb_down_line
            btnLike.setImageResource(likeIcon)
            btnDislike.setImageResource(dislikeIcon)
            btnLike.contentDescription = if (pref == Preference.LIKE) "좋아요 선택됨" else "좋아요 선택"
            btnDislike.contentDescription = if (pref == Preference.DISLIKE) "싫어요 선택됨" else "싫어요 선택"
        }

        // 카드 렌더 + 클릭 리스너
        songsForUi.forEachIndexed { index, song ->
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_song_card, songContainer, false)

            val title = itemView.findViewById<TextView>(R.id.songTitle)
            val artist = itemView.findViewById<TextView>(R.id.songArtist)
            val image = itemView.findViewById<ImageView>(R.id.albumImageView)
            val btnLike = itemView.findViewById<ImageView>(R.id.btnLike)
            val btnDislike = itemView.findViewById<ImageView>(R.id.btnDislike)

            title.text = song.title
            artist.text = song.artist
            Glide.with(itemView.context).load(song.albumImageUrl).into(image)

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

            songContainer.addView(itemView)
        }

        val time = intent.getIntExtra("time", 0)
        val calorie = intent.getDoubleExtra("calorie", 0.0)
        val distance = intent.getFloatExtra("distance", 0f) // 전달 타입 유지
        val averageBPM = intent.getIntExtra("averageBPM", 0)

        val minutes = (time % 3600) / 60
        val seconds = time % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

        findViewById<TextView>(R.id.ResultTimeText).text = timeFormatted
        findViewById<TextView>(R.id.ResultBPMText).text = averageBPM.toString()
        findViewById<TextView>(R.id.ResultCalorieText).text = calorie.toString()

        val resultConfirmButton: Button = findViewById(R.id.resultConfirmButton)

        resultConfirmButton.setOnClickListener {
            val userId = loadUserIdOrNull()
            val runId = loadRunIdOrNull()

            if (userId == null || runId == null) {
                Toast.makeText(this, "로그인 정보 또는 실행 세션(runId)이 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 전송할 항목(스킵 제외) → 설문 코드와 동일 형태
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
                        // spotify:track:XYZ → XYZ
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

            // 디버그 로그
            if (BuildConfig.DEBUG) {
                Log.d("SongFeedbackDebug", "runId=$runId, count=${items.size}")
                Log.d("SongFeedbackDebug", "sample=${items.first().toString(2)}")
            }

            // 순차 전송
            val url = "$BASE_URL/api/runs/feedback"
            setLoading(true, resultConfirmButton)

            lifecycleScope.launch(Dispatchers.IO) {
                var failed = false
                items.forEachIndexed { idx, obj ->
                    val ok = postOne(url, obj)
                    if (!ok) {
                        failed = true
                        Log.e("SongFeedbackDebug", "전송 실패 index=$idx body=$obj")
                    }
                }

                withContext(Dispatchers.Main) {
                    setLoading(false, resultConfirmButton)
                    if (!failed) {
                        Toast.makeText(this@ResultActivity, "피드백 전송 완료", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ResultActivity, "일부 전송 실패(로그 확인)", Toast.LENGTH_LONG).show()
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
                val body = resp.body?.string().orEmpty()
                Log.d("SongFeedbackDebug", ">>> POST $url\nREQ=$obj\n<<< ${resp.code} ${resp.message}\n$body")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SongFeedbackDebug", "요청 오류", e)
            false
        }
    }

    private fun setLoading(loading: Boolean, button: Button) {
        button.isEnabled = !loading
        // 필요하면 ProgressBar 추가해서 보여줘도 됨
    }

    private fun goHome() {
        val intent = Intent(this, RootActivity::class.java).apply {
            putExtra("targetFragment", "home")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    // 디버그용 더미 데이터
    private fun makeStubSongs(): ArrayList<Song> {
        return arrayListOf(
            Song(
                trackId = "spotify:track:TEST_1",
                title = "테스트 곡 1",
                artist = "아티스트 A",
                albumImageUrl = "https://picsum.photos/seed/1/200/200"
            ),
            Song(
                trackId = "spotify:track:TEST_2",
                title = "테스트 곡 2",
                artist = "아티스트 B",
                albumImageUrl = "https://picsum.photos/seed/2/200/200"
            ),
            Song(
                trackId = "spotify:track:TEST_3",
                title = "테스트 곡 3",
                artist = "아티스트 C",
                albumImageUrl = "https://picsum.photos/seed/3/200/200"
            ),
            Song(
                trackId = "spotify:track:TEST_4",
                title = "테스트 곡 4",
                artist = "아티스트 D",
                albumImageUrl = "https://picsum.photos/seed/4/200/200"
            ),
            Song(
                trackId = "spotify:track:TEST_5",
                title = "테스트 곡 5",
                artist = "아티스트 E",
                albumImageUrl = "https://picsum.photos/seed/5/200/200"
            ),
        )
    }
}
