package com.temrun_finalprojects

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.temrun_finalprojects.data.Song
import com.temrun_finalprojects.result.ResultActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

    //웹에 토큰 보내는 코드. 노래가 안나오면
    //https://developer.spotify.com/documentation/web-playback-sdk/tutorials/getting-started
    //여기서 토큰 받아와서 바꿔주면 됨
    private val TOKEN = "BQD3SSpY95MvXw1ouD86X-wM4rFMB5AbphaXCi_BhGFFtdsdWQFxkH0Q3Vxciqndvd7eLqzvqkf7844HToC6VGW24VjIGUih7PFMGMllGnIBBVNtHJkC_uNcwxSLYswYeB5ZBlQlA0TibDciS4mvsEa-Zxj5dbZb5DNjetTu2kdzBaBcwapdiPeJudmsPIm8C_3XLT2koD3-1M4yfopKTyDVvwcNKah7oDvzaJrZsKAHUFnZL2jyQH92ShUG1W2gecUWh_bx"

    private lateinit var webView: WebView
    private lateinit var cadenceTextView: TextView
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var tflite: Interpreter? = null

    //케이던스 모델 관련
    private val modelName = "model_0528_5s.tflite"      //모델 파일명
    private val windowSizeMillis = 5000L                // 예측에 사용할 윈도우 크기: 4초
    private val slideIntervalMillis = 1000L
    private val sensorBuffer = mutableListOf<Triple<Long, SensorType, FloatArray>>()
    private val kalmanFilters = mutableMapOf<String, KalmanFilter1D>() // 칼만 필터 저장용

    private val cadenceHandler = Handler(Looper.getMainLooper())
    //private lateinit var cadenceRunnable: Runnable
    private val executor = Executors.newSingleThreadExecutor()
    private var latestPredictedCadence: Int = 0

    private val predictionHistory = mutableListOf<Int>()       // 최근 예측값 리스트
    private val smoothingWindowSize = 5                        // 무빙 평균 윈도우 크기
    private val outlierThreshold = 50                          // 튀는 값 판단 기준

    private var isPaused = false  // 일시정지 상태 저장

    private var currentBpm = 0

    //애니메이션 관련 변수
    data class RingHolder(val view: ImageView, var isAnimating: Boolean = false)
    private var ringAnimatorHandler: Handler? = null
    private var ringAnimatorRunnable: Runnable? = null
    private val ringPool = mutableListOf<RingHolder>()

    //센서 관련 변수
    private var lastStepTime = 0L
    private val stepIntervalThreshold = 300  // 최소 300ms 간격 (즉, 최대 약 3.3Hz)

    //메트로놈 관련 변수
    private var metronomeJob: Job? = null
    private var shouldVibrate = false

    //거리, 칼로리 대충...
    private lateinit var calorieTextView: TextView
    private var calorie = 0.0

    private lateinit var distanceTextView: TextView
    private var distance = 0.0


    //달린 시간 관련 함수
    private var elapsedSeconds = 0
    private lateinit var timeTextView: TextView
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null

    //재생됐던 음악 리스트
    private val playedTracks = ArrayList<Song>()
    private val bpmList = ArrayList<Int>()




    enum class SensorType { ACCELEROMETER, GYROSCOPE }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        setupLayoutConstraints()
        setupTFLite()
        setupSensors()
        setupWebView()

        cadenceTextView = findViewById(R.id.TextAvgNum)
        cadenceHandler.post(cadenceRunnable)

        //애니메이션 관련 함수 초기화
        val ringContainer = findViewById<FrameLayout>(R.id.circleStats)
        initRingPool(ringContainer)
//        startPulsingRings(150)

        // 버튼 연결
        val pauseButton : ImageButton = findViewById(R.id.pauseButton)
        val pauseOptions : LinearLayout = findViewById(R.id.pauseOptions)
        val resumeButton : ImageButton= findViewById(R.id.resumeButton)
        val endButton : ImageButton= findViewById(R.id.endButton)
        val debugButton : Button= findViewById(R.id.btnDebug)


        pauseButton.setOnClickListener {
            webView.evaluateJavascript("window.setMovementState(false);", null)
            isPaused = true
            pauseButton.visibility = View.GONE
            pauseOptions.visibility = View.VISIBLE
        }

        resumeButton.setOnClickListener {
            webView.evaluateJavascript("window.setMovementState(true);", null)
            isPaused = false
            pauseOptions.visibility = View.GONE
            pauseButton.visibility = View.VISIBLE
        }

        endButton.setOnClickListener {
            stopTimer()
            webView.evaluateJavascript("window.setMovementState(false);", null)
            val intent = Intent(this, ResultActivity::class.java)
            intent.putParcelableArrayListExtra("songs", playedTracks)
            intent.putExtra("time",elapsedSeconds)
            intent.putExtra("calorie",calorie)
            intent.putExtra("distance",distance)
            intent.putExtra("averageBPM", bpmList.average().toInt())
            startActivity(intent)
        }

        debugButton.setOnClickListener{
            //케이던스피드백!!!!! 피드백 필요할 때 아래 함수가져다가 쓰세요!!!!
            requestVibrationFeedback()
            showCadenceFeedbackToast(this)
        }

        //모드, 시간 선택화면에서 가져옴
        val mode = intent.getStringExtra("mode")
        val time = intent.getIntExtra("time", 0)
        val breath = intent.getStringExtra("breath")

        //모드
        if(mode == "normal"){
            //일반 모드일때 할 것
        }
        else{
            //초보자 모드일때 할 것
            //노래 끝나면 선택창 띄우기등...
        }

        //호흡!!! 이거 참고하시길!!!!
        if(breath=="1_1"){}
        else if(breath=="2_1"){}
        else{}


        // 걸음 감지 센서
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)

        //달린 시간
        timeTextView = findViewById(R.id.timeText)

        //칼로리
        calorieTextView = findViewById(R.id.calorieText)

        //거리
        distanceTextView = findViewById(R.id.distanceText)
    }


    //달린 시간 관련 함수-----------------------------------------
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




    //센서 -------------------------------------------------
    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        event?.let {
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

                //걸음 감지됐을때 실행할 것
                if (detectStep(accel) && !isPaused ) { // 적절한 임계값 기반
//                  triggerVibration() // 디버깅용 진동
                    triggerPulseOnce() // 애니메이션을 한 번만 재생

                    calorie += 0.03
                    calorieTextView.text = String.format("%.01f",calorie)

                    distance += 0.001
                    distanceTextView.text = String.format("%.01f",distance)
                }
            }
        }
    }

    private fun detectStep(accelMagnitude: Float): Boolean {
        val now = System.currentTimeMillis()

        // 기준보다 큰 가속 + 최소 간격
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
    //웹뷰 세팅 함수
    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
        }
        webView.loadUrl("https://temrun.netlify.app/")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                val resources = request?.resources
                for (i in resources?.indices!!) {
                    if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID == resources[i]) {
                        request.grant(resources)
                        return
                    }
                }
                super.onPermissionRequest(request)
            }
        }

        //웹에 토큰 보내는 코드. 노래가 안나오면
        //https://developer.spotify.com/documentation/web-playback-sdk/tutorials/getting-started
        //여기서 토큰 받아와서 바꿔주면 됨
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onWebReady() {
                // 웹에서 준비됐다고 알림 > 이때 토큰 보냄
                    webView.post {
                    webView.evaluateJavascript("window.receiveToken('$TOKEN');", null)
                    give_TrackList(webView)
                }
            }
            //웹뷰에서 노래 재생됐을때 받아온 것
            @JavascriptInterface
            fun onTrackPlayWithInfo(title: String, artist: String, albumImageUrl: String, bpm: Int) {
                runOnUiThread {

                    playedTracks.add(Song(title, artist, albumImageUrl))
                    bpmList.add(bpm)
                    //노래시작에 맞춰서 메트로놈 카운트
                    stopMetronome()
                    startMetronome(bpm)
                    //달린시간 역시 여기서 시작
                    startTimer()
                }
            }
            //노래 끝났을때
            @JavascriptInterface
            fun onTrackEnd() {
                webView.evaluateJavascript("window.setMovementState(false);", null)
                Log.d("WebSignal", "🎵 트랙이 끝났습니다!")

                //초보자 모드일때
                val mode = intent.getStringExtra("mode")
                if(mode == "beginner"){
                    runOnUiThread {
                        isPaused = true

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("이번 곡은 어땠나요?")
                            .setItems(arrayOf("힘들어요", "괜찮아요", "더 빠르게")) { _, which ->
                                when (which) {
                                    0 -> { // 힘들어요
                                        currentBpm = currentBpm - 5
                                        requestNewTrackFromServer(currentBpm)
                                        isPaused = false
                                    }
                                    1 -> { // 괜찮아요
                                        webView.evaluateJavascript("window.setMovementState(true);", null)
                                        isPaused = false
                                    }
                                    2 -> { // 더 빠르게
                                        currentBpm = currentBpm + 5
                                        requestNewTrackFromServer(currentBpm)
                                        isPaused = false
                                    }
                                }
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        }, "AndroidInterface")


    }

    fun requestNewTrackFromServer(bpm:Int){
        //초보자 모드에서 새로운 bpm으로 노래를 줘야할 때
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://e49e-203-229-19-88.ngrok-free.app/recommend?bpm=$bpm")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                val body = response.body?.string()

                // 응답 그대로 JS로 넘김
                val jsCode = "window.receiveTrack($body);"

                runOnUiThread {
                    webView.evaluateJavascript(jsCode, null)
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("추천결과", "요청 실패", e)
            }
        })
    }

    fun give_TrackList(webView: WebView){
        //서버에서 추천 받아오는 코드
        val client = OkHttpClient()
        val bpm = intent.getIntExtra("cadence", 150)
        currentBpm = bpm

        val request = Request.Builder()
            .url("https://e49e-203-229-19-88.ngrok-free.app/recommend?bpm=$bpm")
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                val body = response.body?.string()

                // 응답 그대로 JS로 넘김
                val jsCode = "window.receiveTrack($body);"

                runOnUiThread {
                    webView.evaluateJavascript(jsCode, null)
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("추천결과", "요청 실패", e)
            }
        })
    }


    fun startMetronome(bpm: Int) {
        val interval = (60_000 / bpm).toLong()

        metronomeJob?.cancel()
        metronomeJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                if (shouldVibrate) {
                    triggerVibration()  // 진동 발생
                }
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
            delay(durationMs)
            shouldVibrate = false
        }
    }



    //-------------------토스트메시지함수

    fun showBreathFeedbackToast(context: Context) {
        Toast.makeText(context, "호흡 피드백이 제공됩니다", Toast.LENGTH_SHORT).show()
    }

    fun showCadenceFeedbackToast(context: Context) {
        Toast.makeText(context, "케이던스 피드백이 제공됩니다", Toast.LENGTH_SHORT).show()
    }


    //-----------------애니메이션
    fun startPulseRingAnimation(ring: ImageView, duration: Long = 1000L, onEnd: () -> Unit = {}) {
        ring.scaleX = 1f
        ring.scaleY = 1f
        ring.alpha = 1f
        ring.visibility = View.VISIBLE

        val animatorSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.1f, 1f),
                ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.1f, 1f),
                ObjectAnimator.ofFloat(ring, "alpha", 1f, 1f, 1f)
            )
            interpolator = AccelerateInterpolator()
            this.duration = duration
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    ring.visibility = View.INVISIBLE
                    onEnd()
                }
            })
        }
        animatorSet.start()
    }

    fun triggerPulseFromPool(bpm: Int) {
        val holder = ringPool.firstOrNull { !it.isAnimating } ?: return

        Log.d("RingDebug", "Triggered ring at ${System.currentTimeMillis()}, pool = ${ringPool.count { it.view.visibility == View.INVISIBLE }}")


        holder.isAnimating = true
        startPulseRingAnimation(holder.view) {
            holder.isAnimating = false
        }
    }

    fun startPulsingRings(defaultBpm: Int) {
        ringAnimatorHandler?.removeCallbacksAndMessages(null)  // 중복 방지
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val bpm = defaultBpm
                triggerPulseFromPool(bpm)
                handler.postDelayed(this, (60000 / bpm).toLong())
            }
        }
        ringAnimatorHandler = handler
        ringAnimatorRunnable = runnable
        handler.post(runnable)
    }

    private fun triggerPulseOnce() {
        val ring = ringPool.firstOrNull { !it.isAnimating } ?: return
        ring.isAnimating = true
        startPulseRingAnimation(ring.view) {
            ring.isAnimating = false
        }
    }


    fun stopPulsingRings() {
        ringAnimatorHandler?.removeCallbacks(ringAnimatorRunnable!!)
        ringAnimatorRunnable = null
        ringAnimatorHandler = null
    }


    fun initRingPool(container: FrameLayout, poolSize: Int = 20) {
        ringPool.clear()
        for (i in 0 until poolSize) {
            val ring = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageResource(R.drawable.ring)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                alpha = 0f
                visibility = View.INVISIBLE
            }
            container.addView(ring)
            ringPool.add(RingHolder(ring))
        }
    }

    //-----------------아래는 케이던스 모델 코드들. 변경됐으면 확인하고 바꿔주시면 될듯

    //케이던스 예측 루프
    private val cadenceRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            val windowStart = currentTime - windowSizeMillis

            // 최근 4초간의 센서 데이터 필터링
            val recentData = sensorBuffer.filter { it.first in windowStart..currentTime }
            sensorBuffer.removeAll { it.first < windowStart }

            Log.d("버퍼", "최근 센서 크기: ${recentData.size}")

            if (recentData.isNotEmpty()) {
                executor.execute {
                    val sensorDataMap = mutableMapOf<String, MutableList<Float>>()

                    // 센서 데이터를 축별로 분리하여 저장
                    for ((_, type, values) in recentData) {
                        val prefix = if (type == SensorType.ACCELEROMETER) "accel" else "gyro"
                        for (i in values.indices) {
                            val key = "${prefix}_$i"
                            sensorDataMap.getOrPut(key) { mutableListOf() }.add(values[i])
                        }
                    }

                    // 모델 입력 데이터 생성
                    val preprocessStart = System.currentTimeMillis()
                    val inputBuffer = convertGraphsToModelInput(sensorDataMap)
                    val preprocessEnd = System.currentTimeMillis()
                    Log.d("TIME", "전처리 시간: ${preprocessEnd - preprocessStart}ms")

                    val inferenceStart = System.currentTimeMillis()
                    val output = Array(1) { FloatArray(1) }
                    tflite?.run(inputBuffer, output)
                    val inferenceEnd = System.currentTimeMillis()
                    Log.d("TIME", "모델 추론 시간: ${inferenceEnd - inferenceStart}ms")

                    val rawPrediction = output[0][0].roundToInt()

                    // ✅ 포스트프로세싱: 무빙 애버리지 + 아웃라이어 제거
                    predictionHistory.add(rawPrediction)
                    if (predictionHistory.size > smoothingWindowSize) {
                        predictionHistory.removeAt(0)
                    }

                    val average = predictionHistory.average().toInt()
                    val finalPrediction = if (predictionHistory.size >= 2) {
                        val prev = predictionHistory[predictionHistory.size - 2]
                        if (kotlin.math.abs(rawPrediction - prev) > outlierThreshold) {
                            Log.w("Outlier", "예측값 $rawPrediction → $average (보정됨)")
                            average
                        } else {
                            rawPrediction
                        }
                    } else {
                        rawPrediction
                    }

                    runOnUiThread {
                        cadenceTextView.text = "$finalPrediction"
                    }
                }

            }

            cadenceHandler.postDelayed(this, slideIntervalMillis)
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        cadenceHandler.removeCallbacks(cadenceRunnable)
        tflite?.close()
        super.onDestroy()
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    //이거 위에 걸음감지때 사용되는 함수라서 거기에 통합해놨음
//    override fun onSensorChanged(event: SensorEvent?) {
//        event?.let {
//            val timestamp = System.currentTimeMillis()
//            val values = it.values.copyOf()
//            val type = when (it.sensor.type) {
//                Sensor.TYPE_ACCELEROMETER -> SensorType.ACCELEROMETER
//                Sensor.TYPE_GYROSCOPE -> SensorType.GYROSCOPE
//                else -> return
//            }
//            sensorBuffer.add(Triple(timestamp, type, values))
//        }
//    }

    //센서 데이터를 그래프로 시각화하여 CNN모델의 입력값으로 들어가는 ByteBuffer 생성.
    private fun convertGraphsToModelInput(sensorDataMap: Map<String, List<Float>>): ByteBuffer {
        val width = 224
        val height = 224
        val bitmaps = mutableListOf<Bitmap>()

        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            isAntiAlias = true
        }

        //센서 데이터 -> 그래프 이미지 변환!
        sensorDataMap.values.take(6).forEach { data ->
            if (data.size < 2) return@forEach
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

            val minVal = data.minOrNull() ?: 0f
            val maxVal = data.maxOrNull() ?: 1f
            val normData = data.map { (it - minVal) / (maxVal - minVal + 1e-6f) }
            val step = width.toFloat() / (normData.size - 1)

            //그래프 그리는 과정
            for (i in 0 until normData.size - 1) {
                val x1 = i * step
                val y1 = height - normData[i] * height
                val x2 = (i + 1) * step
                val y2 = height - normData[i + 1] * height
                canvas.drawLine(x1, y1, x2, y2, paint) //센서 데이터를 그래프로 그림.
            }
            bitmaps.add(bitmap)
        }

        //부족하면 빈 이미지로 채움
        while (bitmaps.size < 6) {
            val blank = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(blank).drawColor(Color.WHITE)
            bitmaps.add(blank)
        }

        //버퍼 메모리 할당
        val buffer = ByteBuffer.allocateDirect(width * height * 6 * 4).order(ByteOrder.nativeOrder())
        for (y in 0 until height) {
            for (x in 0 until width) {
                for (bitmap in bitmaps) {
                    val pixel = bitmap.getPixel(x, y)
                    buffer.putFloat(Color.red(pixel) / 255f)
                }
            }
        }

        buffer.rewind() //버퍼의 위치를 맨 앞으로 되돌림.
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

    private fun startCadenceInferenceLoop() {
        val executor = Executors.newSingleThreadExecutor()

        val runnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val cutoff = now - 4000
                val recent = sensorBuffer.filter { it.first >= cutoff }
                sensorBuffer.removeAll { it.first < cutoff }

                if (recent.isNotEmpty()) {
                    val sensorDataMap = mutableMapOf<String, MutableList<Float>>()
                    for ((_, type, values) in recent) {
                        val prefix = if (type == SensorType.ACCELEROMETER) "accel" else "gyro"
                        for (i in values.indices) {
                            val key = "${prefix}_$i"
                            sensorDataMap.getOrPut(key) { mutableListOf() }.add(values[i])
                        }
                    }

                    executor.execute {
                        val inputBuffer = convertGraphsToModelInput(sensorDataMap)
                        val output = Array(1) { FloatArray(1) }
                        tflite?.run(inputBuffer, output)
                        val bpm = output[0][0].roundToInt()
                        runOnUiThread {
                            cadenceTextView.text = bpm.toString()
                            latestPredictedCadence = bpm
                        }
                    }
                }

                cadenceHandler.postDelayed(this, 1000)
            }
        }

        cadenceHandler.post(runnable)
    }

    private fun setupSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

//------------------------------------------------------
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


}