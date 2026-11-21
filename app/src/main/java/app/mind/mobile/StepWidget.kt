package app.mind.mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen step counter backed by the same account-scoped sensor snapshot
 * that StepCounterService updates and the user's configurable daily goal.
 */
class StepWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == StepCounterService.ACTION_STEP_UPDATE) {
            val currentUid = verifiedUid()
            val updateUid = intent.getStringExtra(StepCounterService.EXTRA_UID)
            if (currentUid == null || updateUid != currentUid) return
            refreshAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StepWidget::class.java))
        render(context, manager, ids)
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uid = verifiedUid()
                val accountKey = uid?.let { MindStorageIdentity.accountKey(it) }
                val prefs = context.getSharedPreferences("mind_step_sensor", Context.MODE_PRIVATE)
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date(System.currentTimeMillis()))
                val storedDay = accountKey?.let { prefs.getString("day_$it", null) }
                val supported = accountKey != null && storedDay == today
                val steps = if (supported) prefs.getInt("daily_total_$accountKey", 0).coerceIn(0, 100_000) else 0
                val goal = readMindStepsGoalForWidget(context, uid)
                val progress = (steps * 100 / goal).coerceIn(0, 100)

                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_steps)
                    views.setTextViewText(R.id.steps_count, steps.toString())
                    views.setTextViewText(R.id.steps_progress, "$progress% · $goal")
                    views.setTextViewText(
                        R.id.steps_status,
                        if (supported) context.getString(R.string.mind_steps_widget_active)
                        else context.getString(R.string.mind_steps_widget_waiting)
                    )
                    val open = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pending = PendingIntent.getActivity(
                        context, id + 1600, open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.steps_widget_root, pending)
                    manager.updateAppWidget(id, views)
                }
            } catch (error: Exception) {
                android.util.Log.e("MIND_STEP_WIDGET", "Unable to render step widget", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun verifiedUid(): String? = runCatching {
        FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified }?.uid
    }.getOrNull()
}
