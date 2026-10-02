package com.campusglass.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.campusglass.R
import com.campusglass.home.Hitokoto
import com.campusglass.schedule.PeriodTable
import com.campusglass.schedule.ScheduleStore
import java.time.LocalDate

/**
 * 桌面小部件：课表（今日/明日）+ 每日诗句。
 * - 深/浅双背景跟随系统；无课时居中显示「今日无课」
 * - 右上角箭头：今日 ⇄ 明日；底部诗句：点一下换一句
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEXT -> {
                val id = intent.getIntExtra("wid", -1)
                if (id >= 0) {
                    val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
                    prefs.edit().putInt("off_$id", if (prefs.getInt("off_$id", 0) == 0) 1 else 0).apply()
                    pushUpdate(context)
                    return
                }
            }
            ACTION_QUOTE -> {
                // 换一句：立即后台拉新句并重绘（避免拉到同一句）
                val pr = goAsync()
                Thread {
                    runCatching {
                        Hitokoto.fetchFresh(context)
                        pushUpdate(context)
                    }
                    pr.finish()
                }.start()
                return
            }
        }
        super.onReceive(context, intent)
        // 首次/每日：后台拉今日诗句后重绘（不阻塞主线程）
        refreshQuoteAsync(context, force = false)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateOne(context, mgr, id)
    }

    private fun refreshQuoteAsync(context: Context, force: Boolean) {
        val pr = goAsync()
        Thread {
            runCatching {
                if (force) Hitokoto.fetchNew(context) else Hitokoto.fetchToday(context)
                pushUpdate(context)
            }
            pr.finish()
        }.start()
    }

    companion object {

        private const val ACTION_NEXT = "com.campusglass.WIDGET_NEXT"
        private const val ACTION_QUOTE = "com.campusglass.WIDGET_QUOTE"

        /** 课表/诗句变化后主动刷新所有小部件 */
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

            // 深/浅双背景
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
            rv.setInt(R.id.widget_quote, "setTextColor", subColor)
            rv.setTextViewText(R.id.widget_title, if (offset == 0) "📚 今日课表" else "📅 明日课表")
            rv.setTextViewText(R.id.widget_date, "第${week}周 · ${base.monthValue}/${base.dayOfMonth}")
            rv.setTextViewText(R.id.widget_next, if (offset == 0) "➡️" else "⬅️")

            // 每日诗句（点击换一句）
            val quote = Hitokoto.cached(context)
            rv.setTextViewText(
                R.id.widget_quote,
                if (quote != null) "「${quote.text}」 —— ${quote.from}（点我换一句）" else "—",
            )

            val courses = ScheduleStore.loadCourses(context)
                .filter { it.weekday == wd && week in it.weeks }
                .sortedBy { it.startPeriod }

            rv.removeAllViews(R.id.widget_rows)

            if (courses.isEmpty()) {
                // 无课：居中显示（区分今日/明日）
                val empty = RemoteViews(context.packageName, R.layout.widget_empty_row)
                empty.setTextViewText(
                    R.id.empty_text,
                    if (offset == 0) "今日无课" else "明日无课",
                )
                empty.setInt(R.id.empty_text, "setTextColor", titleColor)
                rv.addView(R.id.widget_rows, empty)
            } else {
                for (c in courses.take(6)) {
                    val row = RemoteViews(context.packageName, R.layout.widget_row)
                    row.setInt(R.id.row_time, "setTextColor", timeColor)
                    row.setInt(R.id.row_name, "setTextColor", titleColor)
                    row.setInt(R.id.row_loc, "setTextColor", subColor)
                    val start = runCatching { PeriodTable.startStr(context, c.startPeriod) }.getOrDefault("")
                    val end = runCatching { PeriodTable.endStr(context, c.endPeriod) }.getOrDefault("")
                    row.setTextViewText(R.id.row_time, "$start\n$end")   // 上/下课时间都标
                    row.setTextViewText(R.id.row_name, c.name + "（第${c.startPeriod}-${c.endPeriod}节）")
                    row.setTextViewText(R.id.row_loc, c.location.ifBlank { c.teacher }.ifBlank { " " })
                    rv.addView(R.id.widget_rows, row)
                }
            }

            // 右上角箭头：今日 ⇄ 明日（广播方案）
            val nextPi = android.app.PendingIntent.getBroadcast(
                context, id,
                Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_NEXT).putExtra("wid", id),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_next, nextPi)

            // 诗句：点一下换一句
            val quotePi = android.app.PendingIntent.getBroadcast(
                context, id + 1000,
                Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_QUOTE),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_quote, quotePi)

            // 主体点击 → App 课表页
            val pi = android.app.PendingIntent.getActivity(
                context, id + 2000,
                Intent(context, com.campusglass.MainActivity::class.java)
                    .putExtra("openTab", "schedule")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)

            mgr.updateAppWidget(id, rv)
        }
    }
}
