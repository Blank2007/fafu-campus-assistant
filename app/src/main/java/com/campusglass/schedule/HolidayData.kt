package com.campusglass.schedule

import java.time.LocalDate

/**
 * 校历事件数据（2026-2027 学年第一学期）。
 *
 * 来源：福建农林大学金山学院 2026-2027 学年第一学期校历（学生工作部 xgb.fafu.edu.cn 发布），
 * App 首页「🖼 查看校历原图」内嵌同名扫描件 assets/calendar_2026_2027_s1.jpg。
 * 具体安排以学校校历/官方通知为准（校历原文注明「时间安排若有调整另行通知」）。
 *
 * 用途：课表页「校历提示栏」（[ScheduleScreen] 的 HolidayBanner）按周展示事件；
 * 首页「本学期校历」卡片亦引用本表。
 *
 * 更新流程见交接文档 04-外部数据来源与更新手册.md 第二节。
 */
object HolidayData {

    /** 校历事件类型：决定提示栏的图标与是否按"假期"高亮 */
    enum class EventKind(val emoji: String, val isHoliday: Boolean) {
        /** 法定节假日放假（中秋节/国庆节/寒假等） */
        HOLIDAY("🏖", true),

        /** 调休上课（周末补课） */
        MAKEUP("⚠️", false),

        /** 教学安排（报到/开课/补考） */
        TEACHING("🏫", false),

        /** 军训 */
        MILITARY("🎖", false),

        /** 传统节日（不放假，如春节/元宵，仅作日历提示） */
        FESTIVAL("🧨", false),

        /** 校级大型活动（运动会等）——课程/作息可能调整，需提醒查看通知 */
        ACTIVITY("🏅", false),
    }

    /** 校历事件：带类型，用于课表校历提示栏 */
    data class CalendarEvent(
        val name: String,
        val start: LocalDate,
        val end: LocalDate,
        val kind: EventKind,
        /** 补充说明（如调休的"上10月6日(周二)的课程"），可空 */
        val note: String = "",
    ) {
        fun overlaps(from: LocalDate, to: LocalDate): Boolean = !start.isAfter(to) && !end.isBefore(from)

        fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

        val isSingleDay: Boolean get() = start == end

        /** "9/25-9/27" 或单日 "8/30" */
        fun dateSpan(): String = if (isSingleDay) "${start.monthValue}/${start.dayOfMonth}"
        else "${start.monthValue}/${start.dayOfMonth}-${end.monthValue}/${end.dayOfMonth}"

        /** "8/30 师生报到" */
        fun title(): String = "${dateSpan()} $name"
    }

    // ======================= 校历事件全表（2026-2027 学年第一学期） =======================

    /**
     * 校历事件表（逐条对照校历原文备注栏录入）。
     * 顺序即校历时间顺序，便于人眼核对。
     */
    val events: List<CalendarEvent> = listOf(
        // —— 8 月 ——
        CalendarEvent("师生报到", LocalDate.of(2026, 8, 30), LocalDate.of(2026, 8, 30), EventKind.TEACHING),
        CalendarEvent("老生上课", LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 31), EventKind.TEACHING),

        // —— 9 月 ——
        CalendarEvent("学生补考", LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 6), EventKind.TEACHING),
        CalendarEvent("新生报到", LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), EventKind.TEACHING),
        CalendarEvent("新生上课", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), EventKind.TEACHING),
        CalendarEvent(
            "调休上课（周日上10/6周二的课）",
            LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20), EventKind.MAKEUP,
            note = "9/20（周日）上 10/6（周二）的课程",
        ),
        CalendarEvent("中秋节放假", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27), EventKind.HOLIDAY, note = "共3天"),

        // —— 10 月 ——
        CalendarEvent("国庆节放假调休", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), EventKind.HOLIDAY, note = "共7天"),
        CalendarEvent(
            "调休上课（周六上10/7周三的课）",
            LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10), EventKind.MAKEUP,
            note = "10/10（周六）上 10/7（周三）的课程",
        ),
        CalendarEvent("新生军训", LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 8), EventKind.MILITARY),

        // —— 11 月 ——
        CalendarEvent(
            "南平校区运动会",
            LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10), EventKind.ACTIVITY,
            note = "南平校区运动会（11/9-11/10）。运动会期间课程与作息可能有调整，请注意查看学院/教务通知。",
        ),

        // —— 2027 年 1 月 ——
        CalendarEvent("元旦放假", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), EventKind.HOLIDAY, note = "以国务院办公厅通知为准"),
        CalendarEvent("寒假", LocalDate.of(2027, 1, 21), LocalDate.of(2027, 2, 20), EventKind.HOLIDAY, note = "共31天"),

        // —— 2027 年 2 月 ——
        CalendarEvent("春节", LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 6), EventKind.FESTIVAL),
        CalendarEvent("元宵节", LocalDate.of(2027, 2, 20), LocalDate.of(2027, 2, 20), EventKind.FESTIVAL),
    )

    // ======================= 查询方法 =======================

    /**
     * 某周（周一~周日）内的**全部**校历事件（课表「校历提示栏」用）。
     * 排序：假期 > 调休 > 军训 > 教学 > 节日；同类型按日期先后。
     */
    fun eventsInWeek(weekStart: LocalDate, weekEnd: LocalDate): List<CalendarEvent> =
        events.filter { it.overlaps(weekStart, weekEnd) }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.start }))

    /** 覆盖某日期的事件（可能多条，如春节与寒假会同时命中） */
    fun eventsOn(date: LocalDate): List<CalendarEvent> = events.filter { it.contains(date) }

    /** 数据覆盖的最后一天（用于过期提醒） */
    val dataEnd: LocalDate = events.maxOfOrNull { it.end } ?: LocalDate.now()
}
