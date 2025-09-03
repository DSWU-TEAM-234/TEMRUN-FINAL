package com.temrun_finalprojects.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.temrun_finalprojects.databinding.FragmentRunningResultBinding
import java.util.concurrent.TimeUnit

class RunningResultBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentRunningResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRunningResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let { args ->
            // 1) 상단 제목에 runId 표시
            val runIdLabel = args.getString("runId")?.takeLast(4)?.let { "러닝 $it" }
            binding.tvTitle.text = runIdLabel

            // 2) 요약 카드뷰에 총 러닝 시간, 평균 BPM, 칼로리 설정
            val durationSec = args.getLong("totalDuration")
            val minutes = TimeUnit.SECONDS.toMinutes(durationSec)
            val seconds = durationSec % 60
            binding.textViewTime.text = String.format("%d:%02d", minutes, seconds)

            binding.textViewBpm.text = args.getInt("averageBpm").toString()
            binding.textViewCalorie.text = args.getInt("totalCalories").toString()
        }

        // ViewPager 어댑터 연결
        binding.viewPager.adapter = ResultPagerAdapter(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
