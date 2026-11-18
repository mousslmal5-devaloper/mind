package app.mind.mobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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

private val Gold = Color(0xFFE8C547)
private val Ink = Color(0xFF0D0D0F)
private val Muted = Color(0xFFA2A2AB)
private val Glass = Color(0x12FFFFFF)
private val GlassBorder = Color(0x20FFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { MindRoot() }
    }
}

@Composable
private fun MindRoot() {
    var page by rememberSaveable { mutableStateOf("home") }
    var water by rememberSaveable { mutableIntStateOf(0) }
    var waterGoal by rememberSaveable { mutableIntStateOf(8) }
    var waterEntries by remember { mutableStateOf<List<WaterEntry>>(emptyList()) }
    var steps by rememberSaveable { mutableIntStateOf(0) }
    var studyMinutes by rememberSaveable { mutableIntStateOf(0) }
    var sleepHours by rememberSaveable { mutableFloatStateOf(0f) }
    var habitMask by rememberSaveable { mutableIntStateOf(0) }
    var habitNames by rememberSaveable { mutableStateOf("الاستيقاظ مبكراً|قراءة|الرياضة|التعلم") }
    var newHabit by rememberSaveable { mutableStateOf("") }
    var noteText by rememberSaveable { mutableStateOf("") }
    var focusSeconds by rememberSaveable { mutableIntStateOf(25 * 60) }
    var focusRunning by rememberSaveable { mutableStateOf(false) }
    var focusMode by rememberSaveable { mutableStateOf("تركيز") }
    var focusDurationMinutes by rememberSaveable { mutableIntStateOf(25) }
    var studySessions by remember { mutableStateOf<List<StudySession>>(emptyList()) }
    val context = LocalContext.current
    var stateLoaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val saved = runCatching { loadMindSavedState(context) }
            .onFailure { Log.e("MIND", "Unable to restore local app data", it) }
            .getOrDefault(MindSavedState())
        water = saved.water
        waterGoal = saved.waterGoal
        waterEntries = saved.waterEntries
        steps = saved.steps
        studyMinutes = saved.studyMinutes
        sleepHours = saved.sleepHours
        habitMask = saved.habitMask
        habitNames = saved.habitNames
        noteText = saved.noteText
        focusSeconds = saved.focusSeconds
        focusMode = saved.focusMode
        focusDurationMinutes = saved.focusDurationMinutes
        studySessions = saved.studySessions
        stateLoaded = true
    }

    LaunchedEffect(
        stateLoaded, water, waterGoal, waterEntries, steps, studyMinutes, sleepHours,
        habitMask, habitNames, noteText, focusSeconds, focusMode,
        focusDurationMinutes, studySessions
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
                        studyMinutes = studyMinutes,
                        sleepHours = sleepHours,
                        habitMask = habitMask,
                        habitNames = habitNames,
                        noteText = noteText,
                        focusSeconds = focusSeconds,
                        focusMode = focusMode,
                        focusDurationMinutes = focusDurationMinutes,
                        studySessions = studySessions
                    )
                )
            }.onFailure { Log.e("MIND", "Unable to persist local app data", it) }
        }
    }
    val habits = remember(habitNames) { habitNames.split("|").filter { it.isNotBlank() } }

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

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Color(0xFF19191D), primary = Gold,
        onPrimary = Color(0xFF15130A), onBackground = Color(0xFFF4F4F6),
        onSurface = Color(0xFFF4F4F6)
    )) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            ReferenceAtmosphere()
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(targetState = page, label = "mind-screen") { target ->
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        if (target != "home") {
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
                                habitsDone = Integer.bitCount(habitMask),
                                habitsTotal = habits.size,
                                water = water,
                                waterGoal = waterGoal,
                                steps = steps,
                                studyMinutes = studyMinutes,
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
                                onToggle = { index -> habitMask = habitMask xor (1 shl index) }
                            )
                            "water" -> WaterScreen(water, waterGoal, waterEntries,
                                { if (water < 40) { water += 1; waterEntries = (waterEntries + WaterEntry(System.currentTimeMillis())).takeLast(20000) } },
                                { if (water > 0) { water -= 1; waterEntries = waterEntries.dropLast(1) } },
                                { waterGoal = it.coerceIn(1, 20) })
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
                            "fitness" -> FitnessScreen(steps, { steps = (steps + 500).coerceAtMost(100000) },
                                { steps = (steps - 500).coerceAtLeast(0) })
                            "sleep" -> SleepScreen(sleepHours, { sleepHours = it })
                            "notes" -> NotesScreen(noteText, { noteText = it })
                            "settings" -> SettingsScreen(onAccount = { page = "auth" })
                            "auth" -> AuthScreen(onSignedIn = { page = "home" })
                            else -> Dashboard(habitsDone = 0, habitsTotal = habits.size, water = water,
                                waterGoal = waterGoal, steps = steps, studyMinutes = studyMinutes,
                                onOpen = { page = it })
                        }
                    }
                }
                NavigationBar(containerColor = Color(0xE6151518), contentColor = Color.White) {
                    NavigationBarItem(page == "home", { page = "home" }, { Icon(Icons.Rounded.Home, null) }, label = { Text("الرئيسية") })
                    NavigationBarItem(page == "habits", { page = "habits" }, { Icon(Icons.Rounded.CheckCircle, null) }, label = { Text("العادات") })
                    NavigationBarItem(page == "water", { page = "water" }, { Icon(Icons.Rounded.WaterDrop, null) }, label = { Text("الماء") })
                    NavigationBarItem(page == "settings", { page = "settings" }, { Icon(Icons.Rounded.Settings, null) }, label = { Text("الإعدادات") })
                }
            }
        }
    }
}

@Composable
private fun Dashboard(
    habitsDone: Int, habitsTotal: Int, water: Int, waterGoal: Int, steps: Int,
    studyMinutes: Int, onOpen: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("MIND", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
            Text("مساحة ليوم أكثر توازناً", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("عقلك، وقتك، حياتك.", color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            GlassCard {
                Text("ملخص اليوم", color = Muted, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${if (habitsTotal == 0) 0 else habitsDone * 100 / habitsTotal}%", fontSize = 31.sp, fontWeight = FontWeight.Bold, color = Gold)
                        Text("إنجاز العادات", color = Muted, fontSize = 12.sp)
                    }
                    MiniStat("الماء", "$water / $waterGoal", "أكواب")
                    MiniStat("الخطوات", steps.toString(), "خطوة")
                }
                LinearProgressIndicator(
                    progress = { if (habitsTotal == 0) 0f else habitsDone.toFloat() / habitsTotal },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(5.dp).clip(CircleShape),
                    color = Gold, trackColor = Color(0x28FFFFFF)
                )
            }
        }
        item { Text("مساحتك", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModuleCard("العادات", "التتبع اليومي", "habits", Modifier.weight(1f), onOpen)
                ModuleCard("الماء", "الهدف اليومي", "water", Modifier.weight(1f), onOpen)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModuleCard("الدراسة والتركيز", "$studyMinutes دقيقة", "study", Modifier.weight(1f), onOpen)
                ModuleCard("الجري واللياقة", "$steps خطوة", "fitness", Modifier.weight(1f), onOpen)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModuleCard("النوم", "سجل ساعاتك", "sleep", Modifier.weight(1f), onOpen)
                ModuleCard("الملاحظات", "مساحة الكتابة", "notes", Modifier.weight(1f), onOpen)
            }
        }
        item { ModuleCard("الحساب والمزامنة", "إدارة حساب MIND", "auth", Modifier.fillMaxWidth(), onOpen) }
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
private fun WaterScreen(water: Int, goal: Int, add: () -> Unit, remove: () -> Unit, setGoal: (Int) -> Unit) {
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
private fun FitnessScreen(steps: Int, add: () -> Unit, remove: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard {
            Text("الجري واللياقة", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("متابعة الخطوات والهدف اليومي", color = Muted, fontSize = 13.sp)
            Text(steps.toString(), color = Gold, fontSize = 42.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text("خطوة", color = Muted)
            LinearProgressIndicator(progress = { (steps / 8000f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).height(7.dp), color = Gold, trackColor = Color(0x22FFFFFF))
            Text("$steps / 8000", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = add, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("+ 500 خطوة") }
                OutlinedButton(onClick = remove, modifier = Modifier.weight(1f)) { Text("− 500") }
            }
        }
        Text("الإضافة اليدوية متاحة هنا؛ عداد الحركة التلقائي وGPS يحتاجان خدمة خلفية وأذونات الجهاز، وسيتم ربطهما في مرحلة التنفيذ الخاصة بهما.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SleepScreen(hours: Float, onHours: (Float) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard {
            Text("النوم", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("سجل مدة نومك", color = Muted)
            Text("%.1f ساعة".format(hours), color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            Slider(value = hours, onValueChange = onHours, valueRange = 0f..16f,
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
            Text("الهدف المرجعي: 8 ساعات", color = Muted, fontSize = 12.sp)
        }
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
private fun SettingsScreen(onAccount: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text("الإعدادات", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("المظهر الأصلي الداكن والذهبي والخلفية المتحركة", color = Muted, fontSize = 13.sp)
        }
        ModuleCard("الحساب", "تسجيل الدخول أو إنشاء حساب", "auth", Modifier.fillMaxWidth()) { onAccount() }
        Text("إعدادات المظهر والصوت والنسخ الاحتياطي والمزامنة ستُربط ببنودها الأصلية من المرجع، دون إضافة خيارات خارج تصميمه.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ReferenceAtmosphere() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val x by transition.animateFloat(-30f, 35f, infiniteRepeatable(tween(30000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "x")
    val y by transition.animateFloat(-20f, 30f, infiniteRepeatable(tween(26000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x20E8C547), Color.Transparent), radius = 950f))) {
        Box(Modifier.offset(x.dp, y.dp).size(300.dp).align(Alignment.TopStart).blur(75.dp).background(Color(0x1FE8C547), CircleShape))
        Box(Modifier.offset((-25).dp, 55.dp).size(280.dp).align(Alignment.BottomEnd).blur(80.dp).background(Color(0x182F79A8), CircleShape))
        Box(Modifier.offset(20.dp, (-25).dp).size(220.dp).align(Alignment.CenterEnd).blur(72.dp).background(Color(0x12E8C547), CircleShape))
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
    "water" -> "الماء"
    "study" -> "الدراسة والتركيز"
    "fitness" -> "الجري واللياقة"
    "sleep" -> "النوم"
    "notes" -> "الملاحظات"
    "settings" -> "الإعدادات"
    "auth" -> "الحساب"
    else -> "MIND"
}
