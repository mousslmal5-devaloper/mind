package app.mind.mobile

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser

/**
 * Versioned, local-only preparation model for cloud sync.
 * Runtime counters, active timers, sensor baselines and presentation settings are excluded.
 */
internal data class MindCloudSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val revision: Long,
    val waterGoal: Int,
    val habitNames: String,
    val noteText: String,
    val focusMode: String,
    val focusDurationMinutes: Int,
    val waterEntries: List<WaterEntry>,
    val stepEntries: List<StepEntry>,
    val habitHistory: List<HabitDayRecord>,
    val studySessions: List<StudySession>,
    val sleepSessions: List<SleepSession>,
    val workoutSessions: List<WorkoutSession>,
    val foodEntries: List<FoodEntry>,
    val journalEntries: List<JournalEntry>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

internal object CloudSyncCodec {
    private val gson = Gson()

    fun fromLocal(state: MindSavedState, revision: Long): MindCloudSnapshot {
        require(revision >= 0L) { "Revision cannot be negative" }
        return MindCloudSnapshot(
            revision = revision,
            waterGoal = state.waterGoal.coerceIn(1, 20),
            habitNames = state.habitNames.take(2_000),
            noteText = state.noteText.take(50_000),
            focusMode = state.focusMode.takeIf { it == "تركيز" || it == "استراحة" } ?: "تركيز",
            focusDurationMinutes = state.focusDurationMinutes.coerceIn(1, 180),
            waterEntries = state.waterEntries.takeLast(20_000),
            stepEntries = state.stepEntries.takeLast(30_000),
            habitHistory = state.habitHistory.takeLast(730),
            studySessions = state.studySessions.takeLast(5_000),
            sleepSessions = state.sleepSessions.takeLast(5_000),
            workoutSessions = state.workoutSessions.takeLast(5_000),
            foodEntries = state.foodEntries.takeLast(20_000),
            journalEntries = state.journalEntries.takeLast(20_000)
        )
    }

    /** Converts a validated cloud snapshot into local state while retaining device-only settings and timers. */
    fun toLocal(snapshot: MindCloudSnapshot, currentLocal: MindSavedState): MindSavedState {
        validate(snapshot)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis()))
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayWater = snapshot.waterEntries.count { it.recordedAt >= todayStart }.coerceIn(0, 40)
        val todaySteps = snapshot.stepEntries
            .filter { it.recordedAt >= todayStart }
            .sumOf { it.delta.coerceAtLeast(0) }
            .coerceIn(0, 100_000)
        val todayHabits = snapshot.habitHistory.lastOrNull { it.dateKey == today }?.completedMask ?: 0
        val totalStudyMinutes = snapshot.studySessions.sumOf { it.durationMinutes.coerceAtLeast(0) }
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val latestSleepHours = snapshot.sleepSessions.maxByOrNull { it.endedAt }
            ?.let { ((it.endedAt - it.startedAt).coerceAtLeast(0L) / 3_600_000f).coerceIn(0f, 16f) }
            ?: currentLocal.sleepHours

        return currentLocal.copy(
            water = todayWater,
            waterGoal = snapshot.waterGoal,
            waterEntries = snapshot.waterEntries,
            steps = todaySteps,
            stepEntries = snapshot.stepEntries,
            stepSensorInitialized = currentLocal.stepSensorInitialized || snapshot.stepEntries.isNotEmpty(),
            studyMinutes = totalStudyMinutes,
            sleepHours = latestSleepHours,
            sleepSessions = snapshot.sleepSessions,
            habitMask = todayHabits,
            habitNames = snapshot.habitNames,
            noteText = snapshot.noteText,
            focusMode = snapshot.focusMode,
            focusDurationMinutes = snapshot.focusDurationMinutes,
            studySessions = snapshot.studySessions,
            workoutSessions = snapshot.workoutSessions,
            foodEntries = snapshot.foodEntries,
            journalEntries = snapshot.journalEntries
        )
    }

    fun encode(snapshot: MindCloudSnapshot): String {
        validate(snapshot)
        return gson.toJson(snapshot)
    }

    fun decode(json: String): MindCloudSnapshot {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_SNAPSHOT_BYTES) {
            "Snapshot exceeds the maximum accepted size"
        }
        val snapshot = gson.fromJson(json, MindCloudSnapshot::class.java)
            ?: throw IllegalArgumentException("Snapshot is empty")
        validate(snapshot)
        return snapshot
    }

    fun chunkJsonArray(jsonArray: String, maxBytes: Int = DEFAULT_CHUNK_BYTES): List<String> {
        require(maxBytes in 1..MAX_CHUNK_BYTES) { "Chunk size is outside the supported range" }
        val source = JsonParser.parseString(jsonArray)
        require(source.isJsonArray) { "Chunk source must be a JSON array" }
        val records = source.asJsonArray
        val result = mutableListOf<String>()
        var current = JsonArray()
        records.forEach { record ->
            val candidate = JsonArray().apply {
                current.forEach { add(it) }
                add(record)
            }
            if (utf8Size(candidate) > maxBytes) {
                require(current.size() > 0) { "A single record exceeds the chunk size limit" }
                result += current.toString()
                current = JsonArray().apply { add(record) }
                require(utf8Size(current) <= maxBytes) { "A single record exceeds the chunk size limit" }
            } else {
                current = candidate
            }
        }
        if (current.size() > 0) result += current.toString()
        return result
    }

    fun validate(snapshot: MindCloudSnapshot) {
        require(snapshot.schemaVersion == MindCloudSnapshot.CURRENT_SCHEMA_VERSION) {
            "Unsupported snapshot schema version"
        }
        require(snapshot.revision >= 0L) { "Revision cannot be negative" }
        require(snapshot.waterGoal in 1..20) { "Water goal is invalid" }

        // Gson can populate Kotlin non-null properties with null when remote JSON is malformed.
        requireNotNull(snapshot.habitNames) { "Habit definitions are missing" }
        requireNotNull(snapshot.noteText) { "Notes are missing" }
        requireNotNull(snapshot.focusMode) { "Focus mode is missing" }
        requireNotNull(snapshot.waterEntries) { "Water history is missing" }
        requireNotNull(snapshot.stepEntries) { "Step history is missing" }
        requireNotNull(snapshot.habitHistory) { "Habit history is missing" }
        requireNotNull(snapshot.studySessions) { "Study history is missing" }
        requireNotNull(snapshot.sleepSessions) { "Sleep history is missing" }
        requireNotNull(snapshot.workoutSessions) { "Workout history is missing" }
        requireNotNull(snapshot.foodEntries) { "Food history is missing" }
        requireNotNull(snapshot.journalEntries) { "Journal history is missing" }

        require(snapshot.habitNames.length <= 2_000) { "Habit definitions are too large" }
        require(snapshot.noteText.length <= 50_000) { "Notes are too large" }
        require(snapshot.focusMode == "تركيز" || snapshot.focusMode == "استراحة") {
            "Focus mode is invalid"
        }
        require(snapshot.focusDurationMinutes in 1..180) { "Focus duration is invalid" }
        require(snapshot.waterEntries.size <= 20_000) { "Too many water records" }
        require(snapshot.stepEntries.size <= 30_000) { "Too many step records" }
        require(snapshot.habitHistory.size <= 730) { "Too many habit records" }
        require(snapshot.studySessions.size <= 5_000) { "Too many study sessions" }
        require(snapshot.sleepSessions.size <= 5_000) { "Too many sleep sessions" }
        require(snapshot.workoutSessions.size <= 5_000) { "Too many workout sessions" }
        require(snapshot.foodEntries.size <= 20_000) { "Too many food records" }
        require(snapshot.journalEntries.size <= 20_000) { "Too many journal records" }

        snapshot.waterEntries.forEach { requireNotNull(it) { "Water history contains a null record" } }
        snapshot.stepEntries.forEach { requireNotNull(it) { "Step history contains a null record" } }
        snapshot.habitHistory.forEach { requireNotNull(it) { "Habit history contains a null record" } }
        snapshot.studySessions.forEach { requireNotNull(it) { "Study history contains a null record" } }
        snapshot.sleepSessions.forEach { requireNotNull(it) { "Sleep history contains a null record" } }
        snapshot.workoutSessions.forEach { requireNotNull(it) { "Workout history contains a null record" } }
        snapshot.foodEntries.forEach { requireNotNull(it) { "Food history contains a null record" } }
        snapshot.journalEntries.forEach { requireNotNull(it) { "Journal history contains a null record" } }
    }

    private fun utf8Size(element: com.google.gson.JsonElement): Int =
        element.toString().toByteArray(Charsets.UTF_8).size

    private const val DEFAULT_CHUNK_BYTES = 256 * 1024
    private const val MAX_CHUNK_BYTES = 256 * 1024
    private const val MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024
}
