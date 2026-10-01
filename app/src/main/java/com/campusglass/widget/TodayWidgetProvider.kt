package com.campusglass.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.campusglass.R
import com.campusglass.schedule.PeriodTable
import com.campusglass.schedule.ScheduleStore
import java.time.LocalDate

/**
 * 桌面小部件：今日课表。
 * - 30 分钟自动刷新 + 课表保存时即时刷新（ScheduleStore.saveCourses 触发）
 * - 深色半透明圆角卡片风格，任何壁纸都清晰
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateOne(context, mgr, id)
    }

    companion object {

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
            val today = LocalDate.now()
            val week = ScheduleStore.currentWeek(context)
            val todayWd = ScheduleStore.weekdayOf(today)

            rv.setTextViewText(R.id.widget_title, "📚 今日课表")
            rv.setTextViewText(R.id.widget_date, "第${week}周 · ${today.monthValue}/${today.dayOfMonth}")

            val courses = ScheduleStore.loadCourses(context)
                .filter { it.weekday == todayWd && week in it.weeks }
                .sortedBy { it.startPeriod }

            rv.removeAllViews(R.id.widget_rows)

            if (courses.isEmpty()) {
                val row = RemoteViews(context.packageName, R.layout.widget_row)
                row.setTextViewText(R.id.row_time, "")
                row.setTextViewText(R.id.row_name, "今天没有课 🎉")
                row.setTextViewText(R.id.row_loc, "点开 App 查看完整课表")
                rv.addView(R.id.widget_rows, row)
            } else {
                for (c in courses.take(6)) {
                    val row = RemoteViews(context.packageName, R.layout.widget_row)
                    val time = runCatching { PeriodTable.startStr(context, c.startPeriod) }.getOrDefault("")
                    row.setTextViewText(R.id.row_time, time)
                    row.setTextViewText(R.id.row_name, c.name + "（第${c.startPeriod}-${c.endPeriod}节）")
                    row.setTextViewText(R.id.row_loc, c.location.ifBlank { c.teacher }.ifBlank { " " })
                    rv.addView(R.id.widget_rows, row)
                }
            }
            mgr.updateAppWidget(id, rv)
        }
    }
}
