package com.campusglass.schedule

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** 一门课（同一门课多个时段拆成多条，同名即同一门课） */
data class Course(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val weekday: Int,          // 1=周一 ... 6=周六 7=周日（周为起始日）
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,       // 支持 2-5,7-8 这类分段周次
)

/**
 * 节次作息（可在设置里逐节修改上课/结束时间；每天节数可自定义）。
 * 默认作息：福建农林大学金山/南平校区常规。
 *
 * v3.3 修复：`all()` 不再过滤“不含 - 的条目”，避免一条异常数据让后面所有节次错位；
 * 新增 reset() 以便“恢复默认作息”。
 */
object PeriodTable {

    private val DEFAULT_TIMES = listOf(
        "08:00-08:45", "08:55-09:40", "10:00-10:45", "10:55-11:40",
        "14:00-14:45", "14:55-15:40", "16:00-16:45", "16:55-17:40",
        "19:00-19:45", "19:55-20:40", "20:50-21:35",
    )

    /** 每天最多节数（用户可自定义 1-24） */
    const val MAX_PERIODS = 24

    /** 未设置时的占位（不要用 LocalTime.parse 去解析它） */
    const val UNSET = "未设置"

    private const val UNSET_RANGE = "$UNSET-$UNSET"

    private fun prefs(c: Context) = c.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    /** 返回每节 "HH:mm-HH:mm"（用户可改），固定 24 条且下标与节次一一对应 */
    fun all(c: Context): List<String> {
        val saved = prefs(c).getString("periodTimes", null)
        val raw = saved?.split(",")?.map { it.trim() } ?: emptyList()
        val out = ArrayList<String>(MAX_PERIODS)
        for (i in 0 until MAX_PERIODS) {
            val v = raw.getOrNull(i)
            out += when {
                v == null -> if (i < DEFAULT_TIMES.size) DEFAULT_TIMES[i] else UNSET_RANGE
                v.isBlank() -> UNSET_RANGE
                v.contains("-") -> v
                else -> "$v-$UNSET"
            }
        }
        return out
    }

    fun save(c: Context, times: List<String>) {
        prefs(c).edit().putString("periodTimes", times.take(MAX_PERIODS).joinToString(",")).apply()
    }

    /** 恢复默认作息与默认每天节数 */
    fun reset(c: Context) {
        prefs(c).edit()
            .remove("periodTimes")
            .putInt("periodsPerDay", DEFAULT_TIMES.size)
            .apply()
    }

    fun startStr(c: Context, p: Int): String =
        all(c)[(p - 1).coerceIn(0, MAX_PERIODS - 1)].substringBefore("-")

    fun endStr(c: Context, p: Int): String =
        all(c)[(p - 1).coerceIn(0, MAX_PERIODS - 1)].substringAfter("-")

    /** 是否是一个可用的 "HH:mm-HH:mm"（允许保留「未设置-未设置」占位） */
    fun isValidRange(v: String): Boolean {
        val s = v.substringBefore("-").trim()
        val e = v.substringAfter("-", "").trim()
        if (s == UNSET && e == UNSET) return true
        if (!isValidTime(s) || !isValidTime(e)) return false
        val start = LocalTime.parse(s)
        val end = LocalTime.parse(e)
        return !end.isBefore(start)
    }

    fun isValidTime(v: String): Boolean = runCatching { LocalTime.parse(v.trim()) }.isSuccess

    /** 每天节数（自定义 1-24，不再被默认课表长度卡死） */
    fun periodsPerDay(c: Context): Int =
        prefs(c).getInt("periodsPerDay", DEFAULT_TIMES.size).coerceIn(1, MAX_PERIODS)

    fun setPeriodsPerDay(c: Context, n: Int) {
        prefs(c).edit().putInt("periodsPerDay", n.coerceIn(1, MAX_PERIODS)).apply()
    }

    /**
     * 由开始时间反查最接近的节次（ICS 备用）。
     * 修复：遇到底部占位「未设置」时不再抛异常，最多返回 1。
     */
    fun periodOfTime(c: Context, t: LocalTime): Int {
        all(c).forEachIndexed { idx, v ->
            val start = runCatching { LocalTime.parse(v.substringBefore("-").trim()) }.getOrNull() ?: return@forEachIndexed
            val end = runCatching { LocalTime.parse(v.substringAfter("-", "").trim()) }.getOrNull() ?: return@forEachIndexed
            if (!start.isAfter(t) && !end.isBefore(t)) return idx + 1
        }
        return 1
    }
}

/** 课表本地存储（周从【周日】起算） */
object ScheduleStore {
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private fun prefs(c: Context) = c.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    /**
     * 读取课程表。
     * 修复：逐条解析 + 逐条容错 —— 旧版只要有一条数据异常（缺 name、类型不对）就整表变空。
     */
    fun loadCourses(c: Context): List<Course> {
        val raw = prefs(c).getString("courses", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Course>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val weekday = o.optInt("weekday", 1).let { if (it in 1..7) it else 1 }
            val start = o.optInt("start", 1).coerceIn(1, PeriodTable.MAX_PERIODS)
            val end = o.optInt("end", start).coerceIn(start, PeriodTable.MAX_PERIODS)
            out += Course(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = name,
                teacher = o.optString("teacher"),
                location = o.optString("location"),
                weekday = weekday,
                startPeriod = start,
                endPeriod = end,
                weeks = parseWeeks(o.optString("weeks")),
            )
        }
        return out
    }

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

    /** 学期第一周的【周日】；9/28（周一）= 第五周 ⇒ 第一周周日 = 2026-08-30 */
    fun termStart(c: Context): LocalDate = runCatching {
        LocalDate.parse(prefs(c).getString("termStart", "2026-08-30"), DATE_FMT)
    }.getOrDefault(LocalDate.of(2026, 8, 30))

    fun setTermStart(c: Context, date: LocalDate) {
        prefs(c).edit().putString("termStart", date.format(DATE_FMT)).apply()
    }

    fun currentWeek(c: Context): Int {
        val days = ChronoUnit.DAYS.between(termStart(c), LocalDate.now())
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }

    /** 第 w 周的周 x 对应日期（weekday: 1=周一 ... 7=周日；周日为一周起点） */
    fun dateOf(c: Context, week: Int, weekday: Int): LocalDate =
        termStart(c).plusDays(((week - 1) * 7 + (weekday % 7)).toLong())

    fun weekdayOf(date: LocalDate): Int = date.dayOfWeek.value   // 1=周一 ... 7=周日

    /** 周次解析：支持 "2-5,7-8"、"1-16周(单)"、"3,5" */
    fun parseWeeks(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
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

    /** 周次格式化：把连续周压缩成区间（如 1-16 / 2-5,7-8），用于编辑框回填与详情展示 */
    fun formatWeeks(weeks: Set<Int>): String {
        val sorted = weeks.sorted()
        if (sorted.isEmpty()) return ""
        val sb = StringBuilder()
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            if (sb.isNotEmpty()) sb.append(",")
            sb.append(if (i == j) "${sorted[i]}" else "${sorted[i]}-${sorted[j]}")
            i = j + 1
        }
        return sb.toString()
    }
}
