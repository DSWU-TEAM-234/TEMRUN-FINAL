package com.temrun_finalprojects.result

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.temrun_finalprojects.breathing.BreathingFragment
import com.temrun_finalprojects.cadence.CadenceFragment
import com.temrun_finalprojects.result.RunningResultBottomSheet

class ResultPagerAdapter(
    fragment: Fragment,
    private val runDetail: RunningResultBottomSheet.RunDetailResponse
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment =
        when (position) {
            0 -> BreathingFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("runDetail", runDetail)
                }
            }
            1 -> CadenceFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("runDetail", runDetail)
                }
            }
            else -> throw IllegalStateException("Invalid position")
        }
}
