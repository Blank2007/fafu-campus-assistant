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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
private fun dayLabel(wd: Int) = "周" + "一二三四五六日"[(wd - 1).coerceIn(0, 6)]

/** 一周显示顺序：周日起始 */
private val DAY_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)

private const val ROW_H = 74  // 每节行高 dp

private data class Slot(val weekday: Int, val start: Int, val end: Int)

// ======================= 主界面 =======================

/**
 * 课表 v2：
 * 周日起算 · 一节一行 · 跨节课程纵向占满多格 · 同时段冲突左右分栏 · 课程可编辑。
 *
 * v3.3 修复：
 *  - 编辑课程时新增的时段不再被丢弃（旧版只保存第一个时段）；
 *  - 周次解析为空时不再静默变成“全部 1-16 周”，改为提示用户修正（配合单/双周冲突提示）；
 *  - 周次芯片范围跟随实际使用到的最大的周（旧版写死 1-20，21 周以后的课永远看不到）；
 *  - 「每天上课节数」调小后越界课程会给出提示（旧版静默不画）；
 *  - 「删除同名」加二次确认；周次展示压缩成区间；色块取模改为 floorMod（消除理论上的负索引崩溃）。
 */
@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    var courses by remember { mutableStateOf(ScheduleStore.loadCourses(context)) }
    var week by rememberSaveable { mutableStateOf(ScheduleStore.currentWeek(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Course?>(null) }
    var editTarget by remember { mutableStateOf<Course?>(null) }
    var confirmDeleteName by remember { mutableStateOf<String?>(null) }

    val periods = PeriodTable.periodsPerDay(context)
    val weekendOn = ThemePrefs.showWeekend.value
    val shownDays = if (weekendOn) DAY_ORDER else listOf(1, 2, 3, 4, 5)
    val maxWeek = remember(courses, week) {
        val used = courses.flatMap { it.weeks }.maxOrNull() ?: 0
        maxOf(20, week, used).coerceIn(1, 30)
    }
    val hiddenCount = remember(courses, week, periods, weekendOn) {
        courses.count { it.weekday in shownDays && week in it.weeks && it.startPeriod > periods }
    }

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
                    items((1..maxWeek).toList()) { w ->
                        FilterChip(
                            selected = week == w,
                            onClick = { week = w },
                            label = { Text("第${w}周") },
                        )
                    }
                }
            }

            if (hiddenCount > 0) {
                item {
                    Text(
                        "⚠ 有 $hiddenCount 个课程时段超出「每天上课节数 = $periods」，未在表格中显示：" +
                            "可在「设置 → 节次时间设置」里增大节数，或编辑这些课程。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
                    Text("周次：" + (ScheduleStore.formatWeeks(c.weeks).ifBlank { "—" }) + " 周",
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
                        confirmDeleteName = c.name
                        detail = null
                    }) { Text("删除同名") }
                }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) { Text("关闭") }
            },
        )
    }

    // 「删除同名」二次确认（旧版一键删掉所有同名课程，无法撤销）
    confirmDeleteName?.let { name ->
        val count = courses.count { it.name == name }
        AlertDialog(
            onDismissRequest = { confirmDeleteName = null },
            title = { Text("删除同名课程") },
            text = { Text("将删除全部名为「$name」的课程时段（共 $count 条），删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    courses = courses.filterNot { it.name == name }
                    ScheduleStore.saveCourses(context, courses)
                    confirmDeleteName = null
                    Toast.makeText(context, "已删除：$name", Toast.LENGTH_SHORT).show()
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteName = null }) { Text("取消") }
            },
        )
    }

    // 新增 / 编辑（编辑时保存全部时段，不再只保存第一个）
    if (showAdd || editTarget != null) {
        AddCourseDialog(
            initial = editTarget,
            onDismiss = {
                showAdd = false
                editTarget = null
            },
            onSave = { newOnes ->
                val target = editTarget
                courses = if (target != null) courses - target + newOnes else courses + newOnes
                ScheduleStore.saveCourses(context, courses)
                val n = newOnes.first().name
                Toast.makeText(
                    context,
                    if (target != null) "已更新：$n（${newOnes.size} 个时段）"
                    else "已添加：$n（${newOnes.size} 个时段）",
                    Toast.LENGTH_SHORT,
                ).show()
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
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
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
                Column(Modifier.width(44.dp)) {
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
                                fontSize = 9.sp,          // v3.3：7sp 太小，确实看不清
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                                        // 越界保护：起始节超过每日节数的课不画；跨行不超出表格
                                        if (c.startPeriod in 1..periods) {
                                            val span = (c.endPeriod.coerceAtMost(periods) - c.startPeriod + 1)
                                                .coerceIn(1, periods)
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
}

@Composable
private fun CourseBlock(c: Course, modifier: Modifier, onCourse: (Course) -> Unit) {
    // floorMod：旧版 `hash * 31 + ...` 取绝对值对 Int.MIN_VALUE 无效，理论上会负索引崩溃
    val seed = c.name.hashCode() * 31 + c.weekday * 7 + c.startPeriod
    val color = PALETTE[Math.floorMod(seed, PALETTE.size)]
    val scale = listOf(0.85f, 1f, 1.2f)[ThemePrefs.scheduleFontScale.value.coerceIn(0, 2)]
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
                color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (c.location.isNotBlank()) {
                Text(c.location, style = style, color = Color.Black.copy(alpha = 0.85f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (c.teacher.isNotBlank()) {
                Text(c.teacher, style = style, color = Color.Black.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val context2 = LocalContext.current
    val maxP = PeriodTable.periodsPerDay(context2).coerceIn(1, 24)   // 节次上限跟随自定义（1-24）
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
        // 回填时把周次压缩成区间（如 1-16 / 2-5,7-8），避免出现超长逗号串
        mutableStateOf(
            if (initial != null) ScheduleStore.formatWeeks(initial.weeks).ifBlank { "1-16" } else "1-16"
        )
    }
    var parity by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "✏️ 编辑课程" else "✏️ 添加课程") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)          // 旧版写死 480dp：横屏/大字号下按钮会被顶出屏幕
                    .imePadding()                    // 键盘不再遮挡底部输入框
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(name, {
                    name = it
                    error = null
                }, Modifier.fillMaxWidth(), singleLine = true,
                    isError = error?.contains("课程名") == true,
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
                                items((1..maxP).toList()) { p ->
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
                                items((1..maxP).toList()) { p ->
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
                OutlinedTextField(weeksText, {
                    weeksText = it
                    error = null
                }, Modifier.fillMaxWidth(), singleLine = true,
                    isError = error?.contains("周次") == true,
                    label = { Text("周次范围") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部周", "单周", "双周").forEachIndexed { i, t ->
                        FilterChip(selected = parity == i, onClick = { parity = i; error = null }, label = { Text(t) })
                    }
                }
                Text(
                    if (editing) {
                        "编辑时周次已按实际周次回填；「单周/双周」只是额外的过滤条件（可与周次范围冲突，冲突时会提示）。"
                    } else {
                        "例：1-16 表示每周都上；2-5,7-8 表示只在第 2-5 周和第 7-8 周上。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "请填写课程名"
                        return@Button
                    }
                    // 修复：周次解析为空时不再静默兜底成 1-16 周（会把「双周」变成「全部周」）
                    val weeks = ScheduleStore.parseWeeks(weeksText)
                        .filter { w ->
                            when (parity) {
                                1 -> w % 2 == 1
                                2 -> w % 2 == 0
                                else -> true
                            }
                        }.toSet()
                    if (weeks.isEmpty()) {
                        error = "周次为空或与「单/双周」冲突，请修改周次（例如 1-16 或 2-5,7-8）"
                        return@Button
                    }
                    error = null
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
