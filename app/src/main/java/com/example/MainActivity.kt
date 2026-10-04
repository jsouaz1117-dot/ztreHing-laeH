package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.FrequencyPreset
import com.example.data.ListeningSession
import com.example.data.SessionNote
import com.example.data.SavedRampingSequence
import com.example.audio.VowelShape
import com.example.audio.WaveShape
import com.example.audio.TunerResult
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.RampingStep
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.MintGlow
import com.example.ui.theme.GoldChakra
import com.example.ui.theme.SoftCopper
import com.example.ui.theme.SoftLavender
import com.example.ui.theme.SoftRose
import com.example.ui.theme.MutedText
import com.example.ui.theme.CyanAura
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

const val MIN_FREQUENCY = 0.01f
const val MAX_FREQUENCY = 50000f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Room Database
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AppRepository(database.appDao())

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(application, repository)
                )

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    containerColor = SlateDark
                ) { innerPadding ->
                    HealingHrtzDashboard(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun HealingHrtzDashboard(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.instrumentTuner.startListening()
        } else {
            viewModel.instrumentTuner.stopListening()
        }
    }

    // Connect to State flows in VM
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsStateWithLifecycle()
    val playLeftFreq by viewModel.audioEngine.currentLeftFreq.collectAsStateWithLifecycle()
    val playRightFreq by viewModel.audioEngine.currentRightFreq.collectAsStateWithLifecycle()
    val playThirdFreq by viewModel.audioEngine.currentThirdFreq.collectAsStateWithLifecycle()
    val sleepTimerMs by viewModel.audioEngine.sleepTimeRemaining.collectAsStateWithLifecycle()

    val leftFreqInput by viewModel.leftFrequencyInput.collectAsStateWithLifecycle()
    val rightFreqInput by viewModel.rightFrequencyInput.collectAsStateWithLifecycle()
    val thirdFreqInput by viewModel.thirdFrequencyInput.collectAsStateWithLifecycle()
    val volumeInput by viewModel.volumeInput.collectAsStateWithLifecycle()
    val selectedVowel by viewModel.selectedVowel.collectAsStateWithLifecycle()
    val selectedWaveShape by viewModel.selectedWaveShape.collectAsStateWithLifecycle()
    val isBinauralEnabled by viewModel.isBinauralEnabled.collectAsStateWithLifecycle()
    val isThirdFrequencyEnabled by viewModel.isThirdFrequencyEnabled.collectAsStateWithLifecycle()

    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val savedRampingSequences by viewModel.savedRampingSequences.collectAsStateWithLifecycle()

    val isSweepActive by viewModel.isSweepActiveInVm.collectAsStateWithLifecycle()
    val sweepStartInput by viewModel.sweepStartFreqInput.collectAsStateWithLifecycle()
    val sweepEndInput by viewModel.sweepEndFreqInput.collectAsStateWithLifecycle()
    val sweepDurationInput by viewModel.sweepDurationInput.collectAsStateWithLifecycle()

    val isTunerListening by viewModel.instrumentTuner.isListening.collectAsStateWithLifecycle()
    val tunerResult by viewModel.instrumentTuner.tunerResult.collectAsStateWithLifecycle()
    val tunerRms by viewModel.instrumentTuner.rmsVolume.collectAsStateWithLifecycle()

    // Equalizer & Sequence Ramping flows
    val eqBass by viewModel.eqBass.collectAsStateWithLifecycle()
    val eqMid by viewModel.eqMid.collectAsStateWithLifecycle()
    val eqHigh by viewModel.eqHigh.collectAsStateWithLifecycle()

    val rampingSteps by viewModel.rampingSteps.collectAsStateWithLifecycle()
    val isRampingActive by viewModel.isRampingActive.collectAsStateWithLifecycle()
    val currentRampingStepIndex by viewModel.currentRampingStepIndex.collectAsStateWithLifecycle()
    val rampingSecondsRemaining by viewModel.rampingSecondsRemaining.collectAsStateWithLifecycle()

    // Dialog for custom presets
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    // Selected Navigation Tab (0: Home, 1: Tools, 2: Spectrum, 3: Ramping, 4: Journal, 5: Tuner/EQ)
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SlateDark,
        bottomBar = {
            NavigationBar(
                containerColor = SlateCard,
                contentColor = EmeraldGreen
            ) {
                val navItems = listOf(
                    Triple(0, "Home", Icons.Default.Home),
                    Triple(1, "Tools", Icons.Default.GraphicEq),
                    Triple(2, "Spectrum", Icons.Default.AutoAwesome),
                    Triple(3, "Ramping", Icons.Default.Timeline),
                    Triple(4, "Journal", Icons.Default.EditNote),
                    Triple(5, "Tuner/EQ", Icons.Default.Tune)
                )

                navItems.forEach { (index, label, icon) ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldGreen,
                            selectedTextColor = EmeraldGreen,
                            unselectedIconColor = MutedText,
                            unselectedTextColor = MutedText,
                            indicatorColor = EmeraldGreen.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Persistent Title Block across tabs
            HeaderBlock(
                isPlaying = isPlaying,
                sleepTimerMs = sleepTimerMs,
                onStopTimer = { viewModel.stopSleepTimer() }
            )

            when (selectedTabIndex) {
                0 -> {
                    // TAB 0: HOME PAGE - STRICT 14-COMPONENT ORDER
                    // 1. Play button with Hz selectors
                    PlaybackControlPanel(
                        isPlaying = isPlaying,
                        onPlayToggle = { viewModel.togglePlayback() },
                        isBinaural = isBinauralEnabled,
                        onBinauralToggle = { viewModel.setBinauralEnabled(it) },
                        isThirdEnabled = isThirdFrequencyEnabled,
                        onThirdToggle = { viewModel.setThirdFrequencyEnabled(it) },
                        volume = volumeInput,
                        onVolumeChange = { viewModel.setMasterVolume(it) },
                        sleepTimerMs = sleepTimerMs,
                        onStartSleepTimer = { viewModel.startSleepTimer(it) }
                    )

                    // 2. Oscillator wave carrier
                    WaveShapeSelectionPanel(
                        waveShape = selectedWaveShape,
                        onWaveShapeSelected = { viewModel.setWaveShape(it) }
                    )

                    // 3. Resonator frequency
                    ResonatorFrequencyPanel(
                        leftFreq = leftFreqInput,
                        onLeftFreqChange = { viewModel.setLeftFrequency(it) },
                        onSavePresetClick = { showSavePresetDialog = true }
                    )

                    // 4. Binaural beat option
                    BinauralBeatPanel(
                        isBinaural = isBinauralEnabled,
                        rightFreq = rightFreqInput,
                        onRightFreqChange = { viewModel.setRightFrequency(it) }
                    )

                    // 4b. Third frequency channel (Planetary resonance)
                    ThirdFrequencyPanel(
                        isThirdEnabled = isThirdFrequencyEnabled,
                        onThirdToggle = { viewModel.setThirdFrequencyEnabled(it) },
                        thirdFreq = thirdFreqInput,
                        onThirdFreqChange = { viewModel.setThirdFrequency(it) }
                    )

                    // 5. Vocal formant shaper
                    VowelShapeSelectionPanel(
                        vowel = selectedVowel,
                        onVowelSelected = { viewModel.setVowel(it) }
                    )

                    // 6. Frequency sequence ramping
                    FrequencyRampingPanel(
                        steps = rampingSteps,
                        isActive = isRampingActive,
                        currentIndex = currentRampingStepIndex,
                        secondsRemaining = rampingSecondsRemaining,
                        onAddStep = { mins, left, right -> viewModel.addRampingStep(mins, left, right) },
                        onRemoveStep = { index -> viewModel.removeRampingStep(index) },
                        onClearAll = { viewModel.clearRampingSteps() },
                        onStart = { viewModel.startRamping() },
                        onStop = { viewModel.stopRamping() },
                        currentLeftFreq = leftFreqInput,
                        currentRightFreq = rightFreqInput,
                        savedSequences = savedRampingSequences,
                        onSaveSequence = { name -> viewModel.saveCurrentRampingSequence(name) },
                        onLoadSequence = { seq -> viewModel.loadRampingSequence(seq) },
                        onDeleteSequence = { seq -> viewModel.deleteRampingSequence(seq) }
                    )

                    // 7. Preset sanctuary
                    PresetSanctuaryPanel(
                        presets = presets,
                        onPresetSelect = { viewModel.selectPreset(it) },
                        onPresetDelete = { viewModel.deletePreset(it) }
                    )

                    // 8. Cognitive brainwave spectrum
                    BrainwaveChartPanel(
                        leftFreq = leftFreqInput,
                        rightFreq = rightFreqInput,
                        isBinaural = isBinauralEnabled
                    )

                    // 9. Resonance synthesizer
                    ResonanceOscilloscopeCanvas(
                        isPlaying = isPlaying,
                        leftFreq = playLeftFreq,
                        rightFreq = playRightFreq,
                        thirdFreq = playThirdFreq,
                        isBinaural = isBinauralEnabled,
                        isThird = isThirdFrequencyEnabled,
                        vowel = selectedVowel,
                        waveShape = selectedWaveShape
                    )

                    // 10. Frequency range sweep
                    SweepControllerPanel(
                        isSweepActive = isSweepActive,
                        sweepStart = sweepStartInput,
                        sweepEnd = sweepEndInput,
                        sweepDuration = sweepDurationInput,
                        onConfigure = { start, end, duration -> viewModel.configureSweep(start, end, duration) },
                        onToggleSweep = { viewModel.toggleSweep() }
                    )

                    // 11. Somatic return and awakening
                    SomaticAwakeningPanel(
                        onTriggerKoshi = { viewModel.triggerKoshiChimes() },
                        onTriggerTingsha = { viewModel.triggerTingshaCymbals() },
                        onTriggerRattle = { viewModel.triggerLightRattling() }
                    )

                    // 12. Session sanctuaries
                    MindfulnessLogPanel(
                        sessions = sessions,
                        onClearLogs = { viewModel.clearHistoricalLog() }
                    )

                    // 13. Harmonic equalizer
                    CosmicEqualizerPanel(
                        eqBass = eqBass,
                        eqMid = eqMid,
                        eqHigh = eqHigh,
                        onBassChange = { viewModel.setEqBass(it) },
                        onMidChange = { viewModel.setEqMid(it) },
                        onHighChange = { viewModel.setEqHigh(it) }
                    )

                    // 14. Instrument tuner
                    InstrumentTunerPanel(
                        isListening = isTunerListening,
                        tunerResult = tunerResult,
                        rms = tunerRms,
                        hasPermission = hasMicPermission,
                        onPermissionRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onToggleTuner = {
                            if (hasMicPermission) viewModel.toggleTuner()
                            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }

                1 -> {
                    // TAB 1: TOOLS (Generator, Hz selectors, Waveform, Vocal Formant)
                    PlaybackControlPanel(
                        isPlaying = isPlaying,
                        onPlayToggle = { viewModel.togglePlayback() },
                        isBinaural = isBinauralEnabled,
                        onBinauralToggle = { viewModel.setBinauralEnabled(it) },
                        isThirdEnabled = isThirdFrequencyEnabled,
                        onThirdToggle = { viewModel.setThirdFrequencyEnabled(it) },
                        volume = volumeInput,
                        onVolumeChange = { viewModel.setMasterVolume(it) },
                        sleepTimerMs = sleepTimerMs,
                        onStartSleepTimer = { viewModel.startSleepTimer(it) }
                    )

                    ResonatorFrequencyPanel(
                        leftFreq = leftFreqInput,
                        onLeftFreqChange = { viewModel.setLeftFrequency(it) },
                        onSavePresetClick = { showSavePresetDialog = true }
                    )

                    BinauralBeatPanel(
                        isBinaural = isBinauralEnabled,
                        rightFreq = rightFreqInput,
                        onRightFreqChange = { viewModel.setRightFrequency(it) }
                    )

                    ThirdFrequencyPanel(
                        isThirdEnabled = isThirdFrequencyEnabled,
                        onThirdToggle = { viewModel.setThirdFrequencyEnabled(it) },
                        thirdFreq = thirdFreqInput,
                        onThirdFreqChange = { viewModel.setThirdFrequency(it) }
                    )

                    WaveShapeSelectionPanel(
                        waveShape = selectedWaveShape,
                        onWaveShapeSelected = { viewModel.setWaveShape(it) }
                    )

                    VowelShapeSelectionPanel(
                        vowel = selectedVowel,
                        onVowelSelected = { viewModel.setVowel(it) }
                    )
                }

                2 -> {
                    // TAB 2: SPECTRUM & PRESETS
                    BrainwaveChartPanel(
                        leftFreq = leftFreqInput,
                        rightFreq = rightFreqInput,
                        isBinaural = isBinauralEnabled
                    )

                    SuperHighFrequenciesPanel(
                        currentFreq = leftFreqInput,
                        onSelectFreq = { freq ->
                            viewModel.setLeftFrequency(freq)
                            if (isBinauralEnabled) {
                                viewModel.setRightFrequency(freq)
                            }
                        }
                    )

                    OrganFrequenciesPanel(
                        currentFreq = leftFreqInput,
                        onOrganSelect = { organName, freq ->
                            val matchingPreset = presets.firstOrNull { it.name.startsWith(organName) }
                            if (matchingPreset != null) {
                                viewModel.selectPreset(matchingPreset)
                            } else {
                                viewModel.setLeftFrequency(freq)
                                if (isBinauralEnabled) {
                                    viewModel.setRightFrequency(freq)
                                }
                            }
                        }
                    )

                    PresetSanctuaryPanel(
                        presets = presets,
                        onPresetSelect = { viewModel.selectPreset(it) },
                        onPresetDelete = { viewModel.deletePreset(it) }
                    )
                }

                3 -> {
                    // TAB 3: RAMPING & SWEEP
                    FrequencyRampingPanel(
                        steps = rampingSteps,
                        isActive = isRampingActive,
                        currentIndex = currentRampingStepIndex,
                        secondsRemaining = rampingSecondsRemaining,
                        onAddStep = { mins, left, right -> viewModel.addRampingStep(mins, left, right) },
                        onRemoveStep = { index -> viewModel.removeRampingStep(index) },
                        onClearAll = { viewModel.clearRampingSteps() },
                        onStart = { viewModel.startRamping() },
                        onStop = { viewModel.stopRamping() },
                        currentLeftFreq = leftFreqInput,
                        currentRightFreq = rightFreqInput,
                        savedSequences = savedRampingSequences,
                        onSaveSequence = { name -> viewModel.saveCurrentRampingSequence(name) },
                        onLoadSequence = { seq -> viewModel.loadRampingSequence(seq) },
                        onDeleteSequence = { seq -> viewModel.deleteRampingSequence(seq) }
                    )

                    SweepControllerPanel(
                        isSweepActive = isSweepActive,
                        sweepStart = sweepStartInput,
                        sweepEnd = sweepEndInput,
                        sweepDuration = sweepDurationInput,
                        onConfigure = { start, end, duration -> viewModel.configureSweep(start, end, duration) },
                        onToggleSweep = { viewModel.toggleSweep() }
                    )
                }

                4 -> {
                    // TAB 4: SOMATIC RETURN, JOURNAL & SESSIONS
                    SomaticAwakeningPanel(
                        onTriggerKoshi = { viewModel.triggerKoshiChimes() },
                        onTriggerTingsha = { viewModel.triggerTingshaCymbals() },
                        onTriggerRattle = { viewModel.triggerLightRattling() }
                    )

                    SessionNotesJournalPanel(
                        notes = notes,
                        onAddNote = { title, content, color -> viewModel.addNote(title, content, color) },
                        onDeleteNote = { note -> viewModel.deleteNote(note) }
                    )

                    MindfulnessLogPanel(
                        sessions = sessions,
                        onClearLogs = { viewModel.clearHistoricalLog() }
                    )
                }

                5 -> {
                    // TAB 5: TUNER & EQUALIZER
                    CosmicEqualizerPanel(
                        eqBass = eqBass,
                        eqMid = eqMid,
                        eqHigh = eqHigh,
                        onBassChange = { viewModel.setEqBass(it) },
                        onMidChange = { viewModel.setEqMid(it) },
                        onHighChange = { viewModel.setEqHigh(it) }
                    )

                    InstrumentTunerPanel(
                        isListening = isTunerListening,
                        tunerResult = tunerResult,
                        rms = tunerRms,
                        hasPermission = hasMicPermission,
                        onPermissionRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onToggleTuner = {
                            if (hasMicPermission) viewModel.toggleTuner()
                            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }
    }

    // Dialog for custom presets
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Custom Frequency") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Name your current sound configuration to find it in your sanctuary later.")
                    TextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        placeholder = { Text("e.g. Cosmical Sleep 432Hz") },
                        modifier = Modifier.fillMaxWidth().testTag("preset_name_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveCustomPreset(presetNameInput)
                        presetNameInput = ""
                        showSavePresetDialog = false
                    },
                    modifier = Modifier.testTag("save_confirm_button")
                ) {
                    Text("Save", color = EmeraldGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel", color = MutedText)
                }
            },
            containerColor = SlateCard
        )
    }
}

@Composable
fun HeaderBlock(
    isPlaying: Boolean,
    sleepTimerMs: Long,
    onStopTimer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "healing hrtz",
                fontSize = 28.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                color = EmeraldGreen,
                letterSpacing = 2.sp
            )
            Text(
                text = "resonant frequency generator",
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                color = MutedText,
                letterSpacing = 1.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sleepTimerMs > 0) {
                val secRemaining = (sleepTimerMs / 1000) % 60
                val minRemaining = (sleepTimerMs / 1000) / 60
                val timerString = String.format(Locale.US, "%02d:%02d", minRemaining, secRemaining)

                AssistChip(
                    onClick = onStopTimer,
                    label = { Text(timerString, color = SoftCopper) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop sleep timer",
                            tint = SoftCopper,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    border = BorderStroke(1.dp, SoftCopper.copy(alpha = 0.4f))
                )
            }

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) MintGlow else Color.Gray.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
fun ResonanceOscilloscopeCanvas(
    isPlaying: Boolean,
    leftFreq: Float,
    rightFreq: Float,
    thirdFreq: Float = 0f,
    isBinaural: Boolean,
    isThird: Boolean = false,
    vowel: VowelShape,
    waveShape: WaveShape
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OscillatorOscilloscope")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseOffset"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "oscillatorPulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val midY = height / 2f
                val pathLeft = Path()
                val pathRight = Path()
                val pathThird = Path()

                val resolution = 300
                val activeMultiplier = if (isPlaying) 1.0f else 0.05f
                val leftAmp = 35.dp.toPx() * activeMultiplier * pulseScale
                val rightAmp = 35.dp.toPx() * activeMultiplier * pulseScale
                val thirdAmp = 30.dp.toPx() * activeMultiplier * pulseScale

                val freqFactorLeft = (0.2f + ((kotlin.math.log10(leftFreq.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)) + 2f) / 6.7f) * 3.8f)
                val freqFactorRight = (0.2f + ((kotlin.math.log10(rightFreq.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)) + 2f) / 6.7f) * 3.8f)
                val freqFactorThird = (0.2f + ((kotlin.math.log10(thirdFreq.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)) + 2f) / 6.7f) * 3.8f)

                pathLeft.moveTo(0f, midY)
                pathRight.moveTo(0f, midY)
                pathThird.moveTo(0f, midY)

                fun getBaseWave(phase: Double): Double {
                    return when (waveShape) {
                        WaveShape.SINE -> sin(phase)
                        WaveShape.SQUARE -> if (sin(phase) >= 0.0) 0.5 else -0.5
                        WaveShape.TRIANGLE -> {
                            val p = phase / (2.0 * Math.PI)
                            val normalized = p - Math.floor(p + 0.5)
                            4.0 * Math.abs(normalized) - 1.0
                        }
                        WaveShape.SAWTOOTH -> {
                            val p = phase / (2.0 * Math.PI)
                            val normalized = p - Math.floor(p)
                            2.0 * normalized - 1.0
                        }
                    }
                }

                fun getVowelVal(phase: Double): Double {
                    return when (vowel) {
                        VowelShape.PURE_SINE -> getBaseWave(phase)
                        VowelShape.EEE -> {
                            (getBaseWave(phase) +
                             0.15 * getBaseWave(2.0 * phase) +
                             0.25 * getBaseWave(3.0 * phase) +
                             0.45 * getBaseWave(4.0 * phase) +
                             0.55 * getBaseWave(5.0 * phase)) / 2.4
                        }
                        VowelShape.AYE -> {
                            (getBaseWave(phase) +
                             0.5 * getBaseWave(2.0 * phase) +
                             0.4 * getBaseWave(3.0 * phase) +
                             0.3 * getBaseWave(4.0 * phase) +
                             0.2 * getBaseWave(5.0 * phase)) / 2.4
                        }
                        VowelShape.EY -> {
                            (getBaseWave(phase) +
                             0.3 * getBaseWave(2.0 * phase) +
                             0.5 * getBaseWave(3.0 * phase) +
                             0.25 * getBaseWave(4.0 * phase)) / 2.05
                        }
                        VowelShape.AH -> {
                            (getBaseWave(phase) +
                             0.65 * getBaseWave(2.0 * phase) +
                             0.45 * getBaseWave(3.0 * phase) +
                             0.15 * getBaseWave(4.0 * phase)) / 2.25
                        }
                        VowelShape.OH -> {
                            (getBaseWave(phase) +
                             0.55 * getBaseWave(2.0 * phase) +
                             0.15 * getBaseWave(3.0 * phase)) / 1.7
                        }
                        VowelShape.OOO -> {
                            (getBaseWave(phase) +
                             0.15 * getBaseWave(2.0 * phase) +
                             0.05 * getBaseWave(3.0 * phase)) / 1.2
                        }
                        VowelShape.HUH -> {
                            (getBaseWave(phase) +
                             0.3 * getBaseWave(2.0 * phase) +
                             0.3 * getBaseWave(3.0 * phase) +
                             0.15 * getBaseWave(4.0 * phase)) / 1.75
                        }
                    }
                }

                for (x in 0..resolution) {
                    val px = x * (width / resolution)
                    val progress = x.toDouble() / resolution

                    val angleOffsetLeft = (progress * 2.0 * Math.PI * 5 * freqFactorLeft) + phaseOffset
                    val leftValue = getVowelVal(angleOffsetLeft)
                    val ly = midY + (leftAmp * leftValue).toFloat()
                    pathLeft.lineTo(px, ly)

                    if (isBinaural) {
                        val angleOffsetRight = (progress * 2.0 * Math.PI * 5 * freqFactorRight) - phaseOffset
                        val rightValue = getVowelVal(angleOffsetRight)
                        val ry = midY + (rightAmp * rightValue).toFloat()
                        pathRight.lineTo(px, ry)
                    }

                    if (isThird) {
                        val angleOffsetThird = (progress * 2.0 * Math.PI * 5 * freqFactorThird) + phaseOffset * 0.7
                        val thirdValue = getVowelVal(angleOffsetThird)
                        val ty = midY + (thirdAmp * thirdValue).toFloat()
                        pathThird.lineTo(px, ty)
                    }
                }

                if (isBinaural) {
                    drawPath(
                        path = pathLeft,
                        color = EmeraldGreen.copy(alpha = 0.8f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawPath(
                        path = pathRight,
                        color = SoftLavender.copy(alpha = 0.7f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                } else {
                    drawPath(
                        path = pathLeft,
                        color = EmeraldGreen,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                if (isThird) {
                    drawPath(
                        path = pathThird,
                        color = GoldChakra.copy(alpha = 0.75f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "L: ${String.format(Locale.US, "%.2f", leftFreq)} Hz",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold
                    )
                    if (isThird) {
                        Text(
                            text = "3rd: ${String.format(Locale.US, "%.2f", thirdFreq)} Hz",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = GoldChakra,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isBinaural) {
                        val difference = Math.abs(leftFreq - rightFreq)
                        Text(
                            text = "Beat: ${String.format(Locale.US, "%.2f", difference)} Hz",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isThird) SoftCopper else GoldChakra,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "R: ${String.format(Locale.US, "%.2f", rightFreq)} Hz",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SoftLavender,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = if (isPlaying) "RESONANT EQUILIBRIUM ACTIVE" else "CONSOLE SILENT",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    color = if (isPlaying) MintGlow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun PlaybackControlPanel(
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    isBinaural: Boolean,
    onBinauralToggle: (Boolean) -> Unit,
    isThirdEnabled: Boolean,
    onThirdToggle: (Boolean) -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    sleepTimerMs: Long,
    onStartSleepTimer: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (isPlaying) listOf(SoftRose, SoftCopper) else listOf(EmeraldGreen, MintGlow)
                            ),
                            shape = CircleShape
                        )
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle playback",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Binaural Beats",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White
                        )
                        Switch(
                            checked = isBinaural,
                            onCheckedChange = onBinauralToggle,
                            modifier = Modifier.testTag("binaural_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldGreen,
                                checkedTrackColor = EmeraldGreen.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "3rd Frequency",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isThirdEnabled) GoldChakra else Color.White
                        )
                        Switch(
                            checked = isThirdEnabled,
                            onCheckedChange = onThirdToggle,
                            modifier = Modifier.testTag("third_freq_quick_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldChakra,
                                checkedTrackColor = GoldChakra.copy(alpha = 0.35f)
                            )
                        )
                    }
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (volume < 0.01) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = "Volume Icon",
                    tint = EmeraldGreen,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Volume", fontSize = 12.sp, color = MutedText)
                        Text("${(volume * 100).toInt()}%", fontSize = 12.sp, color = EmeraldGreen, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.testTag("master_volume_slider"),
                        colors = SliderDefaults.colors(
                            activeTrackColor = EmeraldGreen,
                            thumbColor = EmeraldGreen
                        )
                    )
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sleep Timer",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val quickTimerOptions = listOf(
                        1 to "1 Min",
                        3 to "3 Min",
                        5 to "5 Min",
                        15 to "15 Min",
                        30 to "30 Min",
                        45 to "45 Min",
                        60 to "1 Hour"
                    )
                    items(quickTimerOptions) { (mins, label) ->
                        val isSelected = sleepTimerMs > 0 && Math.round(sleepTimerMs / 60000.0).toInt() == mins
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SoftCopper.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.03f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) SoftCopper else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onStartSleepTimer(mins) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (isSelected) SoftCopper else Color.White.copy(alpha = 0.8f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WaveShapeSelectionPanel(
    waveShape: WaveShape,
    onWaveShapeSelected: (WaveShape) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Oscillator Wave Carrier",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WaveShape.values().forEach { ws ->
                    val isSelected = waveShape == ws
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) EmeraldGreen else Color.White.copy(alpha = 0.03f))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) EmeraldGreen else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onWaveShapeSelected(ws) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ws.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }
            Text(
                text = "Sine (Pure), Square (Buzzy), Triangle (Warm), Sawtooth (Bright).",
                fontSize = 10.sp,
                color = MutedText
            )
        }
    }
}

@Composable
fun ResonatorFrequencyPanel(
    leftFreq: Float,
    onLeftFreqChange: (Float) -> Unit,
    onSavePresetClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resonator Frequency",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(
                    onClick = onSavePresetClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .testTag("save_preset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save custom preset",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val quickJumps = listOf(
                    0.01f to "0.01 Hz (Infra)",
                    0.1f to "0.1 Hz",
                    7.83f to "7.83 Hz (Schumann)",
                    432f to "432 Hz",
                    528f to "528 Hz",
                    1000f to "1 kHz",
                    10000f to "10 kHz",
                    20000f to "20 kHz (Inaudible)",
                    35000f to "35 kHz",
                    50000f to "50 kHz (Max)"
                )
                items(quickJumps) { (freq, label) ->
                    val isSelected = Math.abs(leftFreq - freq) < 0.005f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) EmeraldGreen.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.04f))
                            .border(1.dp, if (isSelected) EmeraldGreen else Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .clickable { onLeftFreqChange(freq) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = if (isSelected) EmeraldGreen else Color.White.copy(alpha = 0.8f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            FrequencyInputRow(
                title = "Primary Resonator Pitch",
                freqValue = leftFreq,
                onFreqChange = onLeftFreqChange,
                tint = EmeraldGreen,
                inputTag = "left_freq_slider"
            )
        }
    }
}

@Composable
fun BinauralBeatPanel(
    isBinaural: Boolean,
    rightFreq: Float,
    onRightFreqChange: (Float) -> Unit
) {
    AnimatedVisibility(
        visible = isBinaural,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Binaural Right Channel Frequency",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SoftLavender
                )

                FrequencyInputRow(
                    title = "Secondary Right Channel",
                    freqValue = rightFreq,
                    onFreqChange = onRightFreqChange,
                    tint = SoftLavender,
                    inputTag = "right_freq_slider"
                )
            }
        }
    }
}

@Composable
fun ThirdFrequencyPanel(
    isThirdEnabled: Boolean,
    onThirdToggle: (Boolean) -> Unit,
    thirdFreq: Float,
    onThirdFreqChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Third Frequency Channel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldChakra
                        )
                        if (isThirdEnabled) {
                            Surface(
                                color = GoldChakra.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, GoldChakra.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldChakra,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Plays centered through both ears / non-headphone stereo",
                        fontSize = 11.sp,
                        color = MutedText
                    )
                }

                Switch(
                    checked = isThirdEnabled,
                    onCheckedChange = onThirdToggle,
                    modifier = Modifier.testTag("third_frequency_panel_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldChakra,
                        checkedTrackColor = GoldChakra.copy(alpha = 0.35f)
                    )
                )
            }

            AnimatedVisibility(
                visible = isThirdEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Divider(color = Color.White.copy(alpha = 0.06f))

                    Text(
                        text = "Planetary & Cosmic Quick Select:",
                        fontSize = 12.sp,
                        color = MutedText,
                        fontWeight = FontWeight.Medium
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val planets = listOf(
                            "Sun" to 126.22f,
                            "Earth" to 136.10f,
                            "Pluto" to 140.25f,
                            "Mercury" to 141.27f,
                            "Mars" to 144.72f,
                            "Saturn" to 147.85f,
                            "Jupiter" to 183.58f,
                            "Uranus" to 207.36f,
                            "Moon" to 210.42f,
                            "Neptune" to 211.44f,
                            "Venus" to 221.23f,
                            "Schumann" to 7.83f,
                            "432 Hz" to 432.00f,
                            "528 Hz" to 528.00f
                        )
                        items(planets) { (planetName, planetHz) ->
                            val isSelected = Math.abs(thirdFreq - planetHz) < 0.05f
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) GoldChakra.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.04f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) GoldChakra else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.clickable { onThirdFreqChange(planetHz) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = planetName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) GoldChakra else Color.White
                                    )
                                    Text(
                                        text = "${planetHz} Hz",
                                        fontSize = 9.sp,
                                        color = if (isSelected) GoldChakra.copy(alpha = 0.9f) else MutedText,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    FrequencyInputRow(
                        title = "Third Resonator Pitch",
                        freqValue = thirdFreq,
                        onFreqChange = onThirdFreqChange,
                        tint = GoldChakra,
                        inputTag = "third_freq_slider"
                    )
                }
            }
        }
    }
}

@Composable
fun VowelShapeSelectionPanel(
    vowel: VowelShape,
    onVowelSelected: (VowelShape) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Vocal Formant Shaper",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = vowel.label,
                    fontSize = 11.sp,
                    color = EmeraldGreen,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VowelShape.values().forEach { v ->
                    val isSelected = vowel == v
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) EmeraldGreen else Color.White.copy(alpha = 0.03f))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) EmeraldGreen else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onVowelSelected(v) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (v) {
                                VowelShape.PURE_SINE -> "Pure Sine"
                                VowelShape.EEE -> "EEE (Crown)"
                                VowelShape.AYE -> "AYE (3rd Eye)"
                                VowelShape.EY -> "EY (Throat)"
                                VowelShape.AH -> "AH (Heart)"
                                VowelShape.OH -> "OH (Plexus)"
                                VowelShape.OOO -> "OOO (Sacral)"
                                VowelShape.HUH -> "HUH (Root)"
                            },
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }
            Text(
                text = "Mimics vocal overtone formants for focused meditation.",
                fontSize = 10.sp,
                color = MutedText
            )
        }
    }
}

fun freqToLogPosition(freq: Float): Float {
    val minLog = kotlin.math.log10(MIN_FREQUENCY)
    val maxLog = kotlin.math.log10(MAX_FREQUENCY)
    val logVal = kotlin.math.log10(freq.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY))
    return ((logVal - minLog) / (maxLog - minLog)).coerceIn(0f, 1f)
}

fun logPositionToFreq(pos: Float): Float {
    val minLog = kotlin.math.log10(MIN_FREQUENCY).toDouble()
    val maxLog = kotlin.math.log10(MAX_FREQUENCY).toDouble()
    val logVal = minLog + pos.coerceIn(0f, 1f) * (maxLog - minLog)
    val rawFreq = Math.pow(10.0, logVal).toFloat()
    val rounded = when {
        rawFreq < 1f -> (Math.round(rawFreq * 100f) / 100f)
        rawFreq < 10f -> (Math.round(rawFreq * 10f) / 10f)
        rawFreq < 1000f -> Math.round(rawFreq).toFloat()
        rawFreq < 10000f -> (Math.round(rawFreq / 10f) * 10f).toFloat()
        else -> (Math.round(rawFreq / 50f) * 50f).toFloat()
    }
    return rounded.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
}

fun formatFreqDisplay(freq: Float): String {
    return if (freq < 100f) {
        String.format(Locale.US, "%.2f", freq)
    } else if (freq < 10000f) {
        String.format(Locale.US, "%.1f", freq)
    } else {
        if (freq == freq.toInt().toFloat()) {
            String.format(Locale.US, "%,d", freq.toInt())
        } else {
            String.format(Locale.US, "%,.1f", freq)
        }
    }
}

fun getAdaptiveStep(freq: Float): Float {
    return when {
        freq < 0.1f -> 0.01f
        freq < 1f -> 0.05f
        freq < 10f -> 0.5f
        freq < 100f -> 1f
        freq < 1000f -> 10f
        freq < 10000f -> 100f
        else -> 1000f
    }
}

@Composable
fun FrequencyInputRow(
    title: String,
    freqValue: Float,
    onFreqChange: (Float) -> Unit,
    tint: Color,
    inputTag: String
) {
    var textInput by remember { mutableStateOf(formatFreqDisplay(freqValue)) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(freqValue) {
        if (!isFocused) {
            textInput = formatFreqDisplay(freqValue)
        }
    }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Title and Large Digital Frequency Readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = MutedText,
                fontWeight = FontWeight.Medium
            )

            Surface(
                color = tint.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "${formatFreqDisplay(freqValue)} Hz",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = tint,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // 2. Direct Typing Numeric Input Field & Stepper Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val step = getAdaptiveStep(freqValue)

            // Minus Stepper Button (-)
            FilledIconButton(
                onClick = {
                    val newFreq = (freqValue - step).coerceAtLeast(MIN_FREQUENCY)
                    onFreqChange(newFreq)
                    textInput = formatFreqDisplay(newFreq)
                },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = tint
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("-", color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            // Direct Typeable Frequency TextField
            OutlinedTextField(
                value = textInput,
                onValueChange = { input ->
                    textInput = input
                    val parsed = input.replace(",", "").toFloatOrNull()
                    if (parsed != null && parsed in MIN_FREQUENCY..MAX_FREQUENCY) {
                        onFreqChange(parsed)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
                    .testTag("${inputTag}_text_field"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val parsed = textInput.replace(",", "").toFloatOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
                            onFreqChange(clamped)
                            textInput = formatFreqDisplay(clamped)
                        } else {
                            textInput = formatFreqDisplay(freqValue)
                        }
                        focusManager.clearFocus()
                    }
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    color = Color.White
                ),
                placeholder = {
                    Text(
                        text = "Enter Hz",
                        fontSize = 13.sp,
                        color = MutedText,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                suffix = {
                    Text(
                        text = "Hz",
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                trailingIcon = {
                    if (isFocused) {
                        IconButton(
                            onClick = {
                                val parsed = textInput.replace(",", "").toFloatOrNull()
                                if (parsed != null) {
                                    val clamped = parsed.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
                                    onFreqChange(clamped)
                                    textInput = formatFreqDisplay(clamped)
                                } else {
                                    textInput = formatFreqDisplay(freqValue)
                                }
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Apply Frequency",
                                tint = MintGlow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = tint,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White.copy(alpha = 0.07f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            )

            // Plus Stepper Button (+)
            FilledIconButton(
                onClick = {
                    val newFreq = (freqValue + step).coerceAtMost(MAX_FREQUENCY)
                    onFreqChange(newFreq)
                    textInput = formatFreqDisplay(newFreq)
                },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = tint
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("+", color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 3. Logarithmic Continuous Slider with Boundary Indicators
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Slider(
                value = freqToLogPosition(freqValue),
                onValueChange = { pos ->
                    val newFreq = logPositionToFreq(pos)
                    onFreqChange(newFreq)
                    if (!isFocused) {
                        textInput = formatFreqDisplay(newFreq)
                    }
                },
                valueRange = 0f..1f,
                modifier = Modifier.testTag(inputTag),
                colors = SliderDefaults.colors(
                    activeTrackColor = tint,
                    thumbColor = tint
                )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.01 Hz", fontSize = 10.sp, color = MutedText, fontFamily = FontFamily.Monospace)
                Text("50,000 Hz", fontSize = 10.sp, color = MutedText, fontFamily = FontFamily.Monospace)
            }
        }

        // 4. Quick Micro/Macro Tuning Chips (Horizontal Scroll)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val stepsList = listOf(-10000f, -1000f, -100f, -10f, -1f, 1f, 10f, 100f, 1000f, 10000f)
            items(stepsList) { stepVal ->
                val label = when {
                    stepVal >= 1000f -> "+${(stepVal / 1000).toInt()}k"
                    stepVal <= -1000f -> "${(stepVal / 1000).toInt()}k"
                    stepVal > 0 -> if (stepVal < 1f) "+${String.format(Locale.US, "%.1f", stepVal)}" else "+${stepVal.toInt()}"
                    else -> if (stepVal > -1f) String.format(Locale.US, "%.1f", stepVal) else stepVal.toInt().toString()
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.clickable {
                        val newFreq = (freqValue + stepVal).coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
                        onFreqChange(newFreq)
                        textInput = formatFreqDisplay(newFreq)
                    }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun FrequencyRampingPanel(
    steps: List<RampingStep>,
    isActive: Boolean,
    currentIndex: Int,
    secondsRemaining: Int,
    onAddStep: (Int, Float, Float) -> Unit,
    onRemoveStep: (Int) -> Unit,
    onClearAll: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    currentLeftFreq: Float,
    currentRightFreq: Float,
    savedSequences: List<SavedRampingSequence>,
    onSaveSequence: (String) -> Unit,
    onLoadSequence: (SavedRampingSequence) -> Unit,
    onDeleteSequence: (SavedRampingSequence) -> Unit
) {
    var stepDurationMins by remember { mutableStateOf(1) }
    var inputLeftFreq by remember { mutableStateOf("432.00") }
    var inputRightFreq by remember { mutableStateOf("432.00") }
    var showSaveSequenceDialog by remember { mutableStateOf(false) }
    var sequenceNameInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, contentDescription = "Ramping", tint = SoftCopper)
                    Column {
                        Text("Frequency Sequence Ramping", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Cycle chosen frequencies automatically", fontSize = 11.sp, color = MutedText)
                    }
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .background(SoftCopper.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Active", color = SoftCopper, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            if (isActive && currentIndex in steps.indices) {
                val currentStep = steps[currentIndex]
                val mins = secondsRemaining / 60
                val secs = secondsRemaining % 60
                val formatSecs = String.format(Locale.US, "%02d", secs)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SoftCopper.copy(alpha = 0.1f))
                        .border(1.dp, SoftCopper, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Now Tuning: Step ${currentIndex + 1} of ${steps.size}",
                            color = SoftCopper,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "L: ${String.format(Locale.US, "%.2f", currentStep.leftFreq)} Hz | R: ${String.format(Locale.US, "%.2f", currentStep.rightFreq)} Hz",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "$mins:$formatSecs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = SoftCopper,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Steps Queue
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { idx, step ->
                    val isCurrent = isActive && idx == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) SoftCopper.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.02f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isCurrent) SoftCopper else Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text((idx + 1).toString(), fontSize = 11.sp, color = if (isCurrent) SlateDark else Color.White, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(
                                    text = "Resonance: ${String.format(Locale.US, "%.2f", step.leftFreq)} Hz" + if (step.leftFreq != step.rightFreq) " / ${String.format(Locale.US, "%.2f", step.rightFreq)} Hz" else "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text("Duration: ${step.durationMins} min", fontSize = 10.sp, color = MutedText)
                            }
                        }

                        if (!isActive) {
                            IconButton(onClick = { onRemoveStep(idx) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = SoftRose, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (steps.isEmpty()) {
                Text(
                    text = "No sequence steps added. Design your cycle below.",
                    fontSize = 11.sp,
                    color = MutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Saved Sequences Section
            if (savedSequences.isNotEmpty()) {
                Divider(color = Color.White.copy(alpha = 0.05f))
                Text("Saved Ramping Sequences", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SoftCopper)

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(savedSequences) { seq ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(1.dp, SoftCopper.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable { onLoadSequence(seq) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(seq.name, fontSize = 11.sp, color = Color.White)
                                IconButton(
                                    onClick = { onDeleteSequence(seq) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Delete sequence", tint = SoftRose, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Sequence controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isActive) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftRose),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Stop Sequence", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (steps.isNotEmpty()) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftCopper),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Start Sequence", color = SlateDark, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showSaveSequenceDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save Sequence", color = Color.White, fontSize = 11.sp)
                    }
                }

                if (!isActive && steps.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearAll,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Clear Queue", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            // Builder Form (Only visible if not active)
            if (!isActive) {
                Divider(color = Color.White.copy(alpha = 0.05f))
                Text("Add Custom Sequence Step", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputLeftFreq,
                        onValueChange = { inputLeftFreq = it },
                        label = { Text("L (Hz)", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SoftCopper,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedLabelColor = MutedText,
                            focusedLabelColor = SoftCopper
                        )
                    )

                    OutlinedTextField(
                        value = inputRightFreq,
                        onValueChange = { inputRightFreq = it },
                        label = { Text("R (Hz)", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SoftCopper,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedLabelColor = MutedText,
                            focusedLabelColor = SoftCopper
                        )
                    )

                    IconButton(
                        onClick = {
                            inputLeftFreq = String.format(Locale.US, "%.2f", currentLeftFreq)
                            inputRightFreq = String.format(Locale.US, "%.2f", currentRightFreq)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(Icons.Default.Check, "Use Current", tint = SoftCopper, modifier = Modifier.size(16.dp))
                    }
                }

                // Duration Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Step Duration", fontSize = 11.sp, color = MutedText)
                        Text("$stepDurationMins min", fontSize = 11.sp, color = SoftCopper, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = stepDurationMins.toFloat(),
                        onValueChange = { stepDurationMins = it.toInt() },
                        valueRange = 1f..5f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = SoftCopper,
                            thumbColor = SoftCopper
                        )
                    )
                }

                Button(
                    onClick = {
                        val left = inputLeftFreq.toFloatOrNull() ?: 432f
                        val right = inputRightFreq.toFloatOrNull() ?: 432f
                        onAddStep(stepDurationMins, left, right)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Step to Sequence", color = Color.White)
                }
            }
        }
    }

    if (showSaveSequenceDialog) {
        AlertDialog(
            onDismissRequest = { showSaveSequenceDialog = false },
            title = { Text("Save Ramping Sequence") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a name for this multi-step sequence to recall it anytime.")
                    TextField(
                        value = sequenceNameInput,
                        onValueChange = { sequenceNameInput = it },
                        placeholder = { Text("e.g. 15m Deep Relaxation") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveSequence(sequenceNameInput)
                        sequenceNameInput = ""
                        showSaveSequenceDialog = false
                    }
                ) {
                    Text("Save", color = SoftCopper)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveSequenceDialog = false }) {
                    Text("Cancel", color = MutedText)
                }
            },
            containerColor = SlateCard
        )
    }
}

@Composable
fun PresetSanctuaryPanel(
    presets: List<FrequencyPreset>,
    onPresetSelect: (FrequencyPreset) -> Unit,
    onPresetDelete: (FrequencyPreset) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Preset Sanctuary",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Divider(color = Color.White.copy(alpha = 0.05f))

            val unexploredPresets = presets.filter { !it.isCustom && it.name.contains("Unexplored") }
            val organPresets = presets.filter { !it.isCustom && it.name.contains("Organ") }
            val ultrasonicPresets = presets.filter { !it.isCustom && (it.name.contains("Ultrasonic") || it.name.contains("Inaudible") || it.frequency1 >= 15000f) }
            val experimentalPresets = presets.filter { !it.isCustom && it.name.contains("Experimental") }
            val ancientPresets = presets.filter { !it.isCustom && it.name.contains("Ancient") }
            val planetaryPresets = presets.filter { !it.isCustom && it.name.contains("Planetary") }
            val notesPresets = presets.filter { !it.isCustom && it.name.startsWith("Note") }
            val chakraPresets = presets.filter { !it.isCustom && it.name.contains("Chakra") }
            val specialPresets = presets.filter { !it.isCustom && (it.name.contains("Relief") || it.name.contains("Repair") || it.name.contains("Ghost") || it.name.contains("Dangerous") || it.name.contains("Cardiac") || it.name.contains("Visual") || it.name.contains("Cleanse") || it.name.contains("Sub-Delta") || it.name.contains("Autonomic")) }
            val binauralPresets = presets.filter { !it.isCustom && it.name.contains("Waves") }
            val customPresets = presets.filter { it.isCustom }

            if (unexploredPresets.isNotEmpty()) {
                PresetGroup(title = "Unexplored Frequencies", items = unexploredPresets, onSelect = onPresetSelect, onDelete = null)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (ultrasonicPresets.isNotEmpty()) {
                PresetGroup(title = "Super High & Ultrasonic (Inaudible)", items = ultrasonicPresets, onSelect = onPresetSelect, onDelete = null)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (organPresets.isNotEmpty()) {
                PresetGroup(title = "Organ Frequencies", items = organPresets, onSelect = onPresetSelect, onDelete = null)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (experimentalPresets.isNotEmpty()) {
                PresetGroup(title = "Experimental Frequencies", items = experimentalPresets, onSelect = onPresetSelect, onDelete = null)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (ancientPresets.isNotEmpty()) {
                PresetGroup(title = "Ancient Frequencies", items = ancientPresets, onSelect = onPresetSelect, onDelete = null)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (planetaryPresets.isNotEmpty()) {
                PresetGroup(title = "Planetary Frequencies", items = planetaryPresets, onSelect = onPresetSelect, onDelete = null)
                Spacer(modifier = Modifier.height(4.dp))
            }
            PresetGroup(title = "Chakras & Solfeggio (Auto Vocal Vowel)", items = chakraPresets, onSelect = onPresetSelect, onDelete = null)
            Spacer(modifier = Modifier.height(4.dp))
            PresetGroup(title = "Special Frequencies", items = specialPresets, onSelect = onPresetSelect, onDelete = null)
            Spacer(modifier = Modifier.height(4.dp))
            PresetGroup(title = "Binaural Beat Waves", items = binauralPresets, onSelect = onPresetSelect, onDelete = null)
            Spacer(modifier = Modifier.height(4.dp))
            PresetGroup(title = "Musical Tuned Notes", items = notesPresets, onSelect = onPresetSelect, onDelete = null)

            if (customPresets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                PresetGroup(title = "Custom Resonances", items = customPresets, onSelect = onPresetSelect, onDelete = onPresetDelete)
            }
        }
    }
}

@Composable
fun PresetGroup(
    title: String,
    items: List<FrequencyPreset>,
    onSelect: (FrequencyPreset) -> Unit,
    onDelete: ((FrequencyPreset) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen, letterSpacing = 1.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { item ->
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(item) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column {
                            Text(
                                text = item.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            )
                            Text(
                                text = if (item.isBinaural) {
                                    "Binaural: ${formatFreqDisplay(item.frequency1)}Hz / ${formatFreqDisplay(item.frequency2)}Hz"
                                } else {
                                    "${formatFreqDisplay(item.frequency1)} Hz"
                                },
                                fontSize = 9.sp,
                                color = MutedText
                            )
                        }

                        if (onDelete != null) {
                            IconButton(
                                onClick = { onDelete(item) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete preset",
                                    tint = SoftRose.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuperHighFrequenciesPanel(
    currentFreq: Float,
    onSelectFreq: (Float) -> Unit
) {
    val ultrasonicList = listOf(
        "Cellular Cleanse" to 15000f,
        "Acoustic Ceiling" to 20000f,
        "Bio-Stimulation" to 25000f,
        "Cavitation Resonance" to 30000f,
        "Harmonic Ultrasound" to 35000f,
        "Gamma Ultrasonic" to 40000f,
        "Membrane Wave" to 45000f,
        "50 kHz Peak Ultrasonic" to 50000f
    )
    val isUltrasonicActive = currentFreq >= 20000f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Ultrasonic Frequencies",
                        tint = CyanAura,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Super High & Inaudible Range",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "15,000 Hz – 50,000 Hz Ultrasound Resonances",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                }

                if (isUltrasonicActive) {
                    Surface(
                        color = CyanAura.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, CyanAura),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "INAUDIBLE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CyanAura,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "Acoustic frequencies from 20 kHz to 50 kHz extend past the threshold of human hearing into ultrasonic vibrational fields, providing gentle micro-acoustic cellular resonance.",
                    fontSize = 11.sp,
                    color = MutedText,
                    lineHeight = 15.sp
                )
            }

            val chunked = ultrasonicList.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chunked.forEach { rowPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPair.forEach { (name, hz) ->
                            val isSelected = Math.abs(currentFreq - hz) < 1f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) CyanAura.copy(alpha = 0.2f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CyanAura else Color.White.copy(alpha = 0.07f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelectFreq(hz) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) CyanAura else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${formatFreqDisplay(hz)} Hz",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isSelected) CyanAura.copy(alpha = 0.9f) else EmeraldGreen
                                        )
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(CyanAura)
                                        )
                                    }
                                }
                            }
                        }
                        if (rowPair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrganFrequenciesPanel(
    currentFreq: Float,
    onOrganSelect: (String, Float) -> Unit
) {
    val organList = listOf(
        "Adrenals & Thyroid" to 492.80f,
        "Bladder" to 352.00f,
        "Blood" to 321.90f,
        "Bone" to 418.30f,
        "Brain" to 315.80f,
        "Colon" to 176.00f,
        "Fat Cells" to 295.80f,
        "Gall Bladder" to 164.30f,
        "Intestines" to 281.00f,
        "Kidneys" to 319.88f,
        "Liver" to 317.83f,
        "Lungs" to 220.00f,
        "Muscles" to 324.00f,
        "Pancreas" to 117.30f,
        "Stomach" to 110.00f
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Organ Frequencies",
                        tint = SoftRose,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Organ Frequencies",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Bio-Resonant Tissue & Somatic Harmonics",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                organList.chunked(2).forEach { rowPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for ((name, hz) in rowPair) {
                            val isSelected = Math.abs(currentFreq - hz) < 0.05f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) SoftRose.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.03f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) SoftRose else Color.White.copy(alpha = 0.07f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onOrganSelect(name, hz) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) SoftRose else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.2f", hz)} Hz",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isSelected) SoftRose.copy(alpha = 0.9f) else EmeraldGreen
                                        )
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(SoftRose)
                                        )
                                    }
                                }
                            }
                        }
                        if (rowPair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrainwaveChartPanel(
    leftFreq: Float,
    rightFreq: Float,
    isBinaural: Boolean
) {
    val activeFreq = if (isBinaural) Math.abs(leftFreq - rightFreq) else leftFreq

    val activeBrainwave = when {
        activeFreq >= 0.5f && activeFreq < 4.0f -> "Delta"
        activeFreq >= 4.0f && activeFreq < 8.0f -> "Theta"
        activeFreq >= 8.0f && activeFreq < 12.0f -> "Alpha"
        activeFreq >= 12.0f && activeFreq <= 30.0f -> "Beta"
        activeFreq >= 20000.0f -> "Ultrasonic"
        else -> "Carrier"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Waves, contentDescription = "Waves", tint = GoldChakra)
                Column {
                    Text("Cognitive Brainwave Spectrum", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = if (isBinaural) {
                            "Measuring Binaural Differential Beat: ${String.format(Locale.US, "%.2f", activeFreq)} Hz"
                        } else {
                            "Measuring Pure Frequency Resonance: ${String.format(Locale.US, "%.2f", activeFreq)} Hz"
                        },
                        fontSize = 11.sp,
                        color = MutedText
                    )
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            val bands = listOf(
                "Delta" to "0.5 - 4 Hz" to "Deep Sleep, Cell Healing",
                "Theta" to "4 - 8 Hz" to "Deep Meditation, Lucid Dreams",
                "Alpha" to "8 - 12 Hz" to "Calm Focus, Ambient Flow",
                "Beta" to "12 - 30 Hz" to "Active Concentration, Awareness"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bands.forEach { (meta, desc) ->
                    val (name, range) = meta
                    val isSelected = activeBrainwave == name
                    val highlightColor = when (name) {
                        "Delta" -> SoftLavender
                        "Theta" -> GoldChakra
                        "Alpha" -> EmeraldGreen
                        else -> SoftCopper
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) highlightColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.02f))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) highlightColor else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) highlightColor else Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(range, fontSize = 9.sp, color = MutedText, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text(desc, fontSize = 11.sp, color = MutedText)
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(highlightColor)
                            )
                        }
                    }
                }
            }

            if (activeBrainwave == "Ultrasonic") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanAura.copy(alpha = 0.12f))
                        .border(1.dp, CyanAura, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Super High Ultrasonic Mode Active (${formatFreqDisplay(activeFreq)} Hz)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAura,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Emitting inaudible ultrasonic sound beyond 20 kHz. Generates micro-vibrational energetic fields with no audible pitch.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (activeBrainwave == "Carrier") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MintGlow.copy(alpha = 0.1f))
                        .border(1.dp, MintGlow, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Solfeggio Carrier Mode Active: High-frequency carrier tones resonate cellular membranes directly.",
                        fontSize = 11.sp,
                        color = MintGlow,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SweepControllerPanel(
    isSweepActive: Boolean,
    sweepStart: Float,
    sweepEnd: Float,
    sweepDuration: Float,
    onConfigure: (Float, Float, Float) -> Unit,
    onToggleSweep: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Frequency Range Sweep", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Gradually morph pitch between two limits", fontSize = 11.sp, color = MutedText)
                }

                Button(
                    onClick = onToggleSweep,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSweepActive) SoftRose else EmeraldGreen
                    ),
                    modifier = Modifier.testTag("toggle_sweep_button")
                ) {
                    Text(if (isSweepActive) "Stop Sweep" else "Start Sweep", color = Color.Black, fontSize = 12.sp)
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            if (!isSweepActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start Freq (Hz)", fontSize = 11.sp, color = MutedText)
                        Slider(
                            value = freqToLogPosition(sweepStart),
                            onValueChange = { onConfigure(logPositionToFreq(it), sweepEnd, sweepDuration) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(activeTrackColor = EmeraldGreen)
                        )
                        Text("${formatFreqDisplay(sweepStart)} Hz", fontSize = 12.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("End Freq (Hz)", fontSize = 11.sp, color = MutedText)
                        Slider(
                            value = freqToLogPosition(sweepEnd),
                            onValueChange = { onConfigure(sweepStart, logPositionToFreq(it), sweepDuration) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(activeTrackColor = SoftLavender)
                        )
                        Text("${formatFreqDisplay(sweepEnd)} Hz", fontSize = 12.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sweep Duration", fontSize = 11.sp, color = MutedText)
                        Text("${sweepDuration.toInt()} Seconds", fontSize = 12.sp, color = EmeraldGreen, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = sweepDuration,
                        onValueChange = { onConfigure(sweepStart, sweepEnd, it) },
                        valueRange = 5f..300f,
                        colors = SliderDefaults.colors(activeTrackColor = GoldChakra)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = EmeraldGreen,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "SWEEPING PRESET RESONANCES...",
                            fontSize = 11.sp,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Cycling from ${formatFreqDisplay(sweepStart)}Hz up to ${formatFreqDisplay(sweepEnd)}Hz over ${sweepDuration.toInt()}s.",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SomaticAwakeningPanel(
    onTriggerKoshi: () -> Unit,
    onTriggerTingsha: () -> Unit,
    onTriggerRattle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, GoldChakra.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = "Bells", tint = GoldChakra)
                Column {
                    Text("Somatic Return & Awakening", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("High-frequency signals to gently restore somatic alertness", fontSize = 11.sp, color = MutedText)
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GoldChakra.copy(alpha = 0.04f))
                    .border(1.dp, GoldChakra.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Final minute integration: Gently wiggle your fingers and toes. Rub your palms together to generate somatic heat, and take three deep, sharp inhalations. Tap the bright, high-pitched instruments below to stimulate crisp, grounded alertness.",
                    fontSize = 11.sp,
                    color = GoldChakra,
                    lineHeight = 16.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTriggerKoshi,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.04f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔔", fontSize = 16.sp)
                        Text("Koshi Chimes", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onTriggerTingsha,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.04f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎵", fontSize = 16.sp)
                        Text("Tingsha", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onTriggerRattle,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.04f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🪘", fontSize = 16.sp)
                        Text("Light Rattle", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SessionNotesJournalPanel(
    notes: List<SessionNote>,
    onAddNote: (String, String, String) -> Unit,
    onDeleteNote: (SessionNote) -> Unit
) {
    var titleInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#1E293B") }

    val colorOptions = listOf(
        "#1E293B" to "Slate",
        "#312E81" to "Indigo",
        "#064E3B" to "Forest",
        "#78350F" to "Amber",
        "#831843" to "Rose"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Session Journal & Notes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Record reflections, bodily feelings & insights", fontSize = 11.sp, color = MutedText)
                }
                Icon(Icons.Default.EditNote, contentDescription = null, tint = EmeraldGreen)
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Note Title") },
                placeholder = { Text("e.g. Post 528Hz Meditation Insights") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White
                )
            )

            OutlinedTextField(
                value = contentInput,
                onValueChange = { contentInput = it },
                label = { Text("Reflection & Observations") },
                placeholder = { Text("Describe physical sensations, breathing depth...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Card Theme Color:", fontSize = 11.sp, color = MutedText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorOptions.forEach { (hex, _) ->
                        val isSel = selectedColor == hex
                        val colorVal = try { Color(android.graphics.Color.parseColor(hex)) } catch(e: Exception) { SlateCard }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(colorVal)
                                .border(
                                    width = if (isSel) 2.dp else 1.dp,
                                    color = if (isSel) EmeraldGreen else Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (titleInput.isNotBlank() || contentInput.isNotBlank()) {
                        onAddNote(titleInput, contentInput, selectedColor)
                        titleInput = ""
                        contentInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Journal Note", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            if (notes.isNotEmpty()) {
                Divider(color = Color.White.copy(alpha = 0.05f))
                Text("Saved Reflections (${notes.size})", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    notes.forEach { note ->
                        val cardBg = try { Color(android.graphics.Color.parseColor(note.colorHex)) } catch(e: Exception) { SlateCard }
                        val formattedTime = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(note.timestamp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(note.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    IconButton(
                                        onClick = { onDeleteNote(note) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete note", tint = SoftRose, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(formattedTime, fontSize = 10.sp, color = MutedText, fontFamily = FontFamily.Monospace)
                                if (note.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(note.content, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MindfulnessLogPanel(
    sessions: List<ListeningSession>,
    onClearLogs: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Session Sanctuaries", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Historical log of sound resonance metrics", fontSize = 11.sp, color = MutedText)
                }

                if (sessions.isNotEmpty()) {
                    TextButton(onClick = onClearLogs) {
                        Text("Clear History", color = SoftRose, fontSize = 12.sp)
                    }
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "History is empty. Select a frequency preset and start a meditative flow to record statistics.",
                        fontSize = 11.sp,
                        color = MutedText,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                ) {
                    sessions.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.presetName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                Text(
                                    text = if (s.isBinaural) {
                                        "Stereo Binaural Beat • ${s.frequency1.toInt()}Hz / ${s.frequency2.toInt()}Hz"
                                    } else {
                                        "Mono Pure Frequency • ${s.frequency1.toInt()} Hz"
                                    },
                                    fontSize = 10.sp,
                                    color = MutedText
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                val min = s.durationSeconds / 60
                                val sec = s.durationSeconds % 60
                                val durationStr = if (min > 0) "${min}m ${sec}s" else "${sec}s"

                                Text(durationStr, fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                Text(sdf.format(Date(s.timestamp)), fontSize = 9.sp, color = MutedText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CosmicEqualizerPanel(
    eqBass: Float,
    eqMid: Float,
    eqHigh: Float,
    onBassChange: (Float) -> Unit,
    onMidChange: (Float) -> Unit,
    onHighChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tune, contentDescription = "EQ", tint = EmeraldGreen)
                Column {
                    Text("Harmonic Equalizer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Refine resonance amplitudes across frequency bands", fontSize = 11.sp, color = MutedText)
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bass Boost (Sub-150 Hz)", fontSize = 12.sp, color = Color.White)
                    Text("${Math.round(eqBass * 100)}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = eqBass,
                    onValueChange = onBassChange,
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = EmeraldGreen,
                        thumbColor = EmeraldGreen
                    )
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mid Presence (150 - 1500 Hz)", fontSize = 12.sp, color = Color.White)
                    Text("${Math.round(eqMid * 100)}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = eqMid,
                    onValueChange = onMidChange,
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = EmeraldGreen,
                        thumbColor = EmeraldGreen
                    )
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("High Brilliance (Above 1500 Hz)", fontSize = 12.sp, color = Color.White)
                    Text("${Math.round(eqHigh * 100)}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = eqHigh,
                    onValueChange = onHighChange,
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = EmeraldGreen,
                        thumbColor = EmeraldGreen
                    )
                )
            }
        }
    }
}

@Composable
fun InstrumentTunerPanel(
    isListening: Boolean,
    tunerResult: TunerResult?,
    rms: Float,
    hasPermission: Boolean,
    onPermissionRequest: () -> Unit,
    onToggleTuner: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Acoustic Instrument Tuner", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Tune string instruments using your microphone", fontSize = 11.sp, color = MutedText)
                }

                Button(
                    onClick = {
                        if (!hasPermission) {
                            onPermissionRequest()
                        } else {
                            onToggleTuner()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) SoftRose else EmeraldGreen
                    ),
                    modifier = Modifier.testTag("toggle_tuner_button")
                ) {
                    Text(
                        text = if (!hasPermission) "Grant Mic" else if (isListening) "Disable Tuner" else "Enable Tuner",
                        color = Color.Black,
                        fontSize = 11.sp
                    )
                }
            }

            Divider(color = Color.White.copy(alpha = 0.05f))

            if (isListening) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Cents Dial Needle
                    val deviation = tunerResult?.deviationCents ?: 0f
                    val animatedDev by animateFloatAsState(targetValue = deviation, animationSpec = tween(150), label = "cents")

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val centerX = width / 2f
                        val bottomY = height - 15.dp.toPx()

                        // Center in-tune line
                        drawLine(
                            color = MintGlow,
                            start = Offset(centerX, 20.dp.toPx()),
                            end = Offset(centerX, height - 20.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )

                        // Needle line
                        val angleRad = (animatedDev / 50.0) * (Math.PI / 3.0) // +/- 60 deg max
                        val needleLen = height * 0.75f
                        val needleX = centerX + (needleLen * sin(angleRad)).toFloat()
                        val needleY = bottomY - (needleLen * cos(angleRad)).toFloat()

                        drawLine(
                            color = if (Math.abs(animatedDev) < 3.0f) MintGlow else SoftRose,
                            start = Offset(centerX, bottomY),
                            end = Offset(needleX, needleY),
                            strokeWidth = 3.dp.toPx()
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (tunerResult != null) {
                            Text(
                                text = tunerResult.noteName,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = if (Math.abs(tunerResult.deviationCents) < 3.0f) MintGlow else Color.White
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.2f", tunerResult.frequency)} Hz (${String.format(Locale.US, "%+.0f", tunerResult.deviationCents)} Cents)",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MutedText
                            )
                        } else {
                            Text(
                                text = "Listening for pitch...",
                                fontSize = 13.sp,
                                color = MutedText,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Acoustic signal analyzer is inactive. Connect instruments or tap Enable to begin audio calibration.",
                    fontSize = 11.sp,
                    color = MutedText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}
