package com.campusglass.schedule

import java.time.LocalDate

/**
 * 节假日参考数据（2026 中秋/国庆 ~ 2027 端午）。
 * 用于课表页"本周期间假期"标记；具体安排以学校校历/官方通知为准。
 */
object HolidayData {

    data class Holiday(val name: String, val start: LocalDate, val end: LocalDate) {
        fun overlaps(from: LocalDate, to: LocalDate): Boolean = !start.isAfter(to) && !end.isBefore(from)
    }

    val holidays = listOf(
        Holiday("中秋节", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27)),
        Holiday("国庆节", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7)),
        Holiday("元旦", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3)),
        Holiday("春节", LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 12)),
        Holiday("清明节", LocalDate.of(2027, 4, 3), LocalDate.of(2027, 4, 5)),
        Holiday("劳动节", LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 5)),
        Holiday("端午节", LocalDate.of(2027, 6, 9), LocalDate.of(2027, 6, 11)),
        Holiday("寒假", LocalDate.of(2027, 1, 21), LocalDate.of(2027, 2, 20)),   // 校历：共31天
    )

    /** 调休上课日（依据 2026-2027 学年第一学期校历） */
    val makeupDays = listOf(
        Holiday("调休上课（周日上10月6日周二的课）", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20)),
        Holiday("调休上课（周六上10月7日周三的课）", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10)),
    )

    /** 某周内的调休上课描述 */
    fun makeupInWeek(weekStart: LocalDate, weekEnd: LocalDate): List<String> =
        makeupDays.filter { it.overlaps(weekStart, weekEnd) }.map { h ->
            "${h.start.monthValue}/${h.start.dayOfMonth} ${h.name}"
        }

    /** 某周（周一~周日）内的假期描述，如 ["中秋节 9/25-9/27"] */
    fun holidaysInWeek(weekStart: LocalDate, weekEnd: LocalDate): List<String> =
        holidays.filter { it.overlaps(weekStart, weekEnd) }.map { h ->
            "${h.name} ${h.start.monthValue}/${h.start.dayOfMonth}-${h.end.monthValue}/${h.end.dayOfMonth}"
        }
}
