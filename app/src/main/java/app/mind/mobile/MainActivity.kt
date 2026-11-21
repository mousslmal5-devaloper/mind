package app.mind.mobile

import android.os.Bundle
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Gold = Color(0xFFE8C547)
private data class MindPalette(
    val background: Color, val surface: Color, val text: Color,
    val muted: Color, val glass: Color, val glassBorder: Color
)
private val DarkMindPalette = MindPalette(Color(0xFF0D0D0F), Color(0xFF19191D), Color(0xFFF4F4F6), Color(0xFFA2A2AB), Color(0x0EFFFFFF), Color(0x17FFFFFF))
private val LightMindPalette = MindPalette(Color(0xFFF5F5F7), Color(0xFFFFFFFF), Color(0xFF16161A), Color(0xFF6B6B75), Color(0xCCFFFFFF), Color(0x14000000))
private val LocalMindPalette = androidx.compose.runtime.staticCompositionLocalOf { DarkMindPalette }
private val Muted: Color @Composable get() = LocalMindPalette.current.muted
private val Glass: Color @Composable get() = LocalMindPalette.current.glass
private val GlassBorder: Color @Composable get() = LocalMindPalette.current.glassBorder
private val Ink = Color(0xFF0D0D0F)
private fun mindDateKey(timestamp: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(timestamp))

private fun refreshMindWaterWidget(context: Context) {
    val component = ComponentName(context, WaterWidget::class.java)
    val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
    if (ids.isNotEmpty()) {
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(component)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
    }
}

private fun refreshMindHabitWidget(context: Context) {
    val component = ComponentName(context, HabitWidget::class.java)
    val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
    if (ids.isNotEmpty()) {
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(component)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
    }
}

private val CairoFamily = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.cairo_variable, weight = FontWeight.Normal)
)

class MainActivity : AppCompatActivity() {
    var sensorSteps by mutableIntStateOf(0)
    var sensorSupported by mutableStateOf(false)
    var sensorDelta by mutableIntStateOf(0)
    var sensorRevision by mutableIntStateOf(0)
    private var receiverRegistered = false
    private val stepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StepCounterService.ACTION_STEP_UPDATE) return
            val broadcastUid = intent.getStringExtra(StepCounterService.EXTRA_UID)
            val currentUid = runCatching {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    ?.takeIf { it.isEmailVerified }?.uid
            }.getOrNull()
            if (broadcastUid.isNullOrBlank() || broadcastUid != currentUid) return
            sensorSupported = intent.getBooleanExtra(StepCounterService.EXTRA_SUPPORTED, false)
            sensorSteps = intent.getIntExtra(StepCounterService.EXTRA_TOTAL, 0).coerceIn(0, 100000)
            sensorDelta = intent.getIntExtra(StepCounterService.EXTRA_DELTA, 0).coerceIn(0, 100000)
            sensorRevision++
            val ack = Intent(this@MainActivity, StepCounterService::class.java)
                .setAction(StepCounterService.ACTION_ACK)
                .putExtra(StepCounterService.EXTRA_TOTAL, sensorSteps)
                .putExtra(StepCounterService.EXTRA_UID, currentUid)
            startService(ack)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { MindRoot(sensorSteps, sensorSupported, sensorDelta, sensorRevision) }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            registerReceiver(stepReceiver, IntentFilter(StepCounterService.ACTION_STEP_UPDATE))
            receiverRegistered = true
        }
        startStepCounterWhenAllowed()
    }

    override fun onStop() {
        if (receiverRegistered) { unregisterReceiver(stepReceiver); receiverRegistered = false }
        super.onStop()
    }

    private fun startStepCounterWhenAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQUEST_ACTIVITY_RECOGNITION)
            return
        }
        val uid = runCatching {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?.takeIf { it.isEmailVerified }?.uid
        }.getOrNull() ?: return
        val serviceIntent = Intent(this, StepCounterService::class.java)
            .putExtra(StepCounterService.EXTRA_UID, uid)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ACTIVITY_RECOGNITION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startStepCounterWhenAllowed()
    }

    companion object { private const val REQUEST_ACTIVITY_RECOGNITION = 7104 }
}

@Composable
private fun MindRoot(sensorSteps: Int, sensorSupported: Boolean, sensorDelta: Int, sensorRevision: Int) {
    var page by rememberSaveable { mutableStateOf("home") }
    var water by rememberSaveable { mutableIntStateOf(0) }
    var waterGoal by rememberSaveable { mutableIntStateOf(8) }
    var waterEntries by remember { mutableStateOf<List<WaterEntry>>(emptyList()) }
    var steps by rememberSaveable { mutableIntStateOf(0) }
    var stepGoal by rememberSaveable { mutableIntStateOf(10_000) }
    var stepSensorAvailable by rememberSaveable { mutableStateOf(false) }
    var stepEntries by remember { mutableStateOf<List<StepEntry>>(emptyList()) }
    var habitHistory by remember { mutableStateOf<List<HabitDayRecord>>(emptyList()) }
    var habitDateKey by rememberSaveable { mutableStateOf(mindDateKey(System.currentTimeMillis())) }
    var studyMinutes by rememberSaveable { mutableIntStateOf(0) }
    var sleepHours by rememberSaveable { mutableFloatStateOf(0f) }
    var sleepSessions by remember { mutableStateOf<List<SleepSession>>(emptyList()) }
    var activeSleepStartedAt by rememberSaveable { mutableLongStateOf(0L) }
    var habitMask by rememberSaveable { mutableIntStateOf(0) }
    var habitNames by rememberSaveable { mutableStateOf("الاستيقاظ مبكراً|قراءة|الرياضة|التعلم") }
    var newHabit by rememberSaveable { mutableStateOf("") }
    var noteText by rememberSaveable { mutableStateOf("") }
    var focusSeconds by rememberSaveable { mutableIntStateOf(25 * 60) }
    var focusRunning by rememberSaveable { mutableStateOf(false) }
    var focusMode by rememberSaveable { mutableStateOf("تركيز") }
    var focusDurationMinutes by rememberSaveable { mutableIntStateOf(25) }
    var studySessions by remember { mutableStateOf<List<StudySession>>(emptyList()) }
    var workoutSessions by remember { mutableStateOf<List<WorkoutSession>>(emptyList()) }
    var foodEntries by remember { mutableStateOf<List<FoodEntry>>(emptyList()) }
    var journalEntries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    val context = LocalContext.current
    val appScope = rememberCoroutineScope()
    var stateLoaded by rememberSaveable { mutableStateOf(false) }
    var uiLanguage by rememberSaveable { mutableStateOf("ar") }
    var uiAppearance by rememberSaveable { mutableStateOf("dark") }
    var activeUid by remember {
        mutableStateOf(runCatching {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?.takeIf { it.isEmailVerified }?.uid
        }.getOrNull())
    }

    var legacyImportBusy by remember { mutableStateOf(false) }
    var legacyImportMessage by remember { mutableStateOf<String?>(null) }
    var cloudSyncBusy by remember { mutableStateOf(false) }
    var cloudSyncMessage by remember { mutableStateOf<String?>(null) }
    var pendingCloudRestore by remember { mutableStateOf<MindCloudSnapshot?>(null) }
    var pendingCloudRestoreInfo by remember { mutableStateOf<FirestoreMindSyncRepository.CloudSnapshotInfo?>(null) }
    var loadedStorageKey by rememberSaveable { mutableStateOf("uninitialized") }
    var accountReloadVersion by remember { mutableIntStateOf(0) }

    // Keep the listener aligned with the latest Compose state. The listener is
    // registered once, so reading activeUid directly would capture its first value.
    val currentActiveUid by rememberUpdatedState(activeUid)
    val sendPomodoroCommand: (String, Int?, String?, Int?) -> Unit = { action, seconds, mode, duration ->
        val command = Intent(context, PomodoroService::class.java).setAction(action)
            .putExtra(PomodoroService.EXTRA_UID, activeUid)
        seconds?.let { command.putExtra(PomodoroService.EXTRA_SECONDS, it) }
        mode?.let { command.putExtra(PomodoroService.EXTRA_MODE, it) }
        duration?.let { command.putExtra(PomodoroService.EXTRA_DURATION, it) }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(command)
            else context.startService(command)
        }.onFailure { Log.e("MIND", "Unable to control Pomodoro timer", it) }
    }
    val firebaseAuth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    DisposableEffect(firebaseAuth) {
        val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
            val verifiedUid = auth.currentUser
                ?.takeIf { it.isEmailVerified }
                ?.uid
            if (verifiedUid != currentActiveUid) {
                activeUid = verifiedUid
                focusRunning = false
                legacyImportMessage = null
                cloudSyncMessage = null
                cloudSyncBusy = false
                pendingCloudRestore = null
                pendingCloudRestoreInfo = null
                page = if (verifiedUid == null) "auth" else "home"
            }
        }
        firebaseAuth.addAuthStateListener(authListener)
        onDispose { firebaseAuth.removeAuthStateListener(authListener) }
    }

    DisposableEffect(context) {
        val timerReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != PomodoroService.ACTION_STATE) return
                focusSeconds = intent.getIntExtra(PomodoroService.EXTRA_SECONDS, focusSeconds).coerceAtLeast(0)
                focusRunning = intent.getBooleanExtra(PomodoroService.EXTRA_RUNNING, false)
                focusMode = intent.getStringExtra(PomodoroService.EXTRA_MODE)?.takeIf { it == "تركيز" || it == "استراحة" } ?: focusMode
                focusDurationMinutes = intent.getIntExtra(PomodoroService.EXTRA_DURATION, focusDurationMinutes).coerceIn(1, 180)
                if (intent.getBooleanExtra(PomodoroService.EXTRA_COMPLETED, false)) {
                    appScope.launch {
                        runCatching { loadMindSavedState(context, currentActiveUid) }.onSuccess { saved ->
                            studySessions = saved.studySessions
                            studyMinutes = saved.studyMinutes
                        }.onFailure { Log.e("MIND", "Unable to refresh completed study session", it) }
                    }
                }
            }
        }
        context.registerReceiver(timerReceiver, IntentFilter(PomodoroService.ACTION_STATE))
        onDispose { runCatching { context.unregisterReceiver(timerReceiver) } }
    }

    LaunchedEffect(activeUid, stateLoaded) {
        if (stateLoaded) sendPomodoroCommand(PomodoroService.ACTION_SYNC, focusSeconds, focusMode, focusDurationMinutes)
    }

    LaunchedEffect(activeUid, accountReloadVersion) {
        stateLoaded = false
        loadedStorageKey = "loading"
        val saved = runCatching { loadMindSavedState(context, activeUid) }
            .onFailure { Log.e("MIND", "Unable to restore local app data", it) }
            .getOrDefault(MindSavedState())
        uiLanguage = saved.uiLanguage
        uiAppearance = saved.uiAppearance
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != saved.uiLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(saved.uiLanguage))
        }
        waterEntries = saved.waterEntries
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        // Water is a daily metric: restore only today's intake, while retaining
        // the complete timestamped history for charts and statistics.
        water = if (saved.waterEntries.isEmpty()) saved.water
            else saved.waterEntries.count { it.recordedAt >= todayStart }.coerceAtMost(40)
        waterGoal = saved.waterGoal
        stepGoal = saved.stepGoal
        val todayKey = mindDateKey(System.currentTimeMillis())
        stepEntries = if (saved.stepSensorInitialized) saved.stepEntries else emptyList()
        steps = if (saved.stepSensorInitialized) stepEntries.filter { mindDateKey(it.recordedAt) == todayKey }.sumOf { it.delta }.coerceIn(0, 100000) else 0
        habitHistory = saved.habitHistory
        habitDateKey = todayKey
        val todayHabitRecord = saved.habitHistory.lastOrNull { it.dateKey == todayKey }
        habitMask = todayHabitRecord?.completedMask ?: if (saved.habitHistory.isEmpty()) saved.habitMask else 0
        studyMinutes = saved.studyMinutes
        sleepHours = saved.sleepHours
        sleepSessions = saved.sleepSessions
        activeSleepStartedAt = saved.activeSleepStartedAt
        habitNames = saved.habitNames
        noteText = saved.noteText
        focusSeconds = saved.focusSeconds
        focusMode = saved.focusMode
        focusDurationMinutes = saved.focusDurationMinutes
        studySessions = saved.studySessions
        workoutSessions = saved.workoutSessions
        foodEntries = saved.foodEntries
        journalEntries = saved.journalEntries
        loadedStorageKey = activeUid ?: "legacy"
        stateLoaded = true
        if (!activeUid.isNullOrBlank() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
        ) {
            val stepIntent = Intent(context, StepCounterService::class.java)
                .putExtra(StepCounterService.EXTRA_UID, activeUid)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(stepIntent)
                else context.startService(stepIntent)
            }.onFailure { Log.e("MIND", "Unable to start account-scoped step counter", it) }
        }
    }

    LaunchedEffect(sensorRevision, stateLoaded) {
        if (stateLoaded && sensorRevision > 0) {
            stepSensorAvailable = sensorSupported
            if (sensorSupported) {
                steps = sensorSteps.coerceIn(0, 100000)
                if (sensorDelta > 0) stepEntries = (stepEntries + StepEntry(System.currentTimeMillis(), sensorDelta)).takeLast(30000)
            }
        }
    }

    LaunchedEffect(
        activeUid, loadedStorageKey, stateLoaded, water, waterGoal, waterEntries, steps, stepGoal, studyMinutes, sleepHours, sleepSessions, activeSleepStartedAt,
        habitMask, habitNames, habitHistory, stepEntries, noteText, focusSeconds, focusMode, uiLanguage, uiAppearance,
        focusDurationMinutes, studySessions, workoutSessions, foodEntries, journalEntries
    ) {
        if (stateLoaded && loadedStorageKey == (activeUid ?: "legacy")) {
            runCatching {
                saveMindSavedState(
                    context,
                    MindSavedState(
                        water = water,
                        waterGoal = waterGoal,
                        waterEntries = waterEntries,
                        steps = steps,
                        stepGoal = stepGoal,
                        stepEntries = stepEntries,
                        stepSensorInitialized = true,
                        habitHistory = habitHistory,
                        studyMinutes = studyMinutes,
                        sleepHours = sleepHours,
                        sleepSessions = sleepSessions,
                        activeSleepStartedAt = activeSleepStartedAt,
                        habitMask = habitMask,
                        habitNames = habitNames,
                        noteText = noteText,
                        focusSeconds = focusSeconds,
                        focusMode = focusMode,
                        focusDurationMinutes = focusDurationMinutes,
                        studySessions = studySessions,
                        workoutSessions = workoutSessions,
                        foodEntries = foodEntries,
                        journalEntries = journalEntries,
                        uiLanguage = uiLanguage,
                        uiAppearance = uiAppearance
                    ),
                    activeUid
                )
            }.onFailure { Log.e("MIND", "Unable to persist local app data", it) }
        }
    }

    LaunchedEffect(activeUid, stateLoaded, water, waterGoal, waterEntries) {
        if (stateLoaded) refreshMindWaterWidget(context)
    }
    LaunchedEffect(activeUid, stateLoaded, habitMask, habitNames, habitHistory) {
        if (stateLoaded) refreshMindHabitWidget(context)
    }
    val habits = remember(habitNames) { habitNames.split("|").filter { it.isNotBlank() } }
    val toggleHabit: (Int) -> Unit = { index ->
        val key = mindDateKey(System.currentTimeMillis())
        val baseMask = if (habitDateKey == key) habitMask else 0
        val nextMask = baseMask xor (1 shl index)
        habitDateKey = key
        habitMask = nextMask
        habitHistory = (habitHistory.filterNot { it.dateKey == key } + HabitDayRecord(key, nextMask, habits.size)).takeLast(730)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val today = mindDateKey(System.currentTimeMillis())
            if (today != habitDateKey) { habitDateKey = today; habitMask = 0; steps = 0 }
        }
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDark = uiAppearance == "dark" || (uiAppearance == "auto" && systemDark)
    val palette = if (useDark) DarkMindPalette else LightMindPalette
    MaterialTheme(colorScheme = if (useDark) darkColorScheme(
        background = palette.background, surface = palette.surface, primary = Gold,
        onPrimary = Color(0xFF15130A), onBackground = palette.text, onSurface = palette.text
    ) else lightColorScheme(
        background = palette.background, surface = palette.surface, primary = Color(0xFF9A7610),
        onPrimary = Color.White, onBackground = palette.text, onSurface = palette.text
    )) {
        CompositionLocalProvider(
            LocalMindPalette provides palette,
            LocalMindLanguage provides uiLanguage,
            LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(fontFamily = CairoFamily)
        ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
            val wideLayout = maxWidth >= 700.dp
            val expandedRail = maxWidth >= 1000.dp
            ReferenceAtmosphere()
            if (activeUid.isNullOrBlank()) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AuthScreen(onSignedIn = {
                        activeUid = runCatching {
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                ?.takeIf { it.isEmailVerified }?.uid
                        }.getOrNull()
                        page = "home"
                    })
                }
            } else {
            Row(Modifier.fillMaxSize()) {
                if (wideLayout) {
                    SideNavigation(
                        page = page,
                        expanded = expandedRail,
                        onSelect = { page = it }
                    )
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = page,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = "mind-screen"
                    ) { target ->
                        Column(Modifier.fillMaxSize()) {
                            if (target !in setOf("home", "habits", "stats", "awards", "me")) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { page = "home" }) {
                                        Icon(Icons.Rounded.ArrowBack, mindText("رجوع", "Back"), tint = Gold)
                                    }
                                    Text(pageTitle(target), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                                }
                            }
                            when (target) {
                                "home" -> Dashboard(
                                    habits = habits, habitMask = habitMask, water = water,
                                    waterGoal = waterGoal, steps = steps, studyMinutes = studyMinutes,
                                    onToggleHabit = toggleHabit,
                                    onWaterAdd = {
                                        if (water < 40) {
                                            water += 1
                                            waterEntries = (waterEntries + WaterEntry(System.currentTimeMillis())).takeLast(20000)
                                        }
                                    },
                                    onWaterRemove = { if (water > 0) { water -= 1; if (waterEntries.isNotEmpty()) waterEntries = waterEntries.dropLast(1) } },
                                    onOpen = { page = it }
                                )
                                "habits" -> HabitsScreen(
                                    habits, habitMask, newHabit,
                                    onNewHabit = { newHabit = it },
                                    onAdd = {
                                        val clean = newHabit.trim()
                                        if (clean.isNotEmpty() && habits.size < 20 && !habits.contains(clean)) {
                                            habitNames = (habits + clean).joinToString("|")
                                            newHabit = ""
                                        }
                                    },
                                    onToggle = toggleHabit
                                )
                                "water" -> WaterScreen(
                                    water, waterGoal, waterEntries,
                                    { if (water < 40) { water += 1; waterEntries = (waterEntries + WaterEntry(System.currentTimeMillis())).takeLast(20000) } },
                                    { if (water > 0) { water -= 1; waterEntries = waterEntries.dropLast(1) } },
                                    { waterGoal = it.coerceIn(1, 20) }
                                )
                                "stats" -> StatisticsScreen(
                                    habits = habits, habitMask = habitMask, water = water, waterGoal = waterGoal,
                                    waterEntries = waterEntries, steps = steps, stepEntries = stepEntries, habitHistory = habitHistory, studySessions = studySessions,
                                    sleepSessions = sleepSessions, workoutSessions = workoutSessions
                                )
                                "awards" -> AwardsScreen(
                                    habits = habits, habitMask = habitMask, waterEntries = waterEntries,
                                    sleepSessions = sleepSessions, studyMinutes = studyMinutes,
                                    studySessions = studySessions, steps = steps, noteText = noteText, workoutSessions = workoutSessions,
                                    foodEntries = foodEntries, journalEntries = journalEntries
                                )
                                "study" -> StudyScreen(
                                    mode = focusMode, seconds = focusSeconds, running = focusRunning,
                                    totalMinutes = studyMinutes, focusDurationMinutes = focusDurationMinutes,
                                    sessions = studySessions,
                                    onOpenMindMap = { page = "mindmap" },
                                    onMode = { mode -> sendPomodoroCommand(PomodoroService.ACTION_MODE, null, mode, null) },
                                    onPreset = { minutes -> sendPomodoroCommand(PomodoroService.ACTION_PRESET, null, null, minutes) },
                                    onToggle = { sendPomodoroCommand(if (focusRunning) PomodoroService.ACTION_PAUSE else PomodoroService.ACTION_START, focusSeconds, focusMode, focusDurationMinutes) },
                                    onReset = { sendPomodoroCommand(PomodoroService.ACTION_RESET, if (focusMode == "تركيز") focusDurationMinutes * 60 else 5 * 60, null, null) },
                                    onAddTime = { sendPomodoroCommand(PomodoroService.ACTION_ADD, null, null, null) }
                                )
                                "mindmap" -> MindMapScreen()
                                "food" -> FoodScreen(
                                    entries = foodEntries,
                                    onAdd = { calories -> foodEntries = (foodEntries + FoodEntry(System.currentTimeMillis(), calories)).takeLast(20000) },
                                    onDelete = { entry -> foodEntries = foodEntries.filterNot { it.recordedAt == entry.recordedAt } }
                                )
                                "journal" -> JournalScreen(
                                    entries = journalEntries,
                                    onAdd = { text -> journalEntries = (journalEntries + JournalEntry(System.currentTimeMillis(), text)).takeLast(20000) },
                                    onDelete = { entry -> journalEntries = journalEntries.filterNot { it.recordedAt == entry.recordedAt } }
                                )
                                "fitness" -> FitnessScreen(steps, stepGoal, stepSensorAvailable, workoutSessions, userId = activeUid, onOpenFood = { page = "food" }, onSessionCompleted = { session ->
                                    workoutSessions = (workoutSessions.filterNot { it.startedAt == session.startedAt } + session).takeLast(2000)
                                }, onDeleteSession = { session ->
                                    workoutSessions = workoutSessions.filterNot { it.startedAt == session.startedAt }
                                }, onRefreshSessions = {
                                    appScope.launch { workoutSessions = loadMindSavedState(context, activeUid).workoutSessions }
                                })
                                "sleep" -> SleepScreen(
                                    hours = sleepHours, sessions = sleepSessions,
                                    activeStartedAt = activeSleepStartedAt,
                                    userId = activeUid,
                                    onStart = { if (activeSleepStartedAt == 0L) activeSleepStartedAt = System.currentTimeMillis() },
                                    onWake = {
                                        val now = System.currentTimeMillis()
                                        val elapsed = (now - activeSleepStartedAt) / 60000L
                                        if (activeSleepStartedAt > 0L && elapsed in 1..960) {
                                            sleepSessions = (sleepSessions + SleepSession(activeSleepStartedAt, now)).takeLast(2000)
                                            sleepHours = elapsed / 60f
                                            activeSleepStartedAt = 0L
                                        }
                                    },
                                    onDelete = { session -> sleepSessions = sleepSessions.filterNot { it.startedAt == session.startedAt } }
                                )
                                "notes" -> NotesScreen(noteText, { noteText = it })
                                "settings", "me" -> SettingsScreen(
                                    language = uiLanguage,
                                    appearance = uiAppearance,
                                    stepGoal = stepGoal,
                                    onStepGoal = { stepGoal = it.coerceIn(1_000, 100_000) },
                                    userId = activeUid,
                                    importBusy = legacyImportBusy,
                                    importMessage = legacyImportMessage,
                                    cloudSyncBusy = cloudSyncBusy,
                                    cloudSyncMessage = cloudSyncMessage,
                                    pendingRestoreRevision = pendingCloudRestore?.revision,
                                    onUploadCloud = {
                                        val uid = activeUid
                                        if (!uid.isNullOrBlank() && stateLoaded && !cloudSyncBusy) {
                                            cloudSyncBusy = true
                                            cloudSyncMessage = null
                                            appScope.launch {
                                                val result = runCatching {
                                                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                                                    val repository = FirestoreMindSyncRepository(
                                                        com.google.firebase.firestore.FirebaseFirestore.getInstance(), auth
                                                    )
                                                    val currentInfo = repository.readSnapshotInfo(uid)
                                                    val nextRevision = (currentInfo?.revision ?: 0L) + 1L
                                                    val snapshot = CloudSyncCodec.fromLocal(
                                                        MindSavedState(
                                                            water = water, waterGoal = waterGoal, waterEntries = waterEntries,
                                                            steps = steps, stepGoal = stepGoal, stepEntries = stepEntries, habitHistory = habitHistory,
                                                            studyMinutes = studyMinutes, sleepHours = sleepHours,
                                                            sleepSessions = sleepSessions, habitNames = habitNames,
                                                            noteText = noteText, focusMode = focusMode,
                                                            focusDurationMinutes = focusDurationMinutes,
                                                            studySessions = studySessions, workoutSessions = workoutSessions,
                                                            foodEntries = foodEntries, journalEntries = journalEntries
                                                        ),
                                                        nextRevision
                                                    )
                                                    repository.uploadSnapshot(uid, snapshot, currentInfo?.revision ?: 0L)
                                                }
                                                cloudSyncMessage = result.fold(
                                                    { info -> mindString(uiLanguage == "en", "اكتمل رفع النسخة السحابية · الإصدار ${info.revision}", "Cloud backup uploaded · revision ${info.revision}") },
                                                    { error ->
                                                        Log.e("MIND", "Cloud backup failed", error)
                                                        mindString(uiLanguage == "en", "تعذر رفع النسخة السحابية. تحقق من الاتصال ثم حاول مجدداً.", "Cloud backup failed. Check your connection and try again.")
                                                    }
                                                )
                                                cloudSyncBusy = false
                                            }
                                        }
                                    },
                                    onCheckCloudRestore = {
                                        val uid = activeUid
                                        if (!uid.isNullOrBlank() && stateLoaded && !cloudSyncBusy) {
                                            cloudSyncBusy = true
                                            cloudSyncMessage = null
                                            pendingCloudRestore = null
                                            pendingCloudRestoreInfo = null
                                            appScope.launch {
                                                val result = runCatching {
                                                    val repository = FirestoreMindSyncRepository(
                                                        com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                                                        com.google.firebase.auth.FirebaseAuth.getInstance()
                                                    )
                                                    repository.restoreSnapshot(uid)
                                                }
                                                cloudSyncMessage = result.fold(
                                                    { restore ->
                                                        when (restore) {
                                                            FirestoreMindSyncRepository.RestoreResult.NoCloudSnapshot ->
                                                                mindString(uiLanguage == "en", "لا توجد نسخة سحابية لهذا الحساب بعد.", "No cloud backup exists for this account yet.")
                                                            is FirestoreMindSyncRepository.RestoreResult.Restored -> {
                                                                pendingCloudRestore = restore.snapshot
                                                                pendingCloudRestoreInfo = restore.info
                                                                mindString(uiLanguage == "en", "تم العثور على النسخة السحابية · الإصدار ${restore.info.revision}. راجع التأكيد قبل الاستعادة.", "Cloud backup found · revision ${restore.info.revision}. Review the confirmation before restoring.")
                                                            }
                                                        }
                                                    },
                                                    { error ->
                                                        Log.e("MIND", "Cloud restore preview failed", error)
                                                        mindString(uiLanguage == "en", "تعذر قراءة النسخة السحابية والتحقق منها.", "Unable to read and validate the cloud backup.")
                                                    }
                                                )
                                                cloudSyncBusy = false
                                            }
                                        }
                                    },
                                    onCancelCloudRestore = {
                                        pendingCloudRestore = null
                                        pendingCloudRestoreInfo = null
                                    },
                                    onConfirmCloudRestore = {
                                        val uid = activeUid
                                        val snapshot = pendingCloudRestore
                                        val previewInfo = pendingCloudRestoreInfo
                                        if (!uid.isNullOrBlank() && snapshot != null && previewInfo != null && stateLoaded && !cloudSyncBusy) {
                                            cloudSyncBusy = true
                                            appScope.launch {
                                                val result = runCatching {
                                                    val repository = FirestoreMindSyncRepository(
                                                        com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                                                        com.google.firebase.auth.FirebaseAuth.getInstance()
                                                    )
                                                    // Revalidate the exact generation shown in the confirmation dialog.
                                                    // Another device may publish a newer backup while the dialog is open.
                                                    val latest = repository.restoreSnapshot(uid)
                                                    check(latest is FirestoreMindSyncRepository.RestoreResult.Restored && latest.info == previewInfo) {
                                                        "Cloud backup changed after preview; check it again."
                                                    }
                                                    val localBeforeRestore = loadMindSavedState(context, uid)
                                                    val backupFile = createMindRestoreBackup(context, uid, localBeforeRestore)
                                                    val restored = CloudSyncCodec.toLocal(latest.snapshot, localBeforeRestore)
                                                    saveMindSavedState(context, restored, uid)
                                                    restored to backupFile
                                                }
                                                result.fold(
                                                    { (restored, backupFile) ->
                                                        water = restored.water
                                                        waterGoal = restored.waterGoal
                                                        waterEntries = restored.waterEntries
                                                        steps = restored.steps
                                                        stepEntries = restored.stepEntries
                                                        habitHistory = restored.habitHistory
                                                        habitDateKey = mindDateKey(System.currentTimeMillis())
                                                        habitMask = restored.habitMask
                                                        studyMinutes = restored.studyMinutes
                                                        sleepHours = restored.sleepHours
                                                        sleepSessions = restored.sleepSessions
                                                        activeSleepStartedAt = restored.activeSleepStartedAt
                                                        habitNames = restored.habitNames
                                                        noteText = restored.noteText
                                                        focusSeconds = restored.focusSeconds
                                                        focusMode = restored.focusMode
                                                        focusDurationMinutes = restored.focusDurationMinutes
                                                        studySessions = restored.studySessions
                                                        workoutSessions = restored.workoutSessions
                                                        stepGoal = restored.stepGoal
                                                        foodEntries = restored.foodEntries
                                                        journalEntries = restored.journalEntries
                                                        pendingCloudRestore = null
                                                        pendingCloudRestoreInfo = null
                                                        cloudSyncMessage = mindString(
                                                            uiLanguage == "en",
                                                            "تمت الاستعادة. حُفظت نسخة محلية سابقة في ${backupFile.name}.",
                                                            "Restore complete. Previous local data was backed up as ${backupFile.name}."
                                                        )
                                                    },
                                                    { error ->
                                                        Log.e("MIND", "Cloud restore failed", error)
                                                        cloudSyncMessage = mindString(
                                                            uiLanguage == "en",
                                                            if (error.message?.contains("Cloud backup changed") == true) "تغيرت النسخة السحابية بعد المعاينة؛ تحقق منها مجدداً قبل الاستعادة." else "فشلت الاستعادة؛ لم يتم استبدال البيانات المحلية.",
                                                            if (error.message?.contains("Cloud backup changed") == true) "The cloud backup changed after preview. Check it again before restoring." else "Restore failed; local data was not replaced."
                                                        )
                                                    }
                                                )
                                                cloudSyncBusy = false
                                            }
                                        }
                                    },
                                    onImportLegacy = {
                                        val uid = activeUid
                                        if (!uid.isNullOrBlank() && !legacyImportBusy) {
                                            legacyImportBusy = true
                                            legacyImportMessage = null
                                            stateLoaded = false
                                            loadedStorageKey = "loading"
                                            appScope.launch {
                                                val result = importLegacyMindData(context, uid)
                                                legacyImportMessage = when (result) {
                                                    LegacyMindImportResult.Imported -> mindString(uiLanguage == "en", "تم استيراد بيانات هذا الجهاز إلى حسابك. بقيت النسخة المحلية الأصلية محفوظة.", "This device's data was imported into your account. The original local copy remains intact.")
                                                    LegacyMindImportResult.NoLegacyData -> mindString(uiLanguage == "en", "لا توجد بيانات محلية قديمة لاستيرادها.", "There is no legacy local data to import.")
                                                    LegacyMindImportResult.AccountAlreadyHasData -> mindString(uiLanguage == "en", "يحتوي هذا الحساب بالفعل على بيانات؛ لم يتم استبدالها.", "This account already contains data; nothing was overwritten.")
                                                    LegacyMindImportResult.AlreadyImported -> mindString(uiLanguage == "en", "سبق استيراد البيانات المحلية إلى هذا الحساب.", "Legacy data has already been imported into this account.")
                                                    LegacyMindImportResult.OwnedByAnotherAccount -> mindString(uiLanguage == "en", "البيانات المحلية مرتبطة بحساب آخر ولا يمكن استيرادها إلى هذا الحساب.", "Legacy data is already assigned to another account and cannot be imported here.")
                                                    LegacyMindImportResult.Failed -> mindString(uiLanguage == "en", "تعذر إتمام الاستيراد بأمان. بياناتك الأصلية لم تُحذف؛ حاول مجدداً.", "The import could not be completed safely. Your original data was not deleted; please retry.")
                                                }
                                                accountReloadVersion++
                                                legacyImportBusy = false
                                            }
                                        }
                                    },
                                    onLanguage = { selected ->
                                        uiLanguage = selected
                                        appScope.launch {
                                            saveMindUiSettings(context, selected, uiAppearance)
                                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected))
                                        }
                                    },
                                    onAppearance = { selected ->
                                        uiAppearance = selected
                                        appScope.launch { saveMindUiSettings(context, uiLanguage, selected) }
                                    },
                                    onAccount = { page = "auth" },
                                    onSignOut = {
                                        focusRunning = false
                                        runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }
                                            .onFailure { Log.e("MIND", "Unable to sign out", it) }
                                        activeUid = null
                                        page = "auth"
                                        legacyImportMessage = null
                                    }
                                )
                                "auth" -> AuthScreen(onSignedIn = {
                                    activeUid = runCatching {
                                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                            ?.takeIf { it.isEmailVerified }?.uid
                                    }.getOrNull()
                                    page = "home"
                                })
                                else -> Dashboard(
                                    habits = habits, habitMask = habitMask, water = water,
                                    waterGoal = waterGoal, steps = steps, studyMinutes = studyMinutes,
                                    onToggleHabit = { index -> habitMask = habitMask xor (1 shl index) },
                                    onWaterAdd = {
                                        if (water < 40) {
                                            water += 1
                                            waterEntries = (waterEntries + WaterEntry(System.currentTimeMillis())).takeLast(20000)
                                        }
                                    },
                                    onWaterRemove = { if (water > 0) water -= 1 },
                                    onOpen = { page = it }
                                )
                            }
                        }
                    }
                    if (!wideLayout) {
                        BottomNavigation(page = page, onSelect = { page = it })
                    }
                }
            }
            }
        }
        }
    }
}

@Composable
private fun SideNavigation(page: String, expanded: Boolean, onSelect: (String) -> Unit) {
    Column(
        Modifier.width(if (expanded) 230.dp else 84.dp).fillMaxHeight()
            .background(Glass).border(1.dp, GlassBorder)
            .padding(horizontal = 12.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (expanded) "MIND" else "M",
            modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
            color = Gold, fontSize = if (expanded) 22.sp else 18.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp,
            textAlign = if (expanded) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center
        )
        RailItem(mindText("الرئيسية", "Home"), "home", Icons.Rounded.Home, page, expanded, onSelect)
        RailItem(mindText("العادات", "Habits"), "habits", Icons.Rounded.CheckCircle, page, expanded, onSelect)
        RailItem(mindText("الإحصائيات", "Statistics"), "stats", Icons.Rounded.School, page, expanded, onSelect)
        RailItem(mindText("الإنجازات", "Achievements"), "awards", Icons.Rounded.CheckCircle, page, expanded, onSelect)
        RailItem(mindText("حسابي", "Account"), "me", Icons.Rounded.Settings, page, expanded, onSelect)
        Spacer(Modifier.weight(1f))
        if (expanded) Text(mindText("عقلك، وقتك، حياتك", "Your mind, your time, your life"), color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun RailItem(
    label: String, route: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    page: String, expanded: Boolean, onSelect: (String) -> Unit
) {
    val selected = page == route
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Gold else Color.Transparent)
            .clickable { onSelect(route) }.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Ink else Muted, modifier = Modifier.size(22.dp))
        if (expanded) Text(label, modifier = Modifier.padding(start = 12.dp), color = if (selected) Ink else Muted, fontSize = 13.sp)
    }
}

@Composable
private fun BottomNavigation(page: String, onSelect: (String) -> Unit) {
    NavigationBar(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
            .clip(RoundedCornerShape(24.dp)).border(1.dp, GlassBorder, RoundedCornerShape(24.dp)),
        containerColor = LocalMindPalette.current.surface.copy(alpha = 0.90f),
        contentColor = LocalMindPalette.current.text,
        tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        NavigationBarItem(page == "home", { onSelect("home") }, { Icon(Icons.Rounded.Home, null) }, label = { Text(mindText("الرئيسية", "Home"), fontSize = 10.sp) })
        NavigationBarItem(page == "habits", { onSelect("habits") }, { Icon(Icons.Rounded.CheckCircle, null) }, label = { Text(mindText("العادات", "Habits"), fontSize = 10.sp) })
        NavigationBarItem(page == "stats", { onSelect("stats") }, { Icon(Icons.Rounded.School, null) }, label = { Text(mindText("الإحصائيات", "Statistics"), fontSize = 10.sp) })
        NavigationBarItem(page == "awards", { onSelect("awards") }, { Icon(Icons.Rounded.CheckCircle, null) }, label = { Text(mindText("الإنجازات", "Achievements"), fontSize = 10.sp) })
        NavigationBarItem(page == "me" || page == "settings", { onSelect("me") }, { Icon(Icons.Rounded.Settings, null) }, label = { Text(mindText("حسابي", "Account"), fontSize = 10.sp) })
    }
}

@Composable
private fun Dashboard(
    habits: List<String>, habitMask: Int, water: Int, waterGoal: Int, steps: Int,
    studyMinutes: Int, onToggleHabit: (Int) -> Unit,
    onWaterAdd: () -> Unit, onWaterRemove: () -> Unit, onOpen: (String) -> Unit
) {
    val done = Integer.bitCount(habitMask)
    val progress = if (habits.isEmpty()) 0f else (done.toFloat() / habits.size).coerceIn(0f, 1f)
    val quotes = listOf(mindText("الاستمرارية أهم من الكمال", "Consistency over perfection"), mindText("رحلة الألف ميل تبدأ بخطوة", "A journey of a thousand miles begins with one step"), mindText("افعل اليوم ما يشكرك عليه غدك", "Do today what your future self will thank you for"), mindText("خطوة صغيرة كل يوم تصنع فرقاً كبيراً", "A small step every day makes a big difference"))
    val quote = quotes[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) % quotes.size]
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 2.dp)) {
                    Text(mindText("أهلاً بك في MIND", "Welcome to MIND"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 35.sp)
                    Text(mindText("عقلك، وقتك، حياتك", "Your mind, your time, your life"), color = Muted, fontSize = 14.sp)
                }
            }
            if (wide) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            DailyProgressCard(progress, done, habits.size)
                            QuickAccessGrid(Modifier.fillMaxWidth(), onOpen)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            WaterSummaryCard(water, waterGoal, onAdd = onWaterAdd, onRemove = onWaterRemove)
                            TodayHabitsCard(habits, habitMask, onToggleHabit)
                            QuoteCard(quote)
                        }
                    }
                }
            } else {
                item { DailyProgressCard(progress, done, habits.size) }
                item { QuickAccessGrid(Modifier.fillMaxWidth(), onOpen) }
                item { WaterSummaryCard(water, waterGoal, onAdd = onWaterAdd, onRemove = onWaterRemove) }
                item { TodayHabitsCard(habits, habitMask, onToggleHabit) }
                item { QuoteCard(quote) }
            }
        }
    }
}

@Composable
private fun DailyProgressCard(progress: Float, done: Int, total: Int) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color.Transparent, shape = RoundedCornerShape(22.dp)) {
        Row(
            Modifier.background(Brush.linearGradient(listOf(Gold, Color(0xFFD4A942)))).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress }, modifier = Modifier.fillMaxSize(),
                    strokeWidth = 9.dp, color = Ink, trackColor = Ink.copy(alpha = .15f),
                    strokeCap = StrokeCap.Round
                )
                Text("${(progress * 100).toInt()}%", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(mindText("إنجاز اليوم", "Today's progress"), color = Ink.copy(alpha = .75f), fontSize = 13.sp)
                Text("$done من $total", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                Text(mindText("عادات مكتملة", "Habits completed"), color = Ink.copy(alpha = .78f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(modifier: Modifier, onOpen: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            QuickAccessCard(mindText("العادات", "Habits"), mindText("تتبع يومك", "Track your day"), "habits", Icons.Rounded.CheckCircle, Modifier.weight(1f), onOpen)
            QuickAccessCard(mindText("النوم", "Sleep"), mindText("راحة أفضل", "Better rest"), "sleep", Icons.Rounded.Bedtime, Modifier.weight(1f), onOpen)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            QuickAccessCard(mindText("الدراسة", "Study"), mindText("جلسات التركيز", "Focus sessions"), "study", Icons.Rounded.School, Modifier.weight(1f), onOpen)
            QuickAccessCard(mindText("اللياقة", "Fitness"), mindText("الجري والحركة", "Running & movement"), "fitness", Icons.Rounded.DirectionsRun, Modifier.weight(1f), onOpen)
        }
    }
}

@Composable
private fun QuickAccessCard(
    title: String, subtitle: String, route: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier, onOpen: (String) -> Unit
) {
    Surface(
        onClick = { onOpen(route) }, modifier = modifier,
        color = Glass, shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Gold.copy(alpha = .14f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(23.dp)) }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WaterSummaryCard(water: Int, goal: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = Color(0xFF5AA9FF), modifier = Modifier.size(20.dp))
            Text(mindText("الماء", "Water"), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(mindText("$water / $goal أكواب", "$water / $goal cups"), color = Muted, fontSize = 12.sp)
            TextButton(onClick = onRemove, enabled = water > 0, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                Text("−", color = Gold, fontSize = 18.sp)
            }
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                Text("+", color = Gold, fontSize = 18.sp)
            }
        }
        LinearProgressIndicator(
            progress = { (water.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = Color(0xFF5AA9FF), trackColor = Color(0x225AA9FF)
        )
        Text(mindText("تقدمك نحو هدفك اليومي", "Your progress toward today's goal"), color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TodayHabitsCard(habits: List<String>, habitMask: Int, onToggle: (Int) -> Unit) {
    GlassCard {
        Text(mindText("عادات اليوم", "Today's habits"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        if (habits.isEmpty()) {
            Text(mindText("لا توجد عادات بعد — أضف أولى عاداتك", "No habits yet — add your first habit"), color = Muted, fontSize = 12.sp)
        } else {
            habits.take(3).forEachIndexed { index, habit ->
                val checked = habitMask and (1 shl index) != 0
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onToggle(index) }) {
                    Icon(
                        if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (checked) Color(0xFF5BD68A) else Muted,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(habit, modifier = Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp,
                        color = if (checked) Muted else Color(0xFFF4F4F6))
                    Text(if (checked) mindText("تم", "Done") else mindText("اليوم", "Today"), color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(quote: String) {
    GlassCard {
        Text("✦", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(quote, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp)
    }
}

@Composable
private fun MiniStat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Muted, fontSize = 11.sp)
        Text(unit, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ModuleCard(title: String, subtitle: String, route: String, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    Surface(
        onClick = { onOpen(route) }, modifier = modifier,
        color = Glass, shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HabitsScreen(
    habits: List<String>, mask: Int, newHabit: String,
    onNewHabit: (String) -> Unit, onAdd: () -> Unit, onToggle: (Int) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard {
                Text(mindText("عادات اليوم", "Today's habits"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(mindText("${Integer.bitCount(mask)} من ${habits.size} مكتملة", "${Integer.bitCount(mask)} of ${habits.size} completed"), color = Muted, fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { if (habits.isEmpty()) 0f else Integer.bitCount(mask).toFloat() / habits.size },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(5.dp),
                    color = Gold, trackColor = Color(0x22FFFFFF)
                )
            }
        }
        itemsIndexed(habits) { index, habit ->
            val done = mask and (1 shl index) != 0
            Surface(
                onClick = { onToggle(index) }, color = Glass, shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (done) Gold.copy(alpha = .55f) else GlassBorder)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, onCheckedChange = { onToggle(index) }, colors = CheckboxDefaults.colors(checkedColor = Gold))
                    Text(habit, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(if (done) mindText("مكتملة", "Completed") else mindText("اليوم", "Today"), color = if (done) Gold else Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            GlassCard {
                Text(mindText("إضافة عادة", "Add habit"), fontWeight = FontWeight.Bold, color = Gold)
                OutlinedTextField(value = newHabit, onValueChange = onNewHabit, modifier = Modifier.fillMaxWidth(),
                    label = { Text(mindText("اسم العادة", "Habit name")) }, singleLine = true)
                Button(onClick = onAdd, enabled = newHabit.isNotBlank() && habits.size < 20,
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                    Text(mindText("إضافة", "Add"))
                }
            }
        }
    }
}

@Composable
private fun WaterScreen(water: Int, goal: Int, entries: List<WaterEntry>, add: () -> Unit, remove: () -> Unit, setGoal: (Int) -> Unit) {
    val hourly = remember(entries) {
        val today = java.util.Calendar.getInstance()
        val start = (today.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val counts = IntArray(24)
        entries.filter { it.recordedAt >= start && it.recordedAt <= System.currentTimeMillis() }.forEach { entry ->
            val hour = java.util.Calendar.getInstance().apply { timeInMillis = entry.recordedAt }
                .get(java.util.Calendar.HOUR_OF_DAY)
            counts[hour]++
        }
        counts.toList()
    }
    val peak = (hourly.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassCard {
            Text(mindText("الماء", "Water"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(mindText("تقدمك نحو الهدف اليومي", "Your progress toward today's goal"), color = Muted, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { (water.toFloat() / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.size(170.dp), strokeWidth = 12.dp, color = Gold,
                    trackColor = Color(0x24FFFFFF), strokeCap = StrokeCap.Round)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$water", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Text(mindText("من $goal أكواب", "of $goal cups"), color = Muted, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = remove, modifier = Modifier.weight(1f)) { Text("−") }
                Button(onClick = add, modifier = Modifier.weight(2f), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text(mindText("إضافة كوب", "Add a cup")) }
            }
        }
        GlassCard {
            Text(mindText("التوزيع خلال اليوم", "Distribution throughout the day"), fontWeight = FontWeight.Bold)
            Text(mindText("عدد الأكواب المسجلة في كل ساعة", "Cups recorded each hour"), color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom) {
                hourly.forEachIndexed { hour, count ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.fillMaxWidth().height((76f * count / peak).coerceAtLeast(if (count > 0) 5f else 2f).dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (count > 0) Gold else Color(0x25FFFFFF)))
                        if (hour % 6 == 0) Text(hour.toString(), fontSize = 8.sp, color = Muted)
                    }
                }
            }
            Text(mindText("الإجمالي المسجل اليوم: ${hourly.sum()} كوب", "Total recorded today: ${hourly.sum()} cups"), color = Gold, fontSize = 12.sp)
        }
        GlassCard {
            Text(mindText("الهدف اليومي", "Daily goal"), fontWeight = FontWeight.Bold)
            Text(mindText("$goal أكواب", "$goal cups"), color = Gold, fontSize = 20.sp)
            Slider(value = goal.toFloat(), onValueChange = { setGoal(it.toInt().coerceAtLeast(1)) },
                valueRange = 1f..20f, steps = 18, colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
        }
    }
}

@Composable
private fun StudyScreen(
    mode: String, seconds: Int, running: Boolean, totalMinutes: Int,
    focusDurationMinutes: Int, sessions: List<StudySession>,
    onOpenMindMap: () -> Unit,
    onMode: (String) -> Unit, onPreset: (Int) -> Unit,
    onToggle: () -> Unit, onReset: () -> Unit, onAddTime: () -> Unit
) {
    val todayMinutes = remember(sessions) {
        val now = java.util.Calendar.getInstance()
        sessions.filter { session ->
            val d = java.util.Calendar.getInstance().apply { timeInMillis = session.completedAt }
            d.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                d.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
        }.sumOf { it.durationMinutes }
    }
    val week = remember(sessions) {
        (6 downTo 0).map { offset ->
            val day = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                add(java.util.Calendar.DAY_OF_YEAR, -offset)
            }
            val total = sessions.filter { session ->
                val d = java.util.Calendar.getInstance().apply { timeInMillis = session.completedAt }
                d.get(java.util.Calendar.YEAR) == day.get(java.util.Calendar.YEAR) &&
                    d.get(java.util.Calendar.DAY_OF_YEAR) == day.get(java.util.Calendar.DAY_OF_YEAR)
            }.sumOf { it.durationMinutes }
            day.getDisplayName(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SHORT, java.util.Locale("ar")) to total
        }
    }
    val maxWeek = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GlassCard {
                Text(mindText("الدراسة والتركيز", "Study & focus"), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(mindText("جلسات التركيز والاستراحة", "Focus and break sessions"), color = Muted, fontSize = 13.sp)
                OutlinedButton(onClick = onOpenMindMap, modifier = Modifier.fillMaxWidth()) { Text(mindText("فتح الخرائط الذهنية", "Open mind maps")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "تركيز", onClick = { onMode("تركيز") }, label = { Text(mindText("تركيز", "Focus")) })
                    FilterChip(selected = mode == "استراحة", onClick = { onMode("استراحة") }, label = { Text(mindText("استراحة 5 د", "5 min break")) })
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { (seconds.toFloat() / (if (mode == "تركيز") focusDurationMinutes * 60f else 300f)).coerceIn(0f, 1f) },
                        modifier = Modifier.size(190.dp), strokeWidth = 10.dp, color = Gold,
                        trackColor = Color(0x22FFFFFF), strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                        Text(if (mode == "تركيز") mindText("تركيز", "Focus") else mindText("راحة", "Break"), color = Muted, fontSize = 12.sp)
                    }
                }
                if (mode == "تركيز") {
                    Text(mindText("مدة الجلسة", "Session length"), color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 25, 45, 60).forEach { duration ->
                            FilterChip(selected = focusDurationMinutes == duration,
                                onClick = { onPreset(duration) }, label = { Text(mindText("$duration د", "$duration min")) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onToggle, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                        Text(if (running) mindText("إيقاف مؤقت", "Pause") else mindText("ابدأ", "Start"))
                    }
                    OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(mindText("إعادة", "Reset")) }
                }
                TextButton(onClick = onAddTime, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(mindText("+ 5 دقائق", "+ 5 minutes")) }
            }
        }
        item {
            GlassCard {
                Text(mindText("ملخص التركيز اليوم", "Today's focus summary"), color = Muted, fontSize = 13.sp)
                Text(mindText("$todayMinutes دقيقة", "$todayMinutes minutes"), color = Gold, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text(mindText("الإجمالي المسجل: $totalMinutes دقيقة · ", "Total recorded: $totalMinutes minutes · ") + sessions.size + mindText(" جلسة", " sessions"), color = Muted, fontSize = 12.sp)
            }
        }
        item {
            GlassCard {
                Text(mindText("آخر 7 أيام", "Last 7 days"), fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().height(132.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    week.forEach { (label, value) ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom) {
                            Text(if (value > 0) value.toString() else " ", fontSize = 10.sp, color = Muted)
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth(.72f)
                                    .height((82f * value / maxWeek).coerceAtLeast(if (value > 0) 5f else 2f).dp)
                                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                    .background(if (value > 0) Gold else Color(0x24FFFFFF)))
                            }
                            Text(label ?: "—", fontSize = 10.sp, color = Muted, maxLines = 1)
                        }
                    }
                }
            }
        }
        item { Text(mindText("سجل الجلسات", "Session history"), fontWeight = FontWeight.Bold, color = Gold) }
        if (sessions.isEmpty()) {
            item { Text(mindText("لا توجد جلسات مكتملة بعد. ستظهر جلساتك هنا تلقائياً.", "No completed sessions yet. Your sessions will appear here automatically."), color = Muted, fontSize = 13.sp) }
        } else {
            items(sessions.sortedByDescending { it.completedAt }.take(30)) { session ->
                val date = java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm", java.util.Locale("ar"))
                    .format(java.util.Date(session.completedAt))
                Surface(color = Glass, shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(mindText("جلسة تركيز", "Focus session"), fontWeight = FontWeight.SemiBold)
                            Text(date, color = Muted, fontSize = 11.sp)
                        }
                        Text(session.durationMinutes.toString() + mindText(" د", " min"), color = Gold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FitnessScreen(steps: Int, stepGoal: Int, sensorAvailable: Boolean, sessions: List<WorkoutSession>, userId: String?, onOpenFood: () -> Unit, onSessionCompleted: (WorkoutSession) -> Unit, onDeleteSession: (WorkoutSession) -> Unit, onRefreshSessions: () -> Unit) {
    val context = LocalContext.current
    val english = LocalMindLanguage.current == "en"
    var workoutType by rememberSaveable { mutableStateOf(mindString(english, "جري", "Run")) }
    var tracking by rememberSaveable { mutableStateOf(false) }
    var distanceMeters by rememberSaveable { mutableFloatStateOf(0f) }
    var elapsedSeconds by rememberSaveable { mutableLongStateOf(0L) }
    var gpsReady by rememberSaveable { mutableStateOf(false) }
    var gpsMessage by rememberSaveable { mutableStateOf(mindString(english, "لم تبدأ جلسة بعد", "No session started yet")) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(context, GpsWorkoutService::class.java).setAction(GpsWorkoutService.ACTION_START).putExtra(GpsWorkoutService.EXTRA_TYPE, workoutType).putExtra(GpsWorkoutService.EXTRA_UID, userId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            tracking = true
            gpsMessage = mindString(english, "جارٍ الحصول على إشارة GPS", "Getting GPS signal")
        } else gpsMessage = mindString(english, "يلزم السماح بالوصول إلى الموقع لقياس المسافة", "Location access is required to measure distance")
    }
    DisposableEffect(context, userId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != GpsWorkoutService.ACTION_UPDATE) return
                tracking = intent.getBooleanExtra(GpsWorkoutService.EXTRA_ACTIVE, false)
                distanceMeters = intent.getFloatExtra(GpsWorkoutService.EXTRA_DISTANCE, 0f)
                elapsedSeconds = intent.getLongExtra(GpsWorkoutService.EXTRA_ELAPSED, 0L)
                gpsReady = intent.getBooleanExtra(GpsWorkoutService.EXTRA_GPS_READY, false)
                gpsMessage = intent.getStringExtra(GpsWorkoutService.EXTRA_MESSAGE) ?: gpsMessage
                if (intent.getBooleanExtra(GpsWorkoutService.EXTRA_COMPLETED, false)) {
                    val started = intent.getLongExtra(GpsWorkoutService.EXTRA_STARTED_AT, 0L)
                    val ended = intent.getLongExtra(GpsWorkoutService.EXTRA_ENDED_AT, 0L)
                    val type = intent.getStringExtra(GpsWorkoutService.EXTRA_TYPE) ?: mindString(english, "جري", "Run")
                    val distance = intent.getFloatExtra(GpsWorkoutService.EXTRA_DISTANCE, 0f)
                    val duration = intent.getLongExtra(GpsWorkoutService.EXTRA_ELAPSED, 0L)
                    if (started > 0L && ended > started && duration >= 5L) {
                        onSessionCompleted(WorkoutSession(started, ended, type, distance, duration))
                    }
                }
            }
        }
        val filter = IntentFilter(GpsWorkoutService.ACTION_UPDATE)
        context.registerReceiver(receiver, filter)
        onRefreshSessions()
        context.startService(Intent(context, GpsWorkoutService::class.java).setAction(GpsWorkoutService.ACTION_STATUS))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(tracking) {
        while (tracking) { delay(1000); elapsedSeconds++ }
    }
    val distanceText = if (distanceMeters >= 1000f) mindString(english, "%.2f كم", "%.2f km").format(java.util.Locale.getDefault(), distanceMeters / 1000f) else mindString(english, "%d م", "%d m").format(distanceMeters.toInt())
    val timeText = "%02d:%02d:%02d".format(elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GlassCard {
                Text(mindString(english, "الجري واللياقة", "Running & fitness"), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(mindString(english, "تتبّع المسافة والمدة مباشرة عبر GPS الهاتف", "Track distance and duration live using your phone's GPS"), color = Muted, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(mindString(english, "مشي", "Walk"), mindString(english, "جري", "Run"), mindString(english, "دراجة", "Cycle")).forEach { type ->
                        FilterChip(selected = workoutType == type, onClick = { if (!tracking) workoutType = type }, label = { Text(type) }, enabled = !tracking)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(mindString(english, "المسافة", "Distance"), color = Muted, fontSize = 12.sp)
                        Text(distanceText, color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(mindString(english, "المدة", "Duration"), color = Muted, fontSize = 12.sp)
                        Text(timeText, color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (gpsReady) Color(0xFF5BD68A) else Gold))
                    Text(gpsMessage, color = Muted, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        if (tracking) {
                            context.startService(Intent(context, GpsWorkoutService::class.java).setAction(GpsWorkoutService.ACTION_STOP))
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            val intent = Intent(context, GpsWorkoutService::class.java).setAction(GpsWorkoutService.ACTION_START).putExtra(GpsWorkoutService.EXTRA_TYPE, workoutType).putExtra(GpsWorkoutService.EXTRA_UID, userId)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                            tracking = true
                            gpsMessage = mindString(english, "جارٍ الحصول على إشارة GPS", "Getting GPS signal")
                            distanceMeters = 0f
                            elapsedSeconds = 0L
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (tracking) Color(0xFFB94B4B) else Gold, contentColor = if (tracking) Color.White else Ink)
                ) { Text(if (tracking) mindString(english, "إنهاء الجلسة", "Finish session") else mindString(english, "بدء ${workoutType}", "Start ${workoutType}")) }
            }
        }
        item {
            GlassCard {
                Text(mindString(english, "خطوات اليوم", "Today's steps"), color = Gold, fontWeight = FontWeight.Bold)
                Text(steps.toString(), color = Gold, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(mindString(english, "من هدف يومي قدره $stepGoal خطوة", "$stepGoal-step daily goal"), color = Muted, fontSize = 12.sp)
                LinearProgressIndicator(progress = { (steps.toFloat() / stepGoal.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Gold)
                Text(if (sensorAvailable) mindString(english, "تُقاس تلقائياً من مستشعر الخطوات", "Measured automatically by the step sensor") else mindString(english, "مستشعر الخطوات غير متاح حالياً", "Step sensor is currently unavailable"), color = Muted, fontSize = 12.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(mindString(english, "سجل النشاط", "Activity history"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(mindString(english, "تُحفظ الجلسات على هذا الجهاز تلقائياً", "Sessions are saved automatically on this device"), color = Muted, fontSize = 11.sp)
            }
        }
        if (sessions.isEmpty()) {
            item { GlassCard { Text(mindString(english, "لا توجد جلسات محفوظة بعد", "No saved sessions yet"), color = Muted, fontSize = 13.sp) } }
        } else {
            items(sessions.sortedByDescending { it.endedAt }.take(50)) { session ->
                GlassCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(session.type, fontWeight = FontWeight.Bold)
                            Text(java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm", java.util.Locale("ar")).format(java.util.Date(session.startedAt)), color = Muted, fontSize = 11.sp)
                            val distanceLabel = if (session.distanceMeters >= 1000f) mindString(english, "%.2f كم", "%.2f km").format(session.distanceKm) else mindString(english, "%d م", "%d m").format(session.distanceMeters.toInt())
                            Text(mindString(english, "${session.durationSeconds / 60L} دقيقة · $distanceLabel", "${session.durationSeconds / 60L} min · $distanceLabel"), color = Muted, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("GPS", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            TextButton(onClick = { onDeleteSession(session) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                Text(mindString(english, "حذف", "Delete"), color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            GlassCard {
                Text(mindString(english, "التغذية", "Nutrition"), color = Gold, fontWeight = FontWeight.Bold)
                Text(mindString(english, "سجّل السعرات اليومية وتابع سجل التغذية", "Log your daily calories and track your nutrition"), color = Muted, fontSize = 12.sp)
                Button(onClick = onOpenFood, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                    Text(mindString(english, "فتح التغذية", "Open nutrition"))
                }
            }
        }
        item { Text(mindString(english, "ملاحظة: قياس المسافة يحتاج رؤية واضحة للأقمار الصناعية. قد تتأخر القراءة داخل المباني، ولن تُحتسب المواقع غير الدقيقة.", "Note: Distance measurement requires a clear satellite signal. Readings may be delayed indoors, and inaccurate locations are ignored."), color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun SleepScreen(hours: Float, sessions: List<SleepSession>, activeStartedAt: Long, userId: String?, onStart: () -> Unit, onWake: () -> Unit, onDelete: (SleepSession) -> Unit) {
    val active = activeStartedAt > 0L
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeStartedAt) {
        if (active) {
            clockNow = System.currentTimeMillis()
            while (true) {
                delay(30_000L)
                clockNow = System.currentTimeMillis()
            }
        }
    }
    val activeMinutes = if (active) ((clockNow - activeStartedAt).coerceAtLeast(0L) / 60_000L) else 0L
    val latest = sessions.maxByOrNull { it.endedAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SleepAlarmCard(userId) }
        item {
            GlassCard {
                Text(mindText("النوم", "Sleep"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(mindText("سجّل بداية نومك واستيقاظك لاحتساب المدة تلقائياً", "Record when you go to sleep and wake up to calculate duration automatically"), color = Muted, fontSize = 13.sp)
                Text(
                    if (active) mindText("%d ساعة %02d دقيقة", "%d hr %02d min").format(activeMinutes / 60L, activeMinutes % 60L) else mindText("%.1f ساعة", "%.1f hours").format(hours),
                    color = Gold, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)
                )
                if (active) {
                    val started = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(activeStartedAt))
                    Text(mindText("وقت البداية: $started · مدة الجلسة الحالية", "Start time: $started · Current session duration"), color = Muted, fontSize = 12.sp)
                    Button(onClick = onWake, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text(mindText("تسجيل الاستيقاظ", "Record wake-up")) }
                } else {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text(mindText("بدء النوم", "Start sleep")) }
                }
                Text(mindText("الهدف المرجعي: 8 ساعات · المدة المقبولة للجلسة: حتى 16 ساعة", "Reference goal: 8 hours · Maximum accepted session: 16 hours"), color = Muted, fontSize = 11.sp)
            }
        }
        item {
            val recentSessions = sessions.sortedByDescending { it.endedAt }.take(7)
            val averageMinutes = recentSessions.takeIf { it.isNotEmpty() }?.map { it.durationMinutes }?.average()
            val goalProgress = ((averageMinutes ?: 0.0) / 480.0).toFloat().coerceIn(0f, 1f)
            GlassCard {
                Text(mindText("ملخص النوم", "Sleep summary"), color = Gold, fontWeight = FontWeight.Bold)
                Text(mindText("عدد الجلسات: ${sessions.size}", "Sessions: ${sessions.size}"))
                Text(latest?.let { mindText("آخر جلسة: %.1f ساعة", "Last session: %.1f hours").format(it.durationMinutes / 60f) } ?: mindText("لا توجد جلسات مسجلة بعد", "No sessions recorded yet"), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(mindText("متوسط آخر 7 جلسات", "Average of last 7 sessions"), color = Muted, fontSize = 11.sp)
                        Text(averageMinutes?.let { mindText("%.1f ساعة", "%.1f hours").format(it / 60.0) } ?: "—", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(mindText("مقارنة بهدف 8 ساعات", "Compared with the 8-hour goal"), color = Muted, fontSize = 11.sp)
                        Text(averageMinutes?.let { "${(it / 480.0 * 100).toInt()}%" } ?: "—", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = goalProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = Gold,
                    trackColor = GlassBorder
                )
                Text(mindText("المتوسط محسوب من الجلسات المسجلة فقط، ولا تُضاف بيانات تقديرية.", "Average is based only on recorded sessions; no estimated data is added."), color = Muted, fontSize = 10.sp)
            }
        }
        item {
            val weeklySleep = remember(sessions) {
                val now = java.util.Calendar.getInstance()
                val today = (now.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                (6 downTo 0).map { daysAgo ->
                    val dayStart = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -daysAgo) }.timeInMillis
                    val dayEnd = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 1 - daysAgo) }.timeInMillis
                    val minutes = sessions.sumOf { session ->
                        (minOf(session.endedAt, dayEnd) - maxOf(session.startedAt, dayStart)).coerceAtLeast(0L) / 60_000L
                    }
                    val label = java.text.SimpleDateFormat("EEE", java.util.Locale("ar")).format(java.util.Date(dayStart))
                    Triple(label, minutes, dayStart)
                }
            }
            val maxMinutes = maxOf(480L, weeklySleep.maxOfOrNull { it.second } ?: 0L)
            GlassCard {
                Text(mindText("النوم خلال آخر 7 أيام", "Sleep over the last 7 days"), color = Gold, fontWeight = FontWeight.Bold)
                Text(mindText("المدة موزعة حسب اليوم اعتماداً على جلساتك المسجلة فقط", "Duration by day, based only on your recorded sessions"), color = Muted, fontSize = 11.sp)
                Row(
                    Modifier.fillMaxWidth().height(132.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    weeklySleep.forEach { (label, minutes, _) ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(if (minutes > 0L) "%.1f".format(minutes / 60f) else "—", color = Muted, fontSize = 9.sp)
                            Box(
                                Modifier.weight(1f).fillMaxWidth().padding(top = 5.dp, bottom = 5.dp)
                                    .background(GlassBorder, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(0.62f)
                                        .fillMaxHeight((minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.025f, 1f))
                                        .background(Gold, RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                )
                            }
                            Text(label, color = Muted, fontSize = 9.sp)
                        }
                    }
                }
                Text(mindText("الساعات فوق الأعمدة؛ قد يكون اليوم بلا بيانات إذا لم تسجل جلسة.", "Hours are shown above each bar; a day may have no data if no session was recorded."), color = Muted, fontSize = 10.sp)
            }
        }
        item { Text(mindText("سجل النوم", "Sleep history"), color = Gold, fontWeight = FontWeight.Bold) }
        if (sessions.isEmpty()) {
            item { Text(mindText("ستظهر جلسات نومك هنا بعد تسجيل الاستيقاظ.", "Your sleep sessions will appear here after you record a wake-up."), color = Muted, fontSize = 13.sp) }
        } else {
            items(sessions.sortedByDescending { it.endedAt }) { session ->
                val formatter = java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm", java.util.Locale.getDefault())
                Surface(color = Glass, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(mindText("%.1f ساعة", "%.1f hours").format(session.durationMinutes / 60f), color = Gold, fontWeight = FontWeight.Bold)
                            Text(mindText("من ${formatter.format(java.util.Date(session.startedAt))}", "From ${formatter.format(java.util.Date(session.startedAt))}"), color = Muted, fontSize = 11.sp)
                            Text(mindText("إلى ${formatter.format(java.util.Date(session.endedAt))}", "To ${formatter.format(java.util.Date(session.endedAt))}"), color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onDelete(session) }) { Text(mindText("حذف", "Delete"), color = Muted) }
                    }
                }
            }
        }
    }
}


@Composable
private fun SleepAlarmCard(userId: String?) {
    val context = LocalContext.current
    var settings by remember(userId) { mutableStateOf(userId?.let { SleepAlarm.read(context, it) } ?: SleepAlarm.Settings(false, 7, 0, "none", null)) }
    var soundMenuExpanded by remember { mutableStateOf(false) }
    val timeLabel = "%02d:%02d".format(settings.hour, settings.minute)
    val soundPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure { Log.w("MIND", "Could not persist alarm audio permission", it) }
            userId?.let { SleepAlarm.saveSound(context, it, "file", uri.toString()) }
            settings = settings.copy(sound = "file", soundUri = uri.toString())
        } else if (settings.sound == "file" && settings.soundUri == null) {
            userId?.let { SleepAlarm.saveSound(context, it, "none") }
            settings = settings.copy(sound = "none")
        }
    }
    val soundLabel = when (settings.sound) {
        "beep" -> mindText("نغمة هادئة", "Gentle tone")
        "file" -> mindText("من ملفاتي", "From my files")
        else -> mindText("بلا صوت (اهتزاز)", "Silent (vibration)")
    }
    GlassCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(mindText("منبّه الاستيقاظ", "Wake-up alarm"), color = Gold, fontWeight = FontWeight.Bold)
                Text(if (settings.enabled) mindText("مفعّل يومياً · $timeLabel", "Enabled daily · $timeLabel") else mindText("غير مفعّل · الوقت المحدد $timeLabel", "Disabled · Set time $timeLabel"), color = Muted, fontSize = 12.sp)
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    userId?.let { SleepAlarm.saveAndSchedule(context, it, settings.hour, settings.minute, enabled) }
                    settings = settings.copy(enabled = enabled)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Gold)
            )
        }
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        userId?.let { SleepAlarm.saveAndSchedule(context, it, hour, minute, settings.enabled) }
                        settings = settings.copy(hour = hour, minute = minute)
                    },
                    settings.hour,
                    settings.minute,
                    true
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ضبط الوقت · $timeLabel", color = Gold) }
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { soundMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(mindText("صوت المنبّه · $soundLabel", "Alarm sound · $soundLabel"), color = Gold)
            }
            DropdownMenu(expanded = soundMenuExpanded, onDismissRequest = { soundMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(mindText("بلا صوت (اهتزاز)", "Silent (vibration)")) },
                    onClick = {
                        soundMenuExpanded = false
                        userId?.let { SleepAlarm.saveSound(context, it, "none") }
                        settings = settings.copy(sound = "none", soundUri = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text(mindText("نغمة هادئة", "Gentle tone")) },
                    onClick = {
                        soundMenuExpanded = false
                        userId?.let { SleepAlarm.saveSound(context, it, "beep") }
                        settings = settings.copy(sound = "beep", soundUri = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text(mindText("اختيار ملف صوتي", "Choose audio file")) },
                    onClick = {
                        soundMenuExpanded = false
                        soundPicker.launch(arrayOf("audio/*"))
                    }
                )
            }
        }
        if (settings.sound == "file") {
            Text(settings.soundUri?.substringAfterLast("/")?.take(36) ?: mindText("ملف صوتي محدد", "Selected audio file"), color = Muted, fontSize = 10.sp)
        }
        Text(mindText("يعمل يومياً ويُعاد ضبطه بعد إعادة تشغيل الهاتف أو تغيير الوقت والمنطقة الزمنية.", "Repeats daily and is rescheduled after a phone restart or time-zone change."), color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun NotesScreen(value: String, onValue: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(mindText("الملاحظات", "Notes"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        GlassCard {
            Text(mindText("مساحة الكتابة", "Writing space"), color = Gold, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                placeholder = { Text(mindText("اكتب أفكارك وملاحظاتك...", "Write your ideas and notes...")) })
            Text(mindText("${value.length} حرف", "${value.length} characters"), color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StepGoalSettingsCard(stepGoal: Int, english: Boolean, onStepGoal: (Int) -> Unit) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable(stepGoal) { mutableStateOf(stepGoal.toString()) }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed in 1_000..100_000

    GlassCard {
        Text(if (english) "Daily goals" else "الأهداف اليومية", fontWeight = FontWeight.Bold, color = Gold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (english) "Daily step goal" else "هدف الخطوات اليومي", fontWeight = FontWeight.SemiBold)
                Text(if (english) "$stepGoal steps" else "$stepGoal خطوة", color = Muted, fontSize = 12.sp)
            }
            OutlinedButton(onClick = {
                input = stepGoal.toString()
                dialogOpen = true
            }) { Text(if (english) "Edit" else "تعديل") }
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(if (english) "Daily step goal" else "هدف الخطوات اليومي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter(Char::isDigit).take(6) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(if (english) "Steps (1,000–100,000)" else "الخطوات (1,000–100,000)") },
                        isError = input.isNotBlank() && !valid
                    )
                    if (input.isNotBlank() && !valid) {
                        Text(if (english) "Enter a value from 1,000 to 100,000." else "أدخل قيمة من 1,000 إلى 100,000.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    onStepGoal(parsed!!)
                    dialogOpen = false
                }) { Text(if (english) "Save" else "حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) { Text(if (english) "Cancel" else "إلغاء") }
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    language: String,
    appearance: String,
    stepGoal: Int,
    onStepGoal: (Int) -> Unit,
    userId: String?,
    importBusy: Boolean,
    importMessage: String?,
    cloudSyncBusy: Boolean,
    cloudSyncMessage: String?,
    pendingRestoreRevision: Long?,
    onUploadCloud: () -> Unit,
    onCheckCloudRestore: () -> Unit,
    onCancelCloudRestore: () -> Unit,
    onConfirmCloudRestore: () -> Unit,
    onImportLegacy: () -> Unit,
    onLanguage: (String) -> Unit,
    onAppearance: (String) -> Unit,
    onAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    val english = language == "en"
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text(if (english) "Settings" else "الإعدادات", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(if (english) "Personalize MIND" else "تخصيص تجربة MIND", color = Muted, fontSize = 13.sp)
        }
        GlassCard {
            Text(if (english) "Appearance" else "المظهر", fontWeight = FontWeight.Bold, color = Gold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to (if (english) "Dark" else "داكن"), "light" to (if (english) "Light" else "فاتح"), "auto" to (if (english) "System" else "حسب الجهاز")).forEach { (key, label) ->
                    FilterChip(
                        selected = appearance == key,
                        onClick = { onAppearance(key) },
                        label = { Text(label) },
                        leadingIcon = if (appearance == key) ({ Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp)) }) else null
                    )
                }
            }
        }
        StepGoalSettingsCard(stepGoal = stepGoal, english = english, onStepGoal = onStepGoal)
        GlassCard {
            Text(if (english) "Language" else "اللغة", fontWeight = FontWeight.Bold, color = Gold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language == "ar", onClick = { onLanguage("ar") }, label = { Text("العربية") })
                FilterChip(selected = language == "en", onClick = { onLanguage("en") }, label = { Text("English") })
            }
        }
        if (userId.isNullOrBlank()) {
            ModuleCard(if (english) "Account" else "الحساب", if (english) "Sign in or create an account" else "تسجيل الدخول أو إنشاء حساب", "auth", Modifier.fillMaxWidth()) { onAccount() }
        } else {
            GlassCard {
                Text(if (english) "Cloud backup" else "النسخة السحابية", fontWeight = FontWeight.Bold, color = Gold)
                Text(
                    if (english) "Upload this account's current local data to its private Firebase space. This is a manual backup; it does not merge or restore data automatically."
                    else "ارفع بيانات هذا الحساب الحالية إلى مساحة Firebase الخاصة به. هذه نسخة احتياطية يدوية ولا تدمج البيانات أو تستعيدها تلقائياً.",
                    color = Muted, fontSize = 12.sp
                )
                cloudSyncMessage?.let { Text(it, color = Muted, fontSize = 12.sp) }
                Button(onClick = onUploadCloud, enabled = !cloudSyncBusy, modifier = Modifier.fillMaxWidth()) {
                    if (cloudSyncBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (english) "Back up to cloud now" else "إنشاء نسخة احتياطية سحابية الآن")
                }
                OutlinedButton(onClick = onCheckCloudRestore, enabled = !cloudSyncBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (english) "Check cloud backup" else "التحقق من النسخة السحابية")
                }
                if (pendingRestoreRevision != null) {
                    AlertDialog(
                        onDismissRequest = onCancelCloudRestore,
                        title = { Text(if (english) "Restore cloud backup?" else "استعادة النسخة السحابية؟") },
                        text = {
                            Text(
                                if (english) "This will replace this account's synced data with cloud revision $pendingRestoreRevision. A private local backup will be saved first. Device language, appearance and active timers are preserved."
                                else "سيتم استبدال البيانات المتزامنة لهذا الحساب بالنسخة السحابية رقم $pendingRestoreRevision. سنحفظ نسخة محلية خاصة أولاً، مع الحفاظ على لغة الجهاز ومظهره والمؤقتات النشطة."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = onConfirmCloudRestore, enabled = !cloudSyncBusy) {
                                Text(if (english) "Back up and restore" else "احفظ واستعد")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = onCancelCloudRestore) {
                                Text(if (english) "Cancel" else "إلغاء")
                            }
                        }
                    )
                }
            }
            GlassCard {
                Text(if (english) "Import this device's local data" else "استيراد بيانات هذا الجهاز", fontWeight = FontWeight.Bold, color = Gold)
                Text(
                    if (english) "One-time, explicit import into this signed-in account. Existing account data is never replaced; the original local copy is kept. Active sleep timers and step sensor baselines are not transferred."
                    else "استيراد لمرة واحدة وباختيارك إلى هذا الحساب. لن نستبدل بيانات الحساب الموجودة، وستبقى النسخة المحلية الأصلية. لا ننقل مؤقت النوم النشط أو خط أساس حساس الخطوات.",
                    color = Muted, fontSize = 12.sp
                )
                importMessage?.let { Text(it, color = Muted, fontSize = 12.sp) }
                Button(onClick = onImportLegacy, enabled = !importBusy, modifier = Modifier.fillMaxWidth()) {
                    if (importBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (english) "Import local data" else "استيراد البيانات المحلية")
                }
            }
            GlassCard {
                Text(if (english) "Account session" else "جلسة الحساب", fontWeight = FontWeight.Bold, color = Gold)
                Text(if (english) "Your account is signed in on this device." else "حسابك مسجل الدخول على هذا الجهاز.", color = Muted, fontSize = 12.sp)
                Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text(if (english) "Sign out" else "تسجيل الخروج")
                }
            }
        }
    }
}

@Composable
private fun ReferenceAtmosphere() {
    val transition = rememberInfiniteTransition(label = "reference-orbs")
    val x0 by transition.animateFloat(-24f, 34f, infiniteRepeatable(tween(30000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orb-x0")
    val y0 by transition.animateFloat(-18f, 28f, infiniteRepeatable(tween(36000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orb-y0")
    val x1 by transition.animateFloat(22f, -30f, infiniteRepeatable(tween(33000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orb-x1")
    val y1 by transition.animateFloat(28f, -20f, infiniteRepeatable(tween(40000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orb-y1")
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x24E8C547), Color.Transparent), radius = 1050f))) {
        Box(Modifier.offset(x0.dp, y0.dp).size(360.dp).align(Alignment.TopStart).blur(78.dp).background(Color(0x22E8C547), CircleShape))
        Box(Modifier.offset(x1.dp, (-20).dp).size(300.dp).align(Alignment.TopEnd).blur(76.dp).background(Color(0x1CE8C547), CircleShape))
        Box(Modifier.offset((-18).dp, y1.dp).size(330.dp).align(Alignment.CenterStart).blur(82.dp).background(Color(0x1AE8C547), CircleShape))
        Box(Modifier.offset(26.dp, y0.dp).size(390.dp).align(Alignment.CenterEnd).blur(88.dp).background(Color(0x20E8C547), CircleShape))
        Box(Modifier.offset(x1.dp, y1.dp).size(280.dp).align(Alignment.BottomStart).blur(82.dp).background(Color(0x18E8C547), CircleShape))
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(Glass)
            .border(1.dp, GlassBorder, RoundedCornerShape(23.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp), content = content
    )
}

@Composable
private fun pageTitle(page: String): String = when (page) {
    "habits" -> mindText("العادات", "Habits")
    "stats" -> mindText("الإحصائيات", "Statistics")
    "awards" -> mindText("الإنجازات", "Achievements")
    "me" -> mindText("حسابي", "Account")
    "water" -> mindText("الماء", "Water")
    "study" -> mindText("الدراسة والتركيز", "Study & focus")
    "fitness" -> mindText("الجري واللياقة", "Running & fitness")
    "food" -> mindText("التغذية", "Nutrition")
    "journal" -> mindText("اليوميات", "Journal")
    "sleep" -> mindText("النوم", "Sleep")
    "notes" -> mindText("الملاحظات", "Notes")
    "settings" -> mindText("الإعدادات", "Settings")
    "auth" -> mindText("الحساب", "Account")
    else -> "MIND"
}
