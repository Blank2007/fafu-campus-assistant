package com.campusglass.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.campusglass.R
import com.campusglass.schedule.PeriodTable
import com.campusglass.schedule.ScheduleStore
import java.time.LocalDate

/**
 * 小部件课程列表数据服务 v4（W-1/W-2/W-5/W-6/W-9）。
 * 深浅色由资源系统处理（widget_colors.xml / -night），不再手写颜色。
 */
class WidgetListService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WidgetFactory(applicationContext, intent)

    class WidgetFactory(
        private val context: Context,
        intent: Intent,
    ) : RemoteViewsFactory {

        private val wid = intent.getIntExtra("wid", -1)
        private var items: List<com.campusglass.schedule.Course> = emptyList()
        private var maxP = PeriodTable.MAX_PERIODS                 // V4-24

        override fun onCreate() {}

        override fun onDataSetChanged() {
            val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val offset = prefs.getInt("off_$wid", 0)
            val base = LocalDate.now().plusDays(offset.toLong())
            val week = ScheduleStore.weekOf(context, base)        // W-1：按 base 算
            val wd = ScheduleStore.weekdayOf(base)
            val showWeekend = com.campusglass.ui.theme.ThemePrefs.showWeekend.value   // V4-24
            maxP = PeriodTable.periodsPerDay(context)                                 // V4-24
            items = ScheduleStore.loadCourses(context)
                .filter { it.weekday == wd && week in it.weeks }
                .filter { showWeekend || (it.weekday != 6 && it.weekday != 7) }       // 与 App 一致
                .sortedBy { it.startPeriod }
        }

        override fun onDestroy() {}

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            // W-2：越界保护（数据变化瞬间按旧位置取行）
            if (position !in items.indices) {
                val empty = RemoteViews(context.packageName, R.layout.widget_empty_row)
                empty.setTextViewText(R.id.empty_text, "")
                return empty
            }
            val c = items[position]
            val rv = RemoteViews(context.packageName, R.layout.widget_row)

            val start = runCatching { PeriodTable.startStr(context, c.startPeriod) }.getOrDefault("")
            val end = runCatching { PeriodTable.endStr(context, c.endPeriod) }.getOrDefault("")
            val endShown = c.endPeriod.coerceAtMost(maxP)          // V4-24：与 App 节数一致
            rv.setTextViewText(R.id.row_time, "$start\n$end")
            rv.setTextViewText(R.id.row_name, c.name + "（第${c.startPeriod}-${endShown}节）")
            rv.setTextViewText(R.id.row_loc, c.location.ifBlank { c.teacher }.ifBlank { " " })
            // W-5：行内点击 fill-in
            rv.setOnClickFillInIntent(
                R.id.row_root,
                Intent().putExtra("openTab", "schedule"),
            )
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        // W-9：稳定 id 用课程 id 哈希（不再用 position）
        override fun getItemId(position: Int): Long =
            items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
