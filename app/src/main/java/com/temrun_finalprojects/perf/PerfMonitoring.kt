package com.temrun_finalprojects.perf

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

///**
// * 처리 지연시간 + 배터리 소모 측정 도구.
// *
// * 로그 필터:  adb logcat -s PERF
// *
// * [지연시간]
// *   val t = PerfMonitor.start()
// *   ... 측정할 코드 ...
// *   PerfMonitor.end("태그", t)
// *   (또는 PerfMonitor.record("태그", ms))
// *
// * [배터리]
// *   PerfMonitor.startSession(context, "라벨")   // 러닝 시작
// *   PerfMonitor.stopSession()                    // 러닝 종료 → 최종 리포트 + CSV 저장
// */
@SuppressLint("StaticFieldLeak")
object PerfMonitor {
    private const val TAG = "PERF"
    private const val MAX_SAMPLES = 300            // 태그당 최근 샘플 (p50/p95 계산용)
    private const val REPORT_INTERVAL_MS = 30_000L // 주기 리포트 간격

    @Volatile var enabled = true

    /** 이 값(ms)을 넘으면 ⚠ SLOW 경고. 필요에 맞게 조정하세요. */
    private val budgets = mapOf(
        "cadence_total" to 1000.0,       // 케이던스 루프 주기(1초) 안에 끝나야 밀리지 않음
        "audio_chunk_process" to 64.0,   // 1024샘플 = 64ms. 이보다 오래 걸리면 녹음을 못 따라감
        "m1_total" to 1000.0,
        "m2_total" to 1000.0,
        "breath_broadcast_delay" to 100.0
    )

    private class Stat {
        var count = 0L
        var sum = 0.0
        var min = Double.MAX_VALUE
        var max = 0.0
        val recent = ArrayDeque<Double>()
    }

    private class Snap(
        val elapsedMs: Long,
        val pct: Float,
        val chargeMah: Float,    // 0 이하면 기기가 미지원
        val currentMa: Float,    // 순간 전류 (절댓값)
        val tempC: Float,
        val thermal: Int,
        val plugged: Boolean
    )

    private val stats = ConcurrentHashMap<String, Stat>()

    // ---------------------------------------------------------------- 지연시간

    fun start(): Long = SystemClock.elapsedRealtimeNanos()

    fun end(tag: String, startNs: Long): Double {
        val ms = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
        record(tag, ms)
        return ms
    }

    fun record(tag: String, ms: Double) {
        if (!enabled) return
        val s = stats.getOrPut(tag) { Stat() }
        synchronized(s) {
            s.count++
            s.sum += ms
            if (ms < s.min) s.min = ms
            if (ms > s.max) s.max = ms
            s.recent.addLast(ms)
            if (s.recent.size > MAX_SAMPLES) s.recent.removeFirst()
        }
        budgets[tag]?.let {
            if (ms > it) Log.w(TAG, "⚠ SLOW  $tag = ${fmt(ms)}ms (기준 ${it.toInt()}ms)")
        }
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val idx = ((sorted.size - 1) * p).toInt()
        return sorted[idx]
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

    fun logSummary(header: String = "지연시간 요약") {
        if (stats.isEmpty()) return
        Log.i(TAG, "┌──── $header ────")
        Log.i(TAG, "│ %-24s %6s %8s %8s %8s %8s %8s".format("tag", "n", "avg", "min", "p50", "p95", "max"))
        stats.keys.sorted().forEach { tag ->
            val s = stats[tag] ?: return@forEach
            synchronized(s) {
                val sorted = s.recent.sorted()
                Log.i(
                    TAG,
                    "│ %-24s %6d %8s %8s %8s %8s %8s".format(
                        tag, s.count, fmt(s.sum / s.count), fmt(s.min),
                        fmt(percentile(sorted, 0.5)), fmt(percentile(sorted, 0.95)), fmt(s.max)
                    )
                )
            }
        }
        Log.i(TAG, "└─────────────────────────")
    }

    // ---------------------------------------------------------------- 배터리

    private var appCtx: Context? = null
    private var handler: Handler? = null
    private var label = ""
    private var startSnap: Snap? = null
    private var sessionStartMs = 0L
    private val samples = mutableListOf<Snap>()

    private val reportRunnable = object : Runnable {
        override fun run() {
            val ctx = appCtx ?: return
            val s = snapshot(ctx)
            samples.add(s)
            logBatterySample(s)
            logSummary()
            handler?.postDelayed(this, REPORT_INTERVAL_MS)
        }
    }

    fun startSession(context: Context, sessionLabel: String = "run") {
        if (appCtx != null) return // 중복 시작 방지
        appCtx = context.applicationContext
        label = sessionLabel
        stats.clear()
        samples.clear()
        sessionStartMs = SystemClock.elapsedRealtime()

        val s = snapshot(appCtx!!)
        startSnap = s
        samples.add(s)

        Log.i(TAG, "═════════ 측정 시작 [$label] ═════════")
        Log.i(TAG, "기기: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
        logBatterySample(s)
        if (s.plugged) {
            Log.e(TAG, "‼ 충전기가 연결되어 있습니다. 배터리 측정이 무의미합니다. (USB 분리 / 무선 adb 사용)")
        }
        if (s.chargeMah <= 0f) {
            Log.w(TAG, "이 기기는 CHARGE_COUNTER(mAh)를 지원하지 않습니다. % 단위로만 비교됩니다.")
        }

        handler = Handler(Looper.getMainLooper()).also {
            it.postDelayed(reportRunnable, REPORT_INTERVAL_MS)
        }
    }

    fun stopSession() {
        val ctx = appCtx ?: return
        handler?.removeCallbacks(reportRunnable)
        handler = null

        val end = snapshot(ctx)
        samples.add(end)
        val start = startSnap ?: return

        val durationMs = SystemClock.elapsedRealtime() - sessionStartMs
        val hours = durationMs / 3_600_000.0
        val pctDrop = start.pct - end.pct
        val mahDrop = start.chargeMah - end.chargeMah
        val avgCurrent = samples.map { it.currentMa }.filter { it > 0f }.average()
        val maxTemp = samples.maxOf { it.tempC }

        Log.i(TAG, "═════════ 측정 종료 [$label] ═════════")
        Log.i(TAG, "측정 시간   : ${fmt(durationMs / 60000.0)} 분")
        Log.i(TAG, "배터리 %    : ${fmt(start.pct.toDouble())} → ${fmt(end.pct.toDouble())}  (−${fmt(pctDrop.toDouble())}%)")
        if (hours > 0) Log.i(TAG, "소모 속도(%) : ${fmt(pctDrop / hours)} %/h   (1% 단위라 짧은 측정은 오차 큼)")
        if (start.chargeMah > 0f && hours > 0) {
            Log.i(TAG, "소모량(mAh) : ${fmt(mahDrop.toDouble())} mAh")
            Log.i(TAG, "소모 속도   : ${fmt(mahDrop / hours)} mAh/h   ← 비교 시 이 값을 사용")
        }
        if (!avgCurrent.isNaN()) Log.i(TAG, "평균 전류    : ${fmt(avgCurrent)} mA (샘플 평균)")
        Log.i(TAG, "최고 온도    : ${fmt(maxTemp.toDouble())} °C")
        if (durationMs < 10 * 60_000L) {
            Log.w(TAG, "측정 시간이 10분 미만이라 신뢰도가 낮습니다. 최소 15~30분 권장.")
        }
        logSummary("최종 지연시간 요약")
        saveCsv(ctx)

        appCtx = null
        startSnap = null
    }

    private fun logBatterySample(s: Snap) {
        val elapsedMin = s.elapsedMs / 60000.0
        val start = startSnap
        var rate = ""
        if (start != null && s.elapsedMs > 60_000L) {
            val h = s.elapsedMs / 3_600_000.0
            rate = if (start.chargeMah > 0f) {
                " | 누적 ${fmt(((start.chargeMah - s.chargeMah) / h))} mAh/h"
            } else {
                " | 누적 ${fmt(((start.pct - s.pct) / h).toDouble())} %/h"
            }
        }
        Log.i(
            TAG,
            "🔋 t=${fmt(elapsedMin)}분 | ${fmt(s.pct.toDouble())}% | ${fmt(s.chargeMah.toDouble())}mAh | " +
                    "전류 ${fmt(s.currentMa.toDouble())}mA | ${fmt(s.tempC.toDouble())}°C | thermal=${s.thermal}$rate"
        )
    }

    private fun snapshot(ctx: Context): Snap {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100f / scale else -1f
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val chargeUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        } else -1

        return Snap(
            elapsedMs = SystemClock.elapsedRealtime() - sessionStartMs,
            pct = pct,
            chargeMah = if (chargeUah > 0) chargeUah / 1000f else 0f,
            // 전류 부호는 제조사마다 달라서 절댓값 사용. 미지원이면 Long.MIN_VALUE
            currentMa = if (currentUa == Long.MIN_VALUE) 0f else abs(currentUa) / 1000f,
            tempC = temp,
            thermal = thermal,
            plugged = plugged
        )
    }

    // ---------------------------------------------------------------- CSV 저장

    private fun saveCsv(ctx: Context) {
        try {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

            val battery = File(dir, "perf_${label}_${ts}_battery.csv")
            battery.printWriter().use { w ->
                w.println("elapsed_s,pct,charge_mAh,current_mA,temp_C,thermal,plugged")
                samples.forEach {
                    w.println("${it.elapsedMs / 1000},${it.pct},${it.chargeMah},${it.currentMa},${it.tempC},${it.thermal},${it.plugged}")
                }
            }

            val latency = File(dir, "perf_${label}_${ts}_latency.csv")
            latency.printWriter().use { w ->
                w.println("tag,count,avg_ms,min_ms,p50_ms,p95_ms,max_ms")
                stats.keys.sorted().forEach { tag ->
                    val s = stats[tag] ?: return@forEach
                    synchronized(s) {
                        val sorted = s.recent.sorted()
                        w.println(
                            "$tag,${s.count},${fmt(s.sum / s.count)},${fmt(s.min)}," +
                                    "${fmt(percentile(sorted, 0.5))},${fmt(percentile(sorted, 0.95))},${fmt(s.max)}"
                        )
                    }
                }
            }
            Log.i(TAG, "📁 CSV 저장: ${dir.absolutePath}")
            Log.i(TAG, "   ${battery.name}")
            Log.i(TAG, "   ${latency.name}")
        } catch (e: Exception) {
            Log.e(TAG, "CSV 저장 실패", e)
        }
    }
}