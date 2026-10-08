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
}
