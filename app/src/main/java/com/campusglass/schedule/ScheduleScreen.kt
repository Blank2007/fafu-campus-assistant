package com.campusglass.schedule

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.campusglass.ui.glass.AcrylicCard
import com.campusglass.ui.theme.ThemePrefs
import com.campusglass.ui.widgets.ScreenHeader
import java.time.LocalDate

/** 高对比课程色块（饱和度高，深色文字保证可读） */
private val PALETTE = listOf(
    Color(0xFF7C9CFF), Color(0xFFFFB74D), Color(0xFF66D9B8), Color(0xFFFF8FA3),
    Color(0xFFB98CFF), Color(0xFFFFD23F), Color(0xFF5EC8E8), Color(0xFF9CCC65),
    Color(0xFFFF9E6D), Color(0xFF80CBC4), Color(0xFFE57398), Color(0xFFA5B4FC),
)

/** 一周显示顺序：周日起始 */
private val DAY_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)
private fun dayLabel(wd: Int) = "周" + "一二三四五六日"[wd - 1]

private data class Slot(var weekday: Int, var start: Int, var end: Int)

/**
 * 课表（手动添加模式）：
 * 周日起始 · 一节一行 · 第三行显示教师 · 支持分段周次（2-5,7-8）与同名课多时段。
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
            "当前：第${week}周（周日起算）— 点周次切换，点课程看详情/删除",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyRow(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
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
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    TimetableGrid(courses = courses, week = week, onClick = { detail = it })
                    HolidayBanner(week)
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
            onSave = { newOnes ->
                courses = courses + newOnes
                ScheduleStore.saveCourses(context, courses)
                showAdd = false
                Toast.makeText(context, "已添加：${newOnes.first().name}（${newOnes.size} 个时段）", Toast.LENGTH_SHORT).show()
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
                    Text("时间：${dayLabel(c.weekday)} 第${c.startPeriod}-${c.endPeriod}节")
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

/** 周表格：一节一行，周日起始 */
@Composable
private fun TimetableGrid(courses: List<Course>, week: Int, onClick: (Course) -> Unit) {
    val context = LocalContext.current
    val periods = PeriodTable.periodsPerDay(context)
    val days = if (ThemePrefs.showWeekend.value) DAY_ORDER else listOf(1, 2, 3, 4, 5)
    val todayWd = ScheduleStore.weekdayOf(LocalDate.now())

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(38.dp))
                days.forEach { wd ->
                    val date = ScheduleStore.dateOf(context, week, wd)
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            dayLabel(wd),
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
            (1..periods).forEach { p ->
                Row(Modifier.height(64.dp)) {
                    Column(
                        Modifier.width(38.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$p", style = MaterialTheme.typography.labelMedium)
                        Text(
                            PeriodTable.startStr(context, p),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 7.sp,
                        )
                    }
                    days.forEach { wd ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(62.dp)
                                .padding(2.dp)
                                .background(
                                    if (wd == todayWd) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                    else Color.Transparent,
                                    RoundedCornerShape(6.dp),
                                )
                        ) {
                            val cell = courses.filter {
                                it.weekday == wd && it.startPeriod == p && week in it.weeks
                            }
                            if (cell.size == 1) {
                                CourseBlock(cell.first(), onClick)
                            } else if (cell.size > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
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

/** 课程色块：名称/地点/教师 三行，高对比 */
@Composable
private fun CourseBlock(c: Course, onClick: (Course) -> Unit) {
    val span = (c.endPeriod - c.startPeriod + 1).coerceIn(1, 6)
    val color = PALETTE[(abs(c.name.hashCode() * 31 + c.weekday * 7 + c.startPeriod)) % PALETTE.size]
    val sc = listOf(0.85f, 1f, 1.2f)[ThemePrefs.scheduleFontScale.value]
    val base = MaterialTheme.typography.labelSmall
    val style = base.copy(fontSize = base.fontSize * sc)

    Card(
        onClick = { onClick(c) },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier
            .fillMaxWidth()
            .height((62 * span + 2 * (span - 1)).dp),
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                c.name,
                style = style,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 2,
            )
            if (c.location.isNotBlank()) {
                Text(
                    c.location,
                    style = style,
                    color = Color.Black.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
            if (c.teacher.isNotBlank()) {
                Text(
                    c.teacher,
                    style = style,
                    color = Color.Black.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun abs(v: Int) = if (v < 0) -v else v

/** 添加课程弹窗：同名多时段 · 分段周次 */
@Composable
private fun AddCourseDialog(onDismiss: () -> Unit, onSave: (List<Course>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    val slots = remember { mutableStateOf(mutableListOf(Slot(1, 1, 2))) }
    var weeksText by remember { mutableStateOf("1-16") }
    var parity by remember { mutableIntStateOf(0) }   // 0全部 1单 2双

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ 添加课程") },
        text = {
            Column(
                Modifier
                    .height(460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("课程名（必填）") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(teacher, { teacher = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text("老师") })
                    OutlinedTextField(location, { location = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text("教室") })
                }

                Text("🕐 上课时间（同一门课可加多个时段）", style = MaterialTheme.typography.labelLarge)
                slots.value.forEachIndexed { i, slot ->
                    AcrylicCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("时段 ${i + 1}", style = MaterialTheme.typography.labelLarge)
                                if (slots.value.size > 1) {
                                    IconButton(onClick = { slots.value.removeAt(i); slots.value = slots.value.toMutableList() }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "删除时段")
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(7, 1, 2, 3, 4, 5, 6).forEach { wd ->
                                    FilterChip(
                                        selected = slot.weekday == wd,
                                        onClick = { slot.weekday = wd; slots.value = slots.value.toMutableList() },
                                        label = { Text(dayLabel(wd), fontSize = 10.sp) },
                                    )
                                }
                            }
                            Text("起始节", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                (1..11).forEach { p ->
                                    FilterChip(
                                        selected = slot.start == p,
                                        onClick = {
                                            slot.start = p
                                            if (slot.end < p) slot.end = p
                                            slots.value = slots.value.toMutableList()
                                        },
                                        label = { Text("$p", fontSize = 11.sp) },
                                    )
                                }
                            }
                            Text("结束节", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                (slot.start..11).forEach { p ->
                                    FilterChip(
                                        selected = slot.end == p,
                                        onClick = { slot.end = p; slots.value = slots.value.toMutableList() },
                                        label = { Text("$p", fontSize = 11.sp) },
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { slots.value.add(Slot(1, 1, 2)); slots.value = slots.value.toMutableList() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("＋ 添加上课时间") }

                Text("📅 周次（支持分段，如 2-5,7-8）", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(weeksText, { weeksText = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("周次范围") })
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
                    val weeks = ScheduleStore.parseWeeks(weeksText)
                        .filter { w ->
                            when (parity) {
                                1 -> w % 2 == 1
                                2 -> w % 2 == 0
                                else -> true
                            }
                        }.toSet().ifEmpty { (1..16).toSet() }
                    onSave(
                        slots.value.map { s ->
                            Course(
                                name = name.trim(),
                                teacher = teacher.trim(),
                                location = location.trim(),
                                weekday = s.weekday,
                                startPeriod = s.start,
                                endPeriod = s.end.coerceAtLeast(s.start),
                                weeks = weeks,
                            )
                        }
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 本周期间假期标记（周日起算） */
@Composable
private fun HolidayBanner(week: Int) {
    val context = LocalContext.current
    val weekStart = ScheduleStore.dateOf(context, week, 7)   // 周日
    val weekEnd = ScheduleStore.dateOf(context, week, 6)     // 周六
    val hits = HolidayData.holidaysInWeek(weekStart, weekEnd)
    val today = LocalDate.now()
    val todayHoliday = HolidayData.holidays.firstOrNull { !today.isBefore(it.start) && !today.isAfter(it.end) }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
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
