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

class WaterWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        val delta = when (intent.action) {
            ACTION_ADD -> 1
            ACTION_REMOVE -> -1
            else -> 0
        }
        if (delta == 0) {
            super.onReceive(context, intent)
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                changeMindWaterFromWidget(context, verifiedUid(), delta)
                refreshAll(context)
            } catch (error: Exception) {
                android.util.Log.e("MIND_WATER_WIDGET", "Unable to update water intake", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, manager, ids)
    }

    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidget::class.java))
        refresh(context, manager, ids)
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (count, goal) = readMindWaterForWidget(context, verifiedUid())
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_water)
                    views.setTextViewText(R.id.water_count, count.toString() + " / " + goal.toString())
                    views.setTextViewText(R.id.water_progress, (count * 100 / goal).coerceAtMost(100).toString() + "%")
                    views.setOnClickPendingIntent(R.id.water_add, actionPendingIntent(context, ACTION_ADD, id * 2 + 1))
                    views.setOnClickPendingIntent(R.id.water_remove, actionPendingIntent(context, ACTION_REMOVE, id * 2 + 2))
                    val open = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val openPending = PendingIntent.getActivity(
                        context, id + 800, open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.water_widget_root, openPending)
                    manager.updateAppWidget(id, views)
                }
            } catch (error: Exception) {
                android.util.Log.e("MIND_WATER_WIDGET", "Unable to render water widget", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WaterWidget::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun verifiedUid(): String? = runCatching {
        FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified }?.uid
    }.getOrNull()

    private companion object {
        const val ACTION_ADD = "app.mind.mobile.action.WATER_ADD"
        const val ACTION_REMOVE = "app.mind.mobile.action.WATER_REMOVE"
    }
}
