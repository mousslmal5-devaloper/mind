package app.mind.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class FoodJournalRulesTest {
    @Test
    fun parsesOnlyFinitePositiveCalories() {
        assertEquals(125.5, parseFoodCalories("125.5")!!, 0.0)
        assertEquals(125.5, parseFoodCalories("125,5")!!, 0.0)
        listOf("", "0", "-2", "NaN", "Infinity", "abc").forEach {
            assertNull("Expected invalid input: $it", parseFoodCalories(it))
        }
    }

    @Test
    fun trimsJournalTextAndRejectsEmptyEntries() {
        assertEquals("يوم جيد", normalizeJournalEntry("  يوم جيد  "))
        assertNull(normalizeJournalEntry("   "))
    }

    @Test
    fun totalsCaloriesOnlyForRequestedLocalDate() {
        val day = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 19, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val first = day.timeInMillis
        val second = day.clone().let { (it as Calendar).apply { set(Calendar.HOUR_OF_DAY, 14) }.timeInMillis }
        val other = day.clone().let { (it as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis }
        val entries = listOf(FoodEntry(first, 120.0), FoodEntry(second, 80.5), FoodEntry(other, 900.0))
        val key = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(first))
        assertEquals(200.5, foodCaloriesForDate(entries, key), 0.0)
    }
}
