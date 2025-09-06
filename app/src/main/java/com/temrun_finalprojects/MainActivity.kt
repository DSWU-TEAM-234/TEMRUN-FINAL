package com.temrun_finalprojects

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.telecom.VideoProfile.isPaused
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.registerReceiver
import com.skyfishjy.library.RippleBackground
import com.temrun_finalprojects.breathing.audio.FeedbackTTS
import com.prolificinteractive.materialcalendarview.BuildConfig
import com.temrun_finalprojects.data.Song
import com.temrun_finalprojects.result.ResultActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.temrun_finalprojects.data.BreathResultCount
import com.temrun_finalprojects.config.ApiConfig


// 칼만 필터 클래스 정의
class KalmanFilter1D(
    private val processNoise: Float = 0.008f,
    private val measurementNoise: Float = 0.1f
) {
    private var estimate = 0f
    private var errorCovariance = 1f

    fun update(measurement: Float): Float {
        val kalmanGain = errorCovariance / (errorCovariance + measurementNoise)
        estimate += kalmanGain * (measurement - estimate)
        errorCovariance = (1 - kalmanGain) * errorCovariance + processNoise
        return estimate
    }
}

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var webView: WebView
    private lateinit var cadenceTextView: TextView
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var tflite: Interpreter? = null
    private val cadenceDataList = mutableListOf<Int>()  // 실시간 케이던스 데이터 저장
    private var targetCadence = 160  // 목표 케이던스 (러닝 시작 시 설정값)

    // 케이던스 모델 관련
    private val modelName = "model_0811_5s.tflite"
    private val windowSizeMillis = 5000L
    private val slideIntervalMillis = 1000L
    private val sensorBuffer = mutableListOf<Triple<Long, SensorType, FloatArray>>()
    private val kalmanFilters = mutableMapOf<String, KalmanFilter1D>()

    private val cadenceHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var latestPredictedCadence: Int = 0

    private val predictionHistory = mutableListOf<Int>()
    private val finalPredictionHistory = mutableListOf<Int>() //  최종 걸음 예측값 리스트

    private val smoothingWindowSize = 5
    private val outlierThreshold = 50

    private var isPaused = false
    private var currentBpm = 0

    // 애니메이션 관련 변수
    data class RingHolder(val view: ImageView, var isAnimating: Boolean = false)
    private var ringAnimatorHandler: Handler? = null
    private var ringAnimatorRunnable: Runnable? = null
    private val ringPool = mutableListOf<RingHolder>()

    private lateinit var rippleHost: FrameLayout
    private lateinit var circleStatsView: FrameLayout

    private val oneShotRippleDurationMs = 1000L
    private val maxConcurrentRipples = 6

    // 센서 관련 변수
    private var lastStepTime = 0L
    private val stepIntervalThreshold = 300

    // 메트로놈 관련 변수
    private var metronomeJob: Job? = null
    private var shouldVibrate = false

    // 거리, 칼로리
    private lateinit var calorieTextView: TextView
    private var calorie = 0.0

    private lateinit var distanceTextView: TextView
    private var distance = 0.0

    // 달린 시간
    private var elapsedSeconds = 0
    private lateinit var timeTextView: TextView
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null

    // 재생됐던 음악 리스트
    private val playedTracks = ArrayList<Song>()
    private val bpmList = ArrayList<Int>()

    // 호흡 피드백
    private var breathingFeedbackTTS: FeedbackTTS? = null

    enum class SensorType { ACCELEROMETER, GYROSCOPE }

    private val breathResultCount = BreathResultCount()

    // *** 추가: TFLite 접근 보호용 락 & 상태 플래그
    private val tfliteLock = Any() // ***
    @Volatile private var isInterpreterClosed = false // ***

/*
// TODO: 서버 재기동 시 교체
private val BASE_URL ="https://4382a6a5c3d2.ngrok-free.app"
// 러닝 세션 시작 API
private val START_RUN_URL = "$BASE_URL/api/runs/start"
// 추천 트랙 API
private val RECO_URL = "$BASE_URL/api/recommend"

 */

    private fun getStartRunUrl() = ApiConfig.getStartRunUrl()
    private fun getRecommendUrl() = ApiConfig.getRecommendUrl()

// OkHttp (로깅 인터셉터 포함)
private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request()
            val ct = req.body?.contentType()
            Log.d("HTTP", "➡ ${req.method} ${req.url} CT=$ct")

            val res = chain.proceed(req)
            val raw = res.body?.string().orEmpty()
            Log.d("HTTP", "⬅ code=${res.code} body=$raw")

            res.newBuilder()
                .body(raw.toByteArray().toResponseBody(res.body?.contentType()))
                .build()
        }
        .build()
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

// 세션 runId 보관
private var currentRunId: String? = null

// 토큰
private var accessToken: String? = null
private var refreshToken: String? = null

private val predictionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val result1 = intent?.getStringExtra("result1")
        val result2 = intent?.getStringExtra("resultToTts")

        result1?.let { breathResultCount.add(it) }
        result2?.let { breathResultCount.add(it) }

        if (!result1.isNullOrEmpty()) {
            Log.d("MainActivity", "받은 예측 결과(모델1): $result1")
        }

        if (!result2.isNullOrEmpty()) {
            Log.d("MainActivity", "받은 예측 결과(모델2): $result2")
            breathingFeedbackTTS?.speak(result2)
        }

        val breathFrame = findViewById<FrameLayout>(R.id.breathFrame)
        when {
            // 모델1: 정상일 때
            result1 == "정상" -> {
                breathFrame.setBackgroundResource(R.drawable.ic_breath_green)
            }

            // 모델2: 상세 피드백이 들어왔을 때
            !result2.isNullOrEmpty() -> {
                breathFrame.setBackgroundResource(R.drawable.ic_breath_red)
            }

            // 그 외 (모델1 비정상)
            else -> {
                breathFrame.setBackgroundResource(R.drawable.ic_breath_red)
            }
        }

    }

}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main2)

    setupLayoutConstraints()
    setupTFLite()
    setupSensors()
    setupWebView()

    val filter = IntentFilter("PREDICTION_UPDATE")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(predictionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("DEPRECATION")
        registerReceiver(predictionReceiver, filter)
    }

    cadenceTextView = findViewById(R.id.TextAvgNum)
    cadenceHandler.post(cadenceRunnable)

    rippleHost = findViewById(R.id.rippleHost)
    circleStatsView = findViewById(R.id.circleStats)

    rippleHost.viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() { /* no-op */ }
        }
    )

    // Home에서 넘어온 값 로그 출력
    val mode = intent.getStringExtra("mode")
    val breath = intent.getStringExtra("breath")
    val cadence = intent.getIntExtra("cadence", -1)
    val time = intent.getIntExtra("time", -1)

    Log.d("MainActivity/Intent", "mode = $mode, breath = $breath, cadence = $cadence, time = $time")

    // 버튼 연결
    val pauseButton: ImageButton = findViewById(R.id.pauseButton)
    val pauseOptions: LinearLayout = findViewById(R.id.pauseOptions)
    val resumeButton: ImageButton = findViewById(R.id.resumeButton)
    val endButton: ImageButton = findViewById(R.id.endButton)

    pauseButton.setOnClickListener {
        webView.evaluateJavascript("window.setMovementState(false);", null)
        isPaused = true
        stopTimer()
        pauseButton.visibility = View.GONE
        pauseOptions.visibility = View.VISIBLE
    }

    resumeButton.setOnClickListener {
        webView.evaluateJavascript("window.setMovementState(true);", null)
        isPaused = false
        startTimer()
        pauseOptions.visibility = View.GONE
        pauseButton.visibility = View.VISIBLE
    }

    endButton.setOnClickListener {
        // 1) 재생/타이머/메트로놈 정지
        webView.evaluateJavascript("window.setMovementState(false);", null)
        stopTimer()
        stopMetronome()
        isPaused = true

        // *** 추가: 케이던스 루프 즉시 중지
        cadenceHandler.removeCallbacks(cadenceRunnable) // ***

        // 2) 전달할 값 스냅샷
        val songsToSend = ArrayList(playedTracks)
        val timeToSend = elapsedSeconds
        val calorieToSend = calorie
        val distanceToSend = distance
        val avgBpmToSend = if (bpmList.isNotEmpty()) bpmList.average().toInt() else 0
        val targetCadence = cadence.toInt()

        // finalPredictionHistory와 averageBPM의 차이 평균 계산
        val averageBPM = bpmList.average().toInt()
        val averageDifference = if (finalPredictionHistory.isNotEmpty()) {
            finalPredictionHistory.map { kotlin.math.abs(it - averageBPM) }.average()
        } else {
            0.0  // finalPredictionHistory가 비어있으면 0 리턴
        }


        // 3) 결과 화면으로 이동
        val intent = Intent(this, ResultActivity::class.java).apply {
            putParcelableArrayListExtra("songs", songsToSend)
            Log.d("ResultIntent", "songs: $songsToSend")

            putExtra("time", timeToSend)
            Log.d("ResultIntent", "time: $timeToSend")

            putExtra("calorie", calorieToSend)
            Log.d("ResultIntent", "calorie: $calorieToSend")

            putExtra("distance", distanceToSend)
            Log.d("ResultIntent", "distance: $distanceToSend")

            putExtra("averageBPM", avgBpmToSend)
            Log.d("ResultIntent", "averageBPM: $avgBpmToSend")

            putExtra("targetCadence", targetCadence)
            Log.d("ResultIntent", "targetCadence: $targetCadence")

            putExtra("cadenceAccuracy", averageDifference)
            Log.d("ResultIntent", "cadenceAccuracy: $averageDifference")

            putExtra("breath_normal", breathResultCount.normal)
            putExtra("breath_patternOnly", breathResultCount.patternOnly)
            putExtra("breath_organOnly", breathResultCount.organOnly)
            putExtra("breath_bothMismatch", breathResultCount.bothMismatch)
            Log.d(
                "ResultIntent",
                "breath -> normal=${breathResultCount.normal}, " +
                        "patternOnly=${breathResultCount.patternOnly}, " +
                        "organOnly=${breathResultCount.organOnly}, " +
                        "bothMismatch=${breathResultCount.bothMismatch}"
            )

            putIntegerArrayListExtra("cadenceDataList", ArrayList(cadenceDataList))
            Log.d("ResultIntent", "cadenceDataList: $cadenceDataList")
        }
        startActivity(intent)

    }


    // 달린 시간
    timeTextView = findViewById(R.id.timeText)
    // 칼로리
    calorieTextView = findViewById(R.id.calorieText)
    // 거리
    distanceTextView = findViewById(R.id.distanceText)

    // 저장된 토큰 불러오기
    val prefs = getSharedPreferences("Spotify", MODE_PRIVATE)
    accessToken = prefs.getString("access_token", null)
    refreshToken = prefs.getString("refresh_token", null)

    // TTS
    breathingFeedbackTTS = FeedbackTTS(this) {
        Log.d("MainActivity/TTS", "TTS 초기화 완료")
    }
}

// 달린 시간 관련 함수 -----------------------------------------
private fun startTimer() {
    timerHandler = Handler(Looper.getMainLooper())
    timerRunnable = object : Runnable {
        override fun run() {
            if (!isPaused) {
                elapsedSeconds += 1
                updateTimerText()
            }
            timerHandler?.postDelayed(this, 1000)
        }
    }
    timerHandler?.post(timerRunnable!!)
}

private fun stopTimer() {
    timerHandler?.removeCallbacks(timerRunnable!!)
}

private fun updateTimerText() {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    timeTextView.text = formattedTime
}

// 센서 -------------------------------------------------
@RequiresPermission(Manifest.permission.VIBRATE)
override fun onSensorChanged(event: SensorEvent?) {
    if (event == null) return

    event.let {
        val timestamp = System.currentTimeMillis()
        val values = it.values.copyOf()
        val type = when (it.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> SensorType.ACCELEROMETER
            Sensor.TYPE_GYROSCOPE -> SensorType.GYROSCOPE
            else -> return
        }
        sensorBuffer.add(Triple(timestamp, type, values))
    }

    when (event.sensor.type) {
        Sensor.TYPE_ACCELEROMETER -> {
            val accel = sqrt(
                event.values[0].pow(2) +
                        event.values[1].pow(2) +
                        event.values[2].pow(2)
            )
            if (detectStep(accel)) {
                spawnOneShotRipple()
                calorie += 0.03
                calorieTextView.text = String.format("%.01f", calorie)
                distance += 0.01
                distanceTextView.text = String.format("%.01f", distance)
            }
        }
    }
}

private fun detectStep(accelMagnitude: Float): Boolean {
    val now = System.currentTimeMillis()
    return if (accelMagnitude > 12.0f && (now - lastStepTime) > stepIntervalThreshold) {
        lastStepTime = now
        true
    } else {
        false
    }
}

@RequiresPermission(Manifest.permission.VIBRATE)
private fun triggerVibration() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(VibratorManager::class.java)
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(
            VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    } else {
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(100)
    }
}

//-------------------------------------------
// 웹뷰 세팅 함수
@SuppressLint("SetJavaScriptEnabled")
private fun setupWebView() {
    webView = findViewById(R.id.webView)
    webView.settings.apply {
        javaScriptEnabled = true
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        domStorageEnabled = true
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest?) {
            val resources = request?.resources ?: return super.onPermissionRequest(request)
            for (r in resources) {
                if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID == r) {
                    request.grant(resources); return
                }
            }
            super.onPermissionRequest(request)
        }
    }

    webView.addJavascriptInterface(object {
        @JavascriptInterface
        fun onWebReady() {
            webView.post {

                val t = accessToken
                if (t.isNullOrBlank()) {
                    Log.e("SPOTIFY", "액세스 토큰 없음(빈 값). Spotify 로그인 필요.")
                    Toast.makeText(this@MainActivity, "Spotify 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                    return@post
                }
                Log.d("SPOTIFY/TOKEN", "len=${t.length} head=${t.take(10)}")

                // 1) 토큰 전달
                val tokenJs = org.json.JSONObject.quote(accessToken ?: "")
                webView.evaluateJavascript("window.receiveToken($tokenJs);", null)

                // 2) 인텐트에서 시작 파라미터 수집
                val uiMode = intent.getStringExtra("mode") ?: "normal"
                val serverMode = when (uiMode.lowercase()) {
                    "normal" -> "normal"
                    "training", "beginner" -> "beginner"
                    else -> "beginner"
                }
                val breath = intent.getStringExtra("breath") ?: "1_1"
                val cadence = intent.getIntExtra("cadence", 150)
                val durationMin = intent.getIntExtra("time", 15).let { if (it <= 0) 60 else it }
                currentBpm = cadence

                // 3) 러닝 세션 시작 → tracks 수신
                startRunSession(
                    mode = serverMode,
                    breathPattern = breath,
                    targetCadence = cadence,
                    durationMin = durationMin
                )
            }
        }

        @JavascriptInterface
        fun onTrackPlayWithInfo(trackId: String, title: String, artist: String, albumImageUrl: String, bpm: Int) {
            runOnUiThread {
                playedTracks.add(Song(trackId, title, artist, albumImageUrl))
                bpmList.add(bpm)
                stopMetronome()
                startMetronome(bpm)
                requestVibrationFeedback()
                startTimer()
            }
        }

        @JavascriptInterface
        fun onTrackEnd() {
            Log.d("WebSignal", "🎵 트랙이 끝났습니다!")
        }
    }, "AndroidInterface")

    webView.loadUrl("https://temrun.netlify.app/")
}

private fun sendTracksToWebViewSafe(jsonText: String) {
    val quoted = org.json.JSONObject.quote(jsonText) // "...." 로 이스케이프
    val js = "window.receiveTrack(JSON.parse($quoted));"
    webView.evaluateJavascript(js, null)
}

private fun tryKickstartSpotifyWebPlayback(firstTrackUri: String) {
    val token = accessToken ?: run {
        Log.e("SPOTIFY", "액세스 토큰 없음"); return
    }

    // 1) 내 디바이스 목록
    val devicesReq = okhttp3.Request.Builder()
        .url("https://api.spotify.com/v1/me/player/devices")
        .get()
        .addHeader("Authorization", "Bearer $token")
        .build()

    httpClient.newCall(devicesReq).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) {
            Log.e("SPOTIFY", "devices 조회 실패", e)
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    Log.e("SPOTIFY", "devices HTTP ${res.code}: $body")
                    return
                }
                val devices = try {
                    org.json.JSONObject(body).optJSONArray("devices") ?: org.json.JSONArray()
                } catch (e: org.json.JSONException) {
                    Log.e("SPOTIFY", "devices 파싱 실패: $body", e)
                    org.json.JSONArray()
                }

                // 웹에서 만든 플레이어 이름과 동일해야 함 (웹 코드: name: "TRUE Main Spotify Player")
                var targetId: String? = null
                for (i in 0 until devices.length()) {
                    val d = devices.optJSONObject(i) ?: continue
                    val name = d.optString("name", "")
                    val id = d.optString("id", "")
                    if (name.contains("TRUE Main Spotify Player")) { targetId = id; break }
                }

                if (targetId.isNullOrBlank()) {
                    Log.e("SPOTIFY", "웹 플레이어 디바이스를 찾지 못함(아직 connect 안 됐을 수 있음).")
                    return
                }

                // 2) 해당 디바이스로 전송(활성화)
                val transferJson = org.json.JSONObject().apply {
                    put("device_ids", org.json.JSONArray().put(targetId))
                    put("play", false)
                }
                val transferReq = okhttp3.Request.Builder()
                    .url("https://api.spotify.com/v1/me/player")
                    .put(transferJson.toString().toRequestBody(JSON_MEDIA))
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .build()

                httpClient.newCall(transferReq).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        Log.e("SPOTIFY", "transfer 실패", e)
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.use { tr ->
                            val tb = tr.body?.string().orEmpty()
                            if (!tr.isSuccessful) {
                                Log.e("SPOTIFY", "transfer HTTP ${tr.code}: $tb")
                                return
                            }

                            // 3) 첫 트랙 재생
                            val playJson = org.json.JSONObject().apply {
                                put("uris", org.json.JSONArray().put(firstTrackUri))
                                put("position_ms", 0)
                            }
                            val playReq = okhttp3.Request.Builder()
                                .url("https://api.spotify.com/v1/me/player/play?device_id=$targetId")
                                .put(playJson.toString().toRequestBody(JSON_MEDIA))
                                .addHeader("Authorization", "Bearer $token")
                                .addHeader("Content-Type", "application/json")
                                .build()

                            httpClient.newCall(playReq).enqueue(object : okhttp3.Callback {
                                override fun onFailure(call: okhttp3.Call, e: IOException) {
                                    Log.e("SPOTIFY", "play 실패", e)
                                }
                                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                                    response.use { pr ->
                                        val pb = pr.body?.string().orEmpty()
                                        if (!pr.isSuccessful) {
                                            Log.e("SPOTIFY", "play HTTP ${pr.code}: $pb")
                                        } else {
                                            Log.d("SPOTIFY", "play OK (웹 플레이어로 킥)")
                                        }
                                    }
                                }
                            })
                        }
                    }
                })
            }
        }
    })
}


fun startMetronome(bpm: Int) {
    val interval = (60_000 / bpm).toLong()
    metronomeJob?.cancel()
    metronomeJob = CoroutineScope(Dispatchers.Main).launch {
        while (isActive) {
            if (shouldVibrate) triggerVibration()
            Log.d("Metronome", "🔔 Tick")
            delay(interval)
        }
    }
}

fun stopMetronome() {
    metronomeJob?.cancel()
}

fun requestVibrationFeedback(durationMs: Long = 5000L) {
    shouldVibrate = true
    CoroutineScope(Dispatchers.Main).launch {
        delay(durationMs); shouldVibrate = false
    }
}



// 애니메이션 --------------------------------------------------

private fun spawnOneShotRipple() {
    val ripple = layoutInflater.inflate(
        R.layout.view_step_ripple, rippleHost, false
    ) as RippleBackground

    rippleHost.addView(ripple)
    ripple.post {
        val circleCenterX = circleStatsView.x + circleStatsView.width / 2f
        val circleCenterY = circleStatsView.y + circleStatsView.height / 2f

        val w = ripple.width.toFloat()
        val h = ripple.height.toFloat()

        ripple.x = circleCenterX - w / 2f
        ripple.y = circleCenterY - h / 2f

        ripple.startRippleAnimation()

        ripple.postDelayed({
            ripple.stopRippleAnimation()
            ripple.post { rippleHost.removeView(ripple) }
        }, oneShotRippleDurationMs)
    }

    if (rippleHost.childCount > maxConcurrentRipples) {
        val oldest = rippleHost.getChildAt(0) as? RippleBackground
        oldest?.stopRippleAnimation()
        rippleHost.removeViewAt(0)
    }
}

// 케이던스 예측 루프 -----------------------------------------
private var lastVibrationTime = 0L
private val cadenceRunnable = object : Runnable {
    override fun run() {
        val currentTime = System.currentTimeMillis()
        val windowStart = currentTime - windowSizeMillis

        val recentData = sensorBuffer.filter { it.first in windowStart..currentTime }
        sensorBuffer.removeAll { it.first < windowStart }

        Log.d("버퍼", "최근 센서 크기: ${recentData.size}")

        if (recentData.isNotEmpty()) {
            executor.execute {
                val sensorDataMap = mutableMapOf<String, MutableList<Float>>()

                for ((_, type, values) in recentData) {
                    val prefix = if (type == SensorType.ACCELEROMETER) "accel" else "gyro"
                    for (i in values.indices) {
                        val key = "${prefix}_$i"
                        sensorDataMap.getOrPut(key) { mutableListOf() }.add(values[i])
                    }
                }

                val preprocessStart = System.currentTimeMillis()
                val inputBuffer = convertGraphsToModelInput(sensorDataMap)
                val preprocessEnd = System.currentTimeMillis()
                Log.d("TIME", "전처리 시간: ${preprocessEnd - preprocessStart}ms")

                val inferenceStart = System.currentTimeMillis()
                val output = Array(1) { FloatArray(1) }
//                tflite?.run(inputBuffer, output)

                // *** 추가: 닫힘/널 방어 + 동기화 + 예외 처리
                try { // ***
                    synchronized(tfliteLock) { // ***
                        if (isInterpreterClosed || tflite == null) return@execute // ***
                        tflite!!.run(inputBuffer, output) // ***
                    } // ***
                } catch (e: IllegalStateException) { // ***
                    Log.w("TFLite", "Interpreter already closed. skip inference.", e) // ***
                    return@execute // ***
                } // ***

                val inferenceEnd = System.currentTimeMillis()
                Log.d("TIME", "모델 추론 시간: ${inferenceEnd - inferenceStart}ms")

                val rawPrediction = output[0][0].roundToInt()

                predictionHistory.add(rawPrediction)
                if (predictionHistory.size > smoothingWindowSize) {
                    predictionHistory.removeAt(0)
                }

                val average = predictionHistory.average().toInt()
                val finalPrediction = if (predictionHistory.size >= 2) {
                    val prev = predictionHistory[predictionHistory.size - 2]
                    if (kotlin.math.abs(rawPrediction - prev) > outlierThreshold) {
                        Log.w("Outlier", "예측값 $rawPrediction → $average (보정)")
                        average
                    } else {
                        rawPrediction
                    }
                } else {
                    rawPrediction
                }

                runOnUiThread {
                    cadenceTextView.text = "$finalPrediction"
                    if (!isPaused) {
                        cadenceDataList.add(finalPrediction)
                    }

                    // 케이던스 정확도 계산을 위함
                    // 배열에 케이던스 값 하나씩 추가하기
                    finalPredictionHistory.add(finalPrediction)
                    Log.d("Checking cadence history", "History: $finalPredictionHistory")

                    val bpmDiff = kotlin.math.abs(finalPrediction - currentBpm)
                    val cadenceFrame = findViewById<FrameLayout>(R.id.cadenceFrame)
                    if (bpmDiff >= 5) {
                        cadenceFrame.setBackgroundResource(R.drawable.ic_cadence_red)

                        val now = System.currentTimeMillis()
                        // 4초(4000ms) 쿨다운 체크
                        if (now - lastVibrationTime >= 10000) {
                            requestVibrationFeedback(8000)
                            lastVibrationTime = now
                        }


                    } else {
                        cadenceFrame.setBackgroundResource(R.drawable.ic_cadence_green)
                    }
                }
            }
        }

        if (isFinishing || isInterpreterClosed) return // ***

        cadenceHandler.postDelayed(this, slideIntervalMillis)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
override fun onResume() {
    super.onResume()
    accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

    registerReceiver(predictionReceiver, IntentFilter("PREDICTION_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
}

override fun onPause() {
    super.onPause()
    sensorManager.unregisterListener(this)

    // *** 추가: 핸들러 루프 중지
    cadenceHandler.removeCallbacks(cadenceRunnable) // ***

    for (i in rippleHost.childCount - 1 downTo 0) {
        val v = rippleHost.getChildAt(i)
        if (v is RippleBackground) v.stopRippleAnimation()
        rippleHost.removeViewAt(i)
    }
    unregisterReceiver(predictionReceiver)
}

override fun onDestroy() {
    cadenceHandler.removeCallbacks(cadenceRunnable)
    tflite?.close()
    super.onDestroy()
    breathingFeedbackTTS?.destroy()
    unregisterReceiver(predictionReceiver)

    // *** 추가: 실행기 중지 (대기 작업 즉시 취소)
    executor.shutdownNow() // ***

    // *** 추가: 닫힘 플래그 먼저 세팅
    isInterpreterClosed = true // ***

    // *** 수정: 닫을 때 동기화 & 널 처리
    synchronized(tfliteLock) { // ***
        tflite?.close()
        tflite = null
    } // ***
}

override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

// 센서 데이터를 그래프로 시각화하여 CNN 모델 입력으로 변환
private fun convertGraphsToModelInput(sensorDataMap: Map<String, List<Float>>): ByteBuffer {
    val width = 224
    val height = 224
    val bitmaps = mutableListOf<Bitmap>()

    val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 2f
        isAntiAlias = true
    }

    sensorDataMap.values.take(6).forEach { data ->
        if (data.size < 2) return@forEach
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

        val minVal = data.minOrNull() ?: 0f
        val maxVal = data.maxOrNull() ?: 1f
        val normData = data.map { (it - minVal) / (maxVal - minVal + 1e-6f) }
        val step = width.toFloat() / (normData.size - 1)

        for (i in 0 until normData.size - 1) {
            val x1 = i * step
            val y1 = height - normData[i] * height
            val x2 = (i + 1) * step
            val y2 = height - normData[i + 1] * height
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
        bitmaps.add(bitmap)
    }

    while (bitmaps.size < 6) {
        val blank = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(blank).drawColor(Color.WHITE)
        bitmaps.add(blank)
    }

    val buffer = ByteBuffer.allocateDirect(width * height * 6 * 4).order(ByteOrder.nativeOrder())
    for (y in 0 until height) {
        for (x in 0 until width) {
            for (bitmap in bitmaps) {
                val pixel = bitmap.getPixel(x, y)
                buffer.putFloat(Color.red(pixel) / 255f)
            }
        }
    }
    buffer.rewind()
    return buffer
}

private fun setupTFLite() {
    try {
        val options = Interpreter.Options().addDelegate(FlexDelegate())
        tflite = Interpreter(loadModelFile(), options)
    } catch (e: IOException) {
        Log.e("TFLite", "모델 로드 실패: ${e.message}")
    }
}

private fun loadModelFile(): ByteBuffer {
    assets.open(modelName).use { inputStream ->
        val buffer = inputStream.readBytes()
        return ByteBuffer.allocateDirect(buffer.size).order(ByteOrder.nativeOrder()).apply {
            put(buffer)
            rewind()
        }
    }
}

private fun setupSensors() {
    sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
}

// 폰 크기에 따라 레이아웃 바꾸는 함수
private fun setupLayoutConstraints() {
    val screenWidthDp = resources.configuration.screenWidthDp
    val ratio = if (screenWidthDp < 360) 0.9f else 0.6f
    findViewById<ConstraintLayout>(R.id.mainLayout2).let {
        ConstraintSet().apply {
            clone(it)
            constrainPercentWidth(R.id.circleStats, ratio)
            constrainPercentWidth(R.id.bottomStatsContainer, ratio)
            constrainPercentWidth(R.id.musicContainer, ratio)
            applyTo(it)
        }
    }
}

// ------------------------- 서버 API: 러닝 세션 시작 -------------------------
/**
 * 서버 스펙 (snake_case):
 * {
 *   "user_id": "u12345",
 *   "mode": "normal" | "beginner",
 *   "target_cadence": 160,
 *   "target_duration": 1800, // seconds
 *   "breath_pattern": "2:2"
 * }
 *
 * 응답 예(현재 서버):
 * {
 *   "id": 6,
 *   "user_id": "...",
 *   "mode": "training",
 *   "target_cadence": 180,
 *   "target_duration": 3600,
 *   "breath_pattern": "1:1",
 *   "start_time": "...",
 *   "end_time": null
 * }
 *  → tracks 필드 없음
 */
private fun startRunSession(
    mode: String,
    breathPattern: String,
    targetCadence: Int,
    durationMin: Int
) {
    val safeMode = if (mode.isBlank()) "beginner" else mode
    val safeBreath = if (breathPattern.isBlank()) "1:1" else breathPattern
    val safeCadence = if (targetCadence <= 0) 150 else targetCadence
    val safeDurationSec = (if (durationMin <= 0) 60 else durationMin) * 60

    val userId = getSharedPreferences("AppUser", MODE_PRIVATE)
        .getString("user_id", null) ?: "u12345"

    val payload = org.json.JSONObject().apply {
        put("userId", userId)
        put("mode", safeMode)               // camelCase
        put("breathPattern", safeBreath)
        put("targetCadence", safeCadence)
        put("targetDuration", safeDurationSec)
    }
    Log.d("RUN_START/PAYLOAD", payload.toString())

    val req = okhttp3.Request.Builder()
//        .url(START_RUN_URL)
        .url(getStartRunUrl())
        .post(payload.toString().toRequestBody(JSON_MEDIA))
        .addHeader("Content-Type", "application/json")
        .addHeader("Accept", "application/json")
        .build()

    httpClient.newCall(req).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            Log.e("RUN_START", "세션 시작 실패", e)
            runOnUiThread {
                Toast.makeText(this@MainActivity, "세션 시작 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    Log.e("RUN_START", "HTTP ${res.code}: $text")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "세션 시작 오류: HTTP ${res.code}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                try {
                    val obj = org.json.JSONObject(text)
                    val runId = obj.opt("id")?.toString()
                    currentRunId = runId
                    runId?.let {
                        getSharedPreferences("AppUser", MODE_PRIVATE)
                            .edit().putString("run_id", it).apply()
                        Log.d("RUN_START", "runId 저장됨: $it")
                    }

                    // 세션 성공 → 추천 호출 (웹으로 즉시 푸시 + 메트로놈/타이머 시작은 그때)
                    fetchRecommendedTracks(userId, safeCadence, safeDurationSec)

                } catch (e: org.json.JSONException) {
                    Log.e("RUN_START", "응답 파싱 실패: $text", e)
                }
            }
        }
    })
}





// 웹뷰 푸시 헬퍼
private fun pushTracksToWebView(tracks: org.json.JSONArray) {
    webView.evaluateJavascript("window.receiveTrack(${tracks.toString()});", null)
}

/** 3분(180초)당 1곡으로 limit 계산 → GET → (성공 시) 즉시 웹뷰로 푸시 */
private fun fetchRecommendedTracks(
    userId: String,
    bpm: Int,
    durationSec: Int
) {
    val limit = kotlin.math.ceil(durationSec / 180.0).toInt().coerceIn(1, 50)

//    val httpUrl = RECO_URL.toHttpUrlOrNull()
    val httpUrl = getRecommendUrl().toHttpUrlOrNull()
        ?.newBuilder()
        ?.addQueryParameter("user_id", userId)
        ?.addQueryParameter("bpm", bpm.toString())
        ?.addQueryParameter("limit", limit.toString())
        ?.build() ?: run {
//        Log.e("RECO", "Invalid RECO_URL: $RECO_URL"); return
        Log.e("RECO", "Invalid RECO_URL"); return
    }

    val req = okhttp3.Request.Builder()
        .url(httpUrl)
        .get()
        .addHeader("Accept", "application/json")
        .build()

    httpClient.newCall(req).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            Log.e("RECO", "추천 요청 실패", e)
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    Log.e("RECO", "HTTP ${res.code}: $body")
                    return
                }

                // 배열 또는 {tracks:[...]} 모두 처리
                val arr = try { org.json.JSONArray(body) }
                catch (_: org.json.JSONException) {
                    val obj = org.json.JSONObject(body)
                    obj.optJSONArray("tracks") ?: org.json.JSONArray()
                }

                Log.d("RECO", "got=${arr.length()} items; first=${arr.optJSONObject(0)}")

                runOnUiThread {
                    if (arr.length() > 0) {
                        // 웹으로 안전 주입 (배열 그대로)
                        sendTracksToWebViewSafe(arr.toString())

                        // 첫 곡 URI 뽑아서, 웹이 자동재생을 못할 때를 대비해 안드가 한 번 킥
                        extractFirstTrackUri(arr)?.let { firstUri ->
                            tryKickstartSpotifyWebPlayback(firstUri)
                        }

                    } else {
                        Log.w("RECO", "추천 0건 - 재생하지 않음")
                    }
                }

            }
        }
    })
}

private fun toSpotifyTrackUri(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return if (raw.startsWith("spotify:track:")) raw else "spotify:track:$raw"
}

private fun extractFirstTrackUri(arr: org.json.JSONArray): String? {
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val raw = o.optString("uri", null) ?: o.optString("id", null)
        val uri = toSpotifyTrackUri(raw)
        if (!uri.isNullOrBlank()) return uri
    }
    return null
}




}