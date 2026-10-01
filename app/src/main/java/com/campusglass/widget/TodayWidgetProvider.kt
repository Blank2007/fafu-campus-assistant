package com.campusglass.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.campusglass.R
import com.campusglass.schedule.PeriodTable
import com.campusglass.schedule.ScheduleStore
import java.time.LocalDate

/**
 * 桌面小部件：课表（今日/明日切换）。
 * - 深/浅两套背景，跟随系统深色模式
 * - 右上角箭头：今日 ⇄ 明日
 * - 课表保存即时刷新 + 每 30 分钟自动刷新
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            val id = intent.getIntExtra("wid", -1)
            if (id >= 0) {
                val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
                prefs.edit().putInt("off_$id", if (prefs.getInt("off_$id", 0) == 0) 1 else 0).apply()
                pushUpdate(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateOne(context, mgr, id)
    }

    companion object {

        private const val ACTION_NEXT = "com.campusglass.WIDGET_NEXT"

        /** 课表变化后主动刷新所有小部件 */
        fun pushUpdate(context: Context) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
                for (id in ids) updateOne(context, mgr, id)
            }
        }

        private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val rv = RemoteViews(context.packageName, R.layout.widget_today)
            val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val offset = prefs.getInt("off_$id", 0)          // 0=今日 1=明日

            // 深/浅双背景（跟随系统深色模式）
            val night =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            rv.setInt(
                R.id.widget_root, "setBackgroundResource",
                if (night) R.drawable.widget_bg else R.drawable.widget_bg_light,
            )

            val titleColor = if (night) 0xFFFFFFFF.toInt() else 0xFF1B2233.toInt()
            val subColor = if (night) 0xB3FFFFFF.toInt() else 0x661B2233.toInt()
            val timeColor = if (night) 0xFF9CC4FF.toInt() else 0xFF2A5CA8.toInt()

            val base = LocalDate.now().plusDays(offset.toLong())
            val week = ScheduleStore.currentWeek(context)
            val wd = ScheduleStore.weekdayOf(base)

            rv.setInt(R.id.widget_title, "setTextColor", titleColor)
            rv.setInt(R.id.widget_date, "setTextColor", subColor)
            rv.setInt(R.id.widget_next, "setTextColor", titleColor)
            rv.setTextViewText(R.id.widget_title, if (offset == 0) "📚 今日课表" else "📅 明日课表")
            rv.setTextViewText(R.id.widget_date, "第${week}周 · ${base.monthValue}/${base.dayOfMonth}")
            rv.setTextViewText(R.id.widget_next, if (offset == 0) "➡️" else "⬅️")

            val courses = ScheduleStore.loadCourses(context)
                .filter { it.weekday == wd && week in it.weeks }
                .sortedBy { it.startPeriod }

            rv.removeAllViews(R.id.widget_rows)

            if (courses.isEmpty()) {
                val row = RemoteViews(context.packageName, R.layout.widget_row)
                row.setInt(R.id.row_name, "setTextColor", titleColor)
                row.setInt(R.id.row_loc, "setTextColor", subColor)
                row.setTextViewText(R.id.row_time, "")
                row.setTextViewText(R.id.row_name, if (offset == 0) "今天没有课 🎉" else "明天没有课 🎉")
                row.setTextViewText(R.id.row_loc, "点开 App 查看完整课表")
                rv.addView(R.id.widget_rows, row)
            } else {
                for (c in courses.take(6)) {
                    val row = RemoteViews(context.packageName, R.layout.widget_row)
                    row.setInt(R.id.row_time, "setTextColor", timeColor)
                    row.setInt(R.id.row_name, "setTextColor", titleColor)
                    row.setInt(R.id.row_loc, "setTextColor", subColor)
                    val time = runCatching { PeriodTable.startStr(context, c.startPeriod) }.getOrDefault("")
                    row.setTextViewText(R.id.row_time, time)
                    row.setTextViewText(R.id.row_name, c.name + "（第${c.startPeriod}-${c.endPeriod}节）")
                    row.setTextViewText(R.id.row_loc, c.location.ifBlank { c.teacher }.ifBlank { " " })
                    rv.addView(R.id.widget_rows, row)
                }
            }

            // 右上角箭头：今日 ⇄ 明日
            val nextIntent = Intent(context, TodayWidgetProvider::class.java)
                .setAction(ACTION_NEXT)
                .putExtra("wid", id)
            val nextPi = android.app.PendingIntent.getBroadcast(
                context, id, nextIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_next, nextPi)

            // 点击主体 → 直达 App 课表页
            val openIntent = Intent(context, com.campusglass.MainActivity::class.java)
                .putExtra("openTab", "schedule")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = android.app.PendingIntent.getActivity(
                context, 0, openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)

            mgr.updateAppWidget(id, rv)
        }
    }
}
