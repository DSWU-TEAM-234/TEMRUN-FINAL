package com.temrun_finalprojects.login

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent

class RegisterActivity : AppCompatActivity() {

    companion object {
        private const val CLIENT_ID = "78d949101bbb4f5497959d62235f2ddd"
        private const val REDIRECT_URI = "mainproject://callback"
        private const val SCOPES = "user-read-private user-read-email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val codeVerifier = SpotifyAuthUtil.generateCodeVerifier()
        val codeChallenge = SpotifyAuthUtil.generateCodeChallenge(codeVerifier)

        // 저장 (나중에 토큰 요청 시 필요)
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
            .build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(this, authUrl)
    }
}