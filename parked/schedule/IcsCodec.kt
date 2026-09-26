package com.campusglass.schedule

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 课表 ⇄ ICS(iCalendar, RFC 5545) 编解码。
 * 生成思路参考：KerryChia/ruc-schedule-extension（节次→时间映射 + VALARM），致谢见“关于”页。
 */
object IcsCodec {

    private val stampFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** 导出：每门课在每个上课周生成一个 VEVENT（含 10 分钟前提醒） */
    fun export(context: Context, courses: List<Course>): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//CampusGlass//FAFU Jinshan Schedule//CN")
        sb.appendLine("X-WR-CALNAME:金山学院课表")
        courses.forEach { c ->
            c.weeks.sorted().forEach { w ->
                val date = ScheduleStore.dateOf(context, w, c.weekday)
                val start = LocalDateTime.of(date, PeriodTable.start(c.startPeriod))
                val end = LocalDateTime.of(date, PeriodTable.end(c.endPeriod))
                sb.appendLine("BEGIN:VEVENT")
                sb.appendLine("UID:${c.id}-w${w}@campusglass")
                sb.appendLine("DTSTAMP:${LocalDateTime.now().format(stampFmt)}")
                sb.appendLine("DTSTART:${start.format(stampFmt)}")
                sb.appendLine("DTEND:${end.format(stampFmt)}")
                sb.appendLine("SUMMARY:${c.name}")
                sb.appendLine("LOCATION:${c.location}")
                sb.appendLine("DESCRIPTION:${c.teacher} 第${w}周 第${c.startPeriod}-${c.endPeriod}节")
                sb.appendLine("BEGIN:VALARM")
                sb.appendLine("TRIGGER:-PT10M")
                sb.appendLine("ACTION:DISPLAY")
                sb.appendLine("DESCRIPTION:${c.name} 即将上课")
                sb.appendLine("END:VALARM")
                sb.appendLine("END:VEVENT")
            }
        }
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    /** 导入：按 (课程名, 星期, 起止时间) 聚合出周次集合 */
    fun import(context: Context, ics: String): List<Course> {
        val events = ics.split("BEGIN:VEVENT").drop(1).map { it.substringBefore("END:VEVENT") }
        data class Key(val name: String, val weekday: Int, val sp: Int, val ep: Int, val loc: String, val teacher: String)
        val weeksByKey = mutableMapOf<Key, MutableSet<Int>>()

        events.forEach { e ->
            fun field(name: String): String =
                Regex("(?im)^$name[;:](.*)$").find(e)?.groupValues?.get(1)?.trim().orEmpty()

            val name = field("SUMMARY").ifBlank { "未命名课程" }
            val loc = field("LOCATION")
            val desc = field("DESCRIPTION")
            val teacher = desc.split(" ").firstOrNull().orEmpty()
            val startRaw = Regex("(?m)^DTSTART[^:]*:(\\d{8}T\\d{6})").find(e)?.groupValues?.get(1) ?: return@forEach
            val endRaw = Regex("(?m)^DTEND[^:]*:(\\d{8}T\\d{6})").find(e)?.groupValues?.get(1) ?: startRaw
            val start = LocalDateTime.parse(startRaw, stampFmt)
            val end = LocalDateTime.parse(endRaw, stampFmt)
            val weekday = ScheduleStore.weekdayOf(start.toLocalDate())
            val sp = PeriodTable.periodOf(start.toLocalTime())
            val ep = PeriodTable.periodOf(end.toLocalTime().minusMinutes(1))
            val week = ((ChronoDays.between(ScheduleStore.termStart(context), start.toLocalDate())) / 7 + 1).toInt()
            weeksByKey.getOrPut(Key(name, weekday, sp, ep, loc, teacher)) { mutableSetOf() } += week
        }
        return weeksByKey.map { (k, ws) ->
            Course(
                name = k.name, teacher = k.teacher, location = k.loc,
                weekday = k.weekday, startPeriod = k.sp, endPeriod = k.ep.coerceAtLeast(k.sp),
                weeks = ws.filter { it > 0 }.toSet(),
            )
        }
    }

    /** JSON 导入：[{"name","teacher","location","weekday","start","end","weeks":[1,2]}] */
    fun importJson(json: String): List<Course> {
        val arr = org.json.JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Course(
                name = o.getString("name"),
                teacher = o.optString("teacher"),
                location = o.optString("location"),
                weekday = o.getInt("weekday"),
                startPeriod = o.getInt("start"),
                endPeriod = o.getInt("end"),
                weeks = o.getJSONArray("weeks").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() },
            )
        }
    }

    fun exportJson(courses: List<Course>): String {
        val arr = org.json.JSONArray()
        courses.forEach { c ->
            arr.put(
                org.json.JSONObject()
                    .put("name", c.name).put("teacher", c.teacher).put("location", c.location)
                    .put("weekday", c.weekday).put("start", c.startPeriod).put("end", c.endPeriod)
                    .put("weeks", org.json.JSONArray(c.weeks.sorted()))
            )
        }
        return arr.toString(2)
    }

    private object ChronoDays {
        fun between(a: LocalDate, b: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(a, b)
    }
}
