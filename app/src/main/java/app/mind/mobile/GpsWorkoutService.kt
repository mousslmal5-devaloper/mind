package app.mind.mobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GpsWorkoutService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private var active = false
    private var workoutType = "جري"
    private var distanceMeters = 0f
    private var startedElapsed = 0L
    private var startedAtMillis = 0L
    private var lastAccepted: Location? = null
    private var gpsReady = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!active && intent?.action != ACTION_STOP) restorePersistedWorkout()
        when (intent?.action) {
            ACTION_STOP -> stopWorkout()
            ACTION_STATUS -> {
                publish(active, if (active) "جلسة $workoutType قيد التسجيل" else "لا توجد جلسة نشطة")
                if (!active) stopSelf(startId)
            }
            ACTION_START -> {
                if (active) {
                    publish(true, "جلسة $workoutType قيد التسجيل")
                    return START_STICKY
                }
                workoutType = intent.getStringExtra(EXTRA_TYPE)?.takeIf { it in setOf("مشي", "جري", "دراجة") } ?: "جري"
                startWorkout()
            }
        }
        return if (active) START_STICKY else START_NOT_STICKY
    }

    private fun startWorkout() {
        if (active) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            publish(false, "إذن الموقع غير ممنوح")
            stopSelf()
            return
        }
        distanceMeters = 0f
        startedElapsed = SystemClock.elapsedRealtime()
        startedAtMillis = System.currentTimeMillis()
        lastAccepted = null
        gpsReady = false
        active = true
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this)
            persistWorkout()
            publish(true, "جارٍ البحث عن إشارة GPS")
        } catch (security: SecurityException) {
            Log.e(TAG, "Location permission was revoked", security)
            active = false
            clearPersistedWorkout()
            stopForegroundCompat()
            publish(false, "تعذر الوصول إلى الموقع")
            stopSelf()
        } catch (failure: Exception) {
            Log.e(TAG, "Unable to start GPS updates", failure)
            active = false
            clearPersistedWorkout()
            stopForegroundCompat()
            publish(false, "تعذر تشغيل GPS على هذا الهاتف")
            stopSelf()
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!active) return
        if (!location.hasAccuracy() || location.accuracy > MAX_ACCURACY_METERS) {
            publish(true, "إشارة GPS ضعيفة — ننتظر قراءة أدق")
            return
        }
        gpsReady = true
        val previous = lastAccepted
        if (previous != null) {
            val segment = previous.distanceTo(location)
            val seconds = ((location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos).coerceAtLeast(0L)) / 1_000_000_000f
            if (!WorkoutDistanceMath.isAcceptableSegment(segment, seconds, location.accuracy, workoutType)) {
                publish(true, "GPS متصل — تم تجاهل قراءة غير موثوقة")
                return
            }
            distanceMeters += segment
        }
        lastAccepted = Location(location)
        persistWorkout()
        publish(true, "GPS متصل — يتم تسجيل المسار")
        updateNotification()
    }

    @Deprecated("Deprecated by Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) publish(true, "GPS متصل") }
    override fun onProviderDisabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) publish(true, "يرجى تشغيل خدمة الموقع") }

    private fun stopWorkout() {
        if (!active) {
            stopSelf()
            return
        }
        val endedAt = System.currentTimeMillis()
        val duration = ((SystemClock.elapsedRealtime() - startedElapsed) / 1000L).coerceAtLeast(0L)
        active = false
        runCatching { locationManager.removeUpdates(this) }
        clearPersistedWorkout()
        publish(false, "انتهت جلسة $workoutType", completed = duration >= 5L, endedAt = endedAt)
        stopForegroundCompat()
        if (duration >= 5L && startedAtMillis > 0L) {
            val session = WorkoutSession(startedAtMillis, endedAt, workoutType, distanceMeters, duration)
            serviceScope.launch {
                runCatching { appendWorkoutSession(applicationContext, session) }
                    .onFailure { Log.e(TAG, "Unable to persist completed workout", it) }
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    private fun persistWorkout() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putString(KEY_TYPE, workoutType)
            .putFloat(KEY_DISTANCE, distanceMeters)
            .putLong(KEY_STARTED_AT, startedAtMillis)
            .putLong(KEY_STARTED_ELAPSED, startedElapsed)
            .apply()
    }

    private fun clearPersistedWorkout() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
    }

    private fun restorePersistedWorkout() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return
        val savedStartedElapsed = prefs.getLong(KEY_STARTED_ELAPSED, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()
        // elapsedRealtime resets after reboot; never revive a stale workout from a previous boot.
        if (savedStartedElapsed <= 0L || nowElapsed < savedStartedElapsed ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            clearPersistedWorkout()
            return
        }
        workoutType = prefs.getString(KEY_TYPE, "جري")?.takeIf { it in setOf("مشي", "جري", "دراجة") } ?: "جري"
        distanceMeters = prefs.getFloat(KEY_DISTANCE, 0f).takeIf { it.isFinite() && it >= 0f } ?: 0f
        startedAtMillis = prefs.getLong(KEY_STARTED_AT, 0L)
        startedElapsed = savedStartedElapsed
        lastAccepted = null
        gpsReady = false
        active = startedAtMillis > 0L
        if (!active) {
            clearPersistedWorkout()
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this)
            publish(true, "استُعيدت الجلسة — جارٍ إعادة الاتصال بـ GPS")
        } catch (failure: Exception) {
            Log.e(TAG, "Unable to restore GPS workout", failure)
            active = false
            clearPersistedWorkout()
            stopForegroundCompat()
            publish(false, "تعذر استعادة تتبع GPS")
        }
    }
    private fun publish(isActive: Boolean, message: String, completed: Boolean = false, endedAt: Long = 0L) {
        val update = Intent(ACTION_UPDATE).setPackage(packageName)
            .putExtra(EXTRA_ACTIVE, isActive)
            .putExtra(EXTRA_DISTANCE, distanceMeters)
            .putExtra(EXTRA_ELAPSED, if (startedElapsed > 0L) (SystemClock.elapsedRealtime() - startedElapsed) / 1000L else 0L)
            .putExtra(EXTRA_GPS_READY, gpsReady)
            .putExtra(EXTRA_MESSAGE, message)
            .putExtra(EXTRA_COMPLETED, completed)
            .putExtra(EXTRA_STARTED_AT, startedAtMillis)
            .putExtra(EXTRA_ENDED_AT, endedAt)
            .putExtra(EXTRA_TYPE, workoutType)
        sendBroadcast(update)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("MIND · تتبع النشاط")
            .setContentText("جلسة $workoutType قيد التسجيل")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "تتبع النشاط الرياضي", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        active = false
        runCatching { locationManager.removeUpdates(this) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "app.mind.mobile.GPS_START"
        const val ACTION_STOP = "app.mind.mobile.GPS_STOP"
        const val ACTION_UPDATE = "app.mind.mobile.GPS_UPDATE"
        const val ACTION_STATUS = "app.mind.mobile.GPS_STATUS"
        const val EXTRA_TYPE = "workout_type"
        const val EXTRA_ACTIVE = "active"
        const val EXTRA_DISTANCE = "distance_meters"
        const val EXTRA_ELAPSED = "elapsed_seconds"
        const val EXTRA_GPS_READY = "gps_ready"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_COMPLETED = "completed"
        const val EXTRA_STARTED_AT = "started_at"
        const val EXTRA_ENDED_AT = "ended_at"
        private const val CHANNEL_ID = "mind_gps_tracking"
        private const val NOTIFICATION_ID = 7105
        private const val MAX_ACCURACY_METERS = 35f
        private const val TAG = "MIND_GPS"
        private const val PREFS_NAME = "mind_gps_workout_state"
        private const val KEY_ACTIVE = "active"
        private const val KEY_TYPE = "type"
        private const val KEY_DISTANCE = "distance_meters"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_STARTED_ELAPSED = "started_elapsed"
    }
}
