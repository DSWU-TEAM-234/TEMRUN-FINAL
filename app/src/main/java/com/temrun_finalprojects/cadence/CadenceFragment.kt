package com.temrun_finalprojects.cadence

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import android.graphics.Color
import com.github.mikephil.charting.formatter.ValueFormatter
import com.temrun_finalprojects.databinding.FragmentCadenceBinding
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class CadenceFragment : Fragment() {

    private var _binding: FragmentCadenceBinding? = null
    private val binding get() = _binding!!

    // 차트 관련 변수
    private lateinit var cadenceChart: LineChart

    // 데이터 저장소
    val cadenceHistory = ArrayList<Int>()
    private val smoothedCadenceHistory = ArrayList<Int>()

    companion object {
        private const val ARG_CADENCE_HISTORY = "cadence_history"

        fun newInstance(cadenceHistory: ArrayList<Int>): CadenceFragment {
            val fragment = CadenceFragment()
            val args = Bundle().apply {
                putIntegerArrayList(ARG_CADENCE_HISTORY, cadenceHistory)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getIntegerArrayList(ARG_CADENCE_HISTORY)?.let {
            cadenceHistory.addAll(it)
            calculateSmoothedData()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCadenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        setupInitialData()
        if (cadenceHistory.isNotEmpty()) {
            updateChart()
            updateStatistics()
        }
    }

    private fun calculateSmoothedData() {
        val smoothingWindow = 5
        for (i in cadenceHistory.indices) {
            val start = maxOf(0, i - smoothingWindow + 1)
            val end = i + 1
            val window = cadenceHistory.subList(start, end)
            smoothedCadenceHistory.add(window.average().toInt())
        }
    }

    private fun setupChart() {
        val chartContainer = binding.chartContainer

        cadenceChart = LineChart(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        binding.imageView.visibility = View.GONE
        chartContainer.addView(cadenceChart)

        // 차트 기본 설정
        cadenceChart.apply {
            setDrawGridBackground(true)
            setBackgroundColor(Color.WHITE)
            setGridBackgroundColor(Color.WHITE)
            setTouchEnabled(false)
            setDragEnabled(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setAutoScaleMinMaxEnabled(true)

            description = Description().apply {
                text = "케이던스 기록 (SPM)"
                textSize = 12f
                textColor = Color.BLACK
            }
        }

        // X축 설정
        cadenceChart.xAxis.apply {
            setDrawGridLines(true)
            setDrawAxisLine(true)
            position = XAxis.XAxisPosition.BOTTOM
            textColor = Color.BLACK
            gridColor = Color.LTGRAY
        }

        // Y축 설정
        cadenceChart.axisLeft.apply {
            setDrawGridLines(true)
            textColor = Color.BLACK
            gridColor = Color.LTGRAY
            axisMinimum = (cadenceHistory.minOrNull()?.toFloat() ?: 140f) - 10f
            axisMaximum = (cadenceHistory.maxOrNull()?.toFloat() ?: 200f) + 10f
        }

        cadenceChart.axisRight.isEnabled = false

        // 범례 설정
        cadenceChart.legend.apply {
            isEnabled = true
            form = Legend.LegendForm.LINE
            textSize = 10f
            textColor = Color.BLACK
        }
    }

    private fun setupInitialData() {
        binding.textCadenceAccuracy.text = "0%"
        binding.textCadenceValue.text = "0"
        binding.textCadencePrecision.text = "±0 spm"
    }

    private fun updateChart() {
        if (cadenceHistory.isEmpty()) {
            cadenceChart.setNoDataText("수집된 데이터가 없습니다")
            cadenceChart.invalidate()
            return
        }

        val displayCount = 30 // 30초
        val startIdx = maxOf(0, smoothedCadenceHistory.size - displayCount)
        val entries = ArrayList<Entry>()
        val targetEntries = ArrayList<Entry>()

        for (i in startIdx until smoothedCadenceHistory.size) {
            val xValue = (i - startIdx).toFloat()
            entries.add(Entry(xValue, smoothedCadenceHistory[i].toFloat()))
            targetEntries.add(Entry(xValue, 165f))
        }

        val actualDataSet = LineDataSet(entries, "실제 케이던스").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val targetDataSet = LineDataSet(targetEntries, "목표 케이던스").apply {
            color = Color.RED
            lineWidth = 1f
            setDrawCircles(false)
            setDrawValues(false)
            enableDashedLine(10f, 5f, 0f)
        }

        val lineData = LineData(actualDataSet, targetDataSet)

        cadenceChart.data = lineData
        cadenceChart.notifyDataSetChanged()
        cadenceChart.invalidate()

        // X축 설정
        cadenceChart.xAxis.apply {
            setDrawGridLines(true)
            setDrawAxisLine(true)
            position = XAxis.XAxisPosition.BOTTOM
            textColor = Color.BLACK
            gridColor = Color.LTGRAY
            granularity = 5f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val seconds = (value * 1).toInt()
                    return "${seconds}s"
                }
            }
            labelCount = 7
            axisMinimum = 0f
            axisMaximum = 30f
        }
    }

    private fun updateStatistics() {
        if (cadenceHistory.isEmpty()) return

        val accurateCount = cadenceHistory.count { abs(it - 165) <= 5 }
        val accuracy = (accurateCount * 100.0 / cadenceHistory.size).toInt()
        val averageCadence = cadenceHistory.average().toInt()
        val recentStdDev = calculateStandardDeviation(cadenceHistory.takeLast(10))

        binding.textCadenceAccuracy.text = "${accuracy}%"
        binding.textCadenceValue.text = "$averageCadence"
        binding.textCadencePrecision.text = "±${recentStdDev.toInt()} spm"
    }

    private fun calculateStandardDeviation(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.map { (it - mean).pow(2) }.average())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
