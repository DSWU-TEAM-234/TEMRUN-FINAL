package com.temrun_finalprojects.breathing

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.temrun_finalprojects.R
import com.temrun_finalprojects.result.RunningResultBottomSheet

class BreathingFragment : Fragment(R.layout.fragment_breathing) {

    private var runDetail: RunningResultBottomSheet.RunDetailResponse? = null
    private lateinit var pieChartBreathing: PieChart
    private lateinit var tvPatternNormal: TextView
    private lateinit var tvPatternAbnormal: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        runDetail = arguments?.getParcelable("runDetail")
        pieChartBreathing = view.findViewById(R.id.pieChartBreathing)
        tvPatternNormal = view.findViewById(R.id.textPatterNormal)
        tvPatternAbnormal = view.findViewById(R.id.textPatternAbnormal)

        runDetail?.let { d ->
            // 1) 비정상 타입 별 비율 원그래프
            val entries = listOf(
                PieEntry(d.breathAbnormalType1.toFloat(), "타입1"),
                PieEntry(d.breathAbnormalType2.toFloat(), "타입2"),
                PieEntry(d.breathAbnormalType3.toFloat(), "타입3")
            )
            val dataSet = PieDataSet(entries, "").apply {
                setColors(
                    resources.getColor(R.color.teal_700, null),
                    resources.getColor(R.color.purple_500, null),
                    resources.getColor(R.color.orange_500, null)
                )
                valueTextSize = 12f
                valueFormatter = PercentFormatter(pieChartBreathing)
            }
            pieChartBreathing.apply {
                data = PieData(dataSet)
                setUsePercentValues(true)
                description.isEnabled = false
                legend.isEnabled = true
                animateY(600)
                invalidate()
            }

            // 2) 정상 : 비정상 호흡 비율 텍스트로 표시
            val normal = d.breathNormalAcc
            val abnormal = d.breathAbnormalAcc
            tvPatternNormal.text = "정상 $normal"
            tvPatternAbnormal.text = "비정상 $abnormal"
        }
    }
}
