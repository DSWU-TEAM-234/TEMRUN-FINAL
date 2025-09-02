package com.temrun_finalprojects.cadence

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.temrun_finalprojects.R
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat

class CadenceFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var cadenceSensor: Sensor? = null

    private lateinit var lineChartCadence: LineChart
    private lateinit var dataSet: LineDataSet
    private var timeIndex = 0f
    private var targetCadence = 160f  // 목표 케이던스

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cadence, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뷰 참조
        lineChartCadence = view.findViewById(R.id.lineChartCadence)

        // 센서 초기화
        sensorManager = requireActivity()
            .getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        cadenceSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        setupChart()
    }

    private fun setupChart() {
        dataSet = LineDataSet(mutableListOf(), "실시간 케이던스").apply {
            color = ContextCompat.getColor(requireContext(), R.color.teal_700)
            lineWidth = 2f
            setDrawCircles(false)
            mode = LineDataSet.Mode.LINEAR
        }

        lineChartCadence.data = LineData(dataSet)

        val leftAxis: YAxis = lineChartCadence.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 200f

        // 목표선 추가
        val limitLine = LimitLine(targetCadence, "목표 케이던스").apply {
            lineWidth = 4f
            lineColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
            textColor = ContextCompat.getColor(requireContext(), R.color.purple_500)
            textSize = 12f
        }
        leftAxis.addLimitLine(limitLine)
        lineChartCadence.axisRight.isEnabled = false

        lineChartCadence.xAxis.apply {
            setDrawGridLines(false)
            setAvoidFirstLastClipping(true)
            granularity = 1f
        }

        lineChartCadence.animateX(500)
    }

    override fun onResume() {
        super.onResume()
        cadenceSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR ||
            event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val cadenceValue = calculateCadence(event.values[0])
            addEntry(cadenceValue)
        }
    }

    private fun calculateCadence(sensorValue: Float): Float {
        return (sensorValue * 60).roundToInt().toFloat()
    }

    private fun addEntry(value: Float) {
        dataSet.addEntry(Entry(timeIndex, value))
        timeIndex += 1f
        lineChartCadence.data.notifyDataChanged()
        lineChartCadence.notifyDataSetChanged()
        lineChartCadence.setVisibleXRangeMaximum(50f)
        lineChartCadence.moveViewToX(timeIndex)
    }
}
