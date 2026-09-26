package com.campusglass.schedule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
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
    val weeks: Set<Int>,       // 第几周集合
)

/** 节次作息（福建农林大学金山学院常规作息，可在 JSON 里直接改） */
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

    /** 由开始时间反查最接近的节次（ICS 导入用） */
    fun periodOf(t: LocalTime): Int =
        times.indexOfFirst { !it.first.isAfter(t) && !it.second.isBefore(t) }
            .let { if (it >= 0) it + 1 else times.indices.minByOrNull { i -> abs(times[i].first, t) }!! + 1 }

    private fun abs(a: LocalTime, b: LocalTime) = Math.abs(ChronoUnit.MINUTES.between(a, b))
}

/** 课表本地存储（JSON over SharedPreferences） */
object ScheduleStore {
    private const val DATE_FMT = "yyyy-MM-dd"
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
                    .map { it.toInt() }.toSet(),
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

    /** 学期第一周周一（计算“第几周”用），默认 2026-09-07，可在界面修改 */
    fun termStart(c: Context): LocalDate {
        val s = prefs(c).getString("termStart", "2026-09-07") ?: "2026-09-07"
        return LocalDate.parse(s, DateTimeFormatter.ofPattern(DATE_FMT))
    }

    fun setTermStart(c: Context, date: LocalDate) {
        prefs(c).edit()
            .putString("termStart", date.format(DateTimeFormatter.ofPattern(DATE_FMT))).apply()
    }

    fun currentWeek(c: Context): Int {
        val days = ChronoUnit.DAYS.between(termStart(c), LocalDate.now())
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }

    /** 第 w 周的周 x 对应日期 */
    fun dateOf(c: Context, week: Int, weekday: Int): LocalDate =
        termStart(c).plusDays(((week - 1) * 7 + (weekday - 1)).toLong())

    fun weekdayOf(date: LocalDate): Int =
        when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> 1; DayOfWeek.TUESDAY -> 2; DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4; DayOfWeek.FRIDAY -> 5; DayOfWeek.SATURDAY -> 6
            else -> 7
        }
}
