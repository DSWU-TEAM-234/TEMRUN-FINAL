package com.temrun_finalprojects.breathing.audio

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.mfcc.MFCC
import be.tarsos.dsp.util.fft.FFT
import org.jtransforms.fft.FloatFFT_1D
import java.io.ByteArrayInputStream
import kotlin.math.*

abstract class AudioProcessor {
    abstract fun processingFinished()

    class FeatureSet {
        lateinit var mfccFrames: Array<FloatArray>
        lateinit var rmsFrames: FloatArray
        lateinit var logMelSpectrogram: FloatArray // Model1용 추가

        //        public float[] fft;
        //        public float bpm;
        //        public float[] breathingPattern; // ✅ float 하나 대신 배열
    }

    // LogMelSpectrogram 클래스를 내부 클래스로 추가
    class LogMelSpectrogram(
        private val sampleRate: Int = 16000,
        private val nFft: Int = 512,
        private val hopLength: Int = 256,
        private val nMels: Int = 128,
        private val specWidth: Int = 128,
        private val specHeight: Int = 128
    ) {

        fun extract(pcmShorts: ShortArray): FloatArray {
            val floatPcm = pcmShorts.map { it / 32768.0f }.toFloatArray()
            val stftMag = computeStft(floatPcm)
            val melSpec = applyMelFilterBank(stftMag)
            val logMel = powerToDb(melSpec)
            val resized = resize(logMel, specWidth, specHeight)
            return normalizeToFloat01(resized)
        }

        private fun computeStft(signal: FloatArray): Array<FloatArray> {
            val frames = mutableListOf<FloatArray>()
            var pos = 0

            while (pos + nFft <= signal.size) {
                val frame = signal.copyOfRange(pos, pos + nFft)
                val windowed = hannWindow(frame)
                val fft = FloatArray(nFft * 2)
                for (i in windowed.indices) fft[2 * i] = windowed[i]
                FloatFFT_1D(nFft.toLong()).realForwardFull(fft)

                val mag = FloatArray(nFft / 2 + 1)
                for (i in 0 until mag.size) {
                    val re = fft[2 * i]
                    val im = fft[2 * i + 1]
                    mag[i] = sqrt(re * re + im * im)
                }
                frames.add(mag)
                pos += hopLength
            }
            return frames.toTypedArray()
        }

        private fun hannWindow(frame: FloatArray): FloatArray {
            val N = frame.size
            return FloatArray(N) { i -> frame[i] * (0.5f - 0.5f * cos(2.0 * Math.PI * i / N).toFloat()) }
        }

        private fun applyMelFilterBank(spectrogram: Array<FloatArray>): Array<FloatArray> {
            val melFilters = createMelFilterBank()
            val melSpec = Array(spectrogram.size) { FloatArray(nMels) }

            for (t in spectrogram.indices) {
                for (m in 0 until nMels) {
                    var sum = 0f
                    for (f in spectrogram[t].indices) {
                        sum += melFilters[m][f] * spectrogram[t][f]
                    }
                    melSpec[t][m] = sum
                }
            }
            return melSpec
        }

        private fun createMelFilterBank(): Array<FloatArray> {
            val fMin = 0.0
            val fMax = sampleRate / 2.0

            fun hzToMel(hz: Double): Double = 2595 * log10((1 + hz / 700).toDouble())
            fun melToHz(mel: Double): Double = 700 * ((10.0.pow(mel / 2595)) - 1)

            val melPoints = DoubleArray(nMels + 2) { i ->
                melToHz(hzToMel(fMin) + i * (hzToMel(fMax) - hzToMel(fMin)) / (nMels + 1))
            }

            val bin = melPoints.map { floor((nFft + 1) * it / sampleRate).toInt() }
            val filterBank = Array(nMels) { FloatArray(nFft / 2 + 1) }

            for (m in 1 until melPoints.size - 1) {
                val f_m_minus = bin[m - 1]
                val f_m = bin[m]
                val f_m_plus = bin[m + 1]

                for (k in f_m_minus until f_m) {
                    filterBank[m - 1][k] = ((k - f_m_minus).toFloat()) / (f_m - f_m_minus)
                }
                for (k in f_m until f_m_plus) {
                    filterBank[m - 1][k] = ((f_m_plus - k).toFloat()) / (f_m_plus - f_m)
                }
            }
            return filterBank
        }

        private fun powerToDb(mel: Array<FloatArray>): Array<FloatArray> {
            val result = Array(mel.size) { FloatArray(mel[0].size) }
            val ref = mel.flatMap { it.asList() }.maxOrNull() ?: 1f

            for (i in mel.indices) {
                for (j in mel[i].indices) {
                    val ratio = (mel[i][j] + 1e-10f) / ref
                    result[i][j] = (10 * (ln(ratio.toDouble()) / ln(10.0))).toFloat()
                }
            }
            return result
        }

        private fun resize(spec: Array<FloatArray>, targetW: Int, targetH: Int): Array<FloatArray> {
            val srcH = spec.size
            val srcW = spec[0].size
            val result = Array(targetH) { FloatArray(targetW) }

            for (i in 0 until targetH) {
                val srcI = (i.toFloat() / targetH * srcH).toInt().coerceIn(0, srcH - 1)
                for (j in 0 until targetW) {
                    val srcJ = (j.toFloat() / targetW * srcW).toInt().coerceIn(0, srcW - 1)
                    result[i][j] = spec[srcI][srcJ]
                }
            }
            return result
        }

        private fun normalizeToFloat01(input: Array<FloatArray>): FloatArray {
            val flat = input.flatMap { it.asList() }
            val min = flat.minOrNull() ?: 0f
            val max = flat.maxOrNull() ?: 1f
            return FloatArray(flat.size) { i ->
                ((flat[i] - min) / (max - min + 1e-6f)).coerceIn(0f, 1f)
            }
        }
    }

    companion object {
        // 기존 메서드는 모델2용으로 유지 (2초, 75프레임)
        fun extractFeatures(audioBuffer: FloatArray): FeatureSet {
            return extractFeaturesInternal(audioBuffer, 75) // 2초용
        }

        // 모델1용 새 메서드 (3초, 112프레임)
        fun extractFeaturesForModel1(audioBuffer: FloatArray): FeatureSet {
            return extractFeaturesInternal(audioBuffer, 112) // 3초용 (75 * 3/2 = 112.5 ≈ 112)
        }

        // 모델2용 새 메서드 (2초, 75프레임)
        fun extractFeaturesForModel2(audioBuffer: FloatArray): FeatureSet {
            return extractFeaturesInternal(audioBuffer, 75) // 2초용
        }

        // 내부 구현 메서드
        private fun extractFeaturesInternal(audioBuffer: FloatArray, maxFrames: Int): FeatureSet {
            val features = FeatureSet()
            val sampleRate = 16000
            val bufferSize = 512
            val overlap = 256

            // 기존 ByteArray 변환 로직...
            val byteBuffer = ByteArray(audioBuffer.size * 2)
            for (i in audioBuffer.indices) {
                val `val` = (audioBuffer[i] * Short.MAX_VALUE).toInt().toShort()
                byteBuffer[2 * i] = (`val`.toInt() and 0x00ff).toByte()
                byteBuffer[2 * i + 1] = ((`val`.toInt() shr 8) and 0xff).toByte()
            }

            // 기존 AudioDispatcher 로직...
            val bais = ByteArrayInputStream(byteBuffer)
            val format = TarsosDSPAudioFormat(sampleRate.toFloat(), 16, 1, true, false)
            val audioStream = UniversalAudioInputStream(bais, format)
            val dispatcher = AudioDispatcher(audioStream, bufferSize, overlap)

            val mfccProcessor = MFCC(bufferSize, sampleRate.toFloat(), 13, 40, 300f, (sampleRate / 2).toFloat())
            dispatcher.addAudioProcessor(mfccProcessor)

            val mfccList: MutableList<FloatArray> = ArrayList()
            val rmsList: MutableList<Float> = ArrayList()

            dispatcher.addAudioProcessor(object : AudioProcessor {
                override fun process(audioEvent: AudioEvent): Boolean {
                    val buffer = audioEvent.floatBuffer

                    // RMS 계산
                    var sum = 0f
                    for (sample in buffer) sum += sample * sample
                    val rms = sqrt((sum / buffer.size).toDouble()).toFloat()
                    rmsList.add(rms)

                    // MFCC 계산
                    val mfcc = mfccProcessor.mfcc
                    mfccList.add(mfcc)

                    // maxFrames까지만 처리
                    return mfccList.size < maxFrames
                }

                override fun processingFinished() {}
            })

            dispatcher.run()

            // MFCC 결과 정리 (13 x maxFrames)
            val frameCount = mfccList.size
            val mfccFrames = Array(13) { FloatArray(maxFrames) }
            for (t in 0 until maxFrames) {
                val mfcc = if (t < frameCount) mfccList[t] else FloatArray(13)
                for (i in 0..12) {
                    mfccFrames[i][t] = mfcc[i]
                }
            }

            // RMS 결과 정리 (maxFrames,)
            val rmsFrames = FloatArray(maxFrames)
            for (t in 0 until maxFrames) {
                rmsFrames[t] = if (t < rmsList.size) rmsList[t] else 0f
            }

            // LogMelSpectrogram는 기존 로직 유지 (모델1에서만 사용)
            val pcmShorts = ShortArray(audioBuffer.size)
            for (i in audioBuffer.indices) {
                pcmShorts[i] = (audioBuffer[i] * Short.MAX_VALUE).toInt().toShort()
            }
            val logMelExtractor = LogMelSpectrogram()
            val logMelSpec = logMelExtractor.extract(pcmShorts)

            features.mfccFrames = mfccFrames
            features.rmsFrames = rmsFrames
            features.logMelSpectrogram = logMelSpec

            return features
        }
    }

}