package app.mind.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class StepCounterMathTest {
    @Test fun firstReadingEstablishesBaselineWithoutInventingSteps() {
        val result = recordStepReading(StepCounterSnapshot(null, -1L, 0, 0), "2026-11-18", 420L)
        assertEquals(0, result.addedSteps)
        assertEquals(0, result.snapshot.dailyTotal)
        assertEquals(420L, result.snapshot.rawTotal)
    }

    @Test fun increasingCumulativeReadingAddsOnlyTheDifference() {
        val result = recordStepReading(StepCounterSnapshot("2026-11-18", 420L, 80, 20), "2026-11-18", 437L)
        assertEquals(17, result.addedSteps)
        assertEquals(97, result.snapshot.dailyTotal)
        assertEquals(20, result.snapshot.syncedTotal)
    }

    @Test fun newDayResetsDailyAndAcknowledgedTotals() {
        val result = recordStepReading(StepCounterSnapshot("2026-11-17", 900L, 500, 450), "2026-11-18", 915L)
        assertEquals(0, result.addedSteps)
        assertEquals(0, result.snapshot.dailyTotal)
        assertEquals(0, result.snapshot.syncedTotal)
        assertEquals(915L, result.snapshot.rawTotal)
    }

    @Test fun sensorResetEstablishesNewBaselineWithoutNegativeOrHugeDelta() {
        val result = recordStepReading(StepCounterSnapshot("2026-11-18", 900L, 500, 450), "2026-11-18", 12L)
        assertEquals(0, result.addedSteps)
        assertEquals(500, result.snapshot.dailyTotal)
        assertEquals(12L, result.snapshot.rawTotal)
    }

    @Test fun dailyTotalIsCappedAtOneHundredThousand() {
        val result = recordStepReading(StepCounterSnapshot("2026-11-18", 100L, 99_990, 0), "2026-11-18", 200L)
        assertEquals(100, result.addedSteps)
        assertEquals(100_000, result.snapshot.dailyTotal)
    }
}
