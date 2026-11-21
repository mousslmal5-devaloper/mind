package app.mind.mobile

internal object WorkoutDistanceMath {
    fun isAcceptableSegment(distanceMeters: Float, elapsedSeconds: Float, accuracyMeters: Float, type: String): Boolean {
        if (!distanceMeters.isFinite() || !elapsedSeconds.isFinite() || !accuracyMeters.isFinite()) return false
        if (distanceMeters < 2f || elapsedSeconds <= 0f || accuracyMeters !in 0f..35f) return false
        val maximumMetersPerSecond = if (type == "دراجة") 25f else 12f
        return distanceMeters / elapsedSeconds <= maximumMetersPerSecond
    }
}
