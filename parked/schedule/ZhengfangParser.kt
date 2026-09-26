package com.campusglass.schedule

import java.util.UUID

/**
 * 正方教务系统课表 HTML 解析器。
 *
 * 解析逻辑（间接）参考以下开源项目，致谢见“关于”页：
 *   - HF-CYGG/Dawn-Course（破晓课程表，新旧正方/强智/青果解析思路）
 *   - whliao5am/zfnew（新版正方课表接口与文本格式）
 *   - KerryChia/ruc-schedule-extension（课表 DOM 解析 → 节次映射）
 *
 * 兼容两种典型单元格文本：
 *  A. 旧版正方 xskbcx.aspx：<br> 分隔的 4 行组  课程名 / 教师 / 周次 / 上课地点
 *  B. 新版正方：课程名 / 教师 / {周次|周X起止节|地点}
 */
object ZhengfangParser {

    private val weekRe = Regex("""[0-9,，、\s]*周|[0-9]+\s*[-~－—]\s*[0-9]+周|单周|双周""")
    private val braceRe = Regex("""\{([^}]*)\}""")

    data class ParseResult(val courses: List<Course>, val warnings: List<String>)

    fun parse(html: String): ParseResult {
        val warnings = mutableListOf<String>()
        val rows = Regex("(?is)<tr[^>]*>(.*?)</tr>").findAll(html).map { it.groupValues[1] }.toList()
        if (rows.isEmpty()) return ParseResult(emptyList(), listOf("未找到课表表格（<tr>）"))

        // 1) 表头行：包含“星期一”的行
        val headerIdx = rows.indexOfFirst { rowText(it).contains("星期一") }
        if (headerIdx < 0) return ParseResult(emptyList(), listOf("未找到含“星期一”的表头，请确认已打开“学生课表”页面"))

        // 2) 列映射：表头单元格 → 星期几
        val headerCells = cells(rows[headerIdx]).map { rowText(it) }
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
        if (colWeekday.none { it > 0 }) return ParseResult(emptyList(), listOf("表头解析失败"))

        // 3) 数据行 → 节次映射
        val dataRows = rows.drop(headerIdx + 1).filter { cells(it).isNotEmpty() }
        val pairs = dataRows.size < 10   // 行数少 ⇒ 两节一行；否则一节一行
        val rowStart = dataRows.indices.map { i -> if (pairs) i * 2 + 1 else i + 1 }
        val rowEnd = dataRows.indices.map { i -> if (pairs) i * 2 + 2 else i + 1 }

        val courses = mutableListOf<Course>()
        dataRows.forEachIndexed { ri, row ->
            val cs = cells(row)
            cs.forEachIndexed { ci, cellHtml ->
                val weekday = colWeekday.getOrNull(ci) ?: 0
                if (weekday > 0) {
                    val text = rowText(cellHtml)
                    if (text.isNotBlank()) {
                        parseCell(text, weekday, rowStart[ri], rowEnd[ri]).forEach {
                            courses += it
                        }
                    }
                }
            }
        }
        if (courses.isEmpty()) warnings += "表格解析为空，可尝试“网页导入”或粘贴 HTML"
        return ParseResult(courses.distinctBy { it.name to it.weekday to it.startPeriod to it.location }, warnings)
    }

    /** 单元格文本 → 课程列表 */
    private fun parseCell(text: String, weekday: Int, pStart: Int, pEnd: Int): List<Course> {
        val out = mutableListOf<Course>()

        // B 格式：{周次|周X起止节|地点}
        val brace = braceRe.find(text)
        if (brace != null) {
            val head = text.substringBefore("{").lines().map { it.trim() }.filter { it.isNotEmpty() }
            val parts = brace.groupValues[1].split("|").map { it.trim() }
            val weeks = parseWeeks(parts.firstOrNull().orEmpty())
            var sp = pStart; var ep = pEnd
            Regex("""周([一二三四五六日天])\s*(\d+)\s*[-~－—]?\s*(\d+)?""").find(parts.getOrNull(1).orEmpty())?.let { m ->
                sp = m.groupValues[2].toIntOrNull() ?: sp
                ep = m.groupValues[3].toIntOrNull() ?: sp
            }
            out.Course(
                head.firstOrNull() ?: "未知课程",
                head.getOrNull(1).orEmpty(),
                parts.getOrNull(2).orEmpty(),
                weekday, sp, ep, weeks,
            )
            return out
        }

        // A 格式：4 行组（或 3 行组：无教师/无地点）
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        var i = 0
        while (i < lines.size) {
            val remain = lines.size - i
            var name = ""; var teacher = ""; var week = ""; var location = ""
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
                    // 兜底：整块当一门课，从任意行抓周次
                    name = lines[i]
                    week = lines.firstOrNull { weekRe.containsMatchIn(it) }.orEmpty()
                    location = lines.lastOrNull { it != name && it != week }.orEmpty()
                    i = lines.size
                }
            }
            val weeks = parseWeeks(week)
            if (name.isNotBlank() && weeks.isNotEmpty()) {
                out.Course(name, teacher, location, weekday, pStart, pEnd, weeks)
                // 注意：Course() 为 MutableList 扩展助手，见文件底部
            }
        }
        return out
    }

    private fun MutableList<Course>.Course(
        name: String, teacher: String, location: String,
        weekday: Int, sp: Int, ep: Int, weeks: Set<Int>,
    ) {
        add(
            Course(UUID.randomUUID().toString(), name, teacher, location,
                weekday, sp, ep, weeks)
        )
    }

    /** 周次文本解析：1-16周 / 1-8周(单) / 2-15周|双周 / 3,5,7周 */
    fun parseWeeks(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
        val single = raw.contains("单")
        val even = raw.contains("双")
        val base = raw.substringBefore("周").ifBlank { raw }
        val weeks = mutableSetOf<Int>()
        Regex("""\d+\s*[-~－—]\s*\d+""").findAll(base).forEach { m ->
            val (a, b) = m.value.split(Regex("""\s*[-~－—]\s*""")).map { it.trim().toIntOrNull() ?: 0 }
            if (a > 0 && b >= a) weeks += a..b
        }
        val withoutRanges = base.replace(Regex("""\d+\s*[-~－—]\s*\d+"""), " ")
        Regex("""\d+""").findAll(withoutRanges).forEach { m ->
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

    private fun cells(rowHtml: String): List<String> =
        Regex("(?is)<td[^>]*>(.*?)</td>").findAll(rowHtml).map { it.groupValues[1] }.toList()

    private fun rowText(html: String): String = html
        .replace(Regex("(?is)<br\\s*/?>"), "\n")
        .replace(Regex("(?is)</p>"), "\n")
        .replace(Regex("(?is)<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .lines().joinToString("\n") { it.trim() }.trim()
}
