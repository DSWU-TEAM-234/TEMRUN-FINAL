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
import androidx.core.content.ContextCompat

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
            // 1) Entry 리스트 생성
            val entries = d.cadenceHistory.mapIndexed { idx, value ->
                Entry(idx.toFloat(), value.toFloat())
            }

            // 2) LineDataSet 설정 (ResultActivity와 동일한 스타일)
            val dataSet = LineDataSet(entries, "케이던스").apply {
                color = ContextCompat.getColor(requireContext(), R.color.teal_700)
                setDrawCircles(true)
                circleRadius = 3f
                setDrawValues(false)
                lineWidth = 2f
            }

            // 3) 차트에 데이터 세팅
            lineChartCadence.data = LineData(dataSet)

            // 4) 축 및 LimitLine 설정
            lineChartCadence.axisRight.isEnabled = false

            lineChartCadence.axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = (d.targetCadence + 20).toFloat()

                val limitLine =
                    LimitLine(d.targetCadence.toFloat(), "목표(${d.targetCadence})").apply {
                        lineWidth = 2f
                        lineColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
                        textColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
                        textSize = 12f
                        labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    }
                addLimitLine(limitLine)
            }

            // 5) 차트 갱신
            lineChartCadence.data?.notifyDataChanged()
            lineChartCadence.notifyDataSetChanged()
            lineChartCadence.invalidate()

            // 6) 통계 텍스트 뷰 바인딩
            tvAvgCadence.text = d.avgCadence.toInt().toString()
            tvAccuracy.text = String.format("±%.0f", d.cadenceAccuracy)
            tvDistance.text = String.format("%.1f km", d.distance)
        }
    }
}
