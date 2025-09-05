package com.temrun_finalprojects.breathing.audio
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.temrun_finalprojects.MainActivity
import com.temrun_finalprojects.breathing.model.ModelInterpreter
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private val handler = Handler()

    // 사용자 설정값 저장 변수 추가
    /**
     * TODO: 사용자 UI랑 연결해야한다.
     * */
    private var userBpm: Int = 150
    private var userPattern: String = "2:1"
    private var userBreathingPattern: IntArray = intArrayOf(2, 1)

    private val model1Buffer = mutableListOf<Float>()
    private val model2Buffer = mutableListOf<Float>()
    private var lastModel1ProcessTime = 0L
    private var lastModel2ProcessTime = 0L


    fun setUserSettings(bpm: Int, pattern: String, breathingPattern: IntArray) {
        this.userBpm = bpm
        this.userPattern = pattern
        this.userBreathingPattern = breathingPattern
    }

    fun startRecording() {
        // 🔐 권한 체크
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "녹음 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }

        Thread(Runnable {
            val recorder: AudioRecord
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    com.temrun_finalprojects.breathing.audio.AudioRecorder.Companion.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    MODEL1_BUFFER_SIZE * 2)
                recorder.startRecording()
                Log.d("AudioRecoder", "스타트 리코딩 부분 무사 통과.")
            } catch (e: SecurityException) {
                e.printStackTrace()
                handler.post {
                    Toast.makeText(
                        context,
                        "녹음 권한이 없습니다",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@Runnable
            }

            val audioBuffer = ShortArray(1024)  // 작은 청크로 연속 읽기
            while (true) {
                val read = recorder.read(audioBuffer, 0, 1024)
                if (read > 0) {
                    val audioFloat = FloatArray(read)
                    for (i in 0 until read) {
                        audioFloat[i] = audioBuffer[i] / 32768f
                    }
                    // 각 모델 버퍼에 추가
                    addToBuffers(audioFloat)
                    // 버퍼가 준비되면 처리
                    processBuffersIfReady()
                }
            }
        }).start()
    }

    private fun addToBuffers(audioFloat: FloatArray) {
        // 모델1 버퍼 (3초)
        model1Buffer.addAll(audioFloat.toList())
        if (model1Buffer.size > MODEL1_BUFFER_SIZE) {
            val excess = model1Buffer.size - MODEL1_BUFFER_SIZE
            repeat(excess) { model1Buffer.removeAt(0) }
        }

        // 모델2 버퍼 (2초)
        model2Buffer.addAll(audioFloat.toList())
        if (model2Buffer.size > MODEL2_BUFFER_SIZE) {
            val excess = model2Buffer.size - MODEL2_BUFFER_SIZE
            repeat(excess) { model2Buffer.removeAt(0) }
        }
    }

    private fun processBuffersIfReady() {
        val now = System.currentTimeMillis()
        // 모델1(3초) 처리: abnormalCount < threshold
        if (model1Buffer.size >= MODEL1_BUFFER_SIZE &&
            now - lastModel1ProcessTime >= MODEL1_RECORD_SECONDS * 1000 &&
            !ModelInterpreter.shouldUseModel2()
        ) {
            processModel1(model1Buffer.toFloatArray())
            lastModel1ProcessTime = now
        }
        // 모델2(2초) 처리: abnormalCount ≥ threshold
        if (model2Buffer.size >= MODEL2_BUFFER_SIZE &&
            now - lastModel2ProcessTime >= MODEL2_RECORD_SECONDS * 1000 &&
            ModelInterpreter.shouldUseModel2()
        ) {
            processModel2(model2Buffer.toFloatArray())
            lastModel2ProcessTime = now
        }
    }


    private fun processModel1(audioData: FloatArray) {
        try {
            val features = AudioProcessor.extractFeaturesForModel1(audioData)
            val sanitizedPattern = userPattern.replace(":", "_")
            val result = ModelInterpreter.runModel1(context, features, userBpm, sanitizedPattern)

            // 기존 브로드캐스트 로직...
            val intent = Intent("PREDICTION_UPDATE")
            intent.putExtra("result", result)
            context.sendBroadcast(intent)

            Log.d("AudioRecorder", "모델1 결과: $result")
        } catch (e: IOException) {
            Log.e("AudioRecorder", "모델1 처리 오류", e)
        }
    }

    private fun processModel2(audioData: FloatArray) {
        try {
            val features = AudioProcessor.extractFeaturesForModel2(audioData)
            val result = ModelInterpreter.runModel2(context, features, userBpm, userBreathingPattern)

            // 기존 브로드캐스트 로직...
            val intent = Intent("PREDICTION_UPDATE")
            intent.putExtra("result", result)
            context.sendBroadcast(intent)

            Log.d("AudioRecorder", "모델2 결과: $result")
            ModelInterpreter.resetAbnormalCount()
        } catch (e: IOException) {
            Log.e("AudioRecorder", "모델2 처리 오류", e)
        }
    }


//    @Throws(IOException::class)
//    private fun processAudio(audioData: FloatArray) {
//        val features: AudioProcessor.FeatureSet = AudioProcessor.extractFeatures(audioData)
//
//        var finalPrediction: String = ""
//
//        // Model1과 Model2 순차 실행 로직
//        if (!ModelInterpreter.shouldUseModel2()) {
//            // Model1 먼저 실행
//            val model1Result = ModelInterpreter.runModel1(context, features, userBpm, userPattern)
//
//            if (model1Result == "정상") {
//                Log.e("Model1", "정상 호흡 패턴")
////                finalPrediction = "정상 호흡 패턴입니다."
//            } else {
//                // 비정상이면 임계값 확인
//                if (ModelInterpreter.shouldUseModel2()) {
//                    // 비정상이 5번 이상이면 Model2 실행
//                    finalPrediction = ModelInterpreter.runModel2(context, features, userBpm, userBreathingPattern)
//                    ModelInterpreter.resetAbnormalCount() // Model2 실행 후 카운트 리셋
//                } else {
////                    finalPrediction = "호흡 패턴에 주의가 필요합니다."
//                }
//            }
//        } else {
//            // 이미 임계값을 넘었으면 바로 Model2 실행
//            finalPrediction = ModelInterpreter.runModel2(context, features, userBpm, userBreathingPattern)
//        }
//
//        // 결과 브로드캐스트
//        val intent = Intent("PREDICTION_UPDATE")
//        intent.putExtra("result", finalPrediction)
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        context.sendBroadcast(intent)
//
//        Log.d("AudioRecorder", "최종 예측 결과: $finalPrediction")
//    }

    companion object {
        private const val SAMPLE_RATE = 16000
        // 모델1: 3초 → 48,000 샘플
        private const val MODEL1_RECORD_SECONDS = 3
        private const val MODEL1_BUFFER_SIZE = SAMPLE_RATE * MODEL1_RECORD_SECONDS
        // 모델2: 2초 → 32,000 샘플
        private const val MODEL2_RECORD_SECONDS = 2
        private const val MODEL2_BUFFER_SIZE = SAMPLE_RATE * MODEL2_RECORD_SECONDS
    }

}

