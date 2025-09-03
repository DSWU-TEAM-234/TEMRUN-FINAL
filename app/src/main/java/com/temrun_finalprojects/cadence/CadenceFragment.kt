package com.temrun_finalprojects.cadence

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.temrun_finalprojects.R
import com.temrun_finalprojects.result.RunningResultBottomSheet

class CadenceFragment : Fragment(R.layout.fragment_cadence) {

    private var runDetail: RunningResultBottomSheet.RunDetailResponse? = null
    private lateinit var lineChartCadence: LineChart

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        runDetail = arguments?.getParcelable("runDetail")
        lineChartCadence = view.findViewById(R.id.lineChartCadence)

        runDetail?.cadenceHistory?.let { history ->
            val entries = history.mapIndexed { idx, value ->
                Entry(idx.toFloat(), value.toFloat())
            }
            val dataSet = LineDataSet(entries, "Cadence").apply {
                lineWidth = 2f
                setDrawCircles(false)
            }
            lineChartCadence.data = LineData(dataSet)
            lineChartCadence.invalidate()
        }
    }
}
