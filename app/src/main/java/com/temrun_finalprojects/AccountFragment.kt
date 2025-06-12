package com.temrun_finalprojects

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class AccountFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 계정 페이지 UI 초기화
        setupAccountUI()
    }

    private fun setupAccountUI() {
        // 계정 관련 UI 구성 요소들을 여기에 구현
        // 예: 프로필 정보, 로그인/로그아웃, 개인정보 설정 등
    }
}
