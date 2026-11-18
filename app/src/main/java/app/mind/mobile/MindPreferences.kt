package app.mind.mobile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import org.json.JSONObject
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.mindDataStore by preferencesDataStore(name = "mind_local_data")

data class StudySession(val completedAt: Long, val durationMinutes: Int)
data class WaterEntry(val recordedAt: Long)

data class MindSavedState(
    val water: Int = 0,
    val waterGoal: Int = 8,\n    val waterEntries: List<WaterEntry> = emptyList(),
    val steps: Int = 0,
    val studyMinutes: Int = 0,
    val sleepHours: Float = 0f,
    val habitMask: Int = 0,
    val habitNames: String = "الاستيقاظ مبكراً|قراءة|الرياضة|التعلم",
    val noteText: String = "",
    val focusSeconds: Int = 25 * 60,
    val focusMode: String = "تركيز",
    val focusDurationMinutes: Int = 25,
    val studySessions: List<StudySession> = emptyList()
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
    val studySessions = stringPreferencesKey("study_sessions")\n    val waterEntries = stringPreferencesKey("water_entries")
}

suspend fun loadMindSavedState(context: Context): MindSavedState {
    val preferences = context.mindDataStore.data.first()
    return MindSavedState(
        water = preferences[MindPreferenceKeys.water] ?: 0,
        waterGoal = (preferences[MindPreferenceKeys.waterGoal] ?: 8).coerceIn(1, 20),
        steps = (preferences[MindPreferenceKeys.steps] ?: 0).coerceIn(0, 100_000),
        studyMinutes = (preferences[MindPreferenceKeys.studyMinutes] ?: 0).coerceAtLeast(0),
        sleepHours = (preferences[MindPreferenceKeys.sleepHours] ?: 0f).coerceIn(0f, 16f),
        habitMask = (preferences[MindPreferenceKeys.habitMask] ?: 0).coerceAtLeast(0),
        habitNames = preferences[MindPreferenceKeys.habitNames]
            ?: "الاستيقاظ مبكراً|قراءة|الرياضة|التعلم",
        noteText = preferences[MindPreferenceKeys.noteText] ?: "",
        focusSeconds = (preferences[MindPreferenceKeys.focusSeconds] ?: 25 * 60).coerceAtLeast(0),
        focusMode = preferences[MindPreferenceKeys.focusMode]
            ?.takeIf { it == "تركيز" || it == "استراحة" } ?: "تركيز",
        focusDurationMinutes = (preferences[MindPreferenceKeys.focusDurationMinutes] ?: 25).coerceIn(1, 180),
        studySessions = decodeStudySessions(preferences[MindPreferenceKeys.studySessions] ?: "[]"),\n        waterEntries = decodeWaterEntries(preferences[MindPreferenceKeys.waterEntries] ?: "[]")
    )
}

suspend fun saveMindSavedState(context: Context, state: MindSavedState) {
    context.mindDataStore.edit { preferences ->
        preferences[MindPreferenceKeys.water] = state.water.coerceIn(0, 40)
        preferences[MindPreferenceKeys.waterGoal] = state.waterGoal.coerceIn(1, 20)
        preferences[MindPreferenceKeys.steps] = state.steps.coerceIn(0, 100_000)
        preferences[MindPreferenceKeys.studyMinutes] = state.studyMinutes.coerceAtLeast(0)
        preferences[MindPreferenceKeys.sleepHours] = state.sleepHours.coerceIn(0f, 16f)
        preferences[MindPreferenceKeys.habitMask] = state.habitMask.coerceAtLeast(0)
        preferences[MindPreferenceKeys.habitNames] = state.habitNames
        preferences[MindPreferenceKeys.noteText] = state.noteText
        preferences[MindPreferenceKeys.focusSeconds] = state.focusSeconds.coerceAtLeast(0)
        preferences[MindPreferenceKeys.focusMode] =
            if (state.focusMode == "استراحة") "استراحة" else "تركيز"
        preferences[MindPreferenceKeys.focusDurationMinutes] = state.focusDurationMinutes.coerceIn(1, 180)
        preferences[MindPreferenceKeys.studySessions] = encodeStudySessions(state.studySessions)\n        preferences[MindPreferenceKeys.waterEntries] = encodeWaterEntries(state.waterEntries)
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
