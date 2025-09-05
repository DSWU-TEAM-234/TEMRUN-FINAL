package com.temrun_finalprojects

import android.app.Application
import com.temrun_finalprojects.config.ApiConfig

class TemrunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // API 설정 초기화
        ApiConfig.init(this)
    }
}