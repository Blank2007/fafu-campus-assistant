package com.campusglass.schedule

import java.util.UUID

/**
 * 正方教务系统课表 HTML 解析器（本机实测适配旧版 V6 的 xskbcx.aspx，同时兼容新版 /jwglxt）。
 *
 * 解析思路参考以下开源项目（仅借鉴思路，代码按本校正方实测结构调整重写）：
 *   - HF-CYGG/Dawn-Course（多校适配分层、DOM → 节次网格）
 *   - KerryChia/ruc-schedule-extension（表头列映射 → 星期）
 *   - YZune/WakeUpSchedule（正方登录与课表页结构）
 *
 * 本校（福建农林大学金山校区/金山学院，旧版 V6）课表的真实形态，2026-11 真机抓页面确认：
 *   表头 8 列：时间(colspan=2) 星期一 … 星期日；
 *   每个非空格子里按 <br> 分块，每门课固定 4 行：
 *       课程名
 *       周四第1,2节{第7-8周}     ← 上课时间 + 周次（自带星期与节次！）
 *       教师（可能带括号里的全名单）
 *       地点
 *   后面还可能跟 3 行调课信息： 2026年12月13日(08:00-09:30) / 正心207 / (调0190)
 *   同一格可能有多门课，所以「每门课 4 行」不能靠固定步长切，必须靠时间行做锚点。
 *
 * 因为表格用了 rowspan/colspan，单元格列号与星期并不一一对应（第1节行的格子会 rowspan 到第2节），
 * 所以主路径完全不看列号：只要格子里的时间行写着「周四第1,2节」，就直接采信。
 * 列位置推算只作为旧版无时间行 / 新版花括号格式的兜底。
 */
object ZfsoftParser {

    private const val MAX_PERIOD = 24

    /** 周次文本特征：用于判断单元格里哪一行是周次 */
    private val weekRe = Regex("""[0-9]{1,2}\s*[-~－—,，、]\s*[0-9]{1,2}\s*周|[0-9]{1,2}\s*周""")

    /** 新版正方的花括号格式 */
    private val braceRe = Regex("""\{([^}]*)\}""")

    /** 第一列节次文本：「第1,2节」「1-2节」「第3节」 */
    private val periodLeadRe = Regex("""([0-9]{1,2})\s*(?:[-~－—,，、]\s*([0-9]{1,2}))?\s*节""")

    /** 上课时间行：「周四第1,2节」「周六第3,4,5节」（本校旧版每门课的第 2 行） */
    private val timeLineRe = Regex("""周([一二三四五六日天])\s*第\s*([0-9,，、\-~－—\s]{1,20}?)节""")

    /** 调课信息的日期行：「2026年12月13日(08:00-09:30)」 */
    private val examDateRe = Regex("""^[0-9]{4}\s*年""")

    private val weekdayOfChar = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '日' to 7, '天' to 7,
    )

    private val rowRe = Regex("(?is)<tr[^>]*>(.*?)</tr>")
    private val cellRe = Regex("(?is)<t[dh][^>]*>(.*?)</t[dh]>")
    private val brRe = Regex("""(?is)<br\s*/?>""")
    private val tagRe = Regex("(?is)<[^>]+>")
    private val entityRe = Regex("""&#(\d+);""")

    /** 解析结果：课程列表 + 面向用户的提示（不是异常，解析能继续） */
    data class ParseResult(val courses: List<Course>, val warnings: List<String>) {
        /** 不同课程名数量（用于给用户展示「N 个时段 / M 门课」） */
        val courseNames: Int get() = courses.map { it.name }.distinct().size
    }

    fun parse(html: String): ParseResult {
        val warnings = mutableListOf<String>()
        if (html.isBlank()) return ParseResult(emptyList(), listOf("页面内容为空，可能没有加载完成"))

        val rows = rowRe.findAll(html).map { it.groupValues[1] }.toList()
        if (rows.isEmpty()) {
            return ParseResult(emptyList(), listOf("页面里没有找到课表表格（无 <tr>），请确认停在「学生个人课表」页面"))
        }

        // 主路径：格子里自带「周X第a,b节{周次}」，比按列号猜可靠得多
        val cells = rows.flatMap { row -> cellRe.findAll(row).map { rowText(it.groupValues[1]) } }
        val anchored = cells.flatMap { parseAnchored(it) }
        val fromAnchor = finish(anchored)
        if (fromAnchor.isNotEmpty()) return ParseResult(fromAnchor, warnings)

        // 兜底路径：按表头列位置推算星期 + 按行序推算节次（老逻辑，保留给结构和本校不同的正方版本）
        return parseByGrid(rows, warnings)
    }

    /** 主路径：从一个单元格文本里，按「上课时间行」为锚点切出一门门课 */
    private fun parseAnchored(text: String): List<Course> {
        if (text.isBlank()) return emptyList()
        val lines = text.lines().map { it.trim() }
        val out = mutableListOf<Course>()
        lines.forEachIndexed { i, line ->
            val tm = timeLineRe.find(line) ?: return@forEachIndexed
            val weekday = weekdayOfChar[tm.groupValues[1].firstOrNull()] ?: return@forEachIndexed
            val periods = Regex("[0-9]{1,2}").findAll(tm.groupValues[2])
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it in 1..MAX_PERIOD }
                .toList()
            if (periods.isEmpty()) return@forEachIndexed
            val inner = braceRe.find(line)?.groupValues?.get(1).orEmpty()
            // 新版正方把「周X第a,b节」塞进花括号里（{第1-16周|周一第3,4节|教室}），上一行是教师不是课程名，
            // 那种结构交给兜底路径处理，这里只认旧版「周X第a,b节{周次}」的行内写法。
            if (inner.split("|").let { it.size >= 2 && it[1].contains("节") }) return@forEachIndexed
            val weeks = parseWeeks(inner.ifBlank { line })
            if (weeks.isEmpty()) return@forEachIndexed
            val name = lines.getOrNull(i - 1).orEmpty()
            if (name.isBlank() || timeLineRe.containsMatchIn(name)) return@forEachIndexed
            out += newCourse(
                name = name,
                teacher = lines.getOrNull(i + 1)?.takeUnless { isMetaLine(it) }.orEmpty(),
                location = lines.getOrNull(i + 2)?.takeUnless { isMetaLine(it) }.orEmpty(),
                weekday = weekday,
                sp = periods.min(),
                ep = periods.max(),
                weeks = weeks,
            )
        }
        return out
    }

    /** 调课信息、空行之类不该被当成教师/地点的行 */
    private fun isMetaLine(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return true
        if (t.startsWith("(") || t.startsWith("（")) return true
        if (examDateRe.containsMatchIn(t)) return true
        if (timeLineRe.containsMatchIn(t)) return true
        return t.none { it.isLetterOrDigit() }
    }

    /** 兜底：老的表头列映射 + 行序推节次 */
    private fun parseByGrid(rows: List<String>, warnings: MutableList<String>): ParseResult {
        val headerIdx = rows.indexOfFirst {
            val t = rowText(it)
            t.contains("星期一") || t.contains("周一")
        }
        if (headerIdx < 0) {
            return ParseResult(emptyList(), listOf("没有找到含「星期一」的表头行，可能停在了登录页或首页"))
        }

        val headerCells = cellRe.findAll(rows[headerIdx]).map { rowText(it.groupValues[1]) }.toList()
        val colWeekday = headerCells.map { cell ->
            when {
                cell.contains("星期一") || cell.contains("周一") -> 1
                cell.contains("星期二") || cell.contains("周二") -> 2
                cell.contains("星期三") || cell.contains("周三") -> 3
                cell.contains("星期四") || cell.contains("周四") -> 4
                cell.contains("星期五") || cell.contains("周五") -> 5
                cell.contains("星期六") || cell.contains("周六") -> 6
                cell.contains("星期日") || cell.contains("星期天") || cell.contains("周日") -> 7
                else -> 0
            }
        }
        if (colWeekday.none { it > 0 }) {
            return ParseResult(emptyList(), listOf("表头里没认出星期列（应为「星期一…星期日」）"))
        }

        val dataRows = rows.drop(headerIdx + 1).filter { cellRe.containsMatchIn(it) }
        if (dataRows.isEmpty()) return ParseResult(emptyList(), listOf("表头下面没有数据行"))

        val fromFirstCol = dataRows.map { row ->
            val firstText = cellRe.findAll(row).firstOrNull()?.let { rowText(it.groupValues[1]) }.orEmpty()
            periodLeadRe.find(firstText)?.let { m ->
                val a = m.groupValues[1].toIntOrNull() ?: 0
                val b = m.groupValues[2].toIntOrNull() ?: a
                if (a in 1 until MAX_PERIOD && b >= a && b <= MAX_PERIOD) a to b else null
            }
        }
        val useFirstCol = fromFirstCol.count { it != null } * 2 >= dataRows.size
        val pairs = !useFirstCol && dataRows.size < 10

        val courses = mutableListOf<Course>()
        dataRows.forEachIndexed { ri, row ->
            val range = fromFirstCol[ri] ?: if (pairs) {
                (ri * 2 + 1) to (ri * 2 + 2)
            } else {
                (ri + 1) to (ri + 1)
            }
            cellRe.findAll(row).forEachIndexed { ci, cellMatch ->
                val weekday = colWeekday.getOrNull(ci) ?: 0
                if (weekday <= 0) return@forEachIndexed
                val text = rowText(cellMatch.groupValues[1])
                if (text.isNotBlank()) courses += parseCell(text, weekday, range.first, range.second)
            }
        }

        val valid = finish(courses)
        if (valid.isEmpty()) warnings += "表格解析为空，可能是课表还没生成（新选课需要教务先排课）"
        return ParseResult(valid, warnings)
    }

    /** 去重、截断、丢掉明显无效的条目 */
    private fun finish(courses: List<Course>): List<Course> = courses
        .filter { it.name.isNotBlank() && it.weeks.isNotEmpty() && it.weekday in 1..7 }
        .map { it.copy(name = it.name.take(30)) }
        .distinctBy { it.name to it.weekday to it.startPeriod to it.endPeriod to it.weeks.toString() }

    /** 单元格文本 → 课程列表（新版花括号格式 / 旧版 4 行组） */
    private fun parseCell(text: String, weekday: Int, pStart: Int, pEnd: Int): List<Course> {
        val out = mutableListOf<Course>()

        val braces = braceRe.findAll(text).toList()
        if (braces.isNotEmpty()) {
            braces.forEachIndexed { idx, brace ->
                val headFrom = if (idx == 0) 0 else braces[idx - 1].range.last + 1
                val head = text.substring(headFrom, brace.range.first)
                    .lines().map { it.trim() }.filter { it.isNotEmpty() }
                val parts = brace.groupValues[1].split("|").map { it.trim() }
                val weeks = parseWeeks(parts.firstOrNull().orEmpty())
                var sp = pStart
                var ep = pEnd
                Regex("""周([一二三四五六日天])\s*第?\s*([0-9]{1,2})\s*[-~－—]?\s*([0-9]{1,2})?""")
                    .find(parts.getOrNull(1).orEmpty())?.let { m ->
                        val a = m.groupValues[2].toIntOrNull()
                        val b = m.groupValues[3].toIntOrNull()
                        if (a != null) { sp = a; ep = b ?: a }
                    }
                if (weeks.isNotEmpty()) {
                    out += newCourse(head.firstOrNull().orEmpty(), head.getOrNull(1).orEmpty(),
                        parts.getOrNull(2).orEmpty(), weekday, sp, ep, weeks)
                }
            }
            return out
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        var i = 0
        while (i < lines.size) {
            val remain = lines.size - i
            var name = ""
            var teacher = ""
            var week = ""
            var location = ""
            when {
                remain >= 4 && weekRe.containsMatchIn(lines[i + 2]) -> {
                    name = lines[i]; teacher = lines[i + 1]; week = lines[i + 2]; location = lines[i + 3]; i += 4
                }
                remain >= 3 && weekRe.containsMatchIn(lines[i + 1]) -> {
                    name = lines[i]; week = lines[i + 1]; location = lines[i + 2]; i += 3
                }
                remain >= 3 && weekRe.containsMatchIn(lines[i + 2]) -> {
                    name = lines[i]; teacher = lines[i + 1]; week = lines[i + 2]; i += 3
                }
                else -> {
                    name = lines[i]
                    week = lines.firstOrNull { weekRe.containsMatchIn(it) }.orEmpty()
                    location = lines.lastOrNull { it != name && it != week }.orEmpty()
                    i = lines.size
                }
            }
            val weeks = parseWeeks(week)
            if (name.isNotBlank() && weeks.isNotEmpty()) {
                out += newCourse(name, teacher, location, weekday, pStart, pEnd, weeks)
            }
        }
        return out
    }

    private fun newCourse(
        name: String, teacher: String, location: String,
        weekday: Int, sp: Int, ep: Int, weeks: Set<Int>,
    ): Course = Course(
        id = UUID.randomUUID().toString(),
        name = name.trim(),
        teacher = teacher.trim(),
        location = location.trim(),
        weekday = weekday,
        startPeriod = sp.coerceIn(1, MAX_PERIOD),
        endPeriod = ep.coerceIn(sp.coerceIn(1, MAX_PERIOD), MAX_PERIOD),
        weeks = weeks,
    )

    /** 周次文本解析：1-16周 / 1-8周(单) / 2-15周|双周 / 3,5,7周 / 第1-16周 */
    fun parseWeeks(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
        val single = raw.contains("单")
        val even = raw.contains("双")
        val base = raw.substringBefore("周").ifBlank { raw }
        val weeks = mutableSetOf<Int>()
        Regex("""[0-9]{1,2}\s*[-~－—]\s*[0-9]{1,2}""").findAll(base).forEach { m ->
            val r = m.value.split(Regex("""\s*[-~－—]\s*"""))
            val a = r.getOrNull(0)?.toIntOrNull() ?: 0
            val b = r.getOrNull(1)?.toIntOrNull() ?: 0
            if (a > 0 && b >= a) weeks += a..b
        }
        val rest = base.replace(Regex("""[0-9]{1,2}\s*[-~－—]\s*[0-9]{1,2}"""), " ")
        Regex("""[0-9]{1,2}""").findAll(rest).forEach { m ->
            val n = m.value.toIntOrNull() ?: 0
            if (n > 0) weeks += n
        }
        return weeks.filter { w ->
            when {
                single -> w % 2 == 1
                even -> w % 2 == 0
                else -> true
            }
        }.filter { it <= ScheduleStore.MAX_WEEK }.toSet()
    }

    /** HTML → 纯文本：<br> 与 </p> 变换行，去标签，解实体 */
    private fun rowText(html: String): String {
        val withBreaks = brRe.replace(html, "\n").replace(Regex("(?is)</p>"), "\n")
        var text = tagRe.replace(withBreaks, "")
        text = entityRe.replace(text) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code == null) m.value else String(Character.toChars(code))
        }
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace('\u00a0', ' ')
            .lines().joinToString("\n") { it.trim() }.trim()
    }
}
