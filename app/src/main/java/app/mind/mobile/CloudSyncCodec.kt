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
    }

    private fun utf8Size(element: com.google.gson.JsonElement): Int =
        element.toString().toByteArray(Charsets.UTF_8).size

    private const val DEFAULT_CHUNK_BYTES = 256 * 1024
    private const val MAX_CHUNK_BYTES = 256 * 1024
    private const val MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024
}
