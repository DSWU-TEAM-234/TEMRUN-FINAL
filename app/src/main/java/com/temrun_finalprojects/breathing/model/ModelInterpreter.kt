package com.temrun_finalprojects.breathing.model

import android.content.Context
import android.util.Log
import com.temrun_finalprojects.breathing.audio.AudioProcessor
import org.tensorflow.lite.Interpreter
import java.io.IOException


object ModelInterpreter {
    private const val TAG = "ModelInterpreter"
    @Throws(IOException::class)
    fun runModel2(context: Context?, features: AudioProcessor.FeatureSet, bpm: Int, bp: IntArray): String {
        Log.d(com.temrun_finalprojects.breathing.model.ModelInterpreter.TAG, "Model 2 시작")

        val tflite = Interpreter(
            com.temrun_finalprojects.util.FileUtil.loadModelFile(
                context!!, "model2_lite_v1.tflite"
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
            1 -> "호흡기관 문제"
            2 -> "호흡 패턴 문제"
            3 -> "복합 문제"
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