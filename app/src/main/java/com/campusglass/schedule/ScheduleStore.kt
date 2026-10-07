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
    val weekday: Int,          // 1=周一 ... 6=周六 7=周日
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
)

/**
 * 节次作息（每天节数 1-24 自定义；逐节时间可改）。
 * 审查修复：T4 条目错位（不再丢条目）、S24 结果缓存、W-7 变更后推送小部件。
 */
object PeriodTable {

    private val DEFAULT_TIMES = listOf(
        "08:00-08:45", "08:55-09:40", "10:00-10:45", "10:55-11:40",
        "14:00-14:45", "14:55-15:40", "16:00-16:45", "16:55-17:40",
        "19:00-19:45", "19:55-20:40", "20:50-21:35",
    )

    const val MAX_PERIODS = 24

    private val TIME_RE = Regex("^\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}$")

    private fun prefs(c: Context) = c.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    // S24：按 prefs 版本缓存解析结果
    @Volatile private var cacheKey = -1
    @Volatile private var cacheList: List<String> = emptyList()

    /** 返回每节 "HH:mm-HH:mm"；条目损坏时按位补 "未设置-未设置"（T4：不再整体错位） */
    fun all(c: Context): List<String> {
        val p = prefs(c)
        val ver = p.getInt("timesVer", 0)
        synchronized(this) {
            if (ver == cacheKey) return cacheList
        }
        val saved = p.getString("periodTimes", null)
        val list = saved?.split(",")?.map { it.trim() } ?: emptyList()
        val normalized = (0 until MAX_PERIODS).map { i ->
            val e = list.getOrNull(i).orEmpty()
            if (TIME_RE.matches(e)) e else if (i < DEFAULT_TIMES.size && list.isEmpty()) DEFAULT_TIMES[i] else "未设置-未设置"
        }
        val result = normalized
        synchronized(this) {
            cacheKey = ver
            cacheList = result
        }
        return result
    }

    fun save(c: Context, times: List<String>) {
        prefs(c).edit()
            .putString("periodTimes", times.take(MAX_PERIODS).joinToString(","))
            .putInt("timesVer", (cacheKey + 1).coerceAtLeast(0))
            .apply()
        runCatching { com.campusglass.widget.TodayWidgetProvider.pushUpdate(c) }   // W-7
    }

    /** 恢复默认作息（T8） */
    fun reset(c: Context) {
        prefs(c).edit()
            .remove("periodTimes")
            .putInt("timesVer", (cacheKey + 1).coerceAtLeast(0))
            .apply()
        runCatching { com.campusglass.widget.TodayWidgetProvider.pushUpdate(c) }
    }

    fun startStr(c: Context, p: Int): String =
        all(c)[(p - 1).coerceIn(0, MAX_PERIODS - 1)].substringBefore("-")

    fun endStr(c: Context, p: Int): String =
        all(c)[(p - 1).coerceIn(0, MAX_PERIODS - 1)].substringAfter("-")

    fun periodsPerDay(c: Context): Int =
        prefs(c).getInt("periodsPerDay", DEFAULT_TIMES.size).coerceIn(1, MAX_PERIODS)

    fun setPeriodsPerDay(c: Context, n: Int) {
        prefs(c).edit().putInt("periodsPerDay", n.coerceIn(1, MAX_PERIODS)).apply()
        runCatching { com.campusglass.widget.TodayWidgetProvider.pushUpdate(c) }   // W-7
    }
}

/**
 * 课表本地存储（周从【周日】起算）。
 * 审查修复：S1 周次上限、S2 学期起始日范围、S11 整表损坏保护、S24 缓存、W-1 weekOf、W-7 推送。
 */
object ScheduleStore {

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 学期起始日合理区间（S2） */
    private val TERM_MIN: LocalDate = LocalDate.of(2000, 1, 1)
    private val TERM_MAX: LocalDate = LocalDate.of(2100, 12, 31)

    /** 周次硬上限（S1：防 OOM） */
    const val MAX_WEEK = 60

    private fun prefs(c: Context) = c.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    /** S11：整串数据损坏时为 true，禁止写回（防止把空表覆盖掉原数据） */
    @Volatile
    var dataCorrupt = false
        private set

    /** 清除损坏标记并允许重新写入（用户显式重置后调用） */
    fun clearCorrupt(c: Context) {
        dataCorrupt = false
        prefs(c).edit().putString("courses", "[]").apply()
        runCatching { com.campusglass.widget.TodayWidgetProvider.pushUpdate(c) }
    }

    fun loadCourses(c: Context): List<Course> {
        val raw = prefs(c).getString("courses", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrNull()
        if (arr == null) {
            // S11：整串损坏——置只读标记，绝不静默清空/写回
            dataCorrupt = true
            prefs(c).edit().putString("courses_corrupt_backup", raw).apply()
            return emptyList()
        }
        dataCorrupt = false
        // S5/SC-5：单条损坏跳过，不影响整表
        return (0 until arr.length()).mapNotNull { i ->
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
    }

    /** 写回课表；数据损坏保护期间拒绝写入（S11）。返回是否写入成功 */
    fun saveCourses(c: Context, courses: List<Course>): Boolean {
        if (dataCorrupt) return false
        val arr = JSONArray()
        courses.forEach { co ->
            arr.put(
                JSONObject()
                    .put("id", co.id).put("name", co.name).put("teacher", co.teacher)
                    .put("location", co.location).put("weekday", co.weekday)
                    .put("start", co.startPeriod).put("end", co.endPeriod)
                    .put("weeks", compactWeeks(co.weeks))
            )
        }
        prefs(c).edit().putString("courses", arr.toString()).apply()
        runCatching { com.campusglass.widget.TodayWidgetProvider.pushUpdate(c) }
        return true
    }

    /** 周次集合 → 压缩区间（1-16 / 2-5,7-8） */
    fun compactWeeks(weeks: Set<Int>): String {
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

    /**
     * 解析周次文本（支持 1-16 / 2-5,7-8 / 单 / 双）。
     * S1：区间上限 MAX_WEEK（b > 60 视为非法，截断并记录）。
     * 返回集合可能为空——调用方需区分"没填"与"解析不出"（S9）。
     */
    fun parseWeeks(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
        val single = raw.contains("单")
        val even = raw.contains("双")
        val base = raw.removeSuffix("周")
        val weeks = mutableSetOf<Int>()
        Regex("\\d+\\s*[-~－—]\\s*\\d+").findAll(base).forEach { m ->
            val parts = m.value.split(Regex("\\s*[-~－—]\\s*"))
            val a = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val bRaw = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val b = bRaw.coerceAtMost(MAX_WEEK)          // S1：上限
            if (a in 1..MAX_WEEK && b >= a) weeks += a..b
        }
        val rest = base.replace(Regex("\\d+\\s*[-~－—]\\s*\\d+"), " ")
        Regex("\\d+").findAll(rest).forEach { m ->
            m.value.toIntOrNull()?.let { if (it in 1..MAX_WEEK) weeks += it }
        }
        return weeks.filter { w ->
            when {
                single && even -> true
                single -> w % 2 == 1
                even -> w % 2 == 0
                else -> true
            }
        }.toSet()
    }

    // ---- 学期与周次 ----

    /** 设置学期起始日（S2：范围 2000-2100，越界拒绝） */
    fun setTermStart(c: Context, d: LocalDate): Boolean {
        if (d.isBefore(TERM_MIN) || d.isAfter(TERM_MAX)) return false
        prefs(c).edit().putString("termStart", d.format(DATE_FMT)).apply()
        runCatching { com.campusglass.widget.TodayWidgetProvider.pushUpdate(c) }   // W-7
        return true
    }

    fun termStart(c: Context): LocalDate {
        val s = prefs(c).getString("termStart", null)
        val d = runCatching { s?.let { LocalDate.parse(it, DATE_FMT) } }.getOrNull()
        return if (d == null || d.isBefore(TERM_MIN) || d.isAfter(TERM_MAX)) {
            LocalDate.of(2026, 8, 30)     // 默认：2026-2027 学年第一周周日
        } else d
    }

    /** 指定日期属于第几周（周日起算）（W-1） */
    fun weekOf(c: Context, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(termStart(c), date)
        return (days / 7 + 1).toInt().coerceIn(1, MAX_WEEK)   // S19：上限
    }

    fun currentWeek(c: Context): Int = weekOf(c, LocalDate.now())

    /** 第 w 周的周 x 对应日期（S2：兜底不抛异常） */
    fun dateOf(c: Context, week: Int, weekday: Int): LocalDate = runCatching {
        termStart(c).plusDays(((week - 1) * 7 + (weekday % 7)).toLong())
    }.getOrDefault(termStart(c))

    /** 星期（1=周一 … 7=周日；周日为一周起点） */
    fun weekdayOf(date: LocalDate): Int {
        val iso = date.dayOfWeek.value          // 1=Mon..7=Sun
        return if (iso == 7) 7 else iso
    }
}

/**
 * 课表分享码：FAFUSCH1: + Base64(deflate(JSON))。
 * 输入侧安全（SH1-SH8）：长度上限、解压上限、字段语义校验、跳过计数、空课表合法。
 */
object ScheduleShare {

    private const val PREFIX = "FAFUSCH1:"
    private const val MAX_CODE_LEN = 8_000
    private const val MAX_INFLATED = 2 * 1024 * 1024
    private const val MAX_COURSES = 200

    /** 导入结果：学期起始日 + 课程 + 跳过条数 */
    data class Imported(val termStart: String, val courses: List<Course>, val skipped: Int)

    fun encode(courses: List<Course>, termStart: String): String {
        val arr = JSONArray()
        courses.take(MAX_COURSES).forEach { c ->
            arr.put(
                JSONArray().put(c.name).put(c.teacher).put(c.location)
                    .put(c.weekday).put(c.startPeriod).put(c.endPeriod)
                    .put(ScheduleStore.compactWeeks(c.weeks))
            )
        }
        val json = JSONArray().put(termStart).put(arr).toString()
        return PREFIX + android.util.Base64.encodeToString(
            deflate(json.toByteArray(Charsets.UTF_8)),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
        )
    }

    /** 识别并解析分享码；非法返回 null。识别到但空课表 → 合法（S21） */
    fun decode(text: String): Imported? {
        val idx = text.indexOf(PREFIX)
        if (idx < 0) return null
        var code = text.substring(idx + PREFIX.length).trim().split(Regex("\\s")).firstOrNull().orEmpty()
        if (code.isBlank() || code.length > MAX_CODE_LEN) return null        // SH1
        while (code.length % 4 != 0) code += "="                             // SH8：聊天软件吞 padding

        val rawBytes = runCatching {
            android.util.Base64.decode(code, android.util.Base64.URL_SAFE)
        }.getOrNull() ?: return null
        val bodyBytes = inflate(rawBytes) ?: rawBytes
        val body = String(bodyBytes, Charsets.UTF_8)

        return runCatching {
            // 新格式：[termStart, [[name,teacher,loc,weekday,start,end,weeks], ...]]
            runCatching { JSONArray(body) }.getOrNull()?.let { a ->
                if (a.length() >= 2 && a.optJSONArray(1) != null) {
                    val term = sanitizeTerm(a.optString(0))
                    val arrIn = a.getJSONArray(1)
                    val list = mutableListOf<Course>()
                    var skipped = 0
                    for (i in 0 until arrIn.length().coerceAtMost(MAX_COURSES)) {
                        val c = arrIn.optJSONArray(i) ?: run { skipped++; continue }
                        val built = buildCourse(
                            name = c.optString(0), teacher = c.optString(1), location = c.optString(2),
                            weekday = c.optInt(3, -1), start = c.optInt(4, -1), end = c.optInt(5, -1),
                            weeksRaw = c.optString(6),
                        ) ?: run { skipped++; continue }
                        list += built
                    }
                    return Imported(term, list, skipped)      // 空课表合法（S21）
                }
                null
            } ?: run {
                // 旧格式兼容：{"termStart":..,"courses":[{..}]}
                val o = JSONObject(body)
                val arrIn = o.optJSONArray("courses") ?: JSONArray()
                val list = mutableListOf<Course>()
                var skipped = 0
                for (i in 0 until arrIn.length().coerceAtMost(MAX_COURSES)) {
                    val c = arrIn.optJSONObject(i) ?: run { skipped++; continue }   // E7 同源防护
                    val built = buildCourse(
                        name = c.optString("name"), teacher = c.optString("teacher"),
                        location = c.optString("location"),
                        weekday = c.optInt("weekday", -1), start = c.optInt("start", -1),
                        end = c.optInt("end", -1), weeksRaw = c.optString("weeks"),
                    ) ?: run { skipped++; continue }
                    list += built
                }
                Imported(sanitizeTerm(o.optString("termStart")), list, skipped)
            }
        }.getOrNull()
    }

    /** S4：字段语义校验；非法返回 null（计入 skipped） */
    private fun buildCourse(
        name: String, teacher: String, location: String,
        weekday: Int, start: Int, end: Int, weeksRaw: String,
    ): Course? {
        if (name.isBlank() || name.length > 30) return null
        if (weekday !in 1..7) return null
        val maxP = PeriodTable.MAX_PERIODS
        if (start !in 1..maxP) return null
        val end2 = end.coerceIn(start, maxP)
        val weeks = ScheduleStore.parseWeeks(weeksRaw)
        if (weeks.isEmpty()) return null                    // 空周次 = 非法（S4）
        return Course(
            name = name.trim(), teacher = teacher.trim().take(20),
            location = location.trim().take(20),
            weekday = weekday, startPeriod = start, endPeriod = end2, weeks = weeks,
        )
    }

    private fun sanitizeTerm(s: String): String {
        val d = runCatching { LocalDate.parse(s) }.getOrNull() ?: return ""
        return if (d.year in 2000..2100) d.toString() else ""   // S2/SH3
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

    /** SH1/S3：输出上限 + finally inf.end()（防 deflate 炸弹） */
    private fun inflate(bytes: ByteArray): ByteArray? {
        var inf: java.util.zip.Inflater? = null
        return try {
            inf = java.util.zip.Inflater()
            inf.setInput(bytes)
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_INFLATED) return null      // 超限中止
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { inf?.end() }
        }
    }
}
