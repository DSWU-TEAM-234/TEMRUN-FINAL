package com.temrun_finalprojects.result

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.temrun_finalprojects.breathing.BreathingFragment
import com.temrun_finalprojects.cadence.CadenceFragment

/**
 * ViewPager2 안에서 '호흡'과 '케이던스' 두 개의 Fragment를
 * 좌우로 넘길 수 있게 연결해 주는 어댑터 클래스.
 */
class ResultPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    // 총 몇 개의 페이지(Fragment)를 가질지 알려줌
    override fun getItemCount(): Int = 2

    // 각 position(인덱스)에 맞는 Fragment를 생성해서 반환
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> BreathingFragment() // 첫 번째 페이지 -> '호흡' 화면
            1 -> CadenceFragment()   // 두 번째 페이지에 -> '케이던스' 화면
            else -> throw IllegalStateException("Invalid position")
        }
    }
}
