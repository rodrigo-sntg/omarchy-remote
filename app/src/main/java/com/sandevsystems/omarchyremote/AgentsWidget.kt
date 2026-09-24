package com.sandevsystems.omarchyremote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home screen widget (docs/PLANO-V2.md §8): how many herdr agents wait for the person. KeypadApp
 * pushes each change ([show]); the last text is kept so a relaunched launcher shows it too.
 */
class AgentsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences("keypad", Context.MODE_PRIVATE)
        render(context, manager, ids, prefs.getString("widget_title", "Omarchy Remote")!!, prefs.getString("widget_detail", "herdr")!!,
            prefs.getInt("widget_text", 0xFFECEEF0.toInt()), prefs.getInt("widget_dim", 0xFF99A1A8.toInt()), prefs.getBoolean("widget_urgent", false),
            prefs.getInt("widget_accent", 0xFFC5F24A.toInt()))
    }

    companion object {
        fun show(context: Context, title: String, detail: String, text: Int, dim: Int, accent: Int, urgent: Boolean) {
            context.getSharedPreferences("keypad", Context.MODE_PRIVATE).edit()
                .putString("widget_title", title).putString("widget_detail", detail).putInt("widget_text", text)
                .putInt("widget_dim", dim).putInt("widget_accent", accent).putBoolean("widget_urgent", urgent).apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AgentsWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids, title, detail, text, dim, urgent, accent)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, title: String, detail: String,
                           text: Int, dim: Int, urgent: Boolean, accent: Int) {
            val open = PendingIntent.getActivity(
                context, 3, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_AGENT, "")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_agents).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_detail, detail)
                setTextColor(R.id.widget_title, if (urgent) accent else text)
                setTextColor(R.id.widget_detail, dim)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
