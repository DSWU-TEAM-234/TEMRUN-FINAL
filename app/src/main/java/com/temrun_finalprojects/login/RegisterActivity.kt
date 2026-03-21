package com.temrun_finalprojects.login

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent

class RegisterActivity : AppCompatActivity() {

    companion object {
        private const val CLIENT_ID = "78d949101bbb4f5497959d62235f2ddd"
        private const val REDIRECT_URI = "mainproject://callback"
        // Web Playback + 재생 제어에 필요한 스코프들
        private const val SCOPES =
            "streaming user-read-playback-state user-modify-playback-state user-read-email user-read-private"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val codeVerifier = SpotifyAuthUtil.generateCodeVerifier()
        val codeChallenge = SpotifyAuthUtil.generateCodeChallenge(codeVerifier)

        // 토큰 교환때 필요
        getSharedPreferences("Spotify", MODE_PRIVATE)
            .edit()
            .putString("code_verifier", codeVerifier)
            .apply()

        val authUrl = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            // .appendQueryParameter("show_dialog", "true") // 필요하면 최초 로그인 강제
            .build()

        CustomTabsIntent.Builder().build().launchUrl(this, authUrl)
        // finish()는 선택
    }
}
