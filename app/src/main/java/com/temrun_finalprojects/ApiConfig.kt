package com.temrun_finalprojects.config

import android.content.Context
import android.content.SharedPreferences

object ApiConfig {
    private const val PREF_NAME = "api_config"
    private const val KEY_BASE_URL = "base_url"

    // 기본 URL (ngrok 재시작 시 여기서 변경)
    private const val DEFAULT_BASE_URL = "https://339cdd456ce9.ngrok-free.app"

    private var baseUrl: String? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL)
    }

    fun getBaseUrl(): String {
        return baseUrl ?: DEFAULT_BASE_URL
    }

    fun updateBaseUrl(context: Context, newUrl: String) {
        baseUrl = newUrl
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, newUrl).apply()
    }

    // 자주 사용하는 엔드포인트들
    fun getSignupUrl() = "${getBaseUrl()}/api/auth/signup"
    fun getDeleteAccountUrl(userId: String) = "${getBaseUrl()}/api/auth/$userId"
    fun getStartRunUrl() = "${getBaseUrl()}/api/runs/start"
    fun getRecommendUrl() = "${getBaseUrl()}/api/recommend"
    fun getFeedbackUrl() = "${getBaseUrl()}/api/runs/feedback"
    fun getRunDetailUrl(runId: String) = "${getBaseUrl()}/api/runs/$runId/view"
    fun getRunsByDateUrl(userId: String, date: String) = "${getBaseUrl()}/api/users/$userId/runs/date/$date"
    fun getCalendarUrl(userId: String, month: String) = "${getBaseUrl()}/api/users/$userId/calendar?month=$month"
}
