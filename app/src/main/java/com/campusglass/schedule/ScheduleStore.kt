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
 */
object PeriodTable {

    private val DEFAULT_TIMES = listOf(
        "08:00-08:45", "08:55-09:40", "10:00-10:45", "10:55-11:40",
        "14:00-14:45", "14:55-15:40", "16:00-16:45", "16:55-17:40",
        "19:00-19:45", "19:55-20:40", "20:50-21:35",
    )

    /** 每天最多节数（用户可自定义 1-24） */
    const val MAX_PERIODS = 24

    private fun prefs(c: Context) = c.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    /** 返回每节 "HH:mm-HH:mm"（用户可改），最多 24 节 */
    fun all(c: Context): List<String> {
        val saved = prefs(c).getString("periodTimes", null)
        val list = saved?.split(",")?.filter { it.contains("-") } ?: emptyList()
        val base = list + DEFAULT_TIMES.drop(list.size) + List(MAX_PERIODS) { "未设置-未设置" }
        return base.take(MAX_PERIODS)
    }

    fun save(c: Context, times: List<String>) {
        prefs(c).edit().putString("periodTimes", times.take(MAX_PERIODS).joinToString(",")).apply()
    }

    fun startStr(c: Context, p: Int): String =
        all(c)[(p - 1).coerceIn(0, MAX_PERIODS - 1)].substringBefore("-")

    fun endStr(c: Context, p: Int): String =
        all(c)[(p - 1).coerceIn(0, MAX_PERIODS - 1)].substringAfter("-")

    /** 每天节数（自定义 1-24，不再被默认课表长度卡死） */
    fun periodsPerDay(c: Context): Int =
        prefs(c).getInt("periodsPerDay", DEFAULT_TIMES.size).coerceIn(1, MAX_PERIODS)

    fun setPeriodsPerDay(c: Context, n: Int) {
        prefs(c).edit().putInt("periodsPerDay", n.coerceIn(1, MAX_PERIODS)).apply()
    }

    /**
 * 课表分享码：FAFUSCH1: + Base64(JSON)，同软件内一键导入。
 */
object ScheduleShare {
    private const val PREFIX = "FAFUSCH1:"

    /** 周次集合 → 压缩区间（1-16 / 2-5,7-8） */
    private fun compactWeeks(weeks: Set<Int>): String {
        val sorted = weeks.sorted()
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

    fun encode(courses: List<Course>, termStart: String): String {
        // 紧凑数组 + Deflate 压缩，分享码长度缩短一半以上
        val arr = JSONArray()
        courses.forEach { c ->
            arr.put(
                JSONArray().put(c.name).put(c.teacher).put(c.location)
                    .put(c.weekday).put(c.startPeriod).put(c.endPeriod).put(compactWeeks(c.weeks))
            )
        }
        val json = JSONArray().put(termStart).put(arr).toString()
        val compressed = deflate(json.toByteArray(Charsets.UTF_8))
        return PREFIX + android.util.Base64.encodeToString(
            compressed,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
        )
    }

    private fun deflate(bytes: ByteArray): ByteArray = runCatching {
        val d = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
        d.setInput(bytes); d.finish()
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        out.toByteArray()
    }.getOrDefault(bytes)

    private fun inflate(bytes: ByteArray): ByteArray? = runCatching {
        val inf = java.util.zip.Inflater()
        inf.setInput(bytes)
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0) break
            out.write(buf, 0, n)
        }
        inf.end()
        out.toByteArray().takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** 从任意文本识别分享码；兼容新（压缩）旧（明文 JSON）两种格式 */
    fun decode(text: String): Pair<String, List<Course>>? {
        val idx = text.indexOf(PREFIX)
        if (idx < 0) return null
        val code = text.substring(idx + PREFIX.length).trim().split(Regex("\\s")).firstOrNull().orEmpty()
        if (code.isBlank()) return null
        val rawBytes = runCatching {
            android.util.Base64.decode(code, android.util.Base64.URL_SAFE)
        }.getOrNull() ?: return null
        val bodyBytes = inflate(rawBytes) ?: rawBytes     // 新格式压缩；旧格式原样
        val body = String(bodyBytes, Charsets.UTF_8)
        return runCatching {
            // 新格式：[termStart, [[name,teacher,loc,weekday,start,end,weeks], ...]]
            runCatching { JSONArray(body) }.getOrNull()?.let { a ->
                if (a.length() >= 2 && a.optJSONArray(1) != null) {
                    val term = a.optString(0)
                    val list = (0 until a.getJSONArray(1).length()).mapNotNull { i ->
                        runCatching {
                            val c = a.getJSONArray(1).getJSONArray(i)
                            Course(
                                name = c.getString(0),
                                teacher = c.optString(1),
                                location = c.optString(2),
                                weekday = c.getInt(3),
                                startPeriod = c.getInt(4),
                                endPeriod = c.getInt(5),
                                weeks = ScheduleStore.parseWeeks(c.optString(6)),
                            )
                        }.getOrNull()
                    }
                    if (list.isNotEmpty()) return term to list
                }
            }
            // 旧格式：{"termStart":..,"courses":[{..}]}
            val o = JSONObject(body)
            val arr = o.optJSONArray("courses") ?: JSONArray()
            val list = (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val c = arr.getJSONObject(i)
                    Course(
                        name = c.getString("name"),
                        teacher = c.optString("teacher"),
                        location = c.optString("location"),
                        weekday = c.getInt("weekday"),
                        startPeriod = c.getInt("start"),
                        endPeriod = c.getInt("end"),
                        weeks = ScheduleStore.parseWeeks(c.optString("weeks")),
                    )
                }.getOrNull()
            }
            if (list.isEmpty()) null else o.optString("termStart", "") to list
        }.getOrNull()
    }
}
}

/** 课表本地存储（周从【周日】起算） */
object ScheduleStore {
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private fun prefs(c: Context) = c.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    fun loadCourses(c: Context): List<Course> = runCatching {
        val arr = JSONArray(prefs(c).getString("courses", "[]"))
        // SC-5：单条损坏不影响整张课表，坏条目跳过
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                Course(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.getString("name"),
                    teacher = o.optString("teacher"),
                    location = o.optString("location"),
                    weekday = o.getInt("weekday"),
                    startPeriod = o.getInt("start"),
                    endPeriod = o.getInt("end"),
                    weeks = parseWeeks(o.optString("weeks")),
                )
            }.getOrNull()
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
        // 课表变化后即时刷新桌面小部件
        runCatching { com.campusglass.widget.TodayWidgetProvider.pushUpdate(c) }
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
}

/** 顶层别名：课表分享码（实际定义在 PeriodTable 内） */
typealias ScheduleShare = PeriodTable.ScheduleShare
