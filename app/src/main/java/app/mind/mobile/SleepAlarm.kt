package app.mind.mobile

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import java.util.Calendar

object SleepAlarm {
    private const val PREFS = "mind_sleep_alarm"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_SOUND = "sound"
    private const val KEY_SOUND_URI = "sound_uri"
    private const val REQUEST_CODE = 7401
    private const val CHANNEL_ID = "mind_sleep_alarm"

    data class Settings(val enabled: Boolean, val hour: Int, val minute: Int, val sound: String, val soundUri: String?)

    fun read(context: Context): Settings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            hour = prefs.getInt(KEY_HOUR, 7).coerceIn(0, 23),
            minute = prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59),
            sound = prefs.getString(KEY_SOUND, "none")?.takeIf { it == "none" || it == "beep" || it == "file" } ?: "none",
            soundUri = prefs.getString(KEY_SOUND_URI, null)
        )
    }

    fun saveAndSchedule(context: Context, hour: Int, minute: Int, enabled: Boolean) {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_HOUR, safeHour)
            .putInt(KEY_MINUTE, safeMinute)
            .apply()
        if (enabled) schedule(context, safeHour, safeMinute) else cancel(context)
    }

    fun saveSound(context: Context, sound: String, soundUri: String? = null) {
        val safeSound = sound.takeIf { it == "none" || it == "beep" || it == "file" } ?: "none"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SOUND, safeSound)
            .putString(KEY_SOUND_URI, if (safeSound == "file") soundUri else null)
            .apply()
    }

    fun rescheduleIfEnabled(context: Context) {
        val settings = read(context)
        if (settings.enabled) schedule(context, settings.hour, settings.minute)
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SleepAlarmReceiver::class.java).setAction("app.mind.mobile.SLEEP_ALARM")
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, alarmPendingIntent(context))
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, next.timeInMillis, alarmPendingIntent(context))
            }
        }.onFailure { Log.e("MIND", "Unable to schedule sleep alarm", it) }
    }

    private fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarmManager.cancel(alarmPendingIntent(context)) }
            .onFailure { Log.e("MIND", "Unable to cancel sleep alarm", it) }
    }

    internal fun showNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val settings = read(context)
        val alarmSound = when (settings.sound) {
            "beep" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "file" -> settings.soundUri?.let { Uri.parse(it) }
            else -> null
        }
        val channelId = CHANNEL_ID + "_" + settings.sound + "_" + (settings.soundUri?.hashCode() ?: 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "منبّه النوم", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه وقت الاستيقاظ المحدد في MIND"
                enableVibration(true)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            manager.createNotificationChannel(channel)
        }
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val contentIntent = PendingIntent.getActivity(context, REQUEST_CODE + 1, openApp, contentFlags)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH).setSound(alarmSound)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان وقت الاستيقاظ")
            .setContentText("صباح الخير، سجّل استيقاظك في MIND")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        runCatching { manager.notify(REQUEST_CODE, notification) }
            .onFailure { Log.e("MIND", "Unable to show sleep alarm notification", it) }
    }
}

class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "app.mind.mobile.SLEEP_ALARM") return
        SleepAlarm.showNotification(context)
        SleepAlarm.rescheduleIfEnabled(context)
    }
}

class SleepAlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_TIME_CHANGED || intent?.action == Intent.ACTION_TIMEZONE_CHANGED) SleepAlarm.rescheduleIfEnabled(context)
    }
}
