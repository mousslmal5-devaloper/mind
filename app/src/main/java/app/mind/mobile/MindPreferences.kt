package app.mind.mobile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import org.json.JSONObject
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.mindDataStore by preferencesDataStore(name = "mind_local_data")

data class StudySession(val completedAt: Long, val durationMinutes: Int)
data class WaterEntry(val recordedAt: Long)
data class StepEntry(val recordedAt: Long, val delta: Int)
data class HabitDayRecord(val dateKey: String, val completedMask: Int, val habitCount: Int)
data class SleepSession(val startedAt: Long, val endedAt: Long) { val durationMinutes: Int get() = ((endedAt - startedAt) / 60000L).toInt() }

data class MindSavedState(
    val water: Int = 0,
    val waterGoal: Int = 8,
    val waterEntries: List<WaterEntry> = emptyList(),
    val steps: Int = 0,
    val studyMinutes: Int = 0,
    val sleepHours: Float = 0f,
    val sleepSessions: List<SleepSession> = emptyList(),
    val activeSleepStartedAt: Long = 0L,
    val habitMask: Int = 0,
    val habitNames: String = "الاستيقاظ مبكراً|قراءة|الرياضة|التعلم",
    val noteText: String = "",
    val focusSeconds: Int = 25 * 60,
    val focusMode: String = "تركيز",
    val focusDurationMinutes: Int = 25,
    val studySessions: List<StudySession> = emptyList(),
    val stepEntries: List<StepEntry> = emptyList(),
    val habitHistory: List<HabitDayRecord> = emptyList(),
    val stepSensorInitialized: Boolean = false
)

private object MindPreferenceKeys {
    val water = intPreferencesKey("water")
    val waterGoal = intPreferencesKey("water_goal")
    val steps = intPreferencesKey("steps")
    val studyMinutes = intPreferencesKey("study_minutes")
    val sleepHours = floatPreferencesKey("sleep_hours")
    val habitMask = intPreferencesKey("habit_mask")
    val habitNames = stringPreferencesKey("habit_names")
    val noteText = stringPreferencesKey("note_text")
    val focusSeconds = intPreferencesKey("focus_seconds")
    val focusMode = stringPreferencesKey("focus_mode")
    val focusDurationMinutes = intPreferencesKey("focus_duration_minutes")
    val studySessions = stringPreferencesKey("study_sessions")
    val waterEntries = stringPreferencesKey("water_entries")
    val sleepSessions = stringPreferencesKey("sleep_sessions")
    val activeSleepStartedAt = longPreferencesKey("active_sleep_started_at")
    val stepEntries = stringPreferencesKey("step_entries")
    val habitHistory = stringPreferencesKey("habit_history")
    val stepSensorInitialized = booleanPreferencesKey("step_sensor_initialized")
}

suspend fun loadMindSavedState(context: Context): MindSavedState {
    val preferences = context.mindDataStore.data.first()
    return MindSavedState(
        water = preferences[MindPreferenceKeys.water] ?: 0,
        waterGoal = (preferences[MindPreferenceKeys.waterGoal] ?: 8).coerceIn(1, 20),
        steps = (preferences[MindPreferenceKeys.steps] ?: 0).coerceIn(0, 100_000),
        studyMinutes = (preferences[MindPreferenceKeys.studyMinutes] ?: 0).coerceAtLeast(0),
        sleepHours = (preferences[MindPreferenceKeys.sleepHours] ?: 0f).coerceIn(0f, 16f),
        sleepSessions = decodeSleepSessions(preferences[MindPreferenceKeys.sleepSessions] ?: "[]"),
        activeSleepStartedAt = (preferences[MindPreferenceKeys.activeSleepStartedAt] ?: 0L).coerceAtLeast(0L),
        habitMask = (preferences[MindPreferenceKeys.habitMask] ?: 0).coerceAtLeast(0),
        habitNames = preferences[MindPreferenceKeys.habitNames]
            ?: "الاستيقاظ مبكراً|قراءة|الرياضة|التعلم",
        noteText = preferences[MindPreferenceKeys.noteText] ?: "",
        focusSeconds = (preferences[MindPreferenceKeys.focusSeconds] ?: 25 * 60).coerceAtLeast(0),
        focusMode = preferences[MindPreferenceKeys.focusMode]
            ?.takeIf { it == "تركيز" || it == "استراحة" } ?: "تركيز",
        focusDurationMinutes = (preferences[MindPreferenceKeys.focusDurationMinutes] ?: 25).coerceIn(1, 180),
        studySessions = decodeStudySessions(preferences[MindPreferenceKeys.studySessions] ?: "[]"),
        waterEntries = decodeWaterEntries(preferences[MindPreferenceKeys.waterEntries] ?: "[]"),
        stepEntries = decodeStepEntries(preferences[MindPreferenceKeys.stepEntries] ?: "[]"),
        habitHistory = decodeHabitHistory(preferences[MindPreferenceKeys.habitHistory] ?: "[]"),
        stepSensorInitialized = preferences[MindPreferenceKeys.stepSensorInitialized] ?: false
    )
}

suspend fun saveMindSavedState(context: Context, state: MindSavedState) {
    context.mindDataStore.edit { preferences ->
        preferences[MindPreferenceKeys.water] = state.water.coerceIn(0, 40)
        preferences[MindPreferenceKeys.waterGoal] = state.waterGoal.coerceIn(1, 20)
        preferences[MindPreferenceKeys.steps] = state.steps.coerceIn(0, 100_000)
        preferences[MindPreferenceKeys.studyMinutes] = state.studyMinutes.coerceAtLeast(0)
        preferences[MindPreferenceKeys.sleepHours] = state.sleepHours.coerceIn(0f, 16f)
        preferences[MindPreferenceKeys.sleepSessions] = encodeSleepSessions(state.sleepSessions)
        preferences[MindPreferenceKeys.activeSleepStartedAt] = state.activeSleepStartedAt.coerceAtLeast(0L)
        preferences[MindPreferenceKeys.habitMask] = state.habitMask.coerceAtLeast(0)
        preferences[MindPreferenceKeys.habitNames] = state.habitNames
        preferences[MindPreferenceKeys.noteText] = state.noteText
        preferences[MindPreferenceKeys.focusSeconds] = state.focusSeconds.coerceAtLeast(0)
        preferences[MindPreferenceKeys.focusMode] =
            if (state.focusMode == "استراحة") "استراحة" else "تركيز"
        preferences[MindPreferenceKeys.focusDurationMinutes] = state.focusDurationMinutes.coerceIn(1, 180)
        preferences[MindPreferenceKeys.studySessions] = encodeStudySessions(state.studySessions)
        preferences[MindPreferenceKeys.waterEntries] = encodeWaterEntries(state.waterEntries)
        preferences[MindPreferenceKeys.stepEntries] = encodeStepEntries(state.stepEntries)
        preferences[MindPreferenceKeys.habitHistory] = encodeHabitHistory(state.habitHistory)
        preferences[MindPreferenceKeys.stepSensorInitialized] = state.stepSensorInitialized
    }
}


private fun encodeStudySessions(sessions: List<StudySession>): String {
    val array = JSONArray()
    sessions.takeLast(5000).forEach { session ->
        array.put(JSONObject().put("completedAt", session.completedAt)
            .put("durationMinutes", session.durationMinutes.coerceIn(1, 180)))
    }
    return array.toString()
}

private fun decodeStudySessions(raw: String): List<StudySession> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val timestamp = item.optLong("completedAt", 0L)
            val minutes = item.optInt("durationMinutes", 0)
            if (timestamp > 0L && minutes in 1..180) add(StudySession(timestamp, minutes))
        }
    }.takeLast(5000)
}.getOrDefault(emptyList())

private fun encodeStepEntries(entries: List<StepEntry>): String = JSONArray().apply {
    entries.takeLast(30000).forEach { put(JSONObject().put("recordedAt", it.recordedAt).put("delta", it.delta)) }
}.toString()

private fun decodeStepEntries(raw: String): List<StepEntry> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val timestamp = item.optLong("recordedAt", 0L)
            val delta = item.optInt("delta", 0)
            if (timestamp > 0L && delta in -100000..100000 && delta != 0) add(StepEntry(timestamp, delta))
        }
    }.takeLast(30000)
}.getOrDefault(emptyList())

private fun encodeHabitHistory(records: List<HabitDayRecord>): String = JSONArray().apply {
    records.takeLast(730).forEach { put(JSONObject().put("dateKey", it.dateKey).put("completedMask", it.completedMask).put("habitCount", it.habitCount)) }
}.toString()

private fun decodeHabitHistory(raw: String): List<HabitDayRecord> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val date = item.optString("dateKey", "")
            val mask = item.optInt("completedMask", -1)
            val count = item.optInt("habitCount", 0)
            if (date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && mask >= 0 && count in 0..20)
                add(HabitDayRecord(date, mask, count))
        }
    }.distinctBy { it.dateKey }.takeLast(730)
}.getOrDefault(emptyList())

private fun encodeWaterEntries(entries: List<WaterEntry>): String = JSONArray().apply {
    entries.takeLast(20000).forEach { put(it.recordedAt) }
}.toString()

private fun decodeWaterEntries(raw: String): List<WaterEntry> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val timestamp = array.optLong(index, 0L)
            if (timestamp > 0L) add(WaterEntry(timestamp))
        }
    }.takeLast(20000)
}.getOrDefault(emptyList())

private fun encodeSleepSessions(sessions: List<SleepSession>): String = JSONArray().apply {
    sessions.takeLast(2000).forEach { put(JSONObject().put("startedAt", it.startedAt).put("endedAt", it.endedAt)) }
}.toString()

private fun decodeSleepSessions(raw: String): List<SleepSession> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val start = item.optLong("startedAt", 0L)
            val end = item.optLong("endedAt", 0L)
            val duration = (end - start) / 60000L
            if (start > 0L && end > start && duration in 1..960) add(SleepSession(start, end))
        }
    }.takeLast(2000)
}.getOrDefault(emptyList())