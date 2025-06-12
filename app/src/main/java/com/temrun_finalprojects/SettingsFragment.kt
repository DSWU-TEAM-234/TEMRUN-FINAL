package com.temrun_finalprojects

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 설정 페이지 UI 초기화
        setupSettingsUI()
    }

    private fun setupSettingsUI() {
        // 설정 관련 UI 구성 요소들을 여기에 구현
        // 예: 알림 설정, 테마 설정, 언어 설정 등
    }
}
