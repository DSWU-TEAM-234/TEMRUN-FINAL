package com.temrun_finalprojects.cadence

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.temrun_finalprojects.databinding.FragmentCadenceBinding

class CadenceFragment : Fragment() {

    private var _binding: FragmentCadenceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCadenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 텍스트뷰에 예시 데이터 설정 (추후 실제 데이터로 교체)
        binding.textCadenceAccuracy.text = "95%"
        binding.textCadenceValue.text = "165"
        binding.textCadencePrecision.text = "±3 spm"

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
