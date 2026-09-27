package com.campusglass.schedule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** 一门课（同一门课多个时段拆成多条） */
data class Course(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val weekday: Int,          // 1=周一 ... 7=周日
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
)

/** 节次作息（南平/金山校区常规作息） */
object PeriodTable {
    val times: List<Pair<LocalTime, LocalTime>> = listOf(
        LocalTime.of(8, 0) to LocalTime.of(8, 45),
        LocalTime.of(8, 55) to LocalTime.of(9, 40),
        LocalTime.of(10, 0) to LocalTime.of(10, 45),
        LocalTime.of(10, 55) to LocalTime.of(11, 40),
        LocalTime.of(14, 0) to LocalTime.of(14, 45),
        LocalTime.of(14, 55) to LocalTime.of(15, 40),
        LocalTime.of(16, 0) to LocalTime.of(16, 45),
        LocalTime.of(16, 55) to LocalTime.of(17, 40),
        LocalTime.of(19, 0) to LocalTime.of(19, 45),
        LocalTime.of(19, 55) to LocalTime.of(20, 40),
        LocalTime.of(20, 50) to LocalTime.of(21, 35),
    )

    fun start(p: Int) = times[(p - 1).coerceIn(0, times.size - 1)].first
    fun end(p: Int) = times[(p - 1).coerceIn(0, times.size - 1)].second
}

/** 课表本地存储 */
object ScheduleStore {
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private fun prefs(c: Context) = c.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    fun loadCourses(c: Context): List<Course> = runCatching {
        val arr = JSONArray(prefs(c).getString("courses", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Course(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.getString("name"),
                teacher = o.optString("teacher"),
                location = o.optString("location"),
                weekday = o.getInt("weekday"),
                startPeriod = o.getInt("start"),
                endPeriod = o.getInt("end"),
                weeks = o.getString("weeks").split(",").filter { it.isNotBlank() }
                    .mapNotNull { it.toIntOrNull() }.toSet(),
            )
        }
    }.getOrDefault(emptyList())

    fun saveCourses(c: Context, courses: List<Course>) {
        val arr = JSONArray()
        courses.forEach { co ->
            arr.put(
                JSONObject()
                    .put("id", co.id).put("name", co.name).put("teacher", co.teacher)
                    .put("location", co.location).put("weekday", co.weekday)
                    .put("start", co.startPeriod).put("end", co.endPeriod)
                    .put("weeks", co.weeks.sorted().joinToString(","))
            )
        }
        prefs(c).edit().putString("courses", arr.toString()).apply()
    }

    /** 学期第一周周一 */
    fun termStart(c: Context): LocalDate = runCatching {
        LocalDate.parse(prefs(c).getString("termStart", "2026-08-31"), DATE_FMT)
    }.getOrDefault(LocalDate.of(2026, 8, 31))   // 2026-09-28 为第 5 周周一

    fun setTermStart(c: Context, date: LocalDate) {
        prefs(c).edit().putString("termStart", date.format(DATE_FMT)).apply()
    }

    fun currentWeek(c: Context): Int {
        val days = ChronoUnit.DAYS.between(termStart(c), LocalDate.now())
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }

    fun weekdayOf(date: LocalDate): Int = date.dayOfWeek.value   // 1=周一 ... 7=周日

    /** 第 w 周的周 x 对应日期 */
    fun dateOf(c: Context, week: Int, weekday: Int): LocalDate =
        termStart(c).plusDays(((week - 1) * 7 + (weekday - 1)).toLong())

    /** 解析周次文本：支持 "1-16" / "1-8,10" / "1-16周(单)" */
    fun parseWeeks(raw: String): Set<Int> {
        val single = raw.contains("单")
        val even = raw.contains("双")
        val base = raw.removeSuffix("周")
        val weeks = mutableSetOf<Int>()
        Regex("\\d+\\s*[-~－—]\\s*\\d+").findAll(base).forEach { m ->
            val parts = m.value.split(Regex("\\s*[-~－—]\\s*"))
            val a = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val b = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (a > 0 && b >= a) weeks += a..b
        }
        val rest = base.replace(Regex("\\d+\\s*[-~－—]\\s*\\d+"), " ")
        Regex("\\d+").findAll(rest).forEach { m ->
            m.value.toIntOrNull()?.let { if (it > 0) weeks += it }
        }
        return weeks.filter { w ->
            when {
                single -> w % 2 == 1
                even -> w % 2 == 0
                else -> true
            }
        }.toSet()
    }
}
