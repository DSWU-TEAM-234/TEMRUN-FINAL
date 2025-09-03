package com.temrun_finalprojects.cadence

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.temrun_finalprojects.R
import com.temrun_finalprojects.result.RunningResultBottomSheet
import android.widget.TextView

class CadenceFragment : Fragment(R.layout.fragment_cadence) {

    private var runDetail: RunningResultBottomSheet.RunDetailResponse? = null
    private lateinit var lineChartCadence: LineChart
    private lateinit var tvAccuracy: TextView
    private lateinit var tvAvgCadence: TextView
    private lateinit var tvDistance: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        runDetail = arguments?.getParcelable("runDetail")
        lineChartCadence = view.findViewById(R.id.lineChartCadence)
        tvAccuracy = view.findViewById(R.id.textCadenceAccuracy)
        tvAvgCadence = view.findViewById(R.id.textCadenceValue)
        tvDistance = view.findViewById(R.id.textCadencePrecision)

        runDetail?.let { d ->
            // 1) 차트에 예측 케이던스 시리즈
            val entries = d.cadenceHistory.mapIndexed { idx, value ->
                Entry(idx.toFloat(), value.toFloat())
            }
            val dataSet = LineDataSet(entries, "Cadence").apply {
                lineWidth = 2f
                setDrawCircles(false)
            }

            // 2) 설정 케이던스 기준선 추가
            val limitLine = LimitLine(d.targetCadence.toFloat(), "Target ${d.targetCadence} spm").apply {
                lineWidth = 2f
                textSize = 10f
            }
            lineChartCadence.axisLeft.addLimitLine(limitLine)

            // 3) 차트 세팅
            lineChartCadence.data = LineData(dataSet)
            lineChartCadence.axisRight.isEnabled = false
            lineChartCadence.axisLeft.axisMinimum = 0f
            lineChartCadence.axisLeft.axisMaximum = (d.targetCadence + 20).toFloat()
            lineChartCadence.invalidate()

            // 4) 통계 지표 바인딩
            tvAccuracy.text = String.format("±%.1f spm", d.cadenceAccuracy)
            tvAvgCadence.text = String.format("%.0f spm", d.avgCadence)
            tvDistance.text = String.format("%.1f km", d.distance)
        }
    }
}
