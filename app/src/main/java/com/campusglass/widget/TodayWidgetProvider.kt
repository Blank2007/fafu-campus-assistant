package com.campusglass.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.campusglass.R
import com.campusglass.home.Hitokoto
import com.campusglass.schedule.ScheduleStore
import java.time.LocalDate

/**
 * 桌面小部件 v4（审查 W-1..W-14 修复）：
 * - 周次按 base 日期计算（W-1）；深浅色交给资源系统（W-6）
 * - 点击走 PendingIntent 模板 + fill-in（W-5）；requestCode 按角色 data URI 区分（W-8）
 * - 零点闹钟 + 开机刷新（W-7）；安装完成刷新（W-4）；避免重复渲染（W-12）
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        // 零点 / 开机 / 安装后 的补刷新
        val data = intent.data?.toString()
        if (data == "widget://midnight" || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            pushUpdate(context)
            scheduleMidnight(context)
            return
        }
        // 今/明切换
        if (intent.getBooleanExtra("op_next", false)) {
            val id = intent.getIntExtra("wid", -1)
            if (id >= 0) {
                val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
                prefs.edit().putInt("off_$id", if (prefs.getInt("off_$id", 0) == 0) 1 else 0).apply()
            }
            pushUpdate(context)
            return
        }
        // 换一句（W-3：单次拉取、短超时，不占满广播时限）
        if (intent.getBooleanExtra("op_quote", false)) {
            val pr = goAsync()
            Thread {
                runCatching {
                    Hitokoto.fetchNew(context)
                    pushUpdate(context)
                }
                pr.finish()
            }.start()
            return
        }
        super.onReceive(context, intent)
        // W-12：只有当天没拉过/拉到新句时才重绘
        refreshQuoteAsync(context)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateOne(context, mgr, id)
        if (appWidgetIds.isNotEmpty()) {
            mgr.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
        }
        scheduleMidnight(context)                                   // W-7
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // W-10：删除小部件时清理它的状态
        val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        appWidgetIds.forEach { editor.remove("off_$it") }
        editor.apply()
        // V4-16：部件删光后取消零点闹钟（不再每天空唤醒）
        runCatching {
            val mgr = android.appwidget.AppWidgetManager.getInstance(context)
            val left = mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            if (left.isEmpty()) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val pi = android.app.PendingIntent.getBroadcast(
                    context, 31415,
                    Intent(context, TodayWidgetProvider::class.java)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setData(Uri.parse("widget://midnight")),
                    android.app.PendingIntent.FLAG_NO_CREATE or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                )
                pi?.let { am.cancel(it) }
            }
        }
    }

    private fun refreshQuoteAsync(context: Context) {
        val before = Hitokoto.cached(context)
        val pr = goAsync()
        Thread {
            runCatching {
                if (Hitokoto.needsFetch(context)) {
                    val after = Hitokoto.fetchToday(context)
                    if (after != before) pushUpdate(context)         // W-12：没变化不重绘
                }
            }
            pr.finish()
        }.start()
    }

    companion object {

        /** 课表/诗句变化后主动刷新所有小部件（含列表数据） */
        fun pushUpdate(context: Context) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
                for (id in ids) updateOne(context, mgr, id)
                if (ids.isNotEmpty()) {
                    mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
                }
            }
        }

        /** W-7：零点后 5 分钟自动刷新（每次更新后重排） */
        private fun scheduleMidnight(context: Context) {
            runCatching {
                val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val pi = android.app.PendingIntent.getBroadcast(
                    context, 31415,
                    Intent(context, TodayWidgetProvider::class.java)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setData(Uri.parse("widget://midnight")),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                )
                val next = LocalDate.now().plusDays(1)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .plusMinutes(5).toInstant().toEpochMilli()
                am.set(android.app.AlarmManager.RTC, next, pi)
            }
        }

        private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val rv = RemoteViews(context.packageName, R.layout.widget_today)
            val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val offset = prefs.getInt("off_$id", 0)          // 0=今日 1=明日

            val base = LocalDate.now().plusDays(offset.toLong())
            val week = ScheduleStore.weekOf(context, base)   // W-1：按 base 算周次
            val wd = ScheduleStore.weekdayOf(base)

            rv.setTextViewText(R.id.widget_title, if (offset == 0) "📚 今日课表" else "📅 明日课表")
            rv.setTextViewText(R.id.widget_date, "第${week}周 · ${base.monthValue}/${base.dayOfMonth}")
            rv.setTextViewText(R.id.widget_next, if (offset == 0) "➡️" else "⬅️")

            // 每日诗句
            val quote = Hitokoto.cached(context)
            rv.setTextViewText(
                R.id.widget_quote,
                if (quote != null) "📜 「${quote.text}」 —— ${quote.from}"
                else "获取失败，稍后再试",
            )

            // 可滚动课程列表
            val svcIntent = Intent(context, WidgetListService::class.java).putExtra("wid", id)
            rv.setRemoteAdapter(R.id.widget_list, svcIntent)
            rv.setEmptyView(R.id.widget_list, R.id.widget_empty_text)
            // W-13：完全没课表 vs 今天/明天没课
            val total = ScheduleStore.loadCourses(context).size
            rv.setTextViewText(
                R.id.widget_empty_text,
                when {
                    total == 0 -> "还没有课表\n去 App 里添加课程吧"
                    offset == 0 -> "今日无课"
                    else -> "明日无课"
                },
            )

            // W-5：列表行点击走模板 + fill-in
            val rowPi = android.app.PendingIntent.getActivity(
                context, id + 30000,
                Intent(context, com.campusglass.MainActivity::class.java)
                    .putExtra("openTab", "schedule")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setPendingIntentTemplate(R.id.widget_list, rowPi)

            // W-8：按角色 data URI 区分，杜绝 requestCode 撞车
            fun rolePi(role: String, op: String?, req: Int): android.app.PendingIntent =
                android.app.PendingIntent.getBroadcast(
                    context, req,
                    Intent(context, TodayWidgetProvider::class.java)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setData(Uri.parse("widget://$role/$id"))
                        .putExtra("wid", id)
                        .apply { if (op != null) putExtra(op, true) },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            rv.setOnClickPendingIntent(R.id.widget_next, rolePi("next", "op_next", 0))
            // 小部件诗词不提供刷新（按需求）

            // 主体点击 → App 课表页
            val pi = android.app.PendingIntent.getActivity(
                context, id + 20000,
                Intent(context, com.campusglass.MainActivity::class.java)
                    .putExtra("openTab", "schedule")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)

            mgr.updateAppWidget(id, rv)
        }
    }
}
