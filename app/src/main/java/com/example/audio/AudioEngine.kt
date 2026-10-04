package com.example.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WaveShape(val label: String) {
    SINE("Sine"),
    SQUARE("Square"),
    TRIANGLE("Triangle"),
    SAWTOOTH("Sawtooth")
}

enum class VowelShape(val label: String) {
    PURE_SINE("Pure Sine"),
    EEE("EEE (Crown)"),
    AYE("AYE (Third Eye)"),
    EY("EY (Throat)"),
    AH("AH (Heart)"),
    OH("OH (Solar Plexus)"),
    OOO("OOO / OOH (Sacral)"),
    HUH("HUH / UH (Root)")
}

class AudioEngine {
    private val TAG = "AudioEngine"
    
    // Support high-resolution sample rates (up to 192 kHz) to synthesize ultrasonic frequencies up to 50,000 Hz
    private var sampleRate = 192000
    private var bufferSize = 8192

    private val _configuredSampleRate = MutableStateFlow(192000)
    val configuredSampleRate: StateFlow<Int> = _configuredSampleRate.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Engine control flows
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentLeftFreq = MutableStateFlow(440f)
    val currentLeftFreq: StateFlow<Float> = _currentLeftFreq.asStateFlow()

    private val _currentRightFreq = MutableStateFlow(440f)
    val currentRightFreq: StateFlow<Float> = _currentRightFreq.asStateFlow()

    private val _currentThirdFreq = MutableStateFlow(183.58f)
    val currentThirdFreq: StateFlow<Float> = _currentThirdFreq.asStateFlow()

    private val _sleepTimeRemaining = MutableStateFlow(0L) // in milliseconds
    val sleepTimeRemaining: StateFlow<Long> = _sleepTimeRemaining.asStateFlow()

    // Parameters with smooth transitions
    @Volatile var targetLeftFreq = 440f
    @Volatile var targetRightFreq = 440f
    @Volatile var targetThirdFreq = 183.58f
    @Volatile var isThirdFrequencyEnabled = false
    @Volatile var targetVolume = 0.5f
    @Volatile var activeVowelShape = VowelShape.PURE_SINE
    @Volatile var activeWaveShape = WaveShape.SINE
    @Volatile var isBinaural = false

    // Equalizer Band gains (1.0f is default)
    @Volatile var eqBass = 1.0f
    @Volatile var eqMid = 1.0f
    @Volatile var eqHigh = 1.0f

    // Grounding sound variables
    @Volatile var koshiEnvelope = 0.0
    private var koshiPhase1 = 0.0
    private var koshiPhase2 = 0.0
    private var koshiPhase3 = 0.0

    @Volatile var tingshaEnvelope = 0.0
    private var tingshaPhase1 = 0.0
    private var tingshaPhase2 = 0.0

    @Volatile var rattleEnvelope = 0.0
    private var rattleTime = 0.0

    fun triggerKoshiChimes() {
        koshiPhase1 = 0.0
        koshiPhase2 = 0.0
        koshiPhase3 = 0.0
        koshiEnvelope = 1.0
        // Ensure engine is running
        start()
    }

    fun triggerTingshaCymbals() {
        tingshaPhase1 = 0.0
        tingshaPhase2 = 0.0
        tingshaEnvelope = 1.0
        // Ensure engine is running
        start()
    }

    fun triggerLightRattling() {
        rattleTime = 0.0
        rattleEnvelope = 1.0
        // Ensure engine is running
        start()
    }

    // Sweep Parameters
    @Volatile var isSweepActive = false
    @Volatile var sweepStartFreq = 100f
    @Volatile var sweepEndFreq = 500f
    @Volatile var sweepDurationSeconds = 30f
    @Volatile var sweepProgress = 0.0 // 0.0 to 1.0

    // Sleep Timer Support
    private var timerJob: Job? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val candidateRates = intArrayOf(192000, 96000, 48000, 44100)
        var initialized = false

        for (rate in candidateRates) {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf > 0) {
                    val bufSize = minBuf.coerceAtLeast(8192)
                    val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        AudioTrack.Builder()
                            .setAudioAttributes(
                                android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(rate)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                    .build()
                            )
                            .setBufferSizeInBytes(bufSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                    } else {
                        @Suppress("DEPRECATION")
                        AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            rate,
                            AudioFormat.CHANNEL_OUT_STEREO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufSize,
                            AudioTrack.MODE_STREAM
                        )
                    }

                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        audioTrack = track
                        sampleRate = rate
                        bufferSize = bufSize
                        _configuredSampleRate.value = rate
                        initialized = true
                        Log.i(TAG, "AudioTrack initialized successfully at $rate Hz (Ultrasonic ready up to ${rate / 2} Hz)")
                        break
                    } else {
                        track.release()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not initialize AudioTrack at $rate Hz: ${e.message}")
            }
        }

        if (!initialized) {
            Log.e(TAG, "Failed to initialize AudioTrack at any candidate sample rate.")
        }
    }

    fun start() {
        if (_isPlaying.value) return

        if (audioTrack == null || audioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
            initAudioTrack()
        }

        try {
            audioTrack?.play()
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioTrack: ${e.message}", e)
            return
        }

        playbackJob = coroutineScope.launch {
            val frameCount = (sampleRate / 40).coerceIn(1024, 4096)
            // Stereo samples: 2 shorts per frame
            val shortBuffer = ShortArray(frameCount * 2)

            var phaseLeft = 0.0
            var phaseRight = 0.0
            var phaseThird = 0.0

            var activeLeftFreq = targetLeftFreq
            var activeRightFreq = targetRightFreq
            var activeThirdFreq = targetThirdFreq
            var activeVolume = 0.0f // start at 0 for fade-in
            var activeThirdGain = 0.0f

            while (isActive && _isPlaying.value) {
                // Apply Sweep logic
                if (isSweepActive) {
                    val frameStep = 1.0 / (sampleRate * sweepDurationSeconds)
                    sweepProgress += frameStep * frameCount
                    if (sweepProgress >= 1.0) {
                        sweepProgress = 0.0 // wrap/loop the sweep
                    }
                    val currentSweepVal = sweepStartFreq + (sweepEndFreq - sweepStartFreq) * sweepProgress.toFloat()
                    targetLeftFreq = currentSweepVal
                    if (isBinaural) {
                        // Offset for binaural beat split during sweep
                        targetRightFreq = currentSweepVal + (targetRightFreq - targetLeftFreq)
                    } else {
                        targetRightFreq = currentSweepVal
                    }
                }

                // Volume crossfade transition
                val volDiff = targetVolume - activeVolume
                if (Math.abs(volDiff) < 0.001f) {
                    activeVolume = targetVolume
                } else {
                    activeVolume += volDiff * 0.05f // de-click volume glide
                }

                // Guard against exceeding Nyquist ceiling for the hardware sample rate
                val maxSynthesizableFreq = (sampleRate * 0.495f).coerceAtMost(50000f)

                for (i in 0 until frameCount) {
                    // Glide frequencies smoothly (Tibetan bowl/glide effect)
                    val lfDiff = targetLeftFreq - activeLeftFreq
                    if (Math.abs(lfDiff) < 0.05f) activeLeftFreq = targetLeftFreq
                    else activeLeftFreq += lfDiff * 0.005f

                    val rfDiff = (if (isBinaural) targetRightFreq else targetLeftFreq) - activeRightFreq
                    if (Math.abs(rfDiff) < 0.05f) activeRightFreq = if (isBinaural) targetRightFreq else targetLeftFreq
                    else activeRightFreq += rfDiff * 0.005f

                    val tfDiff = targetThirdFreq - activeThirdFreq
                    if (Math.abs(tfDiff) < 0.05f) activeThirdFreq = targetThirdFreq
                    else activeThirdFreq += tfDiff * 0.005f

                    // Third frequency gain glide (de-click on/off toggle)
                    val targetThirdGain = if (isThirdFrequencyEnabled) 1.0f else 0.0f
                    val tgDiff = targetThirdGain - activeThirdGain
                    if (Math.abs(tgDiff) < 0.01f) activeThirdGain = targetThirdGain
                    else activeThirdGain += tgDiff * 0.05f

                    val safeLeft = activeLeftFreq.coerceIn(0.01f, maxSynthesizableFreq)
                    val safeRight = activeRightFreq.coerceIn(0.01f, maxSynthesizableFreq)
                    val safeThird = activeThirdFreq.coerceIn(0.01f, maxSynthesizableFreq)

                    // Accumulate phases
                    val stepLeft = (2.0 * Math.PI * safeLeft) / sampleRate
                    val stepRight = (2.0 * Math.PI * safeRight) / sampleRate
                    val stepThird = (2.0 * Math.PI * safeThird) / sampleRate

                    phaseLeft += stepLeft
                    if (phaseLeft > 2.0 * Math.PI) phaseLeft -= 2.0 * Math.PI

                    phaseRight += stepRight
                    if (phaseRight > 2.0 * Math.PI) phaseRight -= 2.0 * Math.PI

                    phaseThird += stepThird
                    if (phaseThird > 2.0 * Math.PI) phaseThird -= 2.0 * Math.PI

                    // Compute samples with Vowel Formants
                    val leftVal = getVowelSample(phaseLeft, activeVowelShape, safeLeft.toDouble()) * activeVolume
                    val rightVal = getVowelSample(phaseRight, activeVowelShape, safeRight.toDouble()) * activeVolume
                    val thirdVal = if (activeThirdGain > 0.001f) {
                        getVowelSample(phaseThird, activeVowelShape, safeThird.toDouble()) * activeVolume * activeThirdGain
                    } else 0.0

                    // Koshi Chimes
                    var koshiSample = 0.0
                    if (koshiEnvelope > 0.0) {
                        val step1 = (2.0 * Math.PI * 783.99) / sampleRate
                        val step2 = (2.0 * Math.PI * 1046.50) / sampleRate
                        val step3 = (2.0 * Math.PI * 1318.51) / sampleRate
                        koshiPhase1 += step1
                        koshiPhase2 += step2
                        koshiPhase3 += step3
                        koshiSample = (Math.sin(koshiPhase1) * 0.4 + Math.sin(koshiPhase2) * 0.35 + Math.sin(koshiPhase3) * 0.25) * koshiEnvelope * 0.35
                        koshiEnvelope -= 1.0 / (sampleRate * 5.0) // 5 seconds decay
                    }

                    // Tingsha Cymbals
                    var tingshaSample = 0.0
                    if (tingshaEnvelope > 0.0) {
                        val step1 = (2.0 * Math.PI * 2500.0) / sampleRate
                        val step2 = (2.0 * Math.PI * 2504.0) / sampleRate
                        tingshaPhase1 += step1
                        tingshaPhase2 += step2
                        tingshaSample = (Math.sin(tingshaPhase1) * 0.5 + Math.sin(tingshaPhase2) * 0.5) * tingshaEnvelope * 0.3
                        tingshaEnvelope -= 1.0 / (sampleRate * 6.0) // 6 seconds decay
                    }

                    // Light Rattling
                    var rattleSample = 0.0
                    if (rattleEnvelope > 0.0) {
                        rattleTime += 1.0 / sampleRate
                        val rawNoise = (Math.random() * 2.0 - 1.0)
                        val rhythmMod = Math.abs(Math.sin(2.0 * Math.PI * 6.0 * rattleTime))
                        rattleSample = rawNoise * rhythmMod * rattleEnvelope * 0.08
                        rattleEnvelope -= 1.0 / (sampleRate * 2.5) // 2.5 seconds decay
                    }

                    // Plays through both left and right ears / stereo speakers
                    val finalLeft = leftVal + thirdVal + koshiSample + tingshaSample + rattleSample
                    val finalRight = rightVal + thirdVal + koshiSample + tingshaSample + rattleSample

                    // Headroom scaling when combining multiple simultaneous planet frequencies
                    val headroomScale = if (activeThirdGain > 0.05f) 0.75 else 1.0
                    val leftShort = ((finalLeft * headroomScale).coerceIn(-1.0, 1.0) * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                    val rightShort = ((finalRight * headroomScale).coerceIn(-1.0, 1.0) * 32767.0).toInt().coerceIn(-32768, 32767).toShort()

                    shortBuffer[i * 2] = leftShort
                    shortBuffer[i * 2 + 1] = rightShort
                }

                _currentLeftFreq.value = activeLeftFreq
                _currentRightFreq.value = activeRightFreq
                _currentThirdFreq.value = activeThirdFreq

                try {
                    // Check if track is still playing and initialized
                    if (audioTrack != null && audioTrack?.state == AudioTrack.STATE_INITIALIZED && isPlaying.value) {
                        audioTrack?.write(shortBuffer, 0, shortBuffer.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception writing audio samples: ${e.message}")
                    break
                }
            }
        }
    }

    private fun getWaveSample(phase: Double, shape: WaveShape): Double {
        return when (shape) {
            WaveShape.SINE -> Math.sin(phase)
            WaveShape.SQUARE -> if (Math.sin(phase) >= 0.0) 0.5 else -0.5
            WaveShape.TRIANGLE -> {
                val p = phase / (2.0 * Math.PI)
                val normalized = p - Math.floor(p + 0.5)
                val v = 4.0 * Math.abs(normalized) - 1.0
                v * 0.8
            }
            WaveShape.SAWTOOTH -> {
                val p = phase / (2.0 * Math.PI)
                val normalized = p - Math.floor(p)
                val v = 2.0 * normalized - 1.0
                v * 0.5
            }
        }
    }

    private fun getEqFilteredHarmonic(fundamentalFreq: Double, harmonicOrder: Double, phase: Double, shape: WaveShape): Double {
        val hFreq = fundamentalFreq * harmonicOrder
        // Anti-aliasing safeguard: ensure harmonics never exceed Nyquist limit
        if (hFreq >= sampleRate * 0.49) {
            return 0.0
        }
        val eqGain = when {
            hFreq < 150.0 -> eqBass
            hFreq < 1500.0 -> eqMid
            else -> eqHigh
        }
        // At ultrasonic frequencies (>20 kHz), enforce pure sine for clean acoustic reproduction
        val effectiveShape = if (hFreq > 20000.0) WaveShape.SINE else shape
        return getWaveSample(phase, effectiveShape) * eqGain
    }

    private fun getVowelSample(phase: Double, vowel: VowelShape, freq: Double): Double {
        val shape = activeWaveShape
        return when (vowel) {
            VowelShape.PURE_SINE -> getEqFilteredHarmonic(freq, 1.0, phase, shape)
            VowelShape.EEE -> {
                // EEE (Crown)
                (getEqFilteredHarmonic(freq, 1.0, phase, shape) +
                 0.15 * getEqFilteredHarmonic(freq, 2.0, 2.0 * phase, shape) +
                 0.25 * getEqFilteredHarmonic(freq, 3.0, 3.0 * phase, shape) +
                 0.45 * getEqFilteredHarmonic(freq, 4.0, 4.0 * phase, shape) +
                 0.55 * getEqFilteredHarmonic(freq, 5.0, 5.0 * phase, shape)) / 2.4
            }
            VowelShape.AYE -> {
                // AYE (Third Eye)
                (getEqFilteredHarmonic(freq, 1.0, phase, shape) +
                 0.5 * getEqFilteredHarmonic(freq, 2.0, 2.0 * phase, shape) +
                 0.4 * getEqFilteredHarmonic(freq, 3.0, 3.0 * phase, shape) +
                 0.3 * getEqFilteredHarmonic(freq, 4.0, 4.0 * phase, shape) +
                 0.2 * getEqFilteredHarmonic(freq, 5.0, 5.0 * phase, shape)) / 2.4
            }
            VowelShape.EY -> {
                // EY (Throat)
                (getEqFilteredHarmonic(freq, 1.0, phase, shape) +
                 0.3 * getEqFilteredHarmonic(freq, 2.0, 2.0 * phase, shape) +
                 0.5 * getEqFilteredHarmonic(freq, 3.0, 3.0 * phase, shape) +
                 0.25 * getEqFilteredHarmonic(freq, 4.0, 4.0 * phase, shape)) / 2.05
            }
            VowelShape.AH -> {
                // AH (Heart)
                (getEqFilteredHarmonic(freq, 1.0, phase, shape) +
                 0.65 * getEqFilteredHarmonic(freq, 2.0, 2.0 * phase, shape) +
                 0.45 * getEqFilteredHarmonic(freq, 3.0, 3.0 * phase, shape) +
                 0.15 * getEqFilteredHarmonic(freq, 4.0, 4.0 * phase, shape)) / 2.25
            }
            VowelShape.OH -> {
                // OH (Solar Plexus)
                (getEqFilteredHarmonic(freq, 1.0, phase, shape) +
                 0.55 * getEqFilteredHarmonic(freq, 2.0, 2.0 * phase, shape) +
                 0.15 * getEqFilteredHarmonic(freq, 3.0, 3.0 * phase, shape)) / 1.7
            }
            VowelShape.OOO -> {
                // OOO / OOH (Sacral)
                (getEqFilteredHarmonic(freq, 1.0, phase, shape) +
                 0.15 * getEqFilteredHarmonic(freq, 2.0, 2.0 * phase, shape) +
                 0.05 * getEqFilteredHarmonic(freq, 3.0, 3.0 * phase, shape)) / 1.2
            }
            VowelShape.HUH -> {
                // HUH / UH (Root)
                (getEqFilteredHarmonic(freq, 1.0, phase, shape) +
                 0.3 * getEqFilteredHarmonic(freq, 2.0, 2.0 * phase, shape) +
                 0.3 * getEqFilteredHarmonic(freq, 3.0, 3.0 * phase, shape) +
                 0.15 * getEqFilteredHarmonic(freq, 4.0, 4.0 * phase, shape)) / 1.75
            }
        }
    }

    fun stop() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        isSweepActive = false
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing AudioTrack: ${e.message}")
        }
    }

    // Sleep Timer controls
    fun startSleepTimer(minutes: Int) {
        timerJob?.cancel()
        _sleepTimeRemaining.value = minutes * 60 * 1000L

        timerJob = coroutineScope.launch {
            while (_sleepTimeRemaining.value > 0L) {
                delay(1000)
                val remaining = _sleepTimeRemaining.value - 1000L
                _sleepTimeRemaining.value = remaining.coerceAtLeast(0L)
            }
            // Sleep timer expired -> fade-out and stop
            fadeOutAndStop()
        }
    }

    fun stopSleepTimer() {
        timerJob?.cancel()
        timerJob = null
        _sleepTimeRemaining.value = 0L
    }

    private suspend fun fadeOutAndStop() {
        val startVal = targetVolume
        val fadeSteps = 50
        for (i in 0..fadeSteps) {
            targetVolume = startVal * (1f - i.toFloat() / fadeSteps)
            delay(10)
        }
        stop()
        targetVolume = startVal // Restore target volume for next play
        stopSleepTimer()
    }

    fun release() {
        stop()
        stopSleepTimer()
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
        }
    }
}
