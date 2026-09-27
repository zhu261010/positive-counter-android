package com.example.positivecounter

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

class TimerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.timerStore.data.first()
                val timers = prefs[TIMERS_JSON]?.let { runCatching { Json.decodeFromString<List<StoredTimer>>(it) }.getOrDefault(emptyList()) } ?: listOfNotNull(prefs[LEGACY_NAME]?.let { n -> prefs[LEGACY_START]?.let { s -> StoredTimer("legacy", n, s) } })
                val timer = timers.firstOrNull()
                val name = timer?.name; val start = timer?.startEpochMillis
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.timer_widget)
                    if (name != null && start != null) {
                        val seconds = maxOf(0, Duration.between(Instant.ofEpochMilli(start), Instant.now()).seconds)
                        views.setTextViewText(R.id.widget_name, name)
                        views.setTextViewText(R.id.widget_elapsed, format(seconds))
                        views.setTextViewText(R.id.widget_hint, "正在累计")
                    } else {
                        views.setTextViewText(R.id.widget_name, "正计时")
                        views.setTextViewText(R.id.widget_elapsed, "尚未创建")
                        views.setTextViewText(R.id.widget_hint, "打开应用开始计时")
                    }
                    manager.updateAppWidget(id, views)
                }
            } finally { pending.finish() }
        }
    }
    private fun format(total: Long): String {
        val days = total / 86400
        return when {
            days < 1 -> { val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60; "%02d小时 %02d分 %02d秒".format(h, m, s) }
            days < 365 -> { val h = (total % 86400) / 3600; "%d天 %02d小时".format(days, h) }
            else -> { val years = days / 365; val remainingDays = days % 365; "%d年 %d天".format(years, remainingDays) }
        }
    }
}
