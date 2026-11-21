package app.mind.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive

class CloudSyncCodecTest {
    @Test
    fun snapshotRoundTripsAndExcludesDeviceRuntimeState() {
        val local = MindSavedState(
            water = 4,
            waterGoal = 9,
            waterEntries = listOf(WaterEntry(100L), WaterEntry(200L)),
            steps = 8_500,
            stepGoal = 12_500,
            stepEntries = listOf(StepEntry(300L, 120)),
            stepSensorInitialized = true,
            activeSleepStartedAt = 999L,
            focusSeconds = 17,
            focusMode = "تركيز",
            focusDurationMinutes = 40,
            uiLanguage = "en",
            uiAppearance = "light",
            noteText = "private note"
        )

        val snapshot = CloudSyncCodec.fromLocal(local, revision = 7L)
        val restored = CloudSyncCodec.decode(CloudSyncCodec.encode(snapshot))

        assertEquals(7L, restored.revision)
        assertEquals(9, restored.waterGoal)
        assertEquals(12_500, restored.stepGoal)
        assertEquals(local.waterEntries, restored.waterEntries)
        assertEquals(local.stepEntries, restored.stepEntries)
        assertEquals("private note", restored.noteText)
        assertFalse(restored.toString().contains("activeSleepStartedAt"))
        assertFalse(restored.toString().contains("stepSensorInitialized"))
        assertFalse(restored.toString().contains("uiLanguage"))
        assertFalse(restored.toString().contains("focusSeconds"))
    }

    @Test
    fun cloudRestoreMapsHistoryAndPreservesDeviceOnlyState() {
        val now = System.currentTimeMillis()
        val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(now))
        val snapshot = CloudSyncCodec.fromLocal(
            MindSavedState(
                waterGoal = 10,
            stepGoal = 15_000,
                waterEntries = listOf(WaterEntry(now - 1_000L), WaterEntry(now)),
                stepEntries = listOf(StepEntry(now, 450)),
                habitHistory = listOf(HabitDayRecord(todayKey, 5, 3)),
                studySessions = listOf(StudySession(now, 30)),
                sleepSessions = listOf(SleepSession(now - 8 * 3_600_000L, now)),
                habitNames = "Read|Walk|Learn",
                noteText = "Cloud note",
                focusMode = "استراحة",
                focusDurationMinutes = 45
            ),
            revision = 2L
        )
        val deviceState = MindSavedState(
            activeSleepStartedAt = now - 600_000L,
            focusSeconds = 123,
            uiLanguage = "en",
            uiAppearance = "light"
        )

        val restored = CloudSyncCodec.toLocal(snapshot, deviceState)

        assertEquals(2, restored.water)
        assertEquals(10, restored.waterGoal)
        assertEquals(15_000, restored.stepGoal)
        assertEquals(450, restored.steps)
        assertEquals(5, restored.habitMask)
        assertEquals(30, restored.studyMinutes)
        assertEquals(8f, restored.sleepHours, 0.01f)
        assertEquals("Cloud note", restored.noteText)
        assertEquals("Read|Walk|Learn", restored.habitNames)
        assertEquals(45, restored.focusDurationMinutes)
        assertEquals("en", restored.uiLanguage)
        assertEquals("light", restored.uiAppearance)
        assertEquals(123, restored.focusSeconds)
        assertEquals(deviceState.activeSleepStartedAt, restored.activeSleepStartedAt)
        assertEquals(false, restored.stepSensorInitialized)
    }

    @Test
    fun oldCloudSnapshotWithoutStepGoalKeepsCurrentDeviceGoal() {
        val now = System.currentTimeMillis()
        val oldJson = CloudSyncCodec.encode(
            CloudSyncCodec.fromLocal(MindSavedState(stepGoal = 10_000), revision = 1L)
        ).replace(",\"stepGoal\":10000", "")
        val decoded = CloudSyncCodec.decode(oldJson)
        val restored = CloudSyncCodec.toLocal(decoded, MindSavedState(stepGoal = 18_000))
        assertEquals(18_000, restored.stepGoal)
    }

    @Test
    fun chunkingRespectsUtf8ByteLimitAndPreservesEveryRecord() {
        val source = JsonArray().apply {
            add(JsonPrimitive("مرحبا".repeat(12)))
            add(JsonPrimitive("second"))
            add(JsonPrimitive("third"))
        }
        val chunks = CloudSyncCodec.chunkJsonArray(source.toString(), maxBytes = 130)

        assertEquals(source.size(), chunks.sumOf { com.google.gson.JsonParser.parseString(it).asJsonArray.size() })
        chunks.forEach { chunk ->
            assertEquals(true, chunk.toByteArray(Charsets.UTF_8).size <= 130)
        }
    }

    @Test
    fun chunkingRejectsSingleRecordLargerThanLimit() {
        val source = JsonArray().apply { add(JsonPrimitive("x".repeat(100))) }
        assertThrows(IllegalArgumentException::class.java) {
            CloudSyncCodec.chunkJsonArray(source.toString(), maxBytes = 20)
        }
    }

    @Test
    fun rejectsMalformedSnapshotsWithMissingOrNullCollections() {
        val validJson = CloudSyncCodec.encode(
            CloudSyncCodec.fromLocal(MindSavedState(), revision = 1L)
        )

        val nullCollection = validJson.replace("\"waterEntries\":[]", "\"waterEntries\":null")
        assertThrows(IllegalArgumentException::class.java) {
            CloudSyncCodec.decode(nullCollection)
        }

        val nullRecord = validJson.replace("\"waterEntries\":[]", "\"waterEntries\":[null]")
        assertThrows(IllegalArgumentException::class.java) {
            CloudSyncCodec.decode(nullRecord)
        }

        assertThrows(IllegalArgumentException::class.java) {
            CloudSyncCodec.decode("{\"schemaVersion\":1}")
        }
    }

    @Test
    fun encodeRejectsSnapshotsLargerThanRestoreLimit() {
        val oversizedState = MindSavedState(
            journalEntries = listOf(JournalEntry(1L, "x".repeat(16 * 1024 * 1024 + 1)))
        )
        val oversizedSnapshot = CloudSyncCodec.fromLocal(oversizedState, revision = 1L)

        assertThrows(IllegalArgumentException::class.java) {
            CloudSyncCodec.encode(oversizedSnapshot)
        }
    }

    @Test
    fun rejectsUnsupportedSchemaAndNegativeRevision() {
        val valid = CloudSyncCodec.fromLocal(MindSavedState(), 0L)
        assertThrows(IllegalArgumentException::class.java) {
            CloudSyncCodec.validate(valid.copy(schemaVersion = 99))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudSyncCodec.fromLocal(MindSavedState(), -1L)
        }
    }
}
