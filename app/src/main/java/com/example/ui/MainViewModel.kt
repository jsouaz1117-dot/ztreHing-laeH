package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.FrequencyPreset
import com.example.data.ListeningSession
import com.example.audio.AudioEngine
import com.example.audio.InstrumentTuner
import com.example.audio.VowelShape
import com.example.audio.WaveShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RampingStep(val durationMins: Int, val leftFreq: Float, val rightFreq: Float)

class MainViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    val audioEngine = AudioEngine()
    val instrumentTuner = InstrumentTuner()

    // Equalizer gains
    private val _eqBass = MutableStateFlow(1.0f)
    val eqBass: StateFlow<Float> = _eqBass.asStateFlow()

    private val _eqMid = MutableStateFlow(1.0f)
    val eqMid: StateFlow<Float> = _eqMid.asStateFlow()

    private val _eqHigh = MutableStateFlow(1.0f)
    val eqHigh: StateFlow<Float> = _eqHigh.asStateFlow()

    // Frequency Ramping queue states
    private val _rampingSteps = MutableStateFlow<List<RampingStep>>(
        listOf(
            RampingStep(1, 396.00f, 396.10f),
            RampingStep(1, 417.00f, 417.15f),
            RampingStep(1, 528.00f, 528.20f)
        ) // pre-populate with a gorgeous relaxing 3-minute journey
    )
    val rampingSteps: StateFlow<List<RampingStep>> = _rampingSteps.asStateFlow()

    private val _isRampingActive = MutableStateFlow(false)
    val isRampingActive: StateFlow<Boolean> = _isRampingActive.asStateFlow()

    private val _currentRampingStepIndex = MutableStateFlow(-1)
    val currentRampingStepIndex: StateFlow<Int> = _currentRampingStepIndex.asStateFlow()

    private val _rampingSecondsRemaining = MutableStateFlow(0)
    val rampingSecondsRemaining: StateFlow<Int> = _rampingSecondsRemaining.asStateFlow()

    private var rampingJob: Job? = null

    fun setEqBass(value: Float) {
        _eqBass.value = value
        audioEngine.eqBass = value
    }

    fun setEqMid(value: Float) {
        _eqMid.value = value
        audioEngine.eqMid = value
    }

    fun setEqHigh(value: Float) {
        _eqHigh.value = value
        audioEngine.eqHigh = value
    }

    fun addRampingStep(mins: Int, left: Float, right: Float) {
        _rampingSteps.value = _rampingSteps.value + RampingStep(
            durationMins = mins,
            leftFreq = left.coerceIn(0.01f, 50000f),
            rightFreq = right.coerceIn(0.01f, 50000f)
        )
    }

    fun clearRampingSteps() {
        _rampingSteps.value = emptyList()
        stopRamping()
    }

    fun removeRampingStep(index: Int) {
        val current = _rampingSteps.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _rampingSteps.value = current
        }
    }

    fun startRamping() {
        if (_rampingSteps.value.isEmpty()) return
        stopRamping()
        _isRampingActive.value = true
        rampingJob = viewModelScope.launch {
            for (index in _rampingSteps.value.indices) {
                _currentRampingStepIndex.value = index
                val step = _rampingSteps.value[index]
                setLeftFrequency(step.leftFreq)
                setRightFrequency(step.rightFreq)
                if (!audioEngine.isPlaying.value) {
                    audioEngine.targetLeftFreq = step.leftFreq
                    audioEngine.targetRightFreq = if (_isBinauralEnabled.value) step.rightFreq else step.leftFreq
                    audioEngine.targetVolume = _volumeInput.value
                    audioEngine.activeVowelShape = _selectedVowel.value
                    audioEngine.activeWaveShape = _selectedWaveShape.value
                    audioEngine.isBinaural = _isBinauralEnabled.value
                    audioEngine.start()
                }
                var secondsRemaining = step.durationMins * 60
                while (secondsRemaining > 0 && _isRampingActive.value) {
                    _rampingSecondsRemaining.value = secondsRemaining
                    delay(1000)
                    secondsRemaining--
                }
                if (!_isRampingActive.value) break
            }
            _isRampingActive.value = false
            _currentRampingStepIndex.value = -1
            _rampingSecondsRemaining.value = 0
        }
    }

    fun stopRamping() {
        _isRampingActive.value = false
        rampingJob?.cancel()
        rampingJob = null
        _currentRampingStepIndex.value = -1
        _rampingSecondsRemaining.value = 0
    }

    fun triggerKoshiChimes() = audioEngine.triggerKoshiChimes()
    fun triggerTingshaCymbals() = audioEngine.triggerTingshaCymbals()
    fun triggerLightRattling() = audioEngine.triggerLightRattling()

    // Exposed flows from room database
    val presets = repository.allPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedRampingSequences = repository.allRampingSequences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notes methods
    fun addNote(title: String, content: String, colorHex: String) {
        viewModelScope.launch {
            val note = com.example.data.SessionNote(
                title = title.ifBlank { "Session Journal Note" },
                content = content,
                colorHex = colorHex
            )
            repository.insertNote(note)
        }
    }

    fun deleteNote(note: com.example.data.SessionNote) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    // Saved Ramping Sequence methods
    fun saveCurrentRampingSequence(name: String) {
        val steps = _rampingSteps.value
        if (steps.isEmpty()) return
        val encoded = steps.joinToString(";") { "${it.durationMins}|${it.leftFreq}|${it.rightFreq}" }
        viewModelScope.launch {
            val seq = com.example.data.SavedRampingSequence(
                name = name.ifBlank { "Sequence Preset (${steps.size} steps)" },
                stepsEncoded = encoded
            )
            repository.insertRampingSequence(seq)
        }
    }

    fun loadRampingSequence(sequence: com.example.data.SavedRampingSequence) {
        if (sequence.stepsEncoded.isBlank()) return
        val decoded = sequence.stepsEncoded.split(";").mapNotNull { stepStr ->
            val parts = stepStr.split("|")
            if (parts.size == 3) {
                val mins = parts[0].toIntOrNull() ?: 1
                val left = parts[1].toFloatOrNull() ?: 432f
                val right = parts[2].toFloatOrNull() ?: 432f
                RampingStep(mins, left, right)
            } else null
        }
        if (decoded.isNotEmpty()) {
            _rampingSteps.value = decoded
        }
    }

    fun deleteRampingSequence(sequence: com.example.data.SavedRampingSequence) {
        viewModelScope.launch {
            repository.deleteRampingSequence(sequence)
        }
    }

    // UI Input states

    private val _leftFrequencyInput = MutableStateFlow(432f)
    val leftFrequencyInput: StateFlow<Float> = _leftFrequencyInput.asStateFlow()

    private val _rightFrequencyInput = MutableStateFlow(440f)
    val rightFrequencyInput: StateFlow<Float> = _rightFrequencyInput.asStateFlow()

    private val _thirdFrequencyInput = MutableStateFlow(183.58f)
    val thirdFrequencyInput: StateFlow<Float> = _thirdFrequencyInput.asStateFlow()

    private val _volumeInput = MutableStateFlow(0.5f)
    val volumeInput: StateFlow<Float> = _volumeInput.asStateFlow()

    private val _selectedVowel = MutableStateFlow(VowelShape.PURE_SINE)
    val selectedVowel: StateFlow<VowelShape> = _selectedVowel.asStateFlow()

    private val _selectedWaveShape = MutableStateFlow(WaveShape.SINE)
    val selectedWaveShape: StateFlow<WaveShape> = _selectedWaveShape.asStateFlow()

    private val _isBinauralEnabled = MutableStateFlow(false)
    val isBinauralEnabled: StateFlow<Boolean> = _isBinauralEnabled.asStateFlow()

    private val _isThirdFrequencyEnabled = MutableStateFlow(false)
    val isThirdFrequencyEnabled: StateFlow<Boolean> = _isThirdFrequencyEnabled.asStateFlow()

    // Sweep properties
    private val _isSweepActiveInVm = MutableStateFlow(false)
    val isSweepActiveInVm: StateFlow<Boolean> = _isSweepActiveInVm.asStateFlow()

    private val _sweepStartFreqInput = MutableStateFlow(100f)
    val sweepStartFreqInput: StateFlow<Float> = _sweepStartFreqInput.asStateFlow()

    private val _sweepEndFreqInput = MutableStateFlow(1000f)
    val sweepEndFreqInput: StateFlow<Float> = _sweepEndFreqInput.asStateFlow()

    private val _sweepDurationInput = MutableStateFlow(30f) // seconds
    val sweepDurationInput: StateFlow<Float> = _sweepDurationInput.asStateFlow()

    // Active session details
    private var sessionStartTime: Long = 0L
    private var currentPlayingPresetName: String? = null

    init {
        // Automatically insert default presets if database is empty or missing defaults
        viewModelScope.launch {
            presets.collect { list ->
                val existingNames = list.map { it.name }.toSet()
                val missingDefaults = getDefaultPresets().filter { it.name !in existingNames }
                if (missingDefaults.isNotEmpty()) {
                    missingDefaults.forEach { repository.insertPreset(it) }
                }
            }
        }

        // Monitor if AudioEngine plays or stops
        viewModelScope.launch {
            audioEngine.isPlaying.collect { playing ->
                if (playing) {
                    sessionStartTime = System.currentTimeMillis()
                } else {
                    saveCompletedListeningSession()
                }
            }
        }
    }

    private fun getDefaultPresets(): List<FrequencyPreset> {
        return listOf(
            // Unexplored Frequencies
            FrequencyPreset(name = "1777 Hz - Time Perception (Unexplored)", frequency1 = 1777f, frequency2 = 1777f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "1999 Hz - Cellular Time Reset (Unexplored)", frequency1 = 1999f, frequency2 = 1999f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "2222 Hz - Dimensional Time Flow (Unexplored)", frequency1 = 2222f, frequency2 = 2222f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "2750 Hz - Eternal Presence (Unexplored)", frequency1 = 2750f, frequency2 = 2750f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "3333 Hz - Quantum Time Travel (Unexplored)", frequency1 = 3333f, frequency2 = 3333f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "5555 Hz - Immortality (Unexplored)", frequency1 = 5555f, frequency2 = 5555f, isBinaural = false, isCustom = false),

            // Organ Frequencies
            FrequencyPreset(name = "Adrenals & Thyroid - 492.80 Hz (Organ)", frequency1 = 492.80f, frequency2 = 492.80f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Bladder - 352.00 Hz (Organ)", frequency1 = 352.00f, frequency2 = 352.00f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Blood - 321.90 Hz (Organ)", frequency1 = 321.90f, frequency2 = 321.90f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Bone - 418.30 Hz (Organ)", frequency1 = 418.30f, frequency2 = 418.30f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Brain - 315.80 Hz (Organ)", frequency1 = 315.80f, frequency2 = 315.80f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Colon - 176.00 Hz (Organ)", frequency1 = 176.00f, frequency2 = 176.00f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Fat Cells - 295.80 Hz (Organ)", frequency1 = 295.80f, frequency2 = 295.80f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Gall Bladder - 164.30 Hz (Organ)", frequency1 = 164.30f, frequency2 = 164.30f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Intestines - 281.00 Hz (Organ)", frequency1 = 281.00f, frequency2 = 281.00f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Kidneys - 319.88 Hz (Organ)", frequency1 = 319.88f, frequency2 = 319.88f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Liver - 317.83 Hz (Organ)", frequency1 = 317.83f, frequency2 = 317.83f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Lungs - 220.00 Hz (Organ)", frequency1 = 220.00f, frequency2 = 220.00f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Muscles - 324.00 Hz (Organ)", frequency1 = 324.00f, frequency2 = 324.00f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Pancreas - 117.30 Hz (Organ)", frequency1 = 117.30f, frequency2 = 117.30f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Stomach - 110.00 Hz (Organ)", frequency1 = 110.00f, frequency2 = 110.00f, isBinaural = false, isCustom = false),

            // Experimental Frequencies
            FrequencyPreset(name = "1050 Hz - Balancing Brain Hemispheres (Experimental)", frequency1 = 1050f, frequency2 = 1050f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "1122 Hz - Activating Dormant Energy (Experimental)", frequency1 = 1122f, frequency2 = 1122f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "1360 Hz - Enhancing Immune Function (Experimental)", frequency1 = 1360f, frequency2 = 1360f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "444 Hz - Realignment of Genetic Code (Experimental)", frequency1 = 444f, frequency2 = 444f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "1575 Hz - Deep Emotional Trauma Release (Experimental)", frequency1 = 1575f, frequency2 = 1575f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "1690 Hz - Expansion of Consciousness (Experimental)", frequency1 = 1690f, frequency2 = 1690f, isBinaural = false, isCustom = false),

            // Ancient Frequencies
            FrequencyPreset(name = "888 Hz - Thought Manifestation (Ancient)", frequency1 = 888f, frequency2 = 888f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "1209 Hz - Interdimensional Comm. (Ancient)", frequency1 = 1209f, frequency2 = 1209f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "2220 Hz - Memory Transfer (Ancient)", frequency1 = 2220f, frequency2 = 2220f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "2777 Hz - Biological Regeneration (Ancient)", frequency1 = 2777f, frequency2 = 2777f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "4044 Hz - Temporal Displacement (Ancient)", frequency1 = 4044f, frequency2 = 4044f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "9999 Hz - Akashic Frequency (Ancient)", frequency1 = 9999f, frequency2 = 9999f, isBinaural = false, isCustom = false),

            // Planetary Frequencies
            FrequencyPreset(name = "Earth - 7.83 Hz (Planetary)", frequency1 = 7.83f, frequency2 = 7.83f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Moon - 210.42 Hz (Planetary)", frequency1 = 210.42f, frequency2 = 210.42f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Sun - 126.22 Hz (Planetary)", frequency1 = 126.22f, frequency2 = 126.22f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Mars - 144.72 Hz (Planetary)", frequency1 = 144.72f, frequency2 = 144.72f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Mercury - 141.27 Hz (Planetary)", frequency1 = 141.27f, frequency2 = 141.27f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Venus - 221.23 Hz (Planetary)", frequency1 = 221.23f, frequency2 = 221.23f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Saturn - 147.85 Hz (Planetary)", frequency1 = 147.85f, frequency2 = 147.85f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Uranus - 207.36 Hz (Planetary)", frequency1 = 207.36f, frequency2 = 207.36f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Neptune - 211.44 Hz (Planetary)", frequency1 = 211.44f, frequency2 = 211.44f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Pluto - 140.25 Hz (Planetary)", frequency1 = 140.25f, frequency2 = 140.25f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Jupiter - 183.58 Hz (Planetary)", frequency1 = 183.58f, frequency2 = 183.58f, isBinaural = false, isCustom = false),

            // Frequencies notes (A, B, C, D, E, F, G)
            FrequencyPreset(name = "Note A4 (Universal Tuning)", frequency1 = 440f, frequency2 = 440f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Note B4 (Resonance)", frequency1 = 494f, frequency2 = 494f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Note C4 (Middle C)", frequency1 = 261.6f, frequency2 = 261.6f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Note D4 (Stress relief)", frequency1 = 293.7f, frequency2 = 293.7f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Note E4 (Life solar)", frequency1 = 329.6f, frequency2 = 329.6f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Note F4 (Organic flow)", frequency1 = 349.2f, frequency2 = 349.2f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "Note G4 (Inner Peace)", frequency1 = 392f, frequency2 = 392f, isBinaural = false, isCustom = false),

            // Chakras solfeggio (with corresponding vowel shapes)
            FrequencyPreset(name = "Root Chakra (396Hz) - Release Fear", frequency1 = 396f, frequency2 = 396f, isBinaural = false, vowelShapeName = "HUH", isCustom = false),
            FrequencyPreset(name = "Sacral Chakra (417Hz) - Change", frequency1 = 417f, frequency2 = 417f, isBinaural = false, vowelShapeName = "OOO", isCustom = false),
            FrequencyPreset(name = "Solar Plexus Chakra (528Hz) - Miracles", frequency1 = 528f, frequency2 = 528f, isBinaural = false, vowelShapeName = "OH", isCustom = false),
            FrequencyPreset(name = "Heart Chakra (639Hz) - Connection", frequency1 = 639f, frequency2 = 639f, isBinaural = false, vowelShapeName = "AH", isCustom = false),
            FrequencyPreset(name = "Throat Chakra (741Hz) - Expression", frequency1 = 741f, frequency2 = 741f, isBinaural = false, vowelShapeName = "EY", isCustom = false),
            FrequencyPreset(name = "Third Eye Chakra (852Hz) - Intuition", frequency1 = 852f, frequency2 = 852f, isBinaural = false, vowelShapeName = "AYE", isCustom = false),
            FrequencyPreset(name = "Crown Chakra (963Hz) - Transcendence", frequency1 = 963f, frequency2 = 963f, isBinaural = false, vowelShapeName = "EEE", isCustom = false),

            // Special & Extreme Frequencies
            FrequencyPreset(name = "0.01 Hz - Sub-Delta Epsilon Wave", frequency1 = 0.01f, frequency2 = 0.01f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "0.10 Hz - Autonomic Coherence", frequency1 = 0.10f, frequency2 = 0.10f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "7.83 Hz - Schumann Earth Resonance", frequency1 = 7.83f, frequency2 = 7.83f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "174 Hz - Pain & Stress Relief", frequency1 = 174f, frequency2 = 174f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "285 Hz - Tissue Repair & Cellular Opt.", frequency1 = 285f, frequency2 = 285f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "18.98 Hz - Ghost Frequency", frequency1 = 18.98f, frequency2 = 18.98f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "7.00 Hz - Dangerous Resonance", frequency1 = 7f, frequency2 = 7f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "10.00 Hz - Cardiac Dampener", frequency1 = 10f, frequency2 = 10f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "770 Hz - Visual Boundary", frequency1 = 770f, frequency2 = 770f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "10,000 Hz - High Frequency Cleanse", frequency1 = 10000f, frequency2 = 10000f, isBinaural = false, isCustom = false),

            // Super High & Ultrasonic Inaudible Frequencies (15 kHz - 50 kHz)
            FrequencyPreset(name = "15,000 Hz - Cellular Cleansing (Ultrasonic)", frequency1 = 15000f, frequency2 = 15000f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "20,000 Hz - Human Acoustic Boundary (Inaudible)", frequency1 = 20000f, frequency2 = 20000f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "25,000 Hz - Bio-Acoustic Stimulation (Inaudible)", frequency1 = 25000f, frequency2 = 25000f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "30,000 Hz - Cellular Cavitation Resonance (Inaudible)", frequency1 = 30000f, frequency2 = 30000f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "35,000 Hz - Harmonic Ultrasonic Field (Inaudible)", frequency1 = 35000f, frequency2 = 35000f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "40,000 Hz - Gamma Neural Ultrasonic (Inaudible)", frequency1 = 40000f, frequency2 = 40000f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "45,000 Hz - Bio-Magnetic Membrane Wave (Inaudible)", frequency1 = 45000f, frequency2 = 45000f, isBinaural = false, isCustom = false),
            FrequencyPreset(name = "50,000 Hz - Super High 50 kHz Ultrasonic (Inaudible)", frequency1 = 50000f, frequency2 = 50000f, isBinaural = false, isCustom = false),

            // Binaural Beats examples
            FrequencyPreset(name = "Theta Waves (6Hz Beat Deep Meditation)", frequency1 = 200f, frequency2 = 206f, isBinaural = true, isCustom = false),
            FrequencyPreset(name = "Delta Waves (2.5Hz Beat Dreamless Sleep)", frequency1 = 100f, frequency2 = 102.5f, isBinaural = true, isCustom = false),
            FrequencyPreset(name = "Alpha Waves (10Hz Beat Study Flow)", frequency1 = 150f, frequency2 = 160f, isBinaural = true, isCustom = false)
        )
    }

    private suspend fun createDefaultPresets() {
        for (preset in getDefaultPresets()) {
            repository.insertPreset(preset)
        }
    }


    // Audio Playback parameters modifier
    fun setLeftFrequency(freq: Float) {
        val coerced = freq.coerceIn(0.01f, 50000f)
        _leftFrequencyInput.value = coerced
        audioEngine.targetLeftFreq = coerced
        if (!_isBinauralEnabled.value) {
            _rightFrequencyInput.value = coerced
            audioEngine.targetRightFreq = coerced
        }
    }

    fun setRightFrequency(freq: Float) {
        val coerced = freq.coerceIn(0.01f, 50000f)
        _rightFrequencyInput.value = coerced
        audioEngine.targetRightFreq = coerced
    }

    fun setThirdFrequency(freq: Float) {
        val coerced = freq.coerceIn(0.01f, 50000f)
        _thirdFrequencyInput.value = coerced
        audioEngine.targetThirdFreq = coerced
    }

    fun setThirdFrequencyEnabled(enabled: Boolean) {
        _isThirdFrequencyEnabled.value = enabled
        audioEngine.isThirdFrequencyEnabled = enabled
    }

    fun setMasterVolume(vol: Float) {
        val coerced = vol.coerceIn(0f, 1f)
        _volumeInput.value = coerced
        audioEngine.targetVolume = coerced
    }

    fun setVowel(vowel: VowelShape) {
        _selectedVowel.value = vowel
        audioEngine.activeVowelShape = vowel
    }

    fun setWaveShape(shape: WaveShape) {
        _selectedWaveShape.value = shape
        audioEngine.activeWaveShape = shape
    }

    fun setBinauralEnabled(enabled: Boolean) {
        _isBinauralEnabled.value = enabled
        audioEngine.isBinaural = enabled
        if (!enabled) {
            // align right channel frequency to left
            setRightFrequency(_leftFrequencyInput.value)
        }
    }

    // Playback functions
    fun togglePlayback() {
        if (audioEngine.isPlaying.value) {
            audioEngine.stop()
        } else {
            // synchronise engine variables with input
            audioEngine.targetLeftFreq = _leftFrequencyInput.value
            audioEngine.targetRightFreq = if (_isBinauralEnabled.value) _rightFrequencyInput.value else _leftFrequencyInput.value
            audioEngine.targetThirdFreq = _thirdFrequencyInput.value
            audioEngine.isThirdFrequencyEnabled = _isThirdFrequencyEnabled.value
            audioEngine.targetVolume = _volumeInput.value
            audioEngine.activeVowelShape = _selectedVowel.value
            audioEngine.activeWaveShape = _selectedWaveShape.value
            audioEngine.isBinaural = _isBinauralEnabled.value

            audioEngine.start()
        }
    }

    // Sweep actions
    fun configureSweep(startFreq: Float, endFreq: Float, durationSec: Float) {
        _sweepStartFreqInput.value = startFreq.coerceIn(0.01f, 50000f)
        _sweepEndFreqInput.value = endFreq.coerceIn(0.01f, 50000f)
        _sweepDurationInput.value = durationSec.coerceIn(5f, 600f)
    }

    fun toggleSweep() {
        if (_isSweepActiveInVm.value) {
            _isSweepActiveInVm.value = false
            audioEngine.isSweepActive = false
        } else {
            // Make sure player is active
            if (!audioEngine.isPlaying.value) {
                togglePlayback()
            }
            _isSweepActiveInVm.value = true
            audioEngine.sweepStartFreq = _sweepStartFreqInput.value
            audioEngine.sweepEndFreq = _sweepEndFreqInput.value
            audioEngine.sweepDurationSeconds = _sweepDurationInput.value
            audioEngine.sweepProgress = 0.0
            audioEngine.isSweepActive = true
        }
    }

    // Presets Management
    fun selectPreset(preset: FrequencyPreset) {
        currentPlayingPresetName = preset.name
        setLeftFrequency(preset.frequency1)
        if (preset.isBinaural) {
            setBinauralEnabled(true)
            setRightFrequency(preset.frequency2)
        } else {
            setBinauralEnabled(false)
        }

        val freq = preset.frequency1
        val autoChakraVowel = when {
            Math.abs(freq - 396f) < 0.5f -> VowelShape.HUH
            Math.abs(freq - 417f) < 0.5f -> VowelShape.OOO
            Math.abs(freq - 528f) < 0.5f -> VowelShape.OH
            Math.abs(freq - 639f) < 0.5f -> VowelShape.AH
            Math.abs(freq - 741f) < 0.5f -> VowelShape.EY
            Math.abs(freq - 852f) < 0.5f -> VowelShape.AYE
            Math.abs(freq - 963f) < 0.5f -> VowelShape.EEE
            else -> null
        }

        val vowel = autoChakraVowel ?: try {
            VowelShape.valueOf(preset.vowelShapeName)
        } catch(e: Exception) {
            VowelShape.PURE_SINE
        }
        setVowel(vowel)

        // If not playing, start it on select to keep UI responsive
        if (!audioEngine.isPlaying.value) {
            togglePlayback()
        }
    }


    fun saveCustomPreset(name: String) {
        viewModelScope.launch {
            val preset = FrequencyPreset(
                name = name.takeIf { it.isNotEmpty() } ?: "Custom Preset ${_leftFrequencyInput.value.toInt()}Hz",
                frequency1 = _leftFrequencyInput.value,
                frequency2 = if (_isBinauralEnabled.value) _rightFrequencyInput.value else _leftFrequencyInput.value,
                isBinaural = _isBinauralEnabled.value,
                vowelShapeName = _selectedVowel.value.name,
                isCustom = true
            )
            repository.insertPreset(preset)
        }
    }

    fun deletePreset(preset: FrequencyPreset) {
        viewModelScope.launch {
            repository.deletePreset(preset)
        }
    }

    // Listening sessions actions
    private fun saveCompletedListeningSession() {
        if (sessionStartTime == 0L) return
        val durationMs = System.currentTimeMillis() - sessionStartTime
        val durationSec = (durationMs / 1000L).toInt()
        sessionStartTime = 0L

        // Log sessions longer than 4 seconds to avoid cluttering with accidental click-to-stops
        if (durationSec >= 4) {
            viewModelScope.launch {
                val presetLabel = currentPlayingPresetName ?: "Custom Resonance"
                val session = ListeningSession(
                    presetName = presetLabel,
                    frequency1 = _leftFrequencyInput.value,
                    frequency2 = if (_isBinauralEnabled.value) _rightFrequencyInput.value else _leftFrequencyInput.value,
                    isBinaural = _isBinauralEnabled.value,
                    durationSeconds = durationSec
                )
                repository.insertSession(session)
                currentPlayingPresetName = null // reset for next session
            }
        }
    }

    fun clearHistoricalLog() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Tuner actions
    fun toggleTuner() {
        if (instrumentTuner.isListening.value) {
            instrumentTuner.stopListening()
        } else {
            instrumentTuner.startListening()
        }
    }

    // Sleep Timer
    fun startSleepTimer(minutes: Int) {
        audioEngine.startSleepTimer(minutes)
    }

    fun stopSleepTimer() {
        audioEngine.stopSleepTimer()
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        instrumentTuner.stopListening()
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
