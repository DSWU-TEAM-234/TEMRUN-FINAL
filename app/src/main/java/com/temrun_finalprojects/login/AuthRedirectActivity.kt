package com.temrun_finalprojects.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.temrun_finalprojects.RootActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import android.util.Log
import androidx.appcompat.app.AlertDialog

class AuthRedirectActivity : AppCompatActivity() {

    private val client = OkHttpClient() // 공용 클라이언트

    // TODO: 백엔드 연결 후 디버그 플래그 해제
    private val SIGNUP_DEBUG = false

    private val PREFS = "Spotify"
    private val KEY_ACCESS = "access_token"
    private val KEY_REFRESH = "refresh_token"
    private val KEY_SCOPE = "token_scope"
    private val KEY_EXPIRES_AT = "access_expires_at" // epoch millis

    private val CLIENT_ID = "78d949101bbb4f5497959d62235f2ddd"
    private val REDIRECT_URI = "mainproject://callback"

    private val REQUIRED_SCOPES = listOf(
        "streaming",
        "user-read-playback-state",
        "user-modify-playback-state"
    )

    // 토큰/코드 마스킹
    private fun mask(v: String?, tail: Int = 4): String {
        if (v.isNullOrEmpty()) return "null"
        return "*".repeat((v.length - tail).coerceAtLeast(0)) + v.takeLast(tail)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = intent?.data?.getQueryParameter("code")

        if (code != null) {
            val codeVerifier = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("code_verifier", null)

            if (codeVerifier != null) {
                requestAccessToken(code, codeVerifier)
            } else {
                Toast.makeText(this, "code_verifier 없음", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "authorization code 없음", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAccessToken(code: String, codeVerifier: String) {
        val client = OkHttpClient()

        val formBody = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@AuthRedirectActivity, "토큰 요청 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e("AuthDebug", "[TOKEN_FAIL] code=${response.code} body=${bodyStr.take(500)}")
                    runOnUiThread {
                        Toast.makeText(this@AuthRedirectActivity, "응답 실패(${response.code})", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val json = JSONObject(bodyStr)
                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", "")
                val scopeGranted = json.optString("scope", "")
                val expiresInSec = json.optLong("expires_in", 3600L) // 기본 1시간

                Log.d("AuthDebug", "[TOKEN_OK] scope=$scopeGranted expires_in=$expiresInSec " +
                        "access=${mask(accessToken)} refresh=${mask(refreshToken)}")

                if (accessToken.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@AuthRedirectActivity, "access_token 없음", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val expiresAt = System.currentTimeMillis() + (expiresInSec * 1000L) - 60_000L // 60초 여유

                // 저장
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_ACCESS, accessToken)
                    .putString(KEY_REFRESH, refreshToken)
                    .putString(KEY_SCOPE, scopeGranted)
                    .putLong(KEY_EXPIRES_AT, expiresAt)
                    .apply()

                // 스코프 부족 경고(진행은 계속)
                val missing = missingScopes(scopeGranted)
                if (missing.isNotEmpty()) {
                    runOnUiThread {
                        AlertDialog.Builder(this@AuthRedirectActivity)
                            .setTitle("Spotify 권한이 부족합니다")
                            .setMessage("다음 권한이 필요해요: ${missing.joinToString(", ")}\n" +
                                    "재생 제어가 실패할 수 있으니, 나중에 다시 로그인해서 권한을 허용해 주세요.")
                            .setPositiveButton("확인", null)
                            .show()
                    }
                }

                // 프로필 조회/저장 후 다음 화면
                fetchSpotifyProfileAndGo(accessToken)
            }
        })
    }

    // 프로필 조회 + 저장 완료 후 ProfileSetupActivity로 이동
    // 401이면 refresh 후 재시도
    private fun fetchSpotifyProfileAndGo(accessToken: String) {
        val req = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        Log.d("AuthDebug", "[PROFILE_REQ] GET /v1/me Authorization=Bearer ${mask(accessToken)}")

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AuthDebug", "[PROFILE_FAIL] ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@AuthRedirectActivity, "프로필 요청 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@AuthRedirectActivity, ProfileSetupActivity::class.java))
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                Log.d("AuthDebug", "[PROFILE_RESP] code=${response.code}, success=${response.isSuccessful}")
                Log.d("AuthDebug", "[PROFILE_BODY] ${bodyStr.take(800)}")

                // 401 → 토큰 재발급 후 재시도
                if (response.code == 401) {
                    val refresh = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_REFRESH, null)
                    Log.w("AuthDebug", "[PROFILE_401] access invalid. refresh=${mask(refresh)}")
                    if (!refresh.isNullOrEmpty()) {
                        refreshAccessTokenAndRetry(refresh)
                        return
                    }
                }

                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@AuthRedirectActivity, "프로필 응답 실패(${response.code})", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@AuthRedirectActivity, ProfileSetupActivity::class.java))
                        finish()
                    }
                    return
                }

                // 성공 파싱
                try {
                    val js = JSONObject(bodyStr)
                    val spotifyId = js.optString("id", "")
                    val displayName = js.optString("display_name", "")
                    val email = js.optString("email", "") // user-read-email 필요
                    var profileImageUrl = ""
                    val images = js.optJSONArray("images")
                    if (images != null && images.length() > 0) {
                        profileImageUrl = images.optJSONObject(0)?.optString("url", "") ?: ""
                    }

                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("spotify_id", spotifyId)
                        .putString("spotify_display_name", displayName)
                        .putString("spotify_email", email)
                        .putString("spotify_profile_image_url", profileImageUrl)
                        .apply()

                    Log.d("AuthDebug", "[PROFILE_PARSED] id=$spotifyId, name=$displayName, email=$email, img=$profileImageUrl")

                    runOnUiThread {
                        startActivity(Intent(this@AuthRedirectActivity, ProfileSetupActivity::class.java))
                        finish()
                    }
                } catch (t: Throwable) {
                    Log.e("AuthDebug", "[PROFILE_PARSE_ERROR] ${t.message}", t)
                    runOnUiThread {
                        Toast.makeText(this@AuthRedirectActivity, "프로필 파싱 실패", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@AuthRedirectActivity, ProfileSetupActivity::class.java))
                        finish()
                    }
                }
            }
        })
    }

    // refresh_token으로 access_token 재발급 후 /v1/me 재시도
    private fun refreshAccessTokenAndRetry(refreshToken: String) {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()

        val req = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(form)
            .build()

        Log.d("AuthDebug", "[REFRESH_REQ] refresh=${mask(refreshToken)}")

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AuthDebug", "[REFRESH_FAIL] ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@AuthRedirectActivity, "토큰 재발급 실패", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@AuthRedirectActivity, ProfileSetupActivity::class.java))
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                Log.d("AuthDebug", "[REFRESH_RESP] code=${response.code}, success=${response.isSuccessful}")
                Log.d("AuthDebug", "[REFRESH_BODY] ${bodyStr.take(500)}")

                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@AuthRedirectActivity, "토큰 재발급 실패(${response.code})", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@AuthRedirectActivity, ProfileSetupActivity::class.java))
                        finish()
                    }
                    return
                }

                val js = JSONObject(bodyStr)
                val newAccess = js.optString("access_token", "")
                val newRefresh = js.optString("refresh_token", null) // 일부 케이스에선 null
                val newScope = js.optString("scope", null) // 보통 생략될 수 있음
                val expiresInSec = js.optLong("expires_in", 3600L)
                val newExpiresAt = System.currentTimeMillis() + (expiresInSec * 1000L) - 60_000L

                if (newAccess.isEmpty()) {
                    Log.e("AuthDebug", "[REFRESH_EMPTY_ACCESS]")
                    runOnUiThread {
                        Toast.makeText(this@AuthRedirectActivity, "토큰 재발급 실패(빈 토큰)", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@AuthRedirectActivity, ProfileSetupActivity::class.java))
                        finish()
                    }
                    return
                }

                getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
                    putString(KEY_ACCESS, newAccess)
                    putLong(KEY_EXPIRES_AT, newExpiresAt)
                    if (!newRefresh.isNullOrEmpty()) putString(KEY_REFRESH, newRefresh)
                    if (!newScope.isNullOrEmpty()) putString(KEY_SCOPE, newScope)
                }.apply()

                Log.d("AuthDebug", "[REFRESH_OK] access=${mask(newAccess)} refresh=${mask(newRefresh)} scope=$newScope expires_in=$expiresInSec")

                // 재시도
                fetchSpotifyProfileAndGo(newAccess)
            }
        })
    }

    private fun missingScopes(granted: String?): List<String> {
        val set = granted?.split(" ")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        return REQUIRED_SCOPES.filterNot { set.contains(it) }
    }
}
