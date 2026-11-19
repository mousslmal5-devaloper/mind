package app.mind.mobile

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun parseFoodCalories(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

internal fun normalizeJournalEntry(raw: String): String? =
    raw.trim().takeIf { it.isNotEmpty() }

internal fun foodCaloriesForDate(entries: List<FoodEntry>, dateKey: String): Double =
    entries.asSequence()
        .filter { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.recordedAt)) == dateKey }
        .sumOf { it.calories }
