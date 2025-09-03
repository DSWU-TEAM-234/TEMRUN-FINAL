package com.temrun_finalprojects.breathing

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.temrun_finalprojects.databinding.FragmentBreathingBinding

class BreathingFragment : Fragment() {

    private var _binding: FragmentBreathingBinding? = null
    private val binding get() = _binding!!

    // 예시 비율 (추후 실제 데이터로 교체)
    private val normalRatio = 60f
    private val abnormalType1 = 20f
    private val abnormalType2 = 15f
    private val abnormalType3 = 5f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBreathingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPieChart(binding.pieChartBreathing)
        loadPieChartData()
    }

    private fun setupPieChart(pieChart: PieChart) {
        pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            legend.isEnabled = true
            setEntryLabelColor(Color.BLACK)
            setDrawEntryLabels(false)
            animateY(600, Easing.EaseInOutQuad)
        }
    }

    private fun loadPieChartData() {
        // 파이 차트에는 비정상 타입만 표시
        val entries = listOf(
            PieEntry(abnormalType1, "비정상1"),
            PieEntry(abnormalType2, "비정상2"),
            PieEntry(abnormalType3, "비정상3")
        )

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#FF9800"), // 비정상1
                Color.parseColor("#2196F3"), // 비정상2
                Color.parseColor("#F44336")  // 비정상3
            )
            valueTextColor = Color.BLACK
            valueTextSize = 14f
        }

        val data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(binding.pieChartBreathing))
        }

        binding.pieChartBreathing.data = data
        binding.pieChartBreathing.invalidate()

        // 하단 텍스트뷰에 정상:비정상 비율 표시
        val totalAbnormal = abnormalType1 + abnormalType2 + abnormalType3
        binding.textAccuracy.text = "정상: ${normalRatio.toInt()}%"
        binding.textPatternAccuracy.text = "비정상: ${totalAbnormal.toInt()}%"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
