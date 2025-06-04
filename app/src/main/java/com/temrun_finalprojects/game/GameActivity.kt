package com.temrun_finalprojects.game

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.Resources
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.temrun_finalprojects.R
import kotlin.math.pow
import kotlin.math.sqrt

class GameActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var bit: MediaPlayer
    private lateinit var base: MediaPlayer
    private lateinit var code: MediaPlayer
    private lateinit var melody: MediaPlayer

    private var percentage = 0f
    private var instrumentNum = 0
    private var isBaseOn = false
    private var isCodeOn = false
    private var isMelodyOn = false
    private lateinit var instruments: Array<ImageView>

    //센서 관련 변수
    private var lastStepTime = 0L
    private val stepIntervalThreshold = 320  // 최소 300ms 간격 (즉, 최대 약 3.3Hz)
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    //메트로놈 관련 변수
    private var toneGenerator: ToneGenerator? = null
    private var isToneRunning = false

    //내려오는 바
    private val activeBars = mutableListOf<View>()

    //판정선 Y값
    private var judgementY = 0



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_game)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.gameLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //선택화면에서 준 케이던스를 저장
//        val cadence = intent.getIntExtra("cadence", -1)  // -1은 기본값 (예외 대비)
        //내려오는 바 생성
        startSpawningBars(bpm = 150)
        startToneMetronome(150)

        val guitarView = findViewById<ImageView>(R.id.instrument1)
        val drumView = findViewById<ImageView>(R.id.instrument2)
        val pianoView = findViewById<ImageView>(R.id.instrument3)

        instruments = arrayOf(guitarView,drumView,pianoView)


        val plusButton = findViewById<Button>(R.id.plusButton)
        val minusButton = findViewById<Button>(R.id.minusButton)
        percentage = 0f
        instrumentNum = 0

        // 미디어 초기화
        bit = createLoopingPlayer(R.raw.bit)
        base = createLoopingPlayer(R.raw.base)
        code = createLoopingPlayer(R.raw.code)
        melody = createLoopingPlayer(R.raw.melody)

        bit.setVolume(0f, 0f)
        base.setVolume(0f, 0f)
        code.setVolume(0f, 0f)
        melody.setVolume(0f, 0f)

        bit.seekTo(0)
        base.seekTo(0)
        code.seekTo(0)
        melody.seekTo(0)

        melody.setOnCompletionListener {
            Log.d("음악", "melody 트랙 종료됨")
            bit.seekTo(0)
            base.seekTo(0)
            code.seekTo(0)
            melody.seekTo(0)

            bit.start()
            base.start()
            code.start()
            melody.start()
        }

        bit.start()
        base.start()
        code.start()
        melody.start()

        // 게이지가 모두 찼을 때 사운드 ON 상태 체크용
        isBaseOn = false
        isCodeOn = false
        isMelodyOn = false


        //이거 드럼소리로 변환 가능한지 찾아보는중임 드럼소리가 아니면 불협화음됨,,
        //bit.setVolume(0.5f, 0.5f)
        plusButton.setOnClickListener {
            Perfect()

        }

        minusButton.setOnClickListener {
            Miss()
        }

        val judgementLine = findViewById<View>(R.id.judgementLine)
        judgementLine.post {
            val pos = IntArray(2)
            judgementLine.getLocationOnScreen(pos)
            judgementY = pos[1]
        }


        // 센서 매니저 초기화
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 센서 리스너 등록
        accelerometer?.also { acc ->
            sensorManager.registerListener(
                this,
                acc,
                SensorManager.SENSOR_DELAY_GAME  // 또는 SENSOR_DELAY_UI
            )
        }


    }


    //센서 ----------------------
    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val accel = sqrt(
                    event.values[0].pow(2) +
                            event.values[1].pow(2) +
                            event.values[2].pow(2)
                )

                if (detectStep(accel)) { // 적절한 임계값 기반
                    triggerVibrationAndJudge()  // 디버깅용 진동
                    println("걸음감ㅈ;")

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
    private fun triggerVibrationAndJudge() {
        val judgementLine = findViewById<View>(R.id.judgementLine)
        val judgementPos = IntArray(2)
        judgementLine.getLocationOnScreen(judgementPos)
        val judgementY = judgementPos[1]

        val iterator = activeBars.iterator()

        // 2개 이상 있는 경우에만 판정 시도
        if (activeBars.size >= 2) {
            if (activeBars.size >= 2) {
                val firstBar = activeBars[0]
                val secondBar = activeBars[1]

                val pos1 = IntArray(2)
                val pos2 = IntArray(2)
                val judgementPos = IntArray(2)

                firstBar.getLocationOnScreen(pos1)
                secondBar.getLocationOnScreen(pos2)
                judgementLine.getLocationOnScreen(judgementPos)

                val y1 = pos1[1]
                val y2 = pos2[1]
                val jy = judgementPos[1]

                val diff1 = kotlin.math.abs(y1 - jy)
                val diff2 = kotlin.math.abs(y2 - jy)

                val (targetBar, targetIdx) = if (diff1 <= diff2) Pair(firstBar, 0) else Pair(secondBar, 1)

                val threshold = 100

                if(kotlin.math.abs(targetBar.y - judgementLine.y) > 400)
                    return
                if (kotlin.math.abs(targetBar.y - judgementLine.y) < threshold ) {
                    vibrateStrong()
                    Perfect()
                    showJudgementText("PERFECT")

                    // 판정된 바만 제거
                    findViewById<FrameLayout>(R.id.rhythmBarContainer).removeView(targetBar)
                    activeBars.removeAt(targetIdx)
                }
                else if(kotlin.math.abs(targetBar.y - judgementLine.y) < 200 ){
                    vibrateWeak()
                    Good()
                    showJudgementText("Good")

                    // 판정된 바만 제거
                    findViewById<FrameLayout>(R.id.rhythmBarContainer).removeView(targetBar)
                    activeBars.removeAt(targetIdx)
                }
                else {
                    // ❌ 판정 실패
                    Miss()
                    showJudgementText("MISS")
                }
            }

        }


    }

    private fun showJudgementText(text: String) {
        val judgementTextView = findViewById<TextView>(R.id.judgementText)
        judgementTextView.text = text
        judgementTextView.visibility = View.VISIBLE

        // 일정 시간 후 자동으로 사라지게
        Handler(Looper.getMainLooper()).postDelayed({
            judgementTextView.visibility = View.GONE
        }, 800) // 800ms 후 사라짐
    }


    fun Miss(){
        // MISS 판정일 때 20퍼씩 감소
        percentage -= 0.2f
        if (percentage < 0f) {
            percentage = 0f
        }

        updateGauge(instruments[instrumentNum], percentage)
        popInstrument(instruments[instrumentNum])

        // 게이지가 0이 되면 악기 볼륨 OFF
        when (instrumentNum) {
            0 -> {
                if (isBaseOn && percentage < 1.0f) {
                    base.setVolume(0f, 0f)
                    isBaseOn = false
                }
            }

            1 -> {
                if (isCodeOn && percentage  < 1.0f) {
                    code.setVolume(0f, 0f)
                    isCodeOn = false
                }
            }

            2 -> {
                if (isMelodyOn && percentage  < 1.0f) {
                    melody.setVolume(0f, 0f)
                    isMelodyOn = false
                }
            }
        }

        // 게이지가 0이고 이전 악기로 이동할 수 있으면 이동
        if (percentage <= 0f && instrumentNum > 0) {
            instrumentNum -= 1
            percentage = 1f  // 이전 악기의 게이지를 끝까지 채운 상태로 되돌아감
            updateGauge(instruments[instrumentNum], percentage)
            popInstrument(instruments[instrumentNum])
        }
    }

    fun Perfect(){
        //게이지가 다 차면 다음 악기로 이동
        if(percentage >= 1.0f && instrumentNum<2) {
            instrumentNum += 1
            percentage = 0f
        }
        if(percentage > 1.5f){
            percentage = 1.5f
        }
        // PERFECT 판정일 때 10퍼씩 증가
        percentage += 0.1f
        updateGauge(instruments[instrumentNum], percentage)
        popInstrument(instruments[instrumentNum])

        when (instrumentNum) {
            0 -> {
                if (!isBaseOn && percentage >= 1.0f) {
                    base.setVolume(1f, 1f)
                    isBaseOn = true
                }
            }

            1 -> {
                if (!isCodeOn && percentage >= 1.0f) {
                    code.setVolume(1f, 1f)
                    isCodeOn = true
                }
            }

            2 -> {
                if (!isMelodyOn && percentage >= 1.0f) {
                    melody.setVolume(1f, 1f)
                    isMelodyOn = true
                }
            }
        }
    }


    fun Good(){
        //게이지가 다 차면 다음 악기로 이동
        if(percentage >= 1.0f && instrumentNum<2) {
            instrumentNum += 1
            percentage = 0f
        }
        // PERFECT 판정일 때 10퍼씩 증가
        percentage += 0.05f
        updateGauge(instruments[instrumentNum], percentage)
        popInstrument(instruments[instrumentNum])

        when (instrumentNum) {
            0 -> {
                if (!isBaseOn && percentage >= 1.0f) {
                    base.setVolume(1f, 1f)
                    isBaseOn = true
                }
            }

            1 -> {
                if (!isCodeOn && percentage >= 1.0f) {
                    code.setVolume(1f, 1f)
                    isCodeOn = true
                }
            }

            2 -> {
                if (!isMelodyOn && percentage >= 1.0f) {
                    melody.setVolume(1f, 1f)
                    isMelodyOn = true
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateStrong() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
            vibrator.vibrate(
                VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(200)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateWeak() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
            vibrator.vibrate(
                VibrationEffect.createOneShot(100, 50)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(200)
        }
    }



    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 정확도 변경 시 동작
        // 사용안함
    }

    //________________________________________


    fun startToneMetronome(bpm: Int) {
        val interval = (60000 / bpm).toLong()
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

        isToneRunning = true
        val handler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                if (!isToneRunning) return
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ONE_MIN_BEEP, 50) // 50ms 삐 소리
                handler.postDelayed(this, interval)
            }
        }

        handler.post(runnable)
    }

    fun stopToneMetronome() {
        isToneRunning = false
        toneGenerator?.release()
        toneGenerator = null
    }

    //-----------------------

    override fun onPause() {
        super.onPause()
        resetAll()
    }

    private fun resetAll() {
        // 음악 정지
        if (::bit.isInitialized) bit.pause()
        if (::base.isInitialized) {
            base.pause()
            base.setVolume(0f, 0f)
            isBaseOn = false
        }
        if (::code.isInitialized) {
            code.pause()
            code.setVolume(0f, 0f)
            isCodeOn = false
        }
        if (::melody.isInitialized) {
            melody.pause()
            melody.setVolume(0f, 0f)
            isMelodyOn = false
        }

        // 게이지 초기화
        resetGauge()
    }

    private fun resetGauge() {
        percentage = 0f
        instrumentNum = 0
        instruments.forEach {
            updateGauge(it, 0f)
        }
    }



    fun spawnRhythmBar(bpm: Int) {
        val rhythmZone = findViewById<FrameLayout>(R.id.rhythmBarContainer)
        val barHeight = 24.dpToPx()
        val bar = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                24.dpToPx()  // 원하는 높이
            )
            background = ContextCompat.getDrawable(context, R.drawable.rhythm_bar)
            elevation = 10f
        }


        rhythmZone.addView(bar)
        activeBars.add(bar)

        val screenHeight = rhythmZone.height
        val offScreenY = screenHeight + barHeight

        val animator = ObjectAnimator.ofFloat(bar, View.TRANSLATION_Y, 0f, offScreenY.toFloat())
        animator.duration = 2000L
        animator.interpolator = LinearInterpolator()

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                val rhythmZone = findViewById<FrameLayout>(R.id.rhythmBarContainer)

                // 바가 bottomZone 아래로 완전히 내려갔는지 확인 후 삭제
                val pos = IntArray(2)
                bar.getLocationOnScreen(pos)
                val barY = pos[1]

                val bottomZone = findViewById<View>(R.id.bottomZone)
                val bottomPos = IntArray(2)
                bottomZone.getLocationOnScreen(bottomPos)
                val bottomY = bottomPos[1]

                if (barY > bottomY + bottomZone.height) {
                    // 완전히 아래로 사라진 바: 그냥 제거
                    Log.d("리듬바", "아래로 내려간 바 제거됨")
                    Miss()
                }

                rhythmZone.removeView(bar)
                activeBars.remove(bar)
            }

            override fun onAnimationCancel(animation: Animator) {
                findViewById<FrameLayout>(R.id.rhythmBarContainer).removeView(bar)
                activeBars.remove(bar)
            }
        })


        animator.start()
    }


    fun startSpawningBars(bpm: Int) {
        val interval = (60000 / bpm).toLong()  // ms
        val handler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                spawnRhythmBar(bpm)
                handler.postDelayed(this, interval)
            }
        }

        handler.post(runnable)
    }

    fun Int.dpToPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }


    fun updateGauge(imageView: ImageView, percent: Float) {
        val clamped = percent.coerceIn(0f, 1f)
        val layer = imageView.drawable as? LayerDrawable ?: return
        val clip = layer.findDrawableByLayerId(android.R.id.progress) as? ClipDrawable ?: return
        clip.level = (clamped * 10000).toInt()
    }


    fun popInstrument(view: View) {
        val scaleUpX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.2f)
        val scaleUpY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.2f)
        val scaleDownX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.2f, 1f)
        val scaleDownY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.2f, 1f)

        scaleUpX.duration = 100
        scaleUpY.duration = 100
        scaleDownX.duration = 100
        scaleDownY.duration = 100

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleUpX, scaleUpY)

        val scaleDownSet = AnimatorSet()
        scaleDownSet.playTogether(scaleDownX, scaleDownY)

        val totalSet = AnimatorSet()
        totalSet.playSequentially(animatorSet, scaleDownSet)

        totalSet.start()
    }

    private fun createLoopingPlayer(@RawRes resId: Int): MediaPlayer {
        return MediaPlayer.create(this, resId).apply {
            isLooping = false
        }
    }


}