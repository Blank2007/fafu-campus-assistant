package com.campusglass

import com.campusglass.pickup.ExpressApi
import com.campusglass.schedule.PeriodTable
import com.campusglass.schedule.ScheduleStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V4-28：纯逻辑单元测试（无 Android 依赖） */
class ScheduleLogicTest {

    @Test
    fun parseWeeks_basics() {
        assertEquals((1..16).toSet(), ScheduleStore.parseWeeks("1-16"))
        assertEquals(setOf(2, 3, 4, 5, 7, 8), ScheduleStore.parseWeeks("2-5,7-8"))
        assertEquals((1..16).filter { it % 2 == 1 }.toSet(), ScheduleStore.parseWeeks("1-16 单"))
        assertEquals(emptySet<Int>(), ScheduleStore.parseWeeks(""))
    }

    @Test
    fun parseWeeks_capsAtMaxWeek() {
        val w = ScheduleStore.parseWeeks("1-9999")
        assertTrue(w.size <= ScheduleStore.MAX_WEEK)
        assertTrue(ScheduleStore.parseWeeks("1-9999").max() <= ScheduleStore.MAX_WEEK)
    }

    @Test
    fun compactWeeks_roundTrip() {
        val set = setOf(2, 3, 4, 5, 7, 8, 11)
        assertEquals(set, ScheduleStore.parseWeeks(ScheduleStore.compactWeeks(set)))
    }

    @Test
    fun validTimeEntry_rejectsOutOfRange() {
        assertTrue(PeriodTable.validTimeEntry("08:00-08:45"))
        assertFalse(PeriodTable.validTimeEntry("99:99-99:99"))   // V4-27
        assertFalse(PeriodTable.validTimeEntry("08:00"))
        assertFalse(PeriodTable.validTimeEntry("未设置-未设置"))
    }

    @Test
    fun normalize_fullWidthAndSpaces() {
        assertEquals("SF1234567890", ExpressApi.normalize("ＳＦ 123 456 7890"))
        assertEquals("ZT000000000000", ExpressApi.normalize("ZT００００００００００００"))
    }

    // ---------------- 校历数据（HolidayData） ----------------

    @Test
    fun calendar_sportsMeet_isPresentWithCourseChangeReminder() {
        // v4.19：南平校区运动会 11/9-11/10，必须带「课程可能有变化」提醒
        val e = com.campusglass.schedule.HolidayData.events
            .single { it.name == "南平校区运动会" }
        assertEquals(java.time.LocalDate.of(2026, 11, 9), e.start)
        assertEquals(java.time.LocalDate.of(2026, 11, 10), e.end)
        assertEquals(com.campusglass.schedule.HolidayData.EventKind.ACTIVITY, e.kind)
        assertFalse("运动会不是放假，不应按假期高亮", e.kind.isHoliday)
        assertTrue("必须提示查看课程调整通知", e.note.contains("课程"))
    }

    @Test
    fun calendar_eventsAreSortedByDate() {
        // 顺序即校历时间顺序（便于人眼核对），并且查周时要能按日期排序
        val evs = com.campusglass.schedule.HolidayData.events
        assertTrue(evs.all { !it.start.isAfter(it.end) })
        assertTrue(evs.zipWithNext().all { (a, b) -> !a.start.isAfter(b.start) })
    }

    @Test
    fun calendar_eventsInWeek_includesSportsMeet() {
        // 11/9(周一) - 11/15(周日) 这一周应命中运动会
        val hit = com.campusglass.schedule.HolidayData.eventsInWeek(
            java.time.LocalDate.of(2026, 11, 9),
            java.time.LocalDate.of(2026, 11, 15),
        )
        assertTrue(hit.any { it.name == "南平校区运动会" })
    }
}
