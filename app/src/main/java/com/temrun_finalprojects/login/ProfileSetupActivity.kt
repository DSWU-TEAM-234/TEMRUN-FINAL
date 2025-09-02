package com.temrun_finalprojects.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.temrun_finalprojects.R
import com.temrun_finalprojects.song_feedback.SurveyActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ProfileSetupActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    // TODO: 백엔드 연결 후 디버그 플래그 해제
    // 디버그 플래그: true면 서버 호출 대신 로그/팝업만 띄우고 다음 화면으로 이동
    private val SIGNUP_DEBUG = false

    // TODO: 서버 재기동 시 교체
    private val BASE_URL = "https://4a0bc02d836c.ngrok-free.app"
    private val SIGNUP_URL = "$BASE_URL/api/auth/signup"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnStart = findViewById<Button>(R.id.btnStart)

        val nickname = findViewById<EditText>(R.id.editNickname)
        val gender = findViewById<Spinner>(R.id.spinnerGender)
        val height = findViewById<EditText>(R.id.editHeight)
        val weight = findViewById<EditText>(R.id.editWeight)

        val year = findViewById<Spinner>(R.id.spinnerYear)
        val month = findViewById<Spinner>(R.id.spinnerMonth)
        val day = findViewById<Spinner>(R.id.spinnerDay)

        val imageProfile = findViewById<ImageView>(R.id.imageProfile)
        val btnAdd = findViewById<ImageButton>(R.id.btnAddProfileImage)

        val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                imageProfile.setImageURI(uri)
                getSharedPreferences("Profile", MODE_PRIVATE)
                    .edit()
                    .putString("local_profile_image_uri", uri.toString())
                    .apply()
            }
        }

        btnAdd.setOnClickListener { galleryLauncher.launch("image/*") }
        btnBack.setOnClickListener { finish() }

        gender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("남", "여"))
        year.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, (1970..2025).toList())
        month.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, (1..12).toList())
        day.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, (1..31).toList())

        btnStart.setOnClickListener {
            val nicknameVal = nickname.text.toString().trim()
            val genderVal = gender.selectedItem?.toString() ?: ""
            val heightVal = height.text.toString().trim()
            val weightVal = weight.text.toString().trim()
            val birth = "${year.selectedItem}-${month.selectedItem}-${day.selectedItem}" // 현재는 서버에 안보냄

            if (nicknameVal.isEmpty() || heightVal.isEmpty() || weightVal.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val h = heightVal.toDoubleOrNull()
            val w = weightVal.toDoubleOrNull()
            if (h == null || w == null) {
                Toast.makeText(this, "키/몸무게는 숫자로 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // SharedPreferences에서 Spotify/프로필 정보 읽기
            val sp = getSharedPreferences("Spotify", MODE_PRIVATE)
            val spProfile = getSharedPreferences("Profile", MODE_PRIVATE)
            val accessToken = sp.getString("access_token", null)
            val refreshToken = sp.getString("refresh_token", null)
            val spotifyId = sp.getString("spotify_id", null)
            val spotifyDisplay = sp.getString("spotify_display_name", "") ?: ""
            val email = sp.getString("spotify_email", "") ?: ""
            val localImageUri  = spProfile.getString("local_profile_image_uri", null)
            val profileImageForSend = localImageUri ?: ""   // 디버그/임시: content://... 그대로 보냄

            if (accessToken.isNullOrEmpty() || refreshToken.isNullOrEmpty() || spotifyId.isNullOrEmpty()) {
                Toast.makeText(this, "Spotify 로그인 정보가 없습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val displayName = if (nicknameVal.isNotEmpty()) nicknameVal else spotifyDisplay

            // 서버에 보낼 실제 페이로드
            val payload = JSONObject().apply {
                put("spotifyAccessToken", accessToken)
                put("spotifyRefreshToken", refreshToken)
                put("spotifyUserId", spotifyId)
                put("displayName", displayName)
                put("email", email)
                put("profileImageUrl", profileImageForSend)
                put("height", h)
                put("weight", w)
            }

            // 디버그 출력 (마스킹 포함)
            val debugPayload = JSONObject().apply {
                put("spotifyAccessToken", mask(accessToken))
                put("spotifyRefreshToken", mask(refreshToken))
                put("spotifyUserId", spotifyId)
                put("displayName", displayName)
                put("email", email)
                put("profileImageUrl", profileImageForSend)
                put("height", h)
                put("weight", w)
            }

            Log.d("SignupDebug", "[REQUEST_URL] $SIGNUP_URL")
            Log.d("SignupDebug", "[REQUEST_BODY] ${pretty(debugPayload)}")

            // ---------------------------
            // 디버그 모드: 더미 userId 저장 후 바로 다음 화면
            // ---------------------------
            if (SIGNUP_DEBUG) {
                val dummyUserId = "debug-user-1234"

                // AppUser 저장소에 더미 userId 저장
                getSharedPreferences("AppUser", MODE_PRIVATE).edit()
                    .putString("user_id", dummyUserId)
                    .putString("display_name", displayName)
                    .putString("profile_image_url", profileImageForSend)
                    .apply()

                // (선택) 서버 베이스 주소가 없다면 디버그 기본값 채우기 (OkHttp 테스트용)
                val appPrefs = getSharedPreferences("MyApp", MODE_PRIVATE)
                if (appPrefs.getString("apiBase", null).isNullOrBlank()) {
                    // 에뮬레이터 로컬 서버: http://10.0.2.2:8080
                    // ngrok를 쓰면 https://xxxxx.ngrok-free.app 형태로 교체
                    appPrefs.edit().putString("apiBase", "http://10.0.2.2:8080").apply()
                }

                AlertDialog.Builder(this)
                    .setTitle("전송 예정 데이터(디버그)")
                    .setMessage(pretty(debugPayload))
                    .setPositiveButton("다음으로") { _, _ ->
                        Toast.makeText(this, "디버그 모드: 서버 호출 생략", Toast.LENGTH_SHORT).show()
                        goNext(displayName, profileImageForSend)
                    }
                    .setNegativeButton("취소", null)
                    .show()
                return@setOnClickListener
            }

            // ---------------------------
            // 실제 서버 호출 (릴리즈/실사용 경로)
            // ---------------------------
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(SIGNUP_URL).post(body).build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@ProfileSetupActivity, "회원가입 요청 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val respStr = response.body?.string() ?: ""
                    Log.d("SignupDebug", "[RESPONSE_CODE] ${response.code}")
                    Log.d("SignupDebug", "[RESPONSE_BODY] ${respStr.take(800)}")

                    if (!response.isSuccessful) {
                        runOnUiThread {
                            Toast.makeText(this@ProfileSetupActivity, "회원가입 실패(${response.code})", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    val js = try { JSONObject(respStr) } catch (_: Exception) { null }
                    val appUserId = js?.optString("userId", "") ?: ""

                    // 앱 사용자 ID 저장(원하면 EncryptedSharedPreferences로 교체)
                    getSharedPreferences("AppUser", MODE_PRIVATE).edit()
                        .putString("user_id", appUserId)
                        .putString("display_name", js?.optString("displayName", displayName))
                        .putString("profile_image_url", js?.optString("profileImageUrl", profileImageForSend))
                        .apply()

                    runOnUiThread {
                        Toast.makeText(this@ProfileSetupActivity, "회원가입 완료", Toast.LENGTH_SHORT).show()
                        goNext(
                            js?.optString("displayName", displayName) ?: displayName,
                            js?.optString("profileImageUrl", profileImageForSend) ?: profileImageForSend
                        )
                    }
                }
            })
        }
    }

    // 다음 화면 이동
    private fun goNext(displayName: String, profileImageUrl: String) {
        startActivity(Intent(this@ProfileSetupActivity, SurveyActivity::class.java))
        finish()
    }

    // 보기 좋게 출력
    private fun pretty(js: JSONObject): String = try { js.toString(2) } catch (_: Exception) { js.toString() }

    // 토큰 마스킹 유틸
    private fun mask(v: String?, tail: Int = 4): String {
        if (v.isNullOrEmpty()) return "null"
        return "*".repeat((v.length - tail).coerceAtLeast(0)) + v.takeLast(tail)
    }
}
