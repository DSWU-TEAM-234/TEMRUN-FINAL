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

class AuthRedirectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = intent?.data?.getQueryParameter("code")

        if (code != null) {
            val codeVerifier = getSharedPreferences("Spotify", MODE_PRIVATE)
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
            .add("client_id", "78d949101bbb4f5497959d62235f2ddd")
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", "mainproject://callback")
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
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val accessToken = json.getString("access_token")
                    val refreshToken = json.getString("refresh_token")

                    // 저장
                    getSharedPreferences("Spotify", MODE_PRIVATE)
                        .edit()
                        .putString("access_token", accessToken)
                        .putString("refresh_token", refreshToken)
                        .apply()


                    runOnUiThread {
                        startActivity(Intent(this@AuthRedirectActivity, ProfileSetupActivity::class.java))
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AuthRedirectActivity, "응답 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }


    private fun fetchSpotifyProfileAndLogin(accessToken: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@AuthRedirectActivity, "프로필 요청 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val spotifyId = json.getString("id")

                    // SharedPreferences에 저장
                    getSharedPreferences("Spotify", MODE_PRIVATE).edit()
                        .putString("spotify_id", spotifyId)
                        .apply()

                    // 서버에 로그인 요청
                    loginOrRegisterToServer(spotifyId)

                } else {
                    runOnUiThread {
                        Toast.makeText(this@AuthRedirectActivity, "프로필 응답 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun loginOrRegisterToServer(spotifyId: String) {
        val client = OkHttpClient()

        val json = JSONObject().apply {
            put("spotify_id", spotifyId)
        }

        val requestBody = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://서버.com/api/login") // 서버 주소 수정
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@AuthRedirectActivity, "로그인 요청 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // 로그인 성공 → RootActivity로 이동
                    runOnUiThread {
                        startActivity(Intent(this@AuthRedirectActivity, RootActivity::class.java))
                        finish()
                    }
                } else if (response.code == 404) {
                    // 회원가입 필요 → ProfileSetupActivity로 이동
                    runOnUiThread {
                        startActivity(Intent(this@AuthRedirectActivity, ProfileSetupActivity::class.java))
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AuthRedirectActivity, "서버 오류", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }


}

