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

class HabitWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra(EXTRA_HABIT_INDEX, -1)
        if (intent.action != ACTION_TOGGLE || index !in 0 until 20) {
            super.onReceive(context, intent)
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                toggleMindHabitFromWidget(context, verifiedUid(), index)
                renderAll(context)
            } catch (error: Exception) {
                android.util.Log.e("MIND_HABIT_WIDGET", "Unable to toggle habit", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    private suspend fun renderAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, HabitWidget::class.java))
        if (ids.isEmpty()) return
        val (mask, names) = readMindHabitsForWidget(context, verifiedUid())
        ids.forEach { renderOne(context, manager, it, mask, names) }
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (mask, names) = readMindHabitsForWidget(context, verifiedUid())
                ids.forEach { renderOne(context, manager, it, mask, names) }
            } catch (error: Exception) {
                android.util.Log.e("MIND_HABIT_WIDGET", "Unable to render habits", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun renderOne(context: Context, manager: AppWidgetManager, widgetId: Int, mask: Int, names: List<String>) {
        val views = RemoteViews(context.packageName, R.layout.widget_habits)
        views.setTextViewText(R.id.habit_summary, Integer.bitCount(mask).toString() + " / " + names.size.toString())
        val ids = intArrayOf(R.id.habit_row_1, R.id.habit_row_2, R.id.habit_row_3)
        val labels = intArrayOf(R.id.habit_name_1, R.id.habit_name_2, R.id.habit_name_3)
        val checks = intArrayOf(R.id.habit_check_1, R.id.habit_check_2, R.id.habit_check_3)
        for (slot in 0..2) {
            val exists = slot < names.size
            val index = slot
            views.setViewVisibility(ids[slot], if (exists) android.view.View.VISIBLE else android.view.View.GONE)
            if (exists) {
                val done = mask and (1 shl index) != 0
                views.setTextViewText(labels[slot], names[index])
                views.setTextViewText(checks[slot], if (done) "✓" else "○")
                views.setTextColor(checks[slot], if (done) android.graphics.Color.rgb(232, 197, 71) else android.graphics.Color.LTGRAY)
                val toggle = Intent(context, HabitWidget::class.java).apply {
                    action = ACTION_TOGGLE
                    putExtra(EXTRA_HABIT_INDEX, index)
                }
                val pending = PendingIntent.getBroadcast(
                    context, widgetId * 100 + index + 1, toggle,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(ids[slot], pending)
            }
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, widgetId + 1200, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.habit_widget_root, openPending)
        manager.updateAppWidget(widgetId, views)
    }

    private fun verifiedUid(): String? = runCatching {
        FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified }?.uid
    }.getOrNull()

    private companion object {
        const val ACTION_TOGGLE = "app.mind.mobile.action.HABIT_TOGGLE"
        const val EXTRA_HABIT_INDEX = "habit_index"
    }
}
