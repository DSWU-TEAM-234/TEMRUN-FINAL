package com.temrun_finalprojects.breathing.audio

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.mfcc.MFCC
import java.io.ByteArrayInputStream
import kotlin.math.sqrt

abstract class AudioProcessor {
    abstract fun processingFinished()

    class FeatureSet {
        lateinit var mfccFrames: Array<FloatArray>
        lateinit var rmsFrames: FloatArray
        //        public float[] fft;
        //        public float bpm;
        //        public float[] breathingPattern; // ✅ float 하나 대신 배열
    }

    companion object {
        fun extractFeatures(audioBuffer: FloatArray): FeatureSet {
            val features = FeatureSet()

            val sampleRate = 16000
            val bufferSize = 512
            val overlap = 256

            // Convert float[] to byte[] (PCM 16-bit signed little endian)
            val byteBuffer = ByteArray(audioBuffer.size * 2)
            for (i in audioBuffer.indices) {
                val `val` = (audioBuffer[i] * Short.MAX_VALUE).toInt().toShort()
                byteBuffer[2 * i] = (`val`.toInt() and 0x00ff).toByte()
                byteBuffer[2 * i + 1] = ((`val`.toInt() shr 8) and 0xff).toByte()
            }

            val bais = ByteArrayInputStream(byteBuffer)

            // ✅ 직접 AudioFormat 대신 TarsosDSPAudioFormat 사용
            val format = TarsosDSPAudioFormat(
                sampleRate.toFloat(),
                16,  // sample size in bits
                1,  // channels
                true,  // signed
                false // little endian
            )

            val audioStream = UniversalAudioInputStream(bais, format)
            val dispatcher = AudioDispatcher(audioStream, bufferSize, overlap)

            val mfccProcessor =
                MFCC(bufferSize, sampleRate.toFloat(), 13, 40, 300f, (sampleRate / 2).toFloat())
            dispatcher.addAudioProcessor(mfccProcessor)

            val mfccList: MutableList<FloatArray> = ArrayList()
            val rmsList: MutableList<Float> = ArrayList()

            dispatcher.addAudioProcessor(object : AudioProcessor {
                override fun process(audioEvent: AudioEvent): Boolean {
                    val buffer = audioEvent.floatBuffer

                    // RMS
                    var sum = 0f
                    for (sample in buffer) sum += sample * sample
                    val rms = sqrt((sum / buffer.size).toDouble()).toFloat()
                    rmsList.add(rms)

                    /**
                     * 모델1의 오디오 특징 추출 코드
                     **/
                    // FFT
//                float[] fftBuffer = new float[buffer.length * 2]; // N*2 for real+imaginary
//                System.arraycopy(buffer, 0, fftBuffer, 0, buffer.length);
//                FFT fft = new FFT(buffer.length);
//                fft.forwardTransform(fftBuffer);
//
//                float[] magnitude = new float[buffer.length];
//                fft.modulus(fftBuffer, magnitude);
//                features.fft = magnitude;
//
                    // MFCC 계산
                    val mfcc = mfccProcessor.mfcc // (13,)
                    mfccList.add(mfcc)

                    // 최대 75프레임만 사용
                    return mfccList.size < 75
                }

                override fun processingFinished() {}
            })

            dispatcher.run()

            // MFCC 결과 정리 (13 x 75)
            val frameCount = mfccList.size
            val mfccFrames = Array(13) { FloatArray(75) }
            for (t in 0..74) {
                val mfcc = if ((t < frameCount)) mfccList[t] else FloatArray(13) // 0-padding
                for (i in 0..12) {
                    mfccFrames[i][t] = mfcc[i]
                }
            }

            // RMS 결과 정리 (75,)
            val rmsFrames = FloatArray(75)
            for (t in 0..74) {
                rmsFrames[t] = if ((t < rmsList.size)) rmsList[t] else 0f
            }

            features.mfccFrames = mfccFrames
            features.rmsFrames = rmsFrames

            return features
        }
    }
}