package app.mind.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StepCounterService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var registered = false

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            publish(false, 0, 0)
            return
        }
        registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (!registered) publish(false, 0, 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis()))
        if (prefs.getString(KEY_DAY, null) != today) {
            prefs.edit().putString(KEY_DAY, today).putInt(KEY_DAILY, 0).putInt(KEY_SYNCED, 0).apply()
        }
        if (intent?.action == ACTION_ACK) {
            prefs.edit().putInt(KEY_SYNCED, intent.getIntExtra(EXTRA_TOTAL, 0).coerceIn(0, 100000)).apply()
        } else {
            val total = prefs.getInt(KEY_DAILY, 0).coerceIn(0, 100000)
            val synced = prefs.getInt(KEY_SYNCED, 0).coerceIn(0, 100000)
            publish(registered, total, (total - synced).coerceAtLeast(0))
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER || event.values.isEmpty()) return
        val raw = event.values[0].toLong().coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousDay = prefs.getString(KEY_DAY, null)
        var daily = prefs.getInt(KEY_DAILY, 0).coerceAtLeast(0)
        val previousRaw = prefs.getLong(KEY_RAW, -1L)
        var delta = 0
        if (previousDay != today) {
            daily = 0
            // A new day starts a fresh app-side acknowledgement baseline too.
            prefs.edit().putInt(KEY_SYNCED, 0).apply()
        } else if (previousRaw >= 0L && raw >= previousRaw) {
            delta = (raw - previousRaw).coerceAtMost(100000L).toInt()
            daily = (daily + delta).coerceAtMost(100000)
        }
        prefs.edit().putString(KEY_DAY, today).putLong(KEY_RAW, raw).putInt(KEY_DAILY, daily).apply()
        val synced = prefs.getInt(KEY_SYNCED, 0).coerceIn(0, 100000)
        publish(true, daily, (daily - synced).coerceAtLeast(0))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun publish(supported: Boolean, total: Int, delta: Int) {
        sendBroadcast(Intent(ACTION_STEP_UPDATE).setPackage(packageName)
            .putExtra(EXTRA_SUPPORTED, supported).putExtra(EXTRA_TOTAL, total)
            .putExtra(EXTRA_DELTA, delta).putExtra(EXTRA_AT, System.currentTimeMillis()))
    }

    private fun startAsForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "عداد خطوات MIND", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)
        val notification = builder.setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("MIND · عداد الخطوات")
            .setContentText("يتم احتساب خطواتك تلقائياً من مستشعر الهاتف")
            .setOngoing(true).build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        if (::sensorManager.isInitialized && registered) sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STEP_UPDATE = "app.mind.mobile.STEP_UPDATE"
        const val ACTION_ACK = "app.mind.mobile.STEP_ACK"
        const val EXTRA_SUPPORTED = "supported"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_DELTA = "delta"
        const val EXTRA_AT = "at"
        private const val PREFS = "mind_step_sensor"
        private const val KEY_DAY = "day"
        private const val KEY_RAW = "raw_total"
        private const val KEY_DAILY = "daily_total"
        private const val KEY_SYNCED = "synced_total"
        private const val CHANNEL_ID = "mind_step_counter"
        private const val NOTIFICATION_ID = 2601
    }
}
