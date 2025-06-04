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
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private val handler = Handler()

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
                    com.temrun_finalprojects.breathing.audio.AudioRecorder.Companion.BUFFER_SIZE * 2
                )
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

            val audioBuffer =
                ShortArray(com.temrun_finalprojects.breathing.audio.AudioRecorder.Companion.BUFFER_SIZE)
            while (true) {
                val read = recorder.read(
                    audioBuffer,
                    0,
                    com.temrun_finalprojects.breathing.audio.AudioRecorder.Companion.BUFFER_SIZE
                )
                if (read > 0) {
                    val audioFloat = FloatArray(read)
                    for (i in 0 until read) {
                        audioFloat[i] = audioBuffer[i] / 32768f
                    }

                    try {
                        processAudio(audioFloat)
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                }

                try {
                    Thread.sleep((com.temrun_finalprojects.breathing.audio.AudioRecorder.Companion.RECORD_SECONDS * 1000).toLong())
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                    break
                }
            }
        }).start()
    }

    @Throws(IOException::class)
    private fun processAudio(audioData: FloatArray) {
        val features: AudioProcessor.FeatureSet = AudioProcessor.extractFeatures(audioData)

        //        String prediction = ModelInterpreter.runModel1(context, features);

        // TODO: 사용자가 실제로 선택하는 호흡 패턴의 값을 가져와서 여기다가 넣어야한다.
        val bp = intArrayOf(2, 1) // 예시
        val prediction: String = com.temrun_finalprojects.breathing.model.ModelInterpreter.runModel2(context, features, 150, bp)

        val intent = Intent(context, MainActivity::class.java)
        intent.putExtra("result", prediction)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 꼭 필요!
        context.startActivity(intent)


        // TODO: 나중에 모델1, 2 연동할 때의 코드
        // 현재 5/28일 기준 -> 모델2만 일단 돌아가는지 확인하기
//        if ("비정상".equals(prediction)) {
//            String result = ModelInterpreter.runModel2(context, features);
//            Intent intent = new Intent(context, ResultActivity.class);
//            intent.putExtra("result", result);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 꼭 필요!
//            context.startActivity(intent);
//        }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val RECORD_SECONDS = 2

        private const val BUFFER_SIZE: Int =
            com.temrun_finalprojects.breathing.audio.AudioRecorder.Companion.SAMPLE_RATE * com.temrun_finalprojects.breathing.audio.AudioRecorder.Companion.RECORD_SECONDS
    }
}

