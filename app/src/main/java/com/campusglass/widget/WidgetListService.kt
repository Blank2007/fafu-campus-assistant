package com.campusglass.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.campusglass.R
import com.campusglass.schedule.PeriodTable
import com.campusglass.schedule.ScheduleStore
import java.time.LocalDate

/**
 * 小部件课程列表数据服务（ListView 可滚动）。
 * 每个小部件实例按 wid 读取自己的今/明偏移。
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

        override fun onCreate() {}

        override fun onDataSetChanged() {
            val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val offset = prefs.getInt("off_$wid", 0)
            val base = LocalDate.now().plusDays(offset.toLong())
            val week = ScheduleStore.currentWeek(context)
            val wd = ScheduleStore.weekdayOf(base)
            items = ScheduleStore.loadCourses(context)
                .filter { it.weekday == wd && week in it.weeks }
                .sortedBy { it.startPeriod }
        }

        override fun onDestroy() {}

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val c = items[position]
            val rv = RemoteViews(context.packageName, R.layout.widget_row)

            val night =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val titleColor = if (night) 0xFFFFFFFF.toInt() else 0xFF1B2233.toInt()
            val subColor = if (night) 0xB3FFFFFF.toInt() else 0x661B2233.toInt()
            val timeColor = if (night) 0xFF9CC4FF.toInt() else 0xFF2A5CA8.toInt()

            val start = runCatching { PeriodTable.startStr(context, c.startPeriod) }.getOrDefault("")
            val end = runCatching { PeriodTable.endStr(context, c.endPeriod) }.getOrDefault("")

            rv.setInt(R.id.row_time, "setTextColor", timeColor)
            rv.setInt(R.id.row_name, "setTextColor", titleColor)
            rv.setInt(R.id.row_loc, "setTextColor", subColor)
            rv.setTextViewText(R.id.row_time, "$start\n$end")          // 上/下课时间
            rv.setTextViewText(R.id.row_name, c.name + "（第${c.startPeriod}-${c.endPeriod}节）")
            rv.setTextViewText(R.id.row_loc, c.location.ifBlank { c.teacher }.ifBlank { " " })
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
