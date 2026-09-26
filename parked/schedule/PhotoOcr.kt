package com.campusglass.schedule

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.UUID

/**
 * 拍照识别课表：ML Kit 中文 OCR（离线模型，Apache-2.0）+ 表格版面还原。
 *
 * 版面还原思路（间接）参考 WakeUp 课程表（YZune/WakeUpSchedule）与
 * ruc-schedule-extension 的课表 DOM→节次映射：用文本行的包围盒重建
 * "星期列 × 节次行" 网格，再把单元格文字解析成课程。
 */
object PhotoOcr {

    private data class LineBox(val text: String, val cx: Float, val cy: Float)

    /** 识别并解析；回调返回课程列表与原始识别文本（便于排查） */
    fun recognize(context: Context, uri: Uri, onResult: (List<Course>, String) -> Unit) {
        runCatching {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { vision -> onResult(parse(vision), vision.text) }
                .addOnFailureListener { onResult(emptyList(), "") }
        }.onFailure { onResult(emptyList(), "") }
    }

    fun parse(vision: Text): List<Course> {
        val lines = vision.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val b = l.boundingBox ?: return@mapNotNull null
            LineBox(l.text.trim(), b.exactCenterX(), b.exactCenterY())
        }.filter { it.text.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        // ---- 1) 表头：找到 "周一…周日" 的列位置 ----
        val wdName = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
        val colLines = lines.filter { l ->
            val t = l.text.removePrefix("周").removePrefix("星期")
            t.length == 1 && wdName.containsKey(t)
        }.map { it to wdName[it.text.removePrefix("周").removePrefix("星期")]!! }

        val hasGrid = colLines.size >= 3
        val courses = mutableListOf<Course>()

        if (hasGrid) {
            val cols = colLines.sortedBy { it.first.cx }
            // ---- 2) 左侧节次行标签 ----
            val colLeft = cols.first().first.cx - 60f
            val rowRegex = Regex("^(\\d{1,2})\\s*[-~－—]\\s*(\\d{1,2})节?$|^(\\d{1,2})节?$")
            val rowLines = lines.filter { it.cx < colLeft }.mapNotNull { l ->
                val m = rowRegex.find(l.text.replace(" ", "")) ?: return@mapNotNull null
                val a = (m.groupValues[1].ifBlank { m.groupValues[3] }).toIntOrNull() ?: return@mapNotNull null
                val b = m.groupValues[2].toIntOrNull() ?: a
                l.cy to (a..b)
            }.sortedBy { it.first }

            // ---- 3) 单元格归位 ----
            val cells = HashMap<Pair<Int, Int>, MutableList<LineBox>>()
            lines.forEach { l ->
                if (l.cx < colLeft) return@forEach
                if (colLines.any { it.first === l }) return@forEach
                // 列：按 x 中心落在哪两列中点之间
                val wd = cols.map { it.first.cx }.let { xs ->
                    var w = cols.last().second
                    for (i in 0 until xs.size - 1) {
                        val mid = (xs[i] + xs[i + 1]) / 2
                        if (l.cx < mid) { w = cols[i].second; break }
                    }
                    w
                }
                // 行：最近的节次行标签
                val period = if (rowLines.isEmpty()) null else {
                    val nearest = rowLines.minByOrNull { Math.abs(it.first - l.cy) }!!
                    nearest.second
                }
                val key = wd to (period?.first ?: 0)
                cells.getOrPut(key) { mutableListOf() }.add(l)
            }

            cells.forEach { (key, ls) ->
                val (wd, start) = key
                val rowRange = rowLines.minByOrNull { Math.abs(it.first - (ls.minOf { l -> l.cy })) }?.second
                val sp = rowRange?.first ?: start
                val ep = rowRange?.last ?: sp
                if (sp > 0) {
                    buildCourse(ls.sortedBy { it.cy }, wd, sp, ep)?.let { courses += it }
                }
            }
        }

        // ---- 4) 兜底：整行正则（如 "高等数学 张三 教301 1-16周 周一1-2节"） ----
        if (courses.isEmpty()) {
            lines.forEach { l ->
                val wdM = Regex("周([一二三四五六日天])").find(l.text) ?: return@forEach
                val wd = wdName[wdM.groupValues[1]] ?: return@forEach
                val pM = Regex("(\\d{1,2})\\s*[-~－—]\\s*(\\d{1,2})\\s*节").find(l.text)
                val sp = pM?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val ep = pM?.groupValues?.get(2)?.toIntOrNull() ?: sp
                buildCourse(listOf(l), wd, sp, ep)?.let { courses += it }
            }
        }

        return courses.distinctBy { it.name to it.weekday to it.startPeriod }
    }

    /** 单元格文字 → 课程：第一行=课名，地点/教师/周次启发式识别 */
    private fun buildCourse(lines: List<LineBox>, weekday: Int, sp: Int, ep: Int): Course? {
        val texts = lines.map { it.text }.filter { it.isNotBlank() }
        if (texts.isEmpty()) return null
        val name = texts.firstOrNull { !isMeta(it) } ?: return null
        val location = texts.firstOrNull { isLocation(it) }.orEmpty()
        val teacher = texts.firstOrNull {
            it != name && it != location && !it.contains("周") && it.length in 2..4 && it.all { c -> c.code < 0x4e00 || c.code > 0x9fff || true }
        }.orEmpty()
        val weeks = texts.mapNotNull { t ->
            ZhengfangParser.parseWeeks(t).ifEmpty { null }
        }.firstOrNull() ?: (1..16).toSet()
        return Course(UUID.randomUUID().toString(), name, teacher, location, weekday, sp, ep, weeks)
    }

    private fun isMeta(t: String): Boolean =
        t.contains("周") && Regex("\\d").containsMatchIn(t) || isLocation(t) ||
            Regex("^\\d{1,2}\\s*[-~－—]?\\s*\\d{0,2}节?$").matches(t.replace(" ", ""))

    private fun isLocation(t: String): Boolean =
        Regex("(教学楼|实验楼|实训楼|图书馆|体育馆|艺术楼|理工楼|文科楼|教[甲乙丙丁]?\\d|\\d{1,2}[A-Za-z]?\\d{3}|[A-Za-z]楼|教室|机房|画室|琴房|操场|食堂|楼\\d)").containsMatchIn(t)
}
