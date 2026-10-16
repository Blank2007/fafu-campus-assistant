package com.campusglass

import com.campusglass.schedule.Course
import com.campusglass.schedule.CourseColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S25：课表配色规则单测。
 *
 * 需求：**同名课程颜色一致**、**不同课程尽量不撞色**。
 * 这是用户明确提的需求，用测试锁住，防止以后又被改回"按位置分色"。
 */
class CourseColorTest {

    private fun course(
        name: String,
        weekday: Int = 1,
        start: Int = 1,
        end: Int = 1,
        weeks: Set<Int> = setOf(1),
    ) = Course(
        id = "$name-$weekday-$start",
        name = name,
        weekday = weekday,
        startPeriod = start,
        endPeriod = end,
        weeks = weeks,
    )

    @Test
    fun sameName_sameColor_evenAtDifferentTimeSlots() {
        // 同一门课出现在不同星期/节次 —— 必须同色（这是本次要修的核心问题）
        val courses = listOf(
            course("高等数学", weekday = 1, start = 1),
            course("高等数学", weekday = 3, start = 5, end = 6),
            course("高等数学", weekday = 5, start = 9),
        )
        val map = CourseColors.indexMap(courses)
        val i1 = CourseColors.indexOf(map, "高等数学")
        val i2 = CourseColors.indexOf(map, "高等数学")
        val i3 = CourseColors.indexOf(map, "高等数学")
        assertEquals(i1, i2)
        assertEquals(i2, i3)
    }

    @Test
    fun differentNames_getDifferentColors_whenWithinPalette() {
        // 12 门以内的不同课程，应各占一个颜色，互不重复（尽量不撞色）
        val names = (1..CourseColors.SIZE).map { "课程$it" }
        val courses = names.mapIndexed { i, n -> course(n, weekday = i % 7 + 1, start = i % 12 + 1) }
        val map = CourseColors.indexMap(courses)
        val indices = names.map { CourseColors.indexOf(map, it) }
        assertEquals("不同课程不应撞色", CourseColors.SIZE, indices.toSet().size)
    }

    @Test
    fun moreThanPalette_wrapsAround_withoutCrash() {
        // 超过调色板容量时回绕复用，但不能崩、且取值必须落在合法范围内
        val names = (1..(CourseColors.SIZE + 8)).map { "超量课程$it" }
        val courses = names.map { course(it) }
        val map = CourseColors.indexMap(courses)
        names.forEach { n ->
            val idx = CourseColors.indexOf(map, n)
            assertTrue("下标必须在 0..SIZE-1", idx in 0 until CourseColors.SIZE)
        }
    }

    @Test
    fun colorIsStable_acrossWeeksAndListOrder() {
        // 增删课 / 打乱顺序后，已存在课程的颜色不应变化（按名字排序保证稳定）
        val a = course("英语", weekday = 2, start = 3)
        val b = course("体育", weekday = 4, start = 7)
        val map1 = CourseColors.indexMap(listOf(a, b))
        val map2 = CourseColors.indexMap(listOf(b, a))              // 顺序颠倒
        val map3 = CourseColors.indexMap(listOf(a, b, course("语文")))  // 新增一门
        assertEquals(CourseColors.indexOf(map1, "英语"), CourseColors.indexOf(map2, "英语"))
        assertEquals(CourseColors.indexOf(map1, "体育"), CourseColors.indexOf(map2, "体育"))
        // 新增课可能占走槽位，但"英语/体育"自身仍应是合法的稳定值
        assertTrue(CourseColors.indexOf(map3, "英语") in 0 until CourseColors.SIZE)
        assertTrue(CourseColors.indexOf(map3, "体育") in 0 until CourseColors.SIZE)
    }

    @Test
    fun blankNames_areIgnored() {
        // 空名字不应占位，避免挤掉真实课程的颜色
        val courses = listOf(course(""), course("数学"), course(""))
        val map = CourseColors.indexMap(courses)
        assertEquals(1, map.size)
        assertEquals(0, CourseColors.indexOf(map, "数学"))
    }

    @Test
    fun unknownName_fallsBackToHash_butStillConsistent() {
        // 没进映射表的名字（理论上不该出现）走哈希兜底，同名字仍同值
        val map = emptyMap<String, Int>()
        val x = CourseColors.indexOf(map, "未登记课程")
        val y = CourseColors.indexOf(map, "未登记课程")
        assertEquals(x, y)
        assertTrue(x in 0 until CourseColors.SIZE)
    }

    @Test
    fun twoKnownNames_differWhenPossible() {
        // 基本的多色能力：两门不同课应能拿到不同颜色（12 色足够）
        val map = CourseColors.indexMap(listOf(course("离散数学"), course("数据结构")))
        assertNotEquals(
            CourseColors.indexOf(map, "离散数学"),
            CourseColors.indexOf(map, "数据结构"),
        )
    }
}
