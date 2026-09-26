package com.campusglass.schedule

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
 * 课表（手动添加模式）：WakeUp 课程表风格完整周表格 + 添加/删除课程。
 * UI 设计参照 WakeUp 课程表（YZune/WakeUpSchedule）。
 */
@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    var courses by remember { mutableStateOf(ScheduleStore.loadCourses(context)) }
    var week by remember { mutableIntStateOf(ScheduleStore.currentWeek(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Course?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("课表")

        Text(
            "当前：第${week}周 —— 点周次切换周，点课程看详情/删除",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // 节假日标记
        HolidayBanner(week)

        LazyRow(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items((1..20).toList()) { w ->
                FilterChip(
                    selected = week == w,
                    onClick = { week = w },
                    label = { Text("第${w}周") },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item {
                    TimetableGrid(courses = courses, week = week, onClick = { detail = it })
                }
            }
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加课程") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }
    }

    if (showAdd) {
        AddCourseDialog(
            onDismiss = { showAdd = false },
            onSave = { c ->
                courses = courses + c
                ScheduleStore.saveCourses(context, courses)
                showAdd = false
                Toast.makeText(context, "已添加：${c.name}", Toast.LENGTH_SHORT).show()
            },
        )
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

/** 添加课程弹窗（手动模式） */
@Composable
private fun AddCourseDialog(onDismiss: () -> Unit, onSave: (Course) -> Unit) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var weekday by remember { mutableIntStateOf(1) }
    var startP by remember { mutableIntStateOf(1) }
    var endP by remember { mutableIntStateOf(2) }
    var startWeek by remember { mutableStateOf("1") }
    var endWeek by remember { mutableStateOf("16") }
    var parity by remember { mutableIntStateOf(0) }   // 0=全部 1=单周 2=双周

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加课程") },
        text = {
            Column(
                Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("课程名（必填）") })
                OutlinedTextField(teacher, { teacher = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("老师") })
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("教室地点") })

                Text("星期", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = weekday == d,
                            onClick = {
                                weekday = d
                                if (endP < startP) endP = startP
                            },
                            label = { Text("周" + "一二三四五六日"[d - 1], fontSize = 11.sp) },
                        )
                    }
                }

                Text("节次", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..11).forEach { p ->
                        FilterChip(
                            selected = startP == p,
                            onClick = { startP = p; if (endP < p) endP = p },
                            label = { Text("$p", fontSize = 11.sp) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("结束", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall)
                    (startP..11).forEach { p ->
                        FilterChip(
                            selected = endP == p,
                            onClick = { endP = p },
                            label = { Text("$p", fontSize = 11.sp) },
                        )
                    }
                }

                Text("周次范围", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startWeek, { startWeek = it.filter(Char::isDigit) },
                        Modifier.weight(1f), singleLine = true, label = { Text("第几周") })
                    Text("到", modifier = Modifier.padding(top = 18.dp))
                    OutlinedTextField(endWeek, { endWeek = it.filter(Char::isDigit) },
                        Modifier.weight(1f), singleLine = true, label = { Text("第几周") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("全部周", "单周", "双周").forEachIndexed { i, t ->
                        FilterChip(selected = parity == i, onClick = { parity = i }, label = { Text(t) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val a = (startWeek.toIntOrNull() ?: 1).coerceIn(1, 30)
                    val b = (endWeek.toIntOrNull() ?: a).coerceIn(a, 30)
                    val weeks = (a..b).filter { w ->
                        when (parity) {
                            1 -> w % 2 == 1
                            2 -> w % 2 == 0
                            else -> true
                        }
                    }.toSet()
                    onSave(
                        Course(
                            name = name.trim(),
                            teacher = teacher.trim(),
                            location = location.trim(),
                            weekday = weekday,
                            startPeriod = startP,
                            endPeriod = endP.coerceAtLeast(startP),
                            weeks = weeks.ifEmpty { (a..b).toSet() },
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 本周期间假期标记 */
@Composable
private fun HolidayBanner(week: Int) {
    val context = LocalContext.current
    val weekStart = ScheduleStore.dateOf(context, week, 1)
    val weekEnd = ScheduleStore.dateOf(context, week, 7)
    val hits = HolidayData.holidaysInWeek(weekStart, weekEnd)
    val today = LocalDate.now()
    val todayHoliday = HolidayData.holidays.firstOrNull {
        !today.isBefore(it.start) && !today.isAfter(it.end)
    }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hits.isEmpty()) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else Color(0xFFFFF3D6)
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (hits.isEmpty()) {
                Text(
                    "本周无假期标记" + if (todayHoliday != null) "（今天：${todayHoliday.name} 🎉）" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "🏖 本周期间假期：" + hits.joinToString("、"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "假期参考（以学校校历为准）" + if (todayHoliday != null) " · 今天：${todayHoliday.name} 🎉" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(38.dp))
                (1..7).forEach { wd ->
                    val date = ScheduleStore.dateOf(LocalContext.current, week, wd)
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "周" + "一二三四五六日"[wd - 1],
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (wd == todayWd) FontWeight.Bold else FontWeight.Normal,
                            color = if (wd == todayWd) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${date.monthValue}/${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (wd == todayWd) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }
            rows.forEach { periods ->
                Row(Modifier.height(92.dp)) {
                    Column(Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Text(c.name, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, maxLines = 3)
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
