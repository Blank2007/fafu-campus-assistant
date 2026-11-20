package com.campusglass

import com.campusglass.schedule.ZfsoftParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v5.7：正方教务课表 HTML 解析器的纯逻辑单测（无 Android 依赖，HTML 为手写样本） */
class ZfsoftParserTest {

    private val header = "<tr>" + "<td>节次</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td>" + "</tr>"

    // ---------------- 旧版正方 xskbcx.aspx：4 行组 ----------------

    @Test
    fun parse_oldFormat_fourLineGroup() {
        val html = "<table id=\"Table1\">" + header +
            "<tr><td>第1,2节</td>" +
            "<td>高等数学<br>张三<br>1-16周<br>教1-101</td>" +
            "<td></td><td></td><td></td><td></td><td></td><td></td></tr>" +
            "</table>"
        val res = ZfsoftParser.parse(html)
        assertEquals(1, res.courses.size)
        val c = res.courses.first()
        assertEquals("高等数学", c.name)
        assertEquals("张三", c.teacher)
        assertEquals("教1-101", c.location)
        assertEquals(1, c.weekday)
        assertEquals(1, c.startPeriod)
        assertEquals(2, c.endPeriod)
        assertEquals((1..16).toSet(), c.weeks)
        assertEquals(1, res.courseNames)
        assertTrue(res.warnings.isEmpty())
    }

    @Test
    fun parse_oldFormat_threeLineGroup() {
        val html = "<table>" + header +
            "<tr><td>第3,4节</td>" +
            "<td>大学英语<br>2-15周<br>外语楼201</td>" +
            "<td></td><td></td><td></td><td></td><td></td><td></td></tr>" +
            "</table>"
        val res = ZfsoftParser.parse(html)
        assertEquals(1, res.courses.size)
        val c = res.courses.first()
        assertEquals("大学英语", c.name)
        assertEquals("外语楼201", c.location)
        assertEquals(1, c.weekday)
        assertEquals(3, c.startPeriod)
        assertEquals(4, c.endPeriod)
        assertEquals((2..15).toSet(), c.weeks)
    }

    // ---------------- 新版正方 /jwglxt：花括号格式 ----------------

    @Test
    fun parse_newFormat_braceAndExplicitPeriods() {
        val html = "<table>" + header +
            "<tr><td>第3,4节</td>" +
            "<td>大学物理<br>李四<br>{1-16周|周一第3-4节|教2-201}</td>" +
            "<td></td><td></td><td></td><td></td><td></td><td></td></tr>" +
            "</table>"
        val res = ZfsoftParser.parse(html)
        assertEquals(1, res.courses.size)
        val c = res.courses.first()
        assertEquals("大学物理", c.name)
        assertEquals("李四", c.teacher)
        assertEquals("教2-201", c.location)
        assertEquals(1, c.weekday)
        assertEquals(3, c.startPeriod)
        assertEquals(4, c.endPeriod)
        assertEquals((1..16).toSet(), c.weeks)
    }

    @Test
    fun parse_newFormat_multipleCoursesInOneCell() {
        val html = "<table>" + header +
            "<tr><td>第1,2节</td>" +
            "<td>课程A<br>王五<br>{1-8周|周一第1-2节|A101}<br>课程B<br>赵六<br>{9-16周|周一第1-2节|B202}</td>" +
            "<td></td><td></td><td></td><td></td><td></td><td></td></tr>" +
            "</table>"
        val res = ZfsoftParser.parse(html)
        assertEquals(2, res.courses.size)
        assertEquals(2, res.courseNames)
        assertEquals(setOf("课程A", "课程B"), res.courses.map { it.name }.toSet())
    }

    // ---------------- 异常输入 ----------------

    @Test
    fun parse_blankHtml_returnsWarning() {
        val res = ZfsoftParser.parse("   ")
        assertTrue(res.courses.isEmpty())
        assertTrue(res.warnings.isNotEmpty())
    }

    @Test
    fun parse_noTable_returnsWarning() {
        val res = ZfsoftParser.parse("<html><body>欢迎使用正方教务管理系统</body></html>")
        assertTrue(res.courses.isEmpty())
        assertTrue(res.warnings.first().contains("没有找到课表表格"))
    }

    @Test
    fun parse_headerWithoutWeekday_returnsWarning() {
        val res = ZfsoftParser.parse("<table><tr><td>a</td><td>b</td></tr></table>")
        assertTrue(res.courses.isEmpty())
        assertTrue(res.warnings.isNotEmpty())
    }

    @Test
    fun parse_emptyTimetable_reportsNoCourses() {
        val html = "<table>" + header +
            "<tr><td>第1,2节</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>" +
            "</table>"
        val res = ZfsoftParser.parse(html)
        assertTrue(res.courses.isEmpty())
        assertTrue(res.warnings.isNotEmpty())
    }

    // ---------------- 周次解析 ----------------

    @Test
    fun parseWeeks_range() {
        assertEquals((1..16).toSet(), ZfsoftParser.parseWeeks("1-16周"))
    }

    @Test
    fun parseWeeks_oddOnly() {
        assertEquals((1..16).filter { it % 2 == 1 }.toSet(), ZfsoftParser.parseWeeks("1-16周(单)"))
    }

    @Test
    fun parseWeeks_evenOnly() {
        assertEquals((2..16).filter { it % 2 == 0 }.toSet(), ZfsoftParser.parseWeeks("2-16周(双)"))
    }

    @Test
    fun parseWeeks_list() {
        assertEquals(setOf(3, 5, 7), ZfsoftParser.parseWeeks("3,5,7周"))
    }

    @Test
    fun parseWeeks_blank() {
        assertTrue(ZfsoftParser.parseWeeks("").isEmpty())
    }

    @Test
    fun parseWeeks_capsAtMaxWeek() {
        assertTrue(ZfsoftParser.parseWeeks("1-9999周").max() <= 60)
    }
}
