package app.mind.mobile

/**
 * Pure state transition for Android's cumulative TYPE_STEP_COUNTER readings.
 * A device reboot or a new local day establishes a baseline without inventing steps.
 */
internal data class StepCounterSnapshot(
    val day: String?,
    val rawTotal: Long,
    val dailyTotal: Int,
    val syncedTotal: Int
)

internal data class StepCounterUpdate(
    val snapshot: StepCounterSnapshot,
    val addedSteps: Int
)

internal fun recordStepReading(
    previous: StepCounterSnapshot,
    today: String,
    rawTotal: Long
): StepCounterUpdate {
    if (rawTotal < 0L) return StepCounterUpdate(previous, 0)

    if (previous.day != today) {
        return StepCounterUpdate(
            StepCounterSnapshot(today, rawTotal, 0, 0),
            0
        )
    }

    val previousRaw = previous.rawTotal
    val added = if (previousRaw >= 0L && rawTotal >= previousRaw) {
        (rawTotal - previousRaw).coerceAtMost(100_000L).toInt()
    } else {
        // First sample or sensor reset after reboot: establish a safe baseline.
        0
    }
    val daily = (previous.dailyTotal.coerceIn(0, 100_000) + added)
        .coerceAtMost(100_000)

    return StepCounterUpdate(
        StepCounterSnapshot(
            day = today,
            rawTotal = rawTotal,
            dailyTotal = daily,
            syncedTotal = previous.syncedTotal.coerceIn(0, 100_000)
        ),
        added
    )
}
