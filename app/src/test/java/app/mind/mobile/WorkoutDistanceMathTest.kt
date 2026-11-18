package app.mind.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDistanceMathTest {
    @Test fun acceptsPlausibleRunningSegment() {
        assertTrue(WorkoutDistanceMath.isAcceptableSegment(24f, 10f, 8f, "جري"))
    }

    @Test fun rejectsImpossibleGpsJump() {
        assertFalse(WorkoutDistanceMath.isAcceptableSegment(500f, 2f, 5f, "جري"))
    }

    @Test fun rejectsInaccurateGpsFix() {
        assertFalse(WorkoutDistanceMath.isAcceptableSegment(20f, 10f, 80f, "مشي"))
    }

    @Test fun rejectsSmallGpsJitter() {
        assertFalse(WorkoutDistanceMath.isAcceptableSegment(0.8f, 3f, 4f, "مشي"))
    }

    @Test fun allowsHigherCyclingSpeedThanRunning() {
        assertTrue(WorkoutDistanceMath.isAcceptableSegment(60f, 4f, 6f, "دراجة"))
        assertFalse(WorkoutDistanceMath.isAcceptableSegment(60f, 4f, 6f, "جري"))
    }
}
