package app.mind.mobile

import android.os.Bundle
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
            sensorSupported = intent.getBooleanExtra(StepCounterService.EXTRA_SUPPORTED, false)
            sensorSteps = intent.getIntExtra(StepCounterService.EXTRA_TOTAL, 0).coerceIn(0, 100000)
            sensorDelta = intent.getIntExtra(StepCounterService.EXTRA_DELTA, 0).coerceIn(0, 100000)
            sensorRevision++
            val ack = Intent(this@MainActivity, StepCounterService::class.java)
                .setAction(StepCounterService.ACTION_ACK)
                .putExtra(StepCounterService.EXTRA_TOTAL, sensorSteps)
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
        val serviceIntent = Intent(this, StepCounterService::class.java)
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

    LaunchedEffect(Unit) {
        val saved = runCatching { loadMindSavedState(context) }
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
        stateLoaded = true
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
        stateLoaded, water, waterGoal, waterEntries, steps, studyMinutes, sleepHours, sleepSessions, activeSleepStartedAt,
        habitMask, habitNames, habitHistory, stepEntries, noteText, focusSeconds, focusMode, uiLanguage, uiAppearance,
        focusDurationMinutes, studySessions, workoutSessions, foodEntries, journalEntries
    ) {
        if (stateLoaded) {
            runCatching {
                saveMindSavedState(
                    context,
                    MindSavedState(
                        water = water,
                        waterGoal = waterGoal,
                        waterEntries = waterEntries,
                        steps = steps,
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
                    )
                )
            }.onFailure { Log.e("MIND", "Unable to persist local app data", it) }
        }
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

    LaunchedEffect(focusRunning, focusSeconds) {
        if (focusRunning && focusSeconds > 0) {
            delay(1000)
            focusSeconds -= 1
            if (focusSeconds == 0) {
                focusRunning = false
                if (focusMode == "تركيز") {
                    studySessions = (studySessions + StudySession(System.currentTimeMillis(), focusDurationMinutes)).takeLast(5000)
                    studyMinutes += focusDurationMinutes
                    focusMode = "استراحة"
                    focusSeconds = 5 * 60
                } else {
                    focusMode = "تركيز"
                    focusSeconds = focusDurationMinutes * 60
                }
            }
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
            LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(fontFamily = CairoFamily)
        ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
            val wideLayout = maxWidth >= 700.dp
            val expandedRail = maxWidth >= 1000.dp
            ReferenceAtmosphere()
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
                                        Icon(Icons.Rounded.ArrowBack, "رجوع", tint = Gold)
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
                                    onMode = { focusMode = it; focusRunning = false; focusSeconds = if (it == "تركيز") focusDurationMinutes * 60 else 5 * 60 },
                                    onPreset = { minutes -> focusRunning = false; focusDurationMinutes = minutes; if (focusMode == "تركيز") focusSeconds = minutes * 60 },
                                    onToggle = { focusRunning = !focusRunning && focusSeconds > 0 },
                                    onReset = { focusRunning = false; focusSeconds = if (focusMode == "تركيز") focusDurationMinutes * 60 else 5 * 60 },
                                    onAddTime = { focusSeconds += 5 * 60; if (focusMode == "تركيز") focusDurationMinutes = (focusDurationMinutes + 5).coerceAtMost(180) }
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
                                "fitness" -> FitnessScreen(steps, stepSensorAvailable, workoutSessions, onOpenFood = { page = "food" }, onSessionCompleted = { session ->
                                    workoutSessions = (workoutSessions.filterNot { it.startedAt == session.startedAt } + session).takeLast(2000)
                                }, onDeleteSession = { session ->
                                    workoutSessions = workoutSessions.filterNot { it.startedAt == session.startedAt }
                                }, onRefreshSessions = {
                                    appScope.launch { workoutSessions = loadMindSavedState(context).workoutSessions }
                                })
                                "sleep" -> SleepScreen(
                                    hours = sleepHours, sessions = sleepSessions,
                                    activeStartedAt = activeSleepStartedAt,
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
                                    onAccount = { page = "auth" }
                                )
                                "auth" -> AuthScreen(onSignedIn = { page = "home" })
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
        RailItem("الرئيسية", "home", Icons.Rounded.Home, page, expanded, onSelect)
        RailItem("العادات", "habits", Icons.Rounded.CheckCircle, page, expanded, onSelect)
        RailItem("الإحصائيات", "stats", Icons.Rounded.School, page, expanded, onSelect)
        RailItem("الإنجازات", "awards", Icons.Rounded.CheckCircle, page, expanded, onSelect)
        RailItem("حسابي", "me", Icons.Rounded.Settings, page, expanded, onSelect)
        Spacer(Modifier.weight(1f))
        if (expanded) Text("عقلك، وقتك، حياتك", color = Muted, fontSize = 11.sp)
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
        containerColor = Color(0xE6151518), contentColor = Color.White,
        tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        NavigationBarItem(page == "home", { onSelect("home") }, { Icon(Icons.Rounded.Home, null) }, label = { Text("الرئيسية", fontSize = 10.sp) })
        NavigationBarItem(page == "habits", { onSelect("habits") }, { Icon(Icons.Rounded.CheckCircle, null) }, label = { Text("العادات", fontSize = 10.sp) })
        NavigationBarItem(page == "stats", { onSelect("stats") }, { Icon(Icons.Rounded.School, null) }, label = { Text("الإحصائيات", fontSize = 10.sp) })
        NavigationBarItem(page == "awards", { onSelect("awards") }, { Icon(Icons.Rounded.CheckCircle, null) }, label = { Text("الإنجازات", fontSize = 10.sp) })
        NavigationBarItem(page == "me" || page == "settings", { onSelect("me") }, { Icon(Icons.Rounded.Settings, null) }, label = { Text("حسابي", fontSize = 10.sp) })
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
    val quotes = listOf("الاستمرارية أهم من الكمال", "رحلة الألف ميل تبدأ بخطوة", "افعل اليوم ما يشكرك عليه غدك", "خطوة صغيرة كل يوم تصنع فرقاً كبيراً")
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
                    Text("أهلاً بك في MIND", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 35.sp)
                    Text("عقلك، وقتك، حياتك", color = Muted, fontSize = 14.sp)
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
                Text("إنجاز اليوم", color = Ink.copy(alpha = .75f), fontSize = 13.sp)
                Text("$done من $total", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                Text("عادات مكتملة", color = Ink.copy(alpha = .78f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(modifier: Modifier, onOpen: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            QuickAccessCard("العادات", "تتبع يومك", "habits", Icons.Rounded.CheckCircle, Modifier.weight(1f), onOpen)
            QuickAccessCard("النوم", "راحة أفضل", "sleep", Icons.Rounded.Bedtime, Modifier.weight(1f), onOpen)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            QuickAccessCard("الدراسة", "جلسات التركيز", "study", Icons.Rounded.School, Modifier.weight(1f), onOpen)
            QuickAccessCard("اللياقة", "الجري والحركة", "fitness", Icons.Rounded.DirectionsRun, Modifier.weight(1f), onOpen)
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
            Text("الماء", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("$water / $goal أكواب", color = Muted, fontSize = 12.sp)
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
        Text("تقدمك نحو هدفك اليومي", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TodayHabitsCard(habits: List<String>, habitMask: Int, onToggle: (Int) -> Unit) {
    GlassCard {
        Text("عادات اليوم", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        if (habits.isEmpty()) {
            Text("لا توجد عادات بعد — أضف أولى عاداتك", color = Muted, fontSize = 12.sp)
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
                    Text(if (checked) "تم" else "اليوم", color = Muted, fontSize = 10.sp)
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
                Text("عادات اليوم", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${Integer.bitCount(mask)} من ${habits.size} مكتملة", color = Muted, fontSize = 13.sp)
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
                    Text(if (done) "مكتملة" else "اليوم", color = if (done) Gold else Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            GlassCard {
                Text("إضافة عادة", fontWeight = FontWeight.Bold, color = Gold)
                OutlinedTextField(value = newHabit, onValueChange = onNewHabit, modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم العادة") }, singleLine = true)
                Button(onClick = onAdd, enabled = newHabit.isNotBlank() && habits.size < 20,
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                    Text("إضافة")
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
            Text("الماء", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("تقدمك نحو الهدف اليومي", color = Muted, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { (water.toFloat() / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.size(170.dp), strokeWidth = 12.dp, color = Gold,
                    trackColor = Color(0x24FFFFFF), strokeCap = StrokeCap.Round)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$water", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Text("من $goal أكواب", color = Muted, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = remove, modifier = Modifier.weight(1f)) { Text("−") }
                Button(onClick = add, modifier = Modifier.weight(2f), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("إضافة كوب") }
            }
        }
        GlassCard {
            Text("التوزيع خلال اليوم", fontWeight = FontWeight.Bold)
            Text("عدد الأكواب المسجلة في كل ساعة", color = Muted, fontSize = 12.sp)
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
            Text("الإجمالي المسجل اليوم: ${hourly.sum()} كوب", color = Gold, fontSize = 12.sp)
        }
        GlassCard {
            Text("الهدف اليومي", fontWeight = FontWeight.Bold)
            Text("$goal أكواب", color = Gold, fontSize = 20.sp)
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
                Text("الدراسة والتركيز", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("جلسات التركيز والاستراحة", color = Muted, fontSize = 13.sp)
                OutlinedButton(onClick = onOpenMindMap, modifier = Modifier.fillMaxWidth()) { Text("فتح الخرائط الذهنية") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "تركيز", onClick = { onMode("تركيز") }, label = { Text("تركيز") })
                    FilterChip(selected = mode == "استراحة", onClick = { onMode("استراحة") }, label = { Text("استراحة 5 د") })
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { (seconds.toFloat() / (if (mode == "تركيز") focusDurationMinutes * 60f else 300f)).coerceIn(0f, 1f) },
                        modifier = Modifier.size(190.dp), strokeWidth = 10.dp, color = Gold,
                        trackColor = Color(0x22FFFFFF), strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                        Text(if (mode == "تركيز") "تركيز" else "راحة", color = Muted, fontSize = 12.sp)
                    }
                }
                if (mode == "تركيز") {
                    Text("مدة الجلسة", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 25, 45, 60).forEach { duration ->
                            FilterChip(selected = focusDurationMinutes == duration,
                                onClick = { onPreset(duration) }, label = { Text("$duration د") })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onToggle, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                        Text(if (running) "إيقاف مؤقت" else "ابدأ")
                    }
                    OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("إعادة") }
                }
                TextButton(onClick = onAddTime, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("+ 5 دقائق") }
            }
        }
        item {
            GlassCard {
                Text("ملخص التركيز اليوم", color = Muted, fontSize = 13.sp)
                Text("$todayMinutes دقيقة", color = Gold, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("الإجمالي المسجل: $totalMinutes دقيقة · " + sessions.size + " جلسة", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            GlassCard {
                Text("آخر 7 أيام", fontWeight = FontWeight.Bold)
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
        item { Text("سجل الجلسات", fontWeight = FontWeight.Bold, color = Gold) }
        if (sessions.isEmpty()) {
            item { Text("لا توجد جلسات مكتملة بعد. ستظهر جلساتك هنا تلقائياً.", color = Muted, fontSize = 13.sp) }
        } else {
            items(sessions.sortedByDescending { it.completedAt }.take(30)) { session ->
                val date = java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm", java.util.Locale("ar"))
                    .format(java.util.Date(session.completedAt))
                Surface(color = Glass, shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("جلسة تركيز", fontWeight = FontWeight.SemiBold)
                            Text(date, color = Muted, fontSize = 11.sp)
                        }
                        Text(session.durationMinutes.toString() + " د", color = Gold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FitnessScreen(steps: Int, sensorAvailable: Boolean, sessions: List<WorkoutSession>, onOpenFood: () -> Unit, onSessionCompleted: (WorkoutSession) -> Unit, onDeleteSession: (WorkoutSession) -> Unit, onRefreshSessions: () -> Unit) {
    val context = LocalContext.current
    var workoutType by rememberSaveable { mutableStateOf("جري") }
    var tracking by rememberSaveable { mutableStateOf(false) }
    var distanceMeters by rememberSaveable { mutableFloatStateOf(0f) }
    var elapsedSeconds by rememberSaveable { mutableLongStateOf(0L) }
    var gpsReady by rememberSaveable { mutableStateOf(false) }
    var gpsMessage by rememberSaveable { mutableStateOf("لم تبدأ جلسة بعد") }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(context, GpsWorkoutService::class.java).setAction(GpsWorkoutService.ACTION_START).putExtra(GpsWorkoutService.EXTRA_TYPE, workoutType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            tracking = true
            gpsMessage = "جارٍ الحصول على إشارة GPS"
        } else gpsMessage = "يلزم السماح بالوصول إلى الموقع لقياس المسافة"
    }
    DisposableEffect(context) {
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
                    val type = intent.getStringExtra(GpsWorkoutService.EXTRA_TYPE) ?: "جري"
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
    val distanceText = if (distanceMeters >= 1000f) "%.2f كم".format(java.util.Locale.getDefault(), distanceMeters / 1000f) else "%d م".format(distanceMeters.toInt())
    val timeText = "%02d:%02d:%02d".format(elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GlassCard {
                Text("الجري واللياقة", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("تتبّع المسافة والمدة مباشرة عبر GPS الهاتف", color = Muted, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("مشي", "جري", "دراجة").forEach { type ->
                        FilterChip(selected = workoutType == type, onClick = { if (!tracking) workoutType = type }, label = { Text(type) }, enabled = !tracking)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("المسافة", color = Muted, fontSize = 12.sp)
                        Text(distanceText, color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("المدة", color = Muted, fontSize = 12.sp)
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
                            val intent = Intent(context, GpsWorkoutService::class.java).setAction(GpsWorkoutService.ACTION_START).putExtra(GpsWorkoutService.EXTRA_TYPE, workoutType)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                            tracking = true
                            gpsMessage = "جارٍ الحصول على إشارة GPS"
                            distanceMeters = 0f
                            elapsedSeconds = 0L
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (tracking) Color(0xFFB94B4B) else Gold, contentColor = if (tracking) Color.White else Ink)
                ) { Text(if (tracking) "إنهاء الجلسة" else "بدء ${workoutType}") }
            }
        }
        item {
            GlassCard {
                Text("خطوات اليوم", color = Gold, fontWeight = FontWeight.Bold)
                Text(steps.toString(), color = Gold, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(if (sensorAvailable) "تُقاس تلقائياً من مستشعر الخطوات" else "مستشعر الخطوات غير متاح حالياً", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("سجل النشاط", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("تُحفظ الجلسات على هذا الجهاز تلقائياً", color = Muted, fontSize = 11.sp)
            }
        }
        if (sessions.isEmpty()) {
            item { GlassCard { Text("لا توجد جلسات محفوظة بعد", color = Muted, fontSize = 13.sp) } }
        } else {
            items(sessions.sortedByDescending { it.endedAt }.take(50)) { session ->
                GlassCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(session.type, fontWeight = FontWeight.Bold)
                            Text(java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm", java.util.Locale("ar")).format(java.util.Date(session.startedAt)), color = Muted, fontSize = 11.sp)
                            val distanceLabel = if (session.distanceMeters >= 1000f) "%.2f كم".format(session.distanceKm) else "%d م".format(session.distanceMeters.toInt())
                            Text("${session.durationSeconds / 60L} دقيقة · $distanceLabel", color = Muted, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("GPS", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            TextButton(onClick = { onDeleteSession(session) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                Text("حذف", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            GlassCard {
                Text("التغذية", color = Gold, fontWeight = FontWeight.Bold)
                Text("سجّل السعرات اليومية وتابع سجل التغذية", color = Muted, fontSize = 12.sp)
                Button(onClick = onOpenFood, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                    Text("فتح التغذية")
                }
            }
        }
        item { Text("ملاحظة: قياس المسافة يحتاج رؤية واضحة للأقمار الصناعية. قد تتأخر القراءة داخل المباني، ولن تُحتسب المواقع غير الدقيقة.", color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun SleepScreen(hours: Float, sessions: List<SleepSession>, activeStartedAt: Long, onStart: () -> Unit, onWake: () -> Unit, onDelete: (SleepSession) -> Unit) {
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
        item { SleepAlarmCard() }
        item {
            GlassCard {
                Text("النوم", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("سجّل بداية نومك واستيقاظك لاحتساب المدة تلقائياً", color = Muted, fontSize = 13.sp)
                Text(
                    if (active) "%d ساعة %02d دقيقة".format(activeMinutes / 60L, activeMinutes % 60L) else "%.1f ساعة".format(hours),
                    color = Gold, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)
                )
                if (active) {
                    val started = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(activeStartedAt))
                    Text("وقت البداية: $started · مدة الجلسة الحالية", color = Muted, fontSize = 12.sp)
                    Button(onClick = onWake, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("تسجيل الاستيقاظ") }
                } else {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("بدء النوم") }
                }
                Text("الهدف المرجعي: 8 ساعات · المدة المقبولة للجلسة: حتى 16 ساعة", color = Muted, fontSize = 11.sp)
            }
        }
        item {
            val recentSessions = sessions.sortedByDescending { it.endedAt }.take(7)
            val averageMinutes = recentSessions.takeIf { it.isNotEmpty() }?.map { it.durationMinutes }?.average()
            val goalProgress = ((averageMinutes ?: 0.0) / 480.0).toFloat().coerceIn(0f, 1f)
            GlassCard {
                Text("ملخص النوم", color = Gold, fontWeight = FontWeight.Bold)
                Text("عدد الجلسات: ${sessions.size}")
                Text(latest?.let { "آخر جلسة: %.1f ساعة".format(it.durationMinutes / 60f) } ?: "لا توجد جلسات مسجلة بعد", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("متوسط آخر 7 جلسات", color = Muted, fontSize = 11.sp)
                        Text(averageMinutes?.let { "%.1f ساعة".format(it / 60.0) } ?: "—", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("مقارنة بهدف 8 ساعات", color = Muted, fontSize = 11.sp)
                        Text(averageMinutes?.let { "${(it / 480.0 * 100).toInt()}%" } ?: "—", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = goalProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = Gold,
                    trackColor = GlassBorder
                )
                Text("المتوسط محسوب من الجلسات المسجلة فقط، ولا تُضاف بيانات تقديرية.", color = Muted, fontSize = 10.sp)
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
                Text("النوم خلال آخر 7 أيام", color = Gold, fontWeight = FontWeight.Bold)
                Text("المدة موزعة حسب اليوم اعتماداً على جلساتك المسجلة فقط", color = Muted, fontSize = 11.sp)
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
                Text("الساعات فوق الأعمدة؛ قد يكون اليوم بلا بيانات إذا لم تسجل جلسة.", color = Muted, fontSize = 10.sp)
            }
        }
        item { Text("سجل النوم", color = Gold, fontWeight = FontWeight.Bold) }
        if (sessions.isEmpty()) {
            item { Text("ستظهر جلسات نومك هنا بعد تسجيل الاستيقاظ.", color = Muted, fontSize = 13.sp) }
        } else {
            items(sessions.sortedByDescending { it.endedAt }) { session ->
                val formatter = java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm", java.util.Locale.getDefault())
                Surface(color = Glass, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("%.1f ساعة".format(session.durationMinutes / 60f), color = Gold, fontWeight = FontWeight.Bold)
                            Text("من ${formatter.format(java.util.Date(session.startedAt))}", color = Muted, fontSize = 11.sp)
                            Text("إلى ${formatter.format(java.util.Date(session.endedAt))}", color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onDelete(session) }) { Text("حذف", color = Muted) }
                    }
                }
            }
        }
    }
}


@Composable
private fun SleepAlarmCard() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(SleepAlarm.read(context)) }
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
            SleepAlarm.saveSound(context, "file", uri.toString())
            settings = settings.copy(sound = "file", soundUri = uri.toString())
        } else if (settings.sound == "file" && settings.soundUri == null) {
            SleepAlarm.saveSound(context, "none")
            settings = settings.copy(sound = "none")
        }
    }
    val soundLabel = when (settings.sound) {
        "beep" -> "نغمة هادئة"
        "file" -> "من ملفاتي"
        else -> "بلا صوت (اهتزاز)"
    }
    GlassCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("منبّه الاستيقاظ", color = Gold, fontWeight = FontWeight.Bold)
                Text(if (settings.enabled) "مفعّل يومياً · $timeLabel" else "غير مفعّل · الوقت المحدد $timeLabel", color = Muted, fontSize = 12.sp)
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    SleepAlarm.saveAndSchedule(context, settings.hour, settings.minute, enabled)
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
                        SleepAlarm.saveAndSchedule(context, hour, minute, settings.enabled)
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
                Text("صوت المنبّه · $soundLabel", color = Gold)
            }
            DropdownMenu(expanded = soundMenuExpanded, onDismissRequest = { soundMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("بلا صوت (اهتزاز)") },
                    onClick = {
                        soundMenuExpanded = false
                        SleepAlarm.saveSound(context, "none")
                        settings = settings.copy(sound = "none", soundUri = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("نغمة هادئة") },
                    onClick = {
                        soundMenuExpanded = false
                        SleepAlarm.saveSound(context, "beep")
                        settings = settings.copy(sound = "beep", soundUri = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("اختيار ملف صوتي") },
                    onClick = {
                        soundMenuExpanded = false
                        soundPicker.launch(arrayOf("audio/*"))
                    }
                )
            }
        }
        if (settings.sound == "file") {
            Text(settings.soundUri?.substringAfterLast("/")?.take(36) ?: "ملف صوتي محدد", color = Muted, fontSize = 10.sp)
        }
        Text("يعمل يومياً ويُعاد ضبطه بعد إعادة تشغيل الهاتف أو تغيير الوقت والمنطقة الزمنية.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun NotesScreen(value: String, onValue: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الملاحظات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        GlassCard {
            Text("مساحة الكتابة", color = Gold, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                placeholder = { Text("اكتب أفكارك وملاحظاتك...") })
            Text("${value.length} حرف", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsScreen(
    language: String,
    appearance: String,
    onLanguage: (String) -> Unit,
    onAppearance: (String) -> Unit,
    onAccount: () -> Unit
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
        GlassCard {
            Text(if (english) "Language" else "اللغة", fontWeight = FontWeight.Bold, color = Gold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language == "ar", onClick = { onLanguage("ar") }, label = { Text("العربية") })
                FilterChip(selected = language == "en", onClick = { onLanguage("en") }, label = { Text("English") })
            }
        }
        ModuleCard(if (english) "Account" else "الحساب", if (english) "Sign in or create an account" else "تسجيل الدخول أو إنشاء حساب", "auth", Modifier.fillMaxWidth()) { onAccount() }
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

private fun pageTitle(page: String): String = when (page) {
    "habits" -> "العادات"
    "stats" -> "الإحصائيات"
    "awards" -> "الإنجازات"
    "me" -> "حسابي"
    "water" -> "الماء"
    "study" -> "الدراسة والتركيز"
    "fitness" -> "الجري واللياقة"
    "food" -> "التغذية"
    "journal" -> "اليوميات"
    "sleep" -> "النوم"
    "notes" -> "الملاحظات"
    "settings" -> "الإعدادات"
    "auth" -> "الحساب"
    else -> "MIND"
}
