package com.temrun_finalprojects.breathing.model

import android.content.Context
import android.util.Log
import com.temrun_finalprojects.breathing.audio.AudioProcessor
import org.tensorflow.lite.Interpreter
import java.io.IOException


object ModelInterpreter {
    private const val TAG = "ModelInterpreter"
    private var abnormalCount = 0 // 비정상 감지 횟수
    private const val ABNORMAL_THRESHOLD = 5 // 비정상 임계값

    // 모델1 관련 정규화 상수 (MainActivity.kt에서 가져옴)
    private val metaMean = floatArrayOf(150.76959248f, 2.01040182f)
    private val metaStd = floatArrayOf(10.08731071f, 0.81218989f)

    // 호흡 패턴 매핑
    private val patternMap = mapOf(
        "1_1" to 1f,
        "2_1" to 2f,
        "2_2" to 3f
    )

    @Throws(IOException::class)
    fun runModel1(context: Context?, features: AudioProcessor.FeatureSet, bpm: Int, pattern: String): String {
        Log.d(TAG, "Model 1 시작")

        val tflite = Interpreter(
            com.temrun_finalprojects.util.FileUtil.loadModelFile(
                context!!, "cnn_mlp_breath_model_v1.tflite"
            )
        )

        Log.d(TAG, "Model 1 로드 완료")

        // CNN 입력 형태로 재구성: (1, 128, 128, 1)
        val cnnInput = Array(1) { Array(128) { Array(128) { FloatArray(1) } } }
        val flatLogmel = features.logMelSpectrogram

        for (i in 0 until 128) {
            for (j in 0 until 128) {
                val idx = i * 128 + j
                cnnInput[0][i][j][0] = flatLogmel[idx]
            }
        }

        // 메타 데이터 정규화
        val bpmFloat = bpm.toFloat()
        val patternCode = patternMap[pattern] ?: 0f
        val normBpm = (bpmFloat - metaMean[0]) / metaStd[0]
        val normPattern = (patternCode - metaMean[1]) / metaStd[1]
        val mlpInput = arrayOf(floatArrayOf(normBpm, normPattern))

        // 모델 추론
        val output = Array(1) { FloatArray(1) }
        tflite.runForMultipleInputsOutputs(arrayOf(cnnInput, mlpInput), mapOf(0 to output))

        val result = output[0][0]
        val prediction = if (result < 0.5) "정상" else "비정상"

        // 비정상 카운트 관리
        if (prediction == "비정상") {
            abnormalCount++
            Log.d(TAG, "비정상 감지 횟수: $abnormalCount")
        } else {
            abnormalCount = 0 // 정상이면 카운트 리셋
        }

        Log.d(TAG, "Model 1 실행 완료. 결과: $prediction")
        return prediction
    }

    fun shouldUseModel2(): Boolean {
        return abnormalCount >= ABNORMAL_THRESHOLD
    }

    fun resetAbnormalCount() {
        abnormalCount = 0
    }

    @Throws(IOException::class)
    fun runModel2(context: Context?, features: AudioProcessor.FeatureSet, bpm: Int, bp: IntArray): String {
        Log.d(com.temrun_finalprojects.breathing.model.ModelInterpreter.TAG, "Model 2 시작")

        val tflite = Interpreter(
            com.temrun_finalprojects.util.FileUtil.loadModelFile(
                context!!, "model2_lite_v2.tflite"
            )
        )

        Log.d(com.temrun_finalprojects.breathing.model.ModelInterpreter.TAG, "Model 2 로드 완료")

        // --------------------------
        // 1. MFCC (1, 13, 75, 1)
        // --------------------------
        val mfccInput = Array(1) {
            Array(13) {
                Array(75) {
                    FloatArray(1)
                }
            }
        }
        for (i in 0..12) {
            for (j in 0..74) {
                mfccInput[0][i][j][0] = features.mfccFrames.get(i).get(j)
            }
        }

        // --------------------------
        // 2. RMS (1, 1, 75)
        // --------------------------
        val rmsInput = Array(1) {
            Array(1) {
                FloatArray(75)
            }
        }
        for (i in 0..74) {
            rmsInput[0][0][i] = features.rmsFrames.get(i)
        }

        // --------------------------
        // 3. BPM (1, 1)
        // --------------------------
        val bpmInput = Array(1) { FloatArray(1) }
        bpmInput[0][0] = bpm.toFloat()

        // --------------------------
        // 4. Breathing Pattern (1, 2)
        // --------------------------
        val bpInput = Array(1) { FloatArray(2) }
        bpInput[0][0] = bp[0].toFloat()
        bpInput[0][1] = bp[1].toFloat()

        // --------------------------
        // 5. Run Multi-Input Inference
        // --------------------------
        val inputs = arrayOf<Any>(mfccInput, bpmInput, bpInput, rmsInput) // 순서 주의

        val output = Array(1) { FloatArray(3) } // softmax 3클래스

        tflite.runForMultipleInputsOutputs(inputs, object : HashMap<Int?, Any?>() {
            init {
                put(0, output)
            }
        })

        val feedbackCode: Int = com.temrun_finalprojects.breathing.model.ModelInterpreter.argmax(output[0])
        Log.d(
            com.temrun_finalprojects.breathing.model.ModelInterpreter.TAG,
            "Model 2 실행 완료. 결과 코드: " + (feedbackCode + 1)
        )

        return when (feedbackCode + 1) {
            1 -> "들숨과 날숨의 호흡 기관이 일치하지 않습니다. 코로 들이마쉬고 입으로 내쉬세요!"
            2 -> "처음 선택한 호흡 패턴과 일치하지 않은 패턴으로 달리고 있습니다. "
            3 -> "들숨은 코로, 날숨은 입으로 호흡하고 음악의 BPM에 맞게 패턴을 유지해주세요!"
            else -> "알 수 없는 오류"
        }
    }


    private fun argmax(array: FloatArray): Int {
        var maxIndex = 0
        var max = array[0]
        for (i in 1 until array.size) {
            if (array[i] > max) {
                max = array[i]
                maxIndex = i
            }
        }
        return maxIndex
    }
}