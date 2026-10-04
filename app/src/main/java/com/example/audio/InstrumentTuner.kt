package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log2
import kotlin.math.roundToInt

data class TunerResult(
    val frequency: Float,
    val noteName: String,
    val deviationCents: Float,
    val targetFreq: Float,
    val midiNote: Int
)

class InstrumentTuner {
    private val TAG = "InstrumentTuner"
    private val SAMPLE_RATE = 22050
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    private var audioRecord: AudioRecord? = null
    private var tuningJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _tunerResult = MutableStateFlow<TunerResult?>(null)
    val tunerResult: StateFlow<TunerResult?> = _tunerResult.asStateFlow()

    private val _rmsVolume = MutableStateFlow(0f)
    val rmsVolume: StateFlow<Float> = _rmsVolume.asStateFlow()

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (_isListening.value) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            _isListening.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting audio capture for tuning: ${e.message}", e)
            return
        }

        tuningJob = coroutineScope.launch {
            val audioBuffer = ShortArray(2048)
            while (isActive && _isListening.value) {
                val readResult = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (readResult > 0) {
                    processAudioFrame(audioBuffer, readResult)
                }
                delay(100) // update tuner reading ~10 times per second for visual responsiveness
            }
        }
    }

    private val pitchHistory = ArrayList<Float>()
    private var smoothedFreq = 0f

    private fun processAudioFrame(buffer: ShortArray, size: Int) {
        // Calculate RMS volume to check if we have a signal
        var sumSquared = 0.0
        for (i in 0 until size) {
            val normSample = buffer[i].toDouble() / 32768.0
            sumSquared += normSample * normSample
        }
        val rms = Math.sqrt(sumSquared / size).toFloat()
        _rmsVolume.value = rms

        // Threshold of silence (0.015 ensures background noise doesn't trigger jitter)
        if (rms < 0.015f) {
            pitchHistory.clear()
            smoothedFreq = 0f
            _tunerResult.value = null
            return
        }

        // Perform Autocorrelation pitch detection
        val pitch = detectPitchAutocorrelation(buffer, size)
        if (pitch in 55.0f..1100.0f) { // typical tuner bounds (A1 to C6)
            pitchHistory.add(pitch)
            if (pitchHistory.size > 5) {
                pitchHistory.removeAt(0)
            }

            // Exponential moving average for smooth display
            val sorted = pitchHistory.sorted()
            val medianPitch = sorted[sorted.size / 2]

            smoothedFreq = if (smoothedFreq == 0f) {
                medianPitch
            } else {
                smoothedFreq * 0.65f + medianPitch * 0.35f
            }

            val result = calculateTuningInfo(smoothedFreq)
            _tunerResult.value = result
        } else {
            // Keep previous smoothed result briefly to prevent instantaneous flicker
            if (pitchHistory.isNotEmpty()) {
                pitchHistory.removeAt(0)
            } else {
                smoothedFreq = 0f
                _tunerResult.value = null
            }
        }
    }


    // High fidelity autocorrelation pitch detection
    private fun detectPitchAutocorrelation(buffer: ShortArray, size: Int): Float {
        // Range of frequencies to detect: 55Hz (A1) to 1050Hz (C6)
        // Lag translates to: Lag = SAMPLE_RATE / Frequency
        val maxLag = (SAMPLE_RATE / 55).coerceAtMost(size - 2)
        val minLag = (SAMPLE_RATE / 1100).coerceAtLeast(2)

        var maxR = 0.0
        var bestLag = -1

        // Autocorrelation calculation
        for (lag in minLag..maxLag) {
            var r = 0.0
            for (i in 0 until (size - lag)) {
                r += (buffer[i].toDouble() / 32768.0) * (buffer[i + lag].toDouble() / 32768.0)
            }
            if (r > maxR) {
                maxR = r
                bestLag = lag
            }
        }

        if (bestLag != -1) {
            // Found a peak lag, refine with basic local peak tracking
            var refinedLag = bestLag.toDouble()
            
            // Parabolas interpolation for sub-lag accuracy (highly improves cent precision!)
            if (bestLag > minLag && bestLag < maxLag) {
                val rLeft = computeLagValue(buffer, size, bestLag - 1)
                val rCenter = maxR
                val rRight = computeLagValue(buffer, size, bestLag + 1)
                
                val denom = 2 * rCenter - rLeft - rRight
                if (denom != 0.0) {
                    refinedLag = bestLag + (rRight - rLeft) / (2 * denom)
                }
            }
            
            return (SAMPLE_RATE / refinedLag).toFloat()
        }

        return -1f
    }

    private fun computeLagValue(buffer: ShortArray, size: Int, lag: Int): Double {
        var r = 0.0
        for (i in 0 until (size - lag)) {
            r += (buffer[i].toDouble() / 32768.0) * (buffer[i + lag].toDouble() / 32768.0)
        }
        return r
    }

    private fun calculateTuningInfo(frequency: Float): TunerResult {
        // Standard musical midi note calculation
        // n = 12 * log2(f / 440) + 69
        val midiNoteVal = 12.0 * log2(frequency.toDouble() / 440.0) + 69.0
        val midiNote = midiNoteVal.roundToInt()
        
        // Closest note calculation
        val noteIndex = (midiNote % 12 + 12) % 12
        val octave = (midiNote / 12) - 1
        val noteName = "${NOTE_NAMES[noteIndex]}$octave"

        // Target perfect frequency calculation
        // ft = 440 * 2^((n - 69)/12)
        val targetFreq = (440.0 * Math.pow(2.0, (midiNote - 69).toDouble() / 12.0)).toFloat()

        // Cents calculation: cents = 1200 * log2(f / ft)
        val cents = (1200.0 * log2((frequency / targetFreq).toDouble())).toFloat()

        return TunerResult(
            frequency = frequency,
            noteName = noteName,
            deviationCents = cents.coerceIn(-50f, 50f),
            targetFreq = targetFreq,
            midiNote = midiNote
        )
    }

    fun stopListening() {
        if (!_isListening.value) return
        _isListening.value = false
        tuningJob?.cancel()
        tuningJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tuner recording: ${e.message}")
        }
        _tunerResult.value = null
        _rmsVolume.value = 0f
    }
}
