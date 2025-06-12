package com.temrun_finalprojects.result

import android.os.Bundle
import android.view.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.temrun_finalprojects.databinding.FragmentRunningResultBinding

class RunningResultBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentRunningResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRunningResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //viewPager 어댑터 연결
        binding.viewPager.adapter = ResultPagerAdapter(this)

        //AI 피드백 텍스트 설정 (나중에 서버로부터 받아오는 구조로 바꿔도 됨)
        binding.textFeedback1.text = "페이스가 안정적으로 유지되고 있습니다."
        binding.textFeedback2.text = "호흡을 적절히 유지하고 있습니다."
        binding.textFeedback3.text = "다음 목표: 6km 달성에 도전해보세요."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
