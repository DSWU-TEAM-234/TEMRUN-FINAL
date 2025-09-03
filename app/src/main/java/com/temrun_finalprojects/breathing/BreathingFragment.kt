package com.temrun_finalprojects.breathing

import android.os.Bundle
import android.view.View
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        runDetail = arguments?.getParcelable("runDetail")
        pieChartBreathing = view.findViewById(R.id.pieChartBreathing)

        runDetail?.let { d ->
            val entries = listOf(
                PieEntry(d.breathNormalAcc.toFloat(), "Normal"),
                PieEntry(d.breathAbnormalAcc.toFloat(), "Abnormal")
            )
            val dataSet = PieDataSet(entries, "").apply {
                setColors(
                    resources.getColor(R.color.teal_700, null),
                    resources.getColor(R.color.purple_500, null)
                )
                valueTextSize = 12f
                valueFormatter = PercentFormatter(pieChartBreathing)
            }
            pieChartBreathing.data = PieData(dataSet)
            pieChartBreathing.invalidate()
        }
    }
}
