package app.mind.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PomodoroService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ticker: Job? = null
    private var ownerUid: String? = null

    override fun onCreate() { super.onCreate(); ensureChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentUid = verifiedUid()
        startForeground(NOTIFICATION_ID, buildNotification(currentRemaining()))
        ownerUid = intent?.getStringExtra(EXTRA_UID) ?: currentUid
        if (ownerUid != currentUid) { stopSelf(startId); return START_NOT_STICKY }
        val action = intent?.action ?: ACTION_SYNC
        val prefs = timerPrefs()
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            val initialMode = intent?.getStringExtra(EXTRA_MODE)?.takeIf { it == "تركيز" || it == "استراحة" } ?: "تركيز"
            val initialDuration = (intent?.getIntExtra(EXTRA_DURATION, 25) ?: 25).coerceIn(1, 180)
            val initialSeconds = (intent?.getIntExtra(EXTRA_SECONDS, if (initialMode == "تركيز") initialDuration * 60 else 300)
                ?: if (initialMode == "تركيز") initialDuration * 60 else 300).coerceAtLeast(1)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).putString(KEY_MODE, initialMode)
                .putInt(KEY_DURATION, initialDuration).putInt(KEY_REMAINING, initialSeconds).apply()
        }
        when (action) {
            ACTION_START -> {
                val remaining = (intent?.getIntExtra(EXTRA_SECONDS, currentRemaining()) ?: currentRemaining()).coerceAtLeast(1)
                prefs.edit().putInt(KEY_REMAINING, remaining).putLong(KEY_END_AT, System.currentTimeMillis() + remaining * 1000L)
                    .putBoolean(KEY_RUNNING, true).putString(KEY_MODE, intent?.getStringExtra(EXTRA_MODE) ?: prefs.getString(KEY_MODE, "تركيز"))
                    .putInt(KEY_DURATION, intent?.getIntExtra(EXTRA_DURATION, prefs.getInt(KEY_DURATION, 25)) ?: 25)
                    .putString(KEY_UID, ownerUid ?: "").putBoolean(KEY_INITIALIZED, true).apply()
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESET -> {
                val mode = prefs.getString(KEY_MODE, "تركيز") ?: "تركيز"
                val duration = prefs.getInt(KEY_DURATION, 25).coerceIn(1, 180)
                val seconds = intent?.getIntExtra(EXTRA_SECONDS, if (mode == "تركيز") duration * 60 else 300) ?: if (mode == "تركيز") duration * 60 else 300
                prefs.edit().putInt(KEY_REMAINING, seconds.coerceAtLeast(1)).putLong(KEY_END_AT, 0L).putBoolean(KEY_RUNNING, false).apply()
            }
            ACTION_MODE -> {
                val mode = intent?.getStringExtra(EXTRA_MODE)?.takeIf { it == "تركيز" || it == "استراحة" } ?: "تركيز"
                val duration = prefs.getInt(KEY_DURATION, 25).coerceIn(1, 180)
                prefs.edit().putString(KEY_MODE, mode).putInt(KEY_REMAINING, if (mode == "تركيز") duration * 60 else 300)
                    .putLong(KEY_END_AT, 0L).putBoolean(KEY_RUNNING, false).apply()
            }
            ACTION_PRESET -> {
                val duration = (intent?.getIntExtra(EXTRA_DURATION, 25) ?: 25).coerceIn(1, 180)
                val edit = prefs.edit().putInt(KEY_DURATION, duration)
                if ((prefs.getString(KEY_MODE, "تركيز") ?: "تركيز") == "تركيز")
                    edit.putInt(KEY_REMAINING, duration * 60).putLong(KEY_END_AT, 0L).putBoolean(KEY_RUNNING, false)
                edit.apply()
            }
            ACTION_ADD -> {
                val next = (currentRemaining() + 300).coerceAtMost(10800)
                val edit = prefs.edit().putInt(KEY_REMAINING, next)
                if (prefs.getBoolean(KEY_RUNNING, false)) edit.putLong(KEY_END_AT, System.currentTimeMillis() + next * 1000L)
                edit.apply()
            }
            ACTION_TOGGLE -> {
                if (prefs.getBoolean(KEY_RUNNING, false)) pauseTimer()
                else {
                    val remaining = currentRemaining().coerceAtLeast(1)
                    prefs.edit().putInt(KEY_REMAINING, remaining).putLong(KEY_END_AT, System.currentTimeMillis() + remaining * 1000L)
                        .putBoolean(KEY_RUNNING, true).putString(KEY_UID, ownerUid ?: "").apply()
                }
            }
        }
        val running = prefs.getBoolean(KEY_RUNNING, false)
        if (running) {
            startForeground(NOTIFICATION_ID, buildNotification(currentRemaining()))
            startTicker()
        } else {
            ticker?.cancel()
            publishState(false)
            renderWidget()
            stopForegroundCompat()
            stopSelf(startId)
        }
        return if (running) START_STICKY else START_NOT_STICKY
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (timerPrefs().getBoolean(KEY_RUNNING, false)) {
                val remaining = currentRemaining()
                if (remaining <= 0) {
                    val prefs = timerPrefs()
                    val mode = prefs.getString(KEY_MODE, "تركيز") ?: "تركيز"
                    val duration = prefs.getInt(KEY_DURATION, 25).coerceIn(1, 180)
                    prefs.edit().putBoolean(KEY_RUNNING, false).putLong(KEY_END_AT, 0L)
                        .putString(KEY_MODE, if (mode == "تركيز") "استراحة" else "تركيز")
                        .putInt(KEY_REMAINING, if (mode == "تركيز") 300 else duration * 60).apply()
                    if (mode == "تركيز") runCatching {
                        recordMindStudySession(applicationContext, ownerUid, duration, System.currentTimeMillis())
                    }.onFailure { android.util.Log.e(TAG, "Unable to record completed focus session", it) }
                    publishState(true); renderWidget(); stopForegroundCompat(); stopSelf(); break
                }
                timerPrefs().edit().putInt(KEY_REMAINING, remaining).apply()
                publishState(false)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(remaining))
                renderWidget()
                delay(1000L)
            }
        }
    }

    private fun currentRemaining(): Int {
        val prefs = timerPrefs()
        if (!prefs.getBoolean(KEY_RUNNING, false)) return prefs.getInt(KEY_REMAINING, 1500).coerceAtLeast(0)
        val endAt = prefs.getLong(KEY_END_AT, 0L)
        return if (endAt <= 0L) prefs.getInt(KEY_REMAINING, 1500).coerceAtLeast(0)
        else ((endAt - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0)
    }

    private fun pauseTimer() {
        timerPrefs().edit().putInt(KEY_REMAINING, currentRemaining()).putLong(KEY_END_AT, 0L).putBoolean(KEY_RUNNING, false).apply()
    }

    private fun publishState(completed: Boolean) {
        val p = timerPrefs()
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra(EXTRA_SECONDS, currentRemaining())
            .putExtra(EXTRA_RUNNING, p.getBoolean(KEY_RUNNING, false)).putExtra(EXTRA_MODE, p.getString(KEY_MODE, "تركيز"))
            .putExtra(EXTRA_DURATION, p.getInt(KEY_DURATION, 25)).putExtra(EXTRA_COMPLETED, completed))
    }

    private fun renderWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, PomodoroWidget::class.java))
        val p = timerPrefs(); val seconds = currentRemaining()
        val mode = p.getString(KEY_MODE, "تركيز") ?: "تركيز"; val running = p.getBoolean(KEY_RUNNING, false)
        ids.forEach { id ->
            val v = RemoteViews(packageName, R.layout.widget_pomodoro)
            v.setTextViewText(R.id.pomodoro_mode, if (mode == "تركيز") "MIND · تركيز" else "MIND · استراحة")
            v.setTextViewText(R.id.pomodoro_time, "%02d:%02d".format(seconds / 60, seconds % 60))
            v.setTextViewText(R.id.pomodoro_toggle, if (running) "إيقاف مؤقت" else "ابدأ")
            v.setOnClickPendingIntent(R.id.pomodoro_toggle, actionIntent(ACTION_TOGGLE, id * 10 + 1))
            v.setOnClickPendingIntent(R.id.pomodoro_reset, actionIntent(ACTION_RESET, id * 10 + 2))
            val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            v.setOnClickPendingIntent(R.id.pomodoro_root, PendingIntent.getActivity(this, id + 1900, open, pendingFlags()))
            manager.updateAppWidget(id, v)
        }
    }

    private fun actionIntent(action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(this, request, Intent(this, PomodoroWidget::class.java).setAction(action), pendingFlags())

    private fun buildNotification(seconds: Int): Notification {
        val mode = timerPrefs().getString(KEY_MODE, "تركيز") ?: "تركيز"
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(if (mode == "تركيز") "MIND · جلسة تركيز" else "MIND · استراحة")
            .setContentText("%02d:%02d متبقية".format(seconds / 60, seconds % 60)).setOngoing(true).setOnlyAlertOnce(true).build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "مؤقت MIND", NotificationManager.IMPORTANCE_LOW))
    }
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else { @Suppress("DEPRECATION") stopForeground(true) }
    }
    private fun timerPrefs() = getSharedPreferences(PREFS + "_" + (ownerUid?.let { MindStorageIdentity.accountKey(it) } ?: "local"), Context.MODE_PRIVATE)
    private fun verifiedUid(): String? = runCatching { FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified }?.uid }.getOrNull()
    private fun pendingFlags() = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    override fun onDestroy() { ticker?.cancel(); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STATE = "app.mind.mobile.POMODORO_STATE"
        const val EXTRA_UID = "owner_uid"; const val EXTRA_SECONDS = "seconds"; const val EXTRA_RUNNING = "running"
        const val EXTRA_MODE = "mode"; const val EXTRA_DURATION = "duration"; const val EXTRA_COMPLETED = "completed"
        const val ACTION_START = "app.mind.mobile.POMODORO_START"; const val ACTION_PAUSE = "app.mind.mobile.POMODORO_PAUSE"
        const val ACTION_RESET = "app.mind.mobile.POMODORO_RESET"; const val ACTION_MODE = "app.mind.mobile.POMODORO_MODE"
        const val ACTION_PRESET = "app.mind.mobile.POMODORO_PRESET"; const val ACTION_ADD = "app.mind.mobile.POMODORO_ADD"
        const val ACTION_TOGGLE = "app.mind.mobile.POMODORO_TOGGLE"; const val ACTION_SYNC = "app.mind.mobile.POMODORO_SYNC"
        private const val PREFS = "mind_pomodoro_timer"; private const val KEY_INITIALIZED = "initialized"; private const val KEY_REMAINING = "remaining"; private const val KEY_END_AT = "end_at"
        private const val KEY_RUNNING = "running"; private const val KEY_MODE = "mode"; private const val KEY_DURATION = "duration"
        private const val CHANNEL_ID = "mind_pomodoro"; private const val NOTIFICATION_ID = 2602; private const val TAG = "MIND_POMODORO"
    }
}

class PomodoroWidget : android.appwidget.AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PomodoroService.ACTION_TOGGLE || intent.action == PomodoroService.ACTION_RESET) {
            val service = Intent(context, PomodoroService::class.java).setAction(intent.action)
                .putExtra(PomodoroService.EXTRA_UID, runCatching { FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified }?.uid }.getOrNull())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service) else context.startService(service)
        } else super.onReceive(context, intent)
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val service = Intent(context, PomodoroService::class.java).setAction(PomodoroService.ACTION_SYNC)
            .putExtra(PomodoroService.EXTRA_UID, runCatching { FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified }?.uid }.getOrNull())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service) else context.startService(service)
    }
}
