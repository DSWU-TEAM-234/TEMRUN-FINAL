package com.temrun_finalprojects.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.login.LoginActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ProfileSetupActivity : AppCompatActivity() {

    private val client = OkHttpClient()

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
            }
        }

        btnAdd.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        btnBack.setOnClickListener { finish() }

        gender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("남", "여"))
        year.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, (1970..2025).toList())
        month.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, (1..12).toList())
        day.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, (1..31).toList())

        btnStart.setOnClickListener {
            val nicknameVal = nickname.text.toString()
            val genderVal = gender.selectedItem.toString()
            val heightVal = height.text.toString()
            val weightVal = weight.text.toString()
            val birth = "${year.selectedItem}-${month.selectedItem}-${day.selectedItem}"

            if (nicknameVal.isEmpty() || heightVal.isEmpty() || weightVal.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startActivity(Intent(this@ProfileSetupActivity, RootActivity::class.java))

//            // 🔐 SharedPreferences에서 access_token 불러오기
//            val accessToken = getSharedPreferences("Spotify", MODE_PRIVATE)
//                .getString("access_token", null)
//
//            if (accessToken.isNullOrEmpty()) {
//                Toast.makeText(this, "Spotify 로그인 정보가 없습니다", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            // Spotify 사용자 ID 조회
//            fetchSpotifyUserId(accessToken) { userId ->
//                if (userId == null) {
//                    runOnUiThread {
//                        Toast.makeText(this, "Spotify ID 조회 실패", Toast.LENGTH_SHORT).show()
//                    }
//                    return@fetchSpotifyUserId
//                }
//
//                // 서버에 회원가입 정보 전송
//                val json = JSONObject().apply {
//                    put("spotify_id", userId)
//                    put("nickname", nicknameVal)
//                    put("gender", genderVal)
//                    put("height", heightVal)
//                    put("weight", weightVal)
//                    put("birth", birth)
//                }
//
//                val body = json.toString().toRequestBody("application/json".toMediaType())
//
//                val request = Request.Builder()
//                    .url("서버 api url") // URL 수정 필요
//                    .post(body)
//                    .build()
//
//                client.newCall(request).enqueue(object : Callback {
//                    override fun onFailure(call: Call, e: IOException) {
//                        runOnUiThread {
//                            Toast.makeText(this@ProfileSetupActivity, "서버 전송 실패", Toast.LENGTH_SHORT).show()
//                        }
//                        Log.e("Register", "Error: ${e.message}")
//                    }
//
//                    override fun onResponse(call: Call, response: Response) {
//                        if (response.isSuccessful) {
//                            runOnUiThread {
//                                startActivity(Intent(this@ProfileSetupActivity, LoginActivity::class.java))
//                                finish()
//                            }
//                        } else {
//                            runOnUiThread {
//                                Toast.makeText(this@ProfileSetupActivity, "회원가입 실패", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                    }
//                })
//            }
        }
    }

    // 🎧 Spotify 사용자 ID 가져오는 함수
    private fun fetchSpotifyUserId(accessToken: String, callback: (String?) -> Unit) {
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body
                    val bodyString = responseBody?.string()
                    val json = JSONObject(bodyString ?: "")
                    val userId = json.getString("id")
                    callback(userId)
                } else {
                    callback(null)
                }
            }

        })
    }
}
