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
        assertEquals(local.waterEntries, restored.waterEntries)
        assertEquals(local.stepEntries, restored.stepEntries)
        assertEquals("private note", restored.noteText)
        assertFalse(restored.toString().contains("activeSleepStartedAt"))
        assertFalse(restored.toString().contains("stepSensorInitialized"))
        assertFalse(restored.toString().contains("uiLanguage"))
        assertFalse(restored.toString().contains("focusSeconds"))
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
