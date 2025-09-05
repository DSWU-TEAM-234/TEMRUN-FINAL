package com.temrun_finalprojects

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.temrun_finalprojects.login.LoginActivity
import com.temrun_finalprojects.login.RegisterActivity
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import com.temrun_finalprojects.song_feedback.SurveyActivity

class StartActivity : AppCompatActivity() {

    // 테스트/디버그용 플래그
    private val FORCE_REGISTER = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



////        혹시 꼬였을때 저장된 id지우기
//        listOf("AppUser", "Spotify", "AppPrefs").forEach { name ->
//            getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
//                .edit().clear().apply()
//        }
//
////        디버그용
//        startActivity(
//            Intent(this, RootActivity::class.java))



        // 1) 이미 로그인된 사용자면 바로 Root로 이동
        if (!FORCE_REGISTER && isLoggedInLocally()) {
            Log.d("StartActivity", "Local user exists → go to RootActivity")
            startActivity(
                Intent(this, SurveyActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                }
            )
            finish()
            return
        }

        // 2) 로그인 정보 없거나 FORCE_REGISTER=true 면 시작 화면 노출
        setContentView(R.layout.activity_start)

        val btnSpotify = findViewById<Button>(R.id.btnStartSpotify)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        btnSpotify.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    /**
     * 로컬 저장값 기준 로그인 여부 판단.
     * - spotify_id: OAuth 프로필 저장 시 기록
     * - user_id: (앱 자체 회원 ID를 사용하는 경우 대비)
     * 둘 중 하나라도 있으면 로그인된 것으로 간주.
     */
    private fun isLoggedInLocally(): Boolean {
        val sp = getSharedPreferences("Spotify", MODE_PRIVATE)
        val spotifyId = sp.getString("spotify_id", null)
        val appUserId = sp.getString("user_id", null)
        return !spotifyId.isNullOrEmpty() || !appUserId.isNullOrEmpty()
    }
}
