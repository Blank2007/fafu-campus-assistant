package com.campusglass.schedule

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusglass.ui.widgets.ScreenHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val PALETTE = listOf(
    Color(0xFFB3C7FF), Color(0xFFFFD6A5), Color(0xFFB9F0D4), Color(0xFFF6B8C8),
    Color(0xFFD5C2FF), Color(0xFFFFE29A), Color(0xFFA8E6E0), Color(0xFFC8D9FF),
)

/**
 * 课表页：WakeUp 课程表风格完整周表格（不阉割）+ 多路导入
 * （拍照识别 / 教务网页抓取 / HTML / ICS / JSON）。
 * UI 设计参照：WakeUp 课程表 — YZune/WakeUpSchedule。
 */
@Composable
fun ScheduleScreen(onBack: () -> Unit, onOpenWebImport: () -> Unit) {
    val context = LocalContext.current
    var courses by remember { mutableStateOf(ScheduleStore.loadCourses(context)) }
    var week by remember { mutableIntStateOf(ScheduleStore.currentWeek(context)) }
    var showPaste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var termStartText by remember { mutableStateOf(ScheduleStore.termStart(context).toString()) }
    var detail by remember { mutableStateOf<Course?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "正在识别课表照片…", Toast.LENGTH_SHORT).show()
            PhotoOcr.recognize(context, uri) { list, _ ->
                if (list.isEmpty()) {
                    Toast.makeText(context, "没识别出课程，试试更清晰的整张课表截图", Toast.LENGTH_LONG).show()
                } else {
                    courses = (courses + list).distinctBy { it.name to it.weekday to it.startPeriod }
                    ScheduleStore.saveCourses(context, courses)
                    Toast.makeText(context, "识别到 ${list.size} 条课程", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val imported = runCatching {
                when {
                    text.trimStart().startsWith("BEGIN:VCALENDAR") -> IcsCodec.import(context, text)
                    text.trimStart().startsWith("[") -> IcsCodec.importJson(text)
                    else -> ZhengfangParser.parse(text).courses
                }
            }.getOrDefault(emptyList())
            if (imported.isEmpty()) {
                Toast.makeText(context, "未解析到课程，请检查文件格式", Toast.LENGTH_LONG).show()
            } else {
                courses = imported
                ScheduleStore.saveCourses(context, imported)
                Toast.makeText(context, "导入 ${imported.size} 门课", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(IcsCodec.export(context, courses).toByteArray())
            }
            Toast.makeText(context, "ICS 已导出，可导入系统日历", Toast.LENGTH_SHORT).show()
        }
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(IcsCodec.exportJson(courses).toByteArray())
            }
            Toast.makeText(context, "JSON 已导出", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("课表", onBack) }

        // ---- 导入导出 ----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("导入课表", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("📷 拍照识别") }
                        Button(
                            onClick = onOpenWebImport,
                            modifier = Modifier.weight(1f),
                        ) { Text("🌐 教务抓取") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { importFile.launch(arrayOf("text/*", "application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                        ) { Text("导入文件") }
                        OutlinedButton(
                            onClick = { showPaste = !showPaste },
                            modifier = Modifier.weight(1f),
                        ) { Text("粘贴 HTML") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { exportIcs.launch("schedule.ics") },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出 ICS") }
                        OutlinedButton(
                            onClick = { exportJson.launch("schedule.json") },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出 JSON") }
                        OutlinedButton(
                            onClick = {
                                courses = emptyList(); ScheduleStore.saveCourses(context, emptyList())
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("清空") }
                    }
                    if (showPaste) {
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                            placeholder = { Text("粘贴教务课表页 HTML") },
                        )
                        Button(onClick = {
                            val r = ZhengfangParser.parse(pasteText)
                            if (r.courses.isEmpty()) {
                                Toast.makeText(context, r.warnings.firstOrNull() ?: "解析失败", Toast.LENGTH_LONG).show()
                            } else {
                                courses = r.courses
                                ScheduleStore.saveCourses(context, r.courses)
                                Toast.makeText(context, "解析 ${r.courses.size} 条课程", Toast.LENGTH_SHORT).show()
                                showPaste = false
                            }
                        }) { Text("解析并保存") }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = termStartText,
                            onValueChange = { termStartText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("开学第一周周一") },
                        )
                        Button(onClick = {
                            runCatching { ScheduleStore.setTermStart(context, LocalDate.parse(termStartText)) }
                                .onSuccess {
                                    week = ScheduleStore.currentWeek(context)
                                    Toast.makeText(context, "学期起始已更新", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { Toast.makeText(context, "日期格式：yyyy-MM-dd", Toast.LENGTH_SHORT).show() }
                        }) { Text("保存") }
                    }
                }
            }
        }

        // ---- 周选择 ----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..6).forEach { w ->
                    FilterChip(selected = week == w, onClick = { week = w }, label = { Text("${w}") })
                }
                OutlinedButton(onClick = { week = (week - 1).coerceAtLeast(1) }) { Text("−") }
                OutlinedButton(onClick = { week = week + 1 }) { Text("＋") }
                Text(
                    "第 $week 周",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
            }
        }

        // ---- 周表格 ----
        item {
            TimetableGrid(courses = courses, week = week, onClick = { detail = it })
        }
    }

    detail?.let { c ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(c.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("老师：${c.teacher.ifBlank { "—" }}")
                    Text("地点：${c.location.ifBlank { "—" }}")
                    Text("时间：周" + "一二三四五六日"[c.weekday - 1] + " 第${c.startPeriod}-${c.endPeriod}节")
                    Text("周次：" + c.weeks.sorted().joinToString(",") + " 周")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    courses = courses - c
                    ScheduleStore.saveCourses(context, courses)
                    detail = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) { Text("关闭") }
            },
        )
    }
}

/** WakeUp 风格周表格：7 天 × 节次行，课程色块跨节次，今日列高亮 */
@Composable
private fun TimetableGrid(courses: List<Course>, week: Int, onClick: (Course) -> Unit) {
    val rows = listOf(1..2, 3..4, 5..6, 7..8, 9..10, 11..11)
    val todayWd = ScheduleStore.weekdayOf(LocalDate.now())
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(6.dp)) {
            // 表头
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(38.dp))
                (1..7).forEach { wd ->
                    Text(
                        "周" + "一二三四五六日"[wd - 1],
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (wd == todayWd) FontWeight.Bold else FontWeight.Normal,
                        color = if (wd == todayWd) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // 课表格
            rows.forEach { periods ->
                Row(Modifier.height(92.dp)) {
                    Column(
                        Modifier.width(38.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "${periods.first}" + if (periods.last != periods.first) "-${periods.last}" else "",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            PeriodTable.start(periods.first).format(timeFmt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                        )
                    }
                    (1..7).forEach { wd ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(88.dp)
                                .padding(2.dp)
                                .background(
                                    if (wd == todayWd) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                        ) {
                            val cell = courses.filter {
                                it.weekday == wd && it.startPeriod in periods && week in it.weeks
                            }
                            if (cell.size == 1) {
                                CourseBlock(cell.first(), onClick)
                            } else if (cell.size > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    cell.forEach { c ->
                                        Box(Modifier.weight(1f)) { CourseBlock(c, onClick) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseBlock(c: Course, onClick: (Course) -> Unit) {
    val span = (c.endPeriod - c.startPeriod + 1).coerceIn(1, 4)
    val color = PALETTE[abs(c.name.hashCode()) % PALETTE.size]
    Card(
        onClick = { onClick(c) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier
            .fillMaxWidth()
            .height((90 * span).dp),
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                c.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
            )
            if (c.location.isNotBlank()) {
                Text(c.location, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
            Text(
                c.weeks.sorted().joinToString(",") + "周",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.sp,
                color = Color.Black.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}
