package com.campusglass.schedule

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusglass.ui.theme.ThemePrefs
import com.campusglass.ui.widgets.ScreenHeader
import java.time.LocalDate

// ======================= 常量 =======================

/** 高对比课程色块（浅色底 + 黑字，深浅模式都清晰） */
private val PALETTE = listOf(
    Color(0xFF8FA8FF), Color(0xFFFFBC6B), Color(0xFF6FDFC0), Color(0xFFFF9DB0),
    Color(0xFFC4A0FF), Color(0xFFFFD966), Color(0xFF72D4F0), Color(0xFFAEDD6E),
    Color(0xFFFFAE82), Color(0xFF8FE0D8), Color(0xFFF08FB4), Color(0xFFB3BEFF),
)

/** 星期标签（1=周一 … 7=周日） */
private fun dayLabel(wd: Int) = "周" + "一二三四五六日"[wd - 1]

/** 一周显示顺序：周日起始 */
private val DAY_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)

private const val ROW_H = 74  // 每节行高 dp

private data class Slot(val weekday: Int, val start: Int, val end: Int)

// ======================= 主界面 =======================

/**
 * 课表 v2：
 * 周日起算 · 一节一行 · 跨节课程纵向占满多格 · 同时段冲突左右分栏 · 课程可编辑。
 */
@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    var courses by remember { mutableStateOf(ScheduleStore.loadCourses(context)) }
    var week by remember { mutableIntStateOf(ScheduleStore.currentWeek(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Course?>(null) }
    var editTarget by remember { mutableStateOf<Course?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ScreenHeader("课表") }

            item {
                Text(
                    "第${week}周（周日起算）· 点周次切换 · 点课程可编辑/删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..20).toList()) { w ->
                        FilterChip(
                            selected = week == w,
                            onClick = { week = w },
                            label = { Text("第${w}周") },
                        )
                    }
                }
            }

            item { HolidayBanner(week) }
            item { Timetable(courses, week) { detail = it } }
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

    // 详情：编辑 / 删除
    detail?.let { c ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(c.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("老师：${c.teacher.ifBlank { "—" }}", color = MaterialTheme.colorScheme.onSurface)
                    Text("地点：${c.location.ifBlank { "—" }}", color = MaterialTheme.colorScheme.onSurface)
                    Text("时间：${dayLabel(c.weekday)} 第${c.startPeriod}-${c.endPeriod}节（占${c.endPeriod - c.startPeriod + 1}格）",
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("周次：" + c.weeks.sorted().joinToString(",") + " 周",
                        color = MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = {
                        editTarget = c
                        detail = null
                    }) { Text("✏️ 编辑") }
                    TextButton(onClick = {
                        courses = courses - c
                        ScheduleStore.saveCourses(context, courses)
                        detail = null
                    }) { Text("删除该时段") }
                    TextButton(onClick = {
                        courses = courses.filterNot { it.name == c.name }
                        ScheduleStore.saveCourses(context, courses)
                        detail = null
                    }) { Text("删除同名") }
                }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) { Text("关闭") }
            },
        )
    }

    // 新增 / 编辑
    if (showAdd || editTarget != null) {
        AddCourseDialog(
            initial = editTarget,
            onDismiss = {
                showAdd = false
                editTarget = null
            },
            onSave = { newOnes ->
                if (editTarget != null) {
                    courses = courses - editTarget!! + newOnes.first()
                    Toast.makeText(context, "已更新：${newOnes.first().name}", Toast.LENGTH_SHORT).show()
                } else {
                    courses = courses + newOnes
                    Toast.makeText(context, "已添加：${newOnes.first().name}（${newOnes.size} 个时段）", Toast.LENGTH_SHORT).show()
                }
                ScheduleStore.saveCourses(context, courses)
                showAdd = false
                editTarget = null
            },
        )
    }
}

// ======================= 周表格（跨节占格） =======================

@Composable
private fun Timetable(courses: List<Course>, week: Int, onCourse: (Course) -> Unit) {
    val context = LocalContext.current
    val periods = PeriodTable.periodsPerDay(context)
    val days = if (ThemePrefs.showWeekend.value) DAY_ORDER else listOf(1, 2, 3, 4, 5)
    val todayWd = ScheduleStore.weekdayOf(LocalDate.now())

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(vertical = 6.dp, horizontal = 4.dp)) {
            // 表头
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(42.dp), contentAlignment = Alignment.Center) {
                    Text("节", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                days.forEach { wd ->
                    val date = ScheduleStore.dateOf(context, week, wd)
                    val today = wd == todayWd
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            dayLabel(wd),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                            color = if (today) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${date.monthValue}/${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (today) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }

            // 表体：左侧节次 + 每天一列（格子背景层 + 课程层绝对定位跨行）
            Row {
                // 节次标签列
                Column(Modifier.width(42.dp)) {
                    (1..periods).forEach { p ->
                        Column(
                            Modifier.height(ROW_H.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("$p", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                PeriodTable.startStr(context, p),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 7.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
                // 每天一列
                days.forEach { wd ->
                    val dayCourses = courses.filter { it.weekday == wd && week in it.weeks }
                    // 冲突分道：同一时段重叠的课各占一道
                    val lanes = mutableListOf<MutableList<Course>>()
                    dayCourses.sortedBy { it.startPeriod }.forEach { c ->
                        val lane = lanes.firstOrNull { l ->
                            l.none { it.startPeriod <= c.endPeriod && c.startPeriod <= it.endPeriod }
                        }
                        if (lane != null) lane.add(c) else lanes.add(mutableListOf(c))
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .height((periods * ROW_H).dp)
                    ) {
                        // 背景格子层
                        Column(Modifier.fillMaxSize()) {
                            (1..periods).forEach { _ ->
                                Box(
                                    Modifier
                                        .height(ROW_H.dp)
                                        .fillMaxWidth()
                                        .padding(2.dp)
                                        .background(
                                            if (wd == todayWd) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                                            else Color.Transparent,
                                            RoundedCornerShape(8.dp),
                                        )
                                )
                            }
                        }
                        // 课程层（绝对定位，跨节纵向占满）
                        Row(Modifier.fillMaxSize()) {
                            lanes.forEach { lane ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    lane.forEach { c ->
                                        val span = (c.endPeriod - c.startPeriod + 1).coerceIn(1, periods)
                                        CourseBlock(
                                            c,
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 2.dp)
                                                .absoluteOffset(y = ((c.startPeriod - 1) * ROW_H + 2).dp)
                                                .height((span * ROW_H - 4).dp),
                                            onCourse,
                                        )
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
private fun CourseBlock(c: Course, modifier: Modifier, onCourse: (Course) -> Unit) {
    val color = PALETTE[(c.name.hashCode() * 31 + c.weekday * 7 + c.startPeriod).let { if (it < 0) -it else it } % PALETTE.size]
    val scale = listOf(0.85f, 1f, 1.2f)[ThemePrefs.scheduleFontScale.value]
    val base = MaterialTheme.typography.labelSmall
    val style = base.copy(fontSize = base.fontSize * scale)

    Card(
        onClick = { onCourse(c) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(c.name, style = style, fontWeight = FontWeight.Bold,
                color = Color.Black, maxLines = 2)
            if (c.location.isNotBlank()) {
                Text(c.location, style = style, color = Color.Black.copy(alpha = 0.85f), maxLines = 1)
            }
            if (c.teacher.isNotBlank()) {
                Text(c.teacher, style = style, color = Color.Black.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }
}

// ======================= 节假日 =======================

@Composable
private fun HolidayBanner(week: Int) {
    val context = LocalContext.current
    val hits = HolidayData.holidaysInWeek(
        ScheduleStore.dateOf(context, week, 7),
        ScheduleStore.dateOf(context, week, 6),
    )
    val today = LocalDate.now()
    val todayHoliday = HolidayData.holidays.firstOrNull { !today.isBefore(it.start) && !today.isAfter(it.end) }
    val dark = ThemePrefs.themeMode.value == ThemePrefs.ThemeMode.DARK ||
        (ThemePrefs.themeMode.value == ThemePrefs.ThemeMode.SYSTEM &&
            androidx.compose.foundation.isSystemInDarkTheme())

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hits.isEmpty()) MaterialTheme.colorScheme.surfaceVariant
            else if (dark) Color(0xFF5A4A20) else Color(0xFFFFF3D6)
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (hits.isEmpty()) {
                Text(
                    "本周无假期" + if (todayHoliday != null) " · 今天是【${todayHoliday.name}】🎉" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    "🏖 本周期间假期：" + hits.joinToString("、"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "假期参考（以学校校历为准）" + if (todayHoliday != null) " · 今天【${todayHoliday.name}】🎉" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}

// ======================= 添加 / 编辑课程 =======================

@Composable
private fun AddCourseDialog(
    initial: Course? = null,
    onDismiss: () -> Unit,
    onSave: (List<Course>) -> Unit,
) {
    val editing = initial != null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    val slots = remember {
        mutableStateListOf(
            if (initial != null) Slot(initial.weekday, initial.startPeriod, initial.endPeriod)
            else Slot(1, 1, 2)
        )
    }
    var weeksText by remember {
        mutableStateOf(
            if (initial != null) initial.weeks.sorted().joinToString("-") else "1-16"
        )
    }
    var parity by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "✏️ 编辑课程" else "✏️ 添加课程") },
        text = {
            Column(
                Modifier
                    .height(480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("课程名（必填）") })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(teacher, { teacher = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text("老师") })
                    OutlinedTextField(location, { location = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text("教室") })
                }

                Text("🕐 上课时间（连上多节就把结束节往后选，自动占满多格）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface)

                slots.forEachIndexed { i, _ ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("时段 ${i + 1}", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface)
                                if (slots.size > 1) {
                                    TextButton(onClick = { slots.removeAt(i) }) { Text("删除") }
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(listOf(7, 1, 2, 3, 4, 5, 6)) { wd ->
                                    FilterChip(
                                        selected = slots[i].weekday == wd,
                                        onClick = { slots[i] = slots[i].copy(weekday = wd) },
                                        label = { Text(dayLabel(wd), fontSize = 10.sp) },
                                    )
                                }
                            }
                            Text("起始节", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items((1..11).toList()) { p ->
                                    FilterChip(
                                        selected = slots[i].start == p,
                                        onClick = {
                                            slots[i] = slots[i].copy(
                                                start = p,
                                                end = if (slots[i].end < p) p else slots[i].end,
                                            )
                                        },
                                        label = { Text("$p", fontSize = 11.sp) },
                                    )
                                }
                            }
                            Text("结束节", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items((1..11).toList()) { p ->
                                    if (p >= slots[i].start) {
                                        FilterChip(
                                            selected = slots[i].end == p,
                                            onClick = { slots[i] = slots[i].copy(end = p) },
                                            label = { Text("$p", fontSize = 11.sp) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { slots.add(Slot(1, 1, 2)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("＋ 添加上课时间") }

                Text("📅 周次（支持分段，如 2-5,7-8）", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                OutlinedTextField(weeksText, { weeksText = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("周次范围") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        slots.map { s ->
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
