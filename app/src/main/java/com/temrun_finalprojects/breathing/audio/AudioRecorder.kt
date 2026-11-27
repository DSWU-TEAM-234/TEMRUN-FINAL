package com.temrun_finalprojects.breathing.audio
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.temrun_finalprojects.MainActivity
import com.temrun_finalprojects.breathing.model.ModelInterpreter
import com.temrun_finalprojects.perf.PerfMonitor
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private val handler = Handler()

    @Volatile private var isRecording = false;
    private var audioRecord: AudioRecord? = null;
    private var recordThread: Thread? = null


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

    // "1_1" / "2:1" 어느 형식이든 허용
    fun setUserSettings(bpm: Int, pattern: String) {
        val normalized = pattern.replace("_", ":")
        val parts = normalized.split(":").mapNotNull { it.trim().toIntOrNull() }
        val arr = if (parts.size == 2) parts.toIntArray() else intArrayOf(2, 1)
        setUserSettings(bpm, normalized, arr)
        Log.d("Recording status", "settings: bpm=$bpm, pattern=$normalized, arr=${arr.toList()}")
    }

    fun startRecording() {
        if (isRecording) {
            Log.d("Recording status", "중복 녹음이 감지!")
            return
        } // 중복 시작 방지
        // 🔐 권한 체크
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "녹음 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }
        isRecording = true
        Log.d("Recording status", "녹음 중")
//        Thread(Runnable {
//            val recorder: AudioRecord
//            try {
//                recorder = AudioRecord(
//                    MediaRecorder.AudioSource.MIC,
//                    com.temrun_finalprojects.breathing.audio.AudioRecorder.Companion.SAMPLE_RATE,
//                    AudioFormat.CHANNEL_IN_MONO,
//                    AudioFormat.ENCODING_PCM_16BIT,
//                    MODEL1_BUFFER_SIZE * 2)
//                recorder.startRecording()
//                Log.d("AudioRecoder", "스타트 리코딩 부분 무사 통과.")
//            } catch (e: SecurityException) {
//                e.printStackTrace()
//                handler.post {
//                    Toast.makeText(
//                        context,
//                        "녹음 권한이 없습니다",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//                return@Runnable
//            }
//
//            val audioBuffer = ShortArray(1024)  // 작은 청크로 연속 읽기
//            while (true) {
//                val read = recorder.read(audioBuffer, 0, 1024)
//                if (read > 0) {
//                    val audioFloat = FloatArray(read)
//                    for (i in 0 until read) {
//                        audioFloat[i] = audioBuffer[i] / 32768f
//                    }
//                    // 각 모델 버퍼에 추가
//                    addToBuffers(audioFloat)
//                    // 버퍼가 준비되면 처리
//                    processBuffersIfReady()
//                }
//            }
//        }).start()
        recordThread = Thread {
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    MODEL1_BUFFER_SIZE * 2
                ).also { it.startRecording() }
            } catch (e: SecurityException) {
                isRecording = false
                return@Thread
            }
            audioRecord = rec

            val audioBuffer = ShortArray(1024)
            try {
                while (isRecording) {
                    /*
                    * 녹음 청크를 처리하는데 64MS를 넘지 않도록 체크
                    * 64를 넘기면 녹음을 따라가지 못하는 것이라고 판단하기
                    * */
                    val read = rec.read(audioBuffer, 0, 1024)
                    if (read > 0) {
                        val tChunk = PerfMonitor.start() // PERF
                        val audioFloat = FloatArray(read) { audioBuffer[it] / 32768f }
                        addToBuffers(audioFloat)
                        processBuffersIfReady()
                        PerfMonitor.end("audio_chunk_process", tChunk) // PERF
                    }
                }
            } finally {
                try { rec.stop() } catch (_: IllegalStateException) {}
                rec.release()
                audioRecord = null
            }
        }.also { it.start() }
    }

    fun stopRecording() {
        isRecording = false          // 루프가 끝나면 finally에서 stop/release
        Log.d("Recording status", "녹음 중지")
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
        val tTotal = PerfMonitor.start() // PERF
        try {
            val tFeat = PerfMonitor.start() // PERF
            val features = AudioProcessor.extractFeaturesForModel1(audioData)
            PerfMonitor.end("breath_m1_features", tFeat) // PERF

            val sanitizedPattern = userPattern.replace(":", "_")
            val tInf = PerfMonitor.start() // PERF
            val result = ModelInterpreter.runModel1(context, features, userBpm, sanitizedPattern)
            PerfMonitor.end("breath_m1_inference", tInf) // PERF

            // 기존 브로드캐스트 로직...
            val intent = Intent("PREDICTION_UPDATE")
            intent.setPackage(context.packageName)
            intent.putExtra("result1", result)
            intent.putExtra("sendAt", SystemClock.elapsedRealtime()) // PERF
            context.sendBroadcast(intent)
            Log.d("Value Detection", "모델1에 들어온 userBpm 값: $userBpm")
            Log.d("AudioRecorder", "모델1 결과: $result")
        } catch (e: IOException) {
            Log.e("AudioRecorder", "모델1 처리 오류", e)
        } finally {
            PerfMonitor.end("breath_m1_total", tTotal) // PERF
        }
    }

    private fun processModel2(audioData: FloatArray) {
        try {
            val features = AudioProcessor.extractFeaturesForModel2(audioData)
            val result = ModelInterpreter.runModel2(context, features, userBpm, userBreathingPattern)

            // 기존 브로드캐스트 로직...
            val intent = Intent("PREDICTION_UPDATE")
            intent.setPackage(context.packageName)
            intent.putExtra("resultToTts", result)
            context.sendBroadcast(intent)

            Log.d("AudioRecorder", "모델2 결과: $result")
            ModelInterpreter.resetAbnormalCount()
        } catch (e: IOException) {
            Log.e("AudioRecorder", "모델2 처리 오류", e)
        }
    }

    /*
    * TODO:
    *  stage1, stage2 최근에 수치 바껴서
    *  해당 부분 값 수정하기
    * */
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

