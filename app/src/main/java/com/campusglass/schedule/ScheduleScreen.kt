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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusglass.ui.theme.ThemePrefs
import com.campusglass.ui.widgets.ScreenHeader
import java.time.LocalDate

// ======================= 常量 =======================

private val PALETTE = listOf(
    Color(0xFF8FA8FF), Color(0xFFFFBC6B), Color(0xFF6FDFC0), Color(0xFFFF9DB0),
    Color(0xFFC4A0FF), Color(0xFFFFD966), Color(0xFF72D4F0), Color(0xFFAEDD6E),
    Color(0xFFFFAE82), Color(0xFF8FE0D8), Color(0xFFF08FB4), Color(0xFFB3BEFF),
)

private fun dayLabel(wd: Int) = "周" + "一二三四五六日"[wd - 1]

private val DAY_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)

/** S15：行高随字号档位缩放（0.85×/1×/1.2× → 70/78/92dp） */
private fun rowHeightDp(): Int = when (ThemePrefs.scheduleFontScale.value) {
    0 -> 70
    2 -> 92
    else -> 78
}

private data class Slot(val weekday: Int, val start: Int, val end: Int)

// ======================= 主界面 =======================

/** 课表 v4：跨节占格 · 冲突分栏 · 可编辑 · 分享导入（审查 S1-S24 全量修复） */
@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    var courses by remember { mutableStateOf(ScheduleStore.loadCourses(context)) }
    var week by rememberSaveable { mutableIntStateOf(ScheduleStore.currentWeek(context)) }   // S17
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf("") }        // S17：存 id 不存对象
    var editId by rememberSaveable { mutableStateOf("") }
    var pendingDeleteAllId by rememberSaveable { mutableStateOf("") }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }      // S17
    var importError by rememberSaveable { mutableStateOf("") }
    var importTermOverride by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }   // V4-1
    var confirmReplace by rememberSaveable { mutableStateOf(false) }   // V4-7

    val detail = courses.firstOrNull { it.id == detailId }
    val editTarget = courses.firstOrNull { it.id == editId }
    val pendingDeleteAll = courses.firstOrNull { it.id == pendingDeleteAllId }

    fun reload() { courses = ScheduleStore.loadCourses(context) }

    // ---- 导入（S5/S6/S7/S8） ----
    fun doImport(merge: Boolean) {
        val parsed = ScheduleShare.decode(importText)
        if (parsed == null) {
            importError = "分享码无效或不完整，请重新粘贴完整的分享码"
            return
        }
        if (parsed.courses.isEmpty() && parsed.skipped == 0) {
            importError = "对方的课表是空的，没有可导入的课程"
            return
        }
        val (term, imported, skipped) = parsed
        // S7：自身去重 + 全字段去重键（名称/星期/起止节/周次）
        fun key(c: Course) = "${c.name}|${c.weekday}|${c.startPeriod}|${c.endPeriod}|${ScheduleStore.compactWeeks(c.weeks)}"
        val distinct = imported.distinctBy { key(it) }

        if (merge) {
            val existingKeys = courses.map { key(it) }.toSet()
            val toAdd = distinct.filterNot { key(it) in existingKeys }
            // S8：合并导入一律保留本机学期起始日
            if (!ScheduleStore.saveCourses(context, courses + toAdd)) {
                importError = "课表数据损坏，已停止写入；请先重置课表"
                return
            }
            Toast.makeText(
                context,
                "已合并导入 ${toAdd.size} 个时段" +
                    (if (distinct.size != toAdd.size) "（去重跳过 ${distinct.size - toAdd.size}）" else "") +
                    (if (skipped > 0) "，跳过 $skipped 条非法记录" else ""),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            // S6：替换前自动备份；S8：覆盖学期起始日需显式勾选，且先设日期再保存
            context.getSharedPreferences("schedule", android.content.Context.MODE_PRIVATE)
                .edit().putString("courses_backup", org.json.JSONArray().let { a ->
                    courses.forEach { co ->
                        a.put(org.json.JSONObject()
                            .put("name", co.name).put("teacher", co.teacher).put("location", co.location)
                            .put("weekday", co.weekday).put("start", co.startPeriod).put("end", co.endPeriod)
                            .put("weeks", ScheduleStore.compactWeeks(co.weeks)))
                    }
                    a.toString()
                }).apply()
            if (!ScheduleStore.saveCourses(context, distinct)) {
                importError = "课表数据损坏，已停止写入；请先重置课表"
                return
            }
            // V4-14：课表写入成功后才动学期起始日（失败不会半改）
            if (importTermOverride && term.isNotBlank()) {
                val ok = runCatching {
                    ScheduleStore.setTermStart(context, java.time.LocalDate.parse(term))
                }.getOrDefault(false)
                if (!ok) Toast.makeText(context, "分享的学期起始日不合法，已保留本机设置", Toast.LENGTH_SHORT).show()
            }
            Toast.makeText(
                context,
                "已替换为分享的课表（${distinct.size} 个时段，原课表已备份）" +
                    (if (skipped > 0) "，跳过 $skipped 条非法记录" else ""),
                Toast.LENGTH_LONG,
            ).show()
        }
        showImport = false
        importText = ""
        importError = ""
        reload()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ScreenHeader("课表") }

            // ---- 警示横幅（S10/S11/S22） ----
            if (ScheduleStore.dataCorrupt) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "⚠️ 课表数据已损坏，为保护原数据已停止写入（原始内容已备份）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    ScheduleStore.clearCorrupt(context)
                                    reload()
                                    Toast.makeText(context, "已重置为空课表（备份仍保留在本机）", Toast.LENGTH_SHORT).show()
                                }) { Text("重置课表") }
                            }
                        }
                    }
                }
            }

            val periodsNow = PeriodTable.periodsPerDay(context)
            val hiddenByPeriod = courses.filter { it.startPeriod > periodsNow }.size
            val weekendHidden = if (!ThemePrefs.showWeekend.value)
                courses.count { it.weekday == 7 || it.weekday == 6 } else 0

            if (hiddenByPeriod > 0) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚠️ 有 $hiddenByPeriod 个时段超出当前每天节数（${periodsNow} 节）未显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                Toast.makeText(context, "请到 设置 → 节次与学期设置 调大每天节数", Toast.LENGTH_LONG).show()
                            }) { Text("去调整") }
                        }
                    }
                }
            }
            if (weekendHidden > 0) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "📅 周末还有 $weekendHidden 门课，当前已隐藏",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { ThemePrefs.setShowWeekend(context, true) }) { Text("显示周末") }
                        }
                    }
                }
            }

            // ---- 分享 / 导入 ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val code = ScheduleShare.encode(courses, ScheduleStore.termStart(context).toString())
                            if (code == null) {
                                Toast.makeText(
                                    context,
                                    "课表过大，放不进分享码（请精简课程后再试）",   // V4-4
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@OutlinedButton
                            }
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("课表分享码", code))
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT,
                                    "我的课表分享码（校园助手 App 课表页点「导入课表」）：\n$code")
                            }
                            runCatching {
                                context.startActivity(android.content.Intent.createChooser(send, "分享课表"))
                            }
                            Toast.makeText(context, "分享码已生成并复制，发给同学即可一键导入", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("📤 分享课表") }
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            val text = runCatching {
                                cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                            }.getOrNull().orEmpty()
                            importText = if (text.contains("FAFUSCH1:")) text else ""
                            importError = ""
                            showImport = true
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("📥 导入课表") }
                }
                // V4-1：备份可恢复（不再只写不读）
                val backup = ScheduleStore.backupInfo(context)
                if (backup != null) {
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("♻️ 从备份恢复课表（${backup.first} · ${backup.second} 条）")
                    }
                }
            }

            item {
                Text(
                    "第${week}周" +
                        if (week == ScheduleStore.currentWeek(context)) "（当前周）" else "（当前为第${ScheduleStore.currentWeek(context)}周）" +
                        "（周日起算）· 点周次切换 · 点课程可编辑/删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            item {
                Text(
                    "💡 桌面小部件在部分系统上需长按小部件才能强制刷新课表",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }

            // ---- 周次芯片（选中周精确居中；当前周标注；S19 范围动态） ----
            item {
                val curWeek = ScheduleStore.currentWeek(context)
                val maxCourseWeek = courses.flatMap { it.weeks }.maxOrNull() ?: 1
                // V4-12：按学期推导（未来无课周也可点，能看到假期横幅）
                val maxWeek = maxOf(week, curWeek + 8, maxCourseWeek, 20)
                    .coerceAtMost(ScheduleStore.MAX_WEEK)
                val chipState = rememberLazyListState()
                // V4-6：选中周【居中】（不是顶到最左/最右）
                LaunchedEffect(week, maxWeek) {
                    chipState.scrollToItem((week - 1).coerceAtLeast(0))
                    val info = chipState.layoutInfo
                    val vp = info.viewportEndOffset - info.viewportStartOffset
                    val item = info.visibleItemsInfo.find { it.index == week - 1 }
                    if (vp > 0 && item != null) {
                        val delta = (item.offset + item.size / 2f) - vp / 2f
                        chipState.scrollBy(delta)          // 正=内容前滚(项左移)，负=后滚(项右移)
                    }
                }
                LazyRow(state = chipState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..maxWeek).toList()) { w ->
                        FilterChip(
                            selected = week == w,
                            onClick = { week = w },
                            label = { Text(if (w == curWeek) "第${w}周·当前" else "第${w}周") },
                        )
                    }
                }
            }

            item { HolidayBanner(week) }
            item { Timetable(courses, week) { detailId = it.id } }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // V4-6：查看其他周时，可一键回到当前周
            val curWeekNow = ScheduleStore.currentWeek(context)
            if (week != curWeekNow) {
                ExtendedFloatingActionButton(
                    onClick = { week = curWeekNow },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    text = { Text("回到当前周") },
                )
            }
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加课程") },
            )
        }
    }

    // ---- 详情（S13/S23） ----
    detail?.let { c ->
        AlertDialog(
            onDismissRequest = { detailId = "" },
            title = { Text(c.name) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),      // S13
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("老师：${c.teacher.ifBlank { "—" }}", color = MaterialTheme.colorScheme.onSurface)
                    Text("地点：${c.location.ifBlank { "—" }}", color = MaterialTheme.colorScheme.onSurface)
                    val span = (c.endPeriod - c.startPeriod + 1).coerceAtLeast(1)          // S13
                    Text("时间：${dayLabel(c.weekday)} 第${c.startPeriod}-${c.endPeriod}节（占${span}格）",
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("周次：" + ScheduleStore.compactWeeks(c.weeks) + " 周",              // S13 压缩
                        color = MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { editId = c.id; detailId = "" }) { Text("✏️ 编辑") }
                        TextButton(onClick = {
                            val next = courses - c
                            if (ScheduleStore.saveCourses(context, next)) {
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "数据已损坏，删除未生效（已保护原数据）", Toast.LENGTH_LONG).show()
                            }
                            detailId = ""
                            reload()          // V4-21：以盘上数据为准
                        }) { Text("删除该时段") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            pendingDeleteAllId = c.id                                     // S6/SC-6
                            detailId = ""
                        }) { Text("删除同名全部") }
                        TextButton(onClick = { detailId = "" }) { Text("关闭") }
                    }
                }
            },
        )
    }

    // ---- 删除同名确认 ----
    pendingDeleteAll?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDeleteAllId = "" },
            title = { Text("删除同名课程？") },
            text = {
                Text("将删除「${c.name}」的全部时段（同名课程会一起删）。确定吗？",
                    color = MaterialTheme.colorScheme.onSurface)
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = courses.filterNot { it.name == c.name }
                    if (ScheduleStore.saveCourses(context, next)) {
                        Toast.makeText(context, "已删除同名课程", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "数据已损坏，删除未生效（已保护原数据）", Toast.LENGTH_LONG).show()
                    }
                    pendingDeleteAllId = ""
                    reload()          // V4-21
                }) { Text("确定删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteAllId = "" }) { Text("取消") } },
        )
    }

    // V4-7：替换导入二次确认
    if (confirmReplace) {
        val parsedCount = ScheduleShare.decode(importText)?.courses?.size ?: 0
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("替换导入？") },
            text = {
                Text(
                    "将用 $parsedCount 条课程替换当前 ${courses.size} 条课表。\n" +
                        "原课表已自动备份，可在课表页点「从备份恢复」找回。确定吗？",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    doImport(false)
                }) { Text("确定替换") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("取消") }
            },
        )
    }

    // V4-1：从备份恢复确认
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("从备份恢复课表？") },
            text = {
                Text(
                    "将用备份替换当前 ${courses.size} 条课表（当前课表会再存一份备份）。确定吗？",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    val n = ScheduleStore.restoreBackup(context)
                    if (n < 0) {
                        Toast.makeText(context, "没有可用的备份", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "已恢复备份（$n 条）", Toast.LENGTH_SHORT).show()
                    }
                    reload()
                }) { Text("确定恢复") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("取消") }
            },
        )
    }

    // ---- 导入弹窗（S8：学期起始日显式勾选） ----
    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("📥 导入课表") },
            text = {
                Column(
                    Modifier.imePadding().verticalScroll(rememberScrollState()),   // S16
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (importError.isNotBlank()) {
                        Text(importError, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("粘贴同学发的分享码（FAFUSCH1: 开头）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it; importError = "" },
                        Modifier.fillMaxWidth().heightIn(max = 120.dp),
                        label = { Text("分享码") },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = importTermOverride,
                            onCheckedChange = { importTermOverride = it },
                        )
                        Text("替换导入时同时使用对方的学期起始日（合并导入永不覆盖）",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text("合并＝加到现有课表后（全字段去重）；替换＝备份后清空导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { doImport(true) }) { Text("合并导入") }
                    Button(onClick = { confirmReplace = true }) { Text("替换导入") }   // V4-7
                }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } },
        )
    }

    // ---- 新增 / 编辑 ----
    if (showAdd || editTarget != null) {
        AddCourseDialog(
            initial = editTarget,
            onDismiss = { showAdd = false; editId = "" },
            onSave = { newOnes ->
                val ok = if (editTarget != null) {
                    ScheduleStore.saveCourses(context, courses - editTarget + newOnes)
                } else {
                    ScheduleStore.saveCourses(context, courses + newOnes)
                }
                if (!ok) {
                    Toast.makeText(context, "课表数据损坏，已停止写入；请先重置课表", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        context,
                        if (editTarget != null) "已更新：${newOnes.first().name}（${newOnes.size} 个时段）"
                        else "已添加：${newOnes.first().name}（${newOnes.size} 个时段）",
                        Toast.LENGTH_SHORT,
                    ).show()
                    showAdd = false
                    editId = ""
                    reload()
                }
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
    val isCurrentWeek = week == ScheduleStore.currentWeek(context)      // S12
    val times = remember(periods) { PeriodTable.all(context) }          // S24：只读一次
    val rowH = rowHeightDp()                                            // S15
    val timeFont = if (ThemePrefs.scheduleFontScale.value == 2) 9 else 8   // S15

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(vertical = 6.dp, horizontal = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) {
                    Text("节", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                days.forEach { wd ->
                    val date = ScheduleStore.dateOf(context, week, wd)
                    val today = isCurrentWeek && wd == todayWd          // S12
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

            Row {
                Column(Modifier.width(46.dp)) {
                    (1..periods).forEach { p ->
                        Column(
                            Modifier.height(rowH.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("$p", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(times.getOrNull(p - 1)?.substringBefore("-") ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = timeFont.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                            Text(times.getOrNull(p - 1)?.substringAfter("-") ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = timeFont.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                        }
                    }
                }
                days.forEach { wd ->
                    val dayCourses = courses.filter { it.weekday == wd && week in it.weeks }
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
                            .height((periods * rowH).dp)
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            repeat(periods) {
                                Box(
                                    Modifier
                                        .height(rowH.dp)
                                        .fillMaxWidth()
                                        .padding(2.dp)
                                        .background(
                                            if (isCurrentWeek && wd == todayWd)      // S12
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                                            else Color.Transparent,
                                            RoundedCornerShape(8.dp),
                                        )
                                )
                            }
                        }
                        Row(Modifier.fillMaxSize()) {
                            lanes.forEach { lane ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    lane.forEach { c ->
                                        if (c.startPeriod in 1..periods) {
                                            val span = (c.endPeriod.coerceAtMost(periods) - c.startPeriod + 1)
                                                .coerceIn(1, periods)
                                            CourseBlock(
                                                c,
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 2.dp)
                                                    .absoluteOffset(y = ((c.startPeriod - 1) * rowH + 2).dp)
                                                    .height((span * rowH - 4).dp),
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
    val color = PALETTE[Math.floorMod(
        c.name.hashCode() * 31 + c.weekday * 7 + c.startPeriod, PALETTE.size
    )]
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
                Text(c.location, style = style, color = Color.Black.copy(alpha = 0.85f),
                    maxLines = 1)
            }
            if (c.teacher.isNotBlank()) {
                Text(c.teacher, style = style, color = Color.Black.copy(alpha = 0.7f),
                    maxLines = 1)
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
    val makeups = HolidayData.makeupInWeek(
        ScheduleStore.dateOf(context, week, 7),
        ScheduleStore.dateOf(context, week, 6),
    )
    val today = LocalDate.now()
    val todayHoliday = HolidayData.holidays.firstOrNull { !today.isBefore(it.start) && !today.isAfter(it.end) }
    val todayMakeup = HolidayData.makeupDays.firstOrNull { it.start == today }
    val dark = ThemePrefs.themeMode.value == ThemePrefs.ThemeMode.DARK ||
        (ThemePrefs.themeMode.value == ThemePrefs.ThemeMode.SYSTEM &&
            androidx.compose.foundation.isSystemInDarkTheme())
    val stale = today.isAfter(HolidayData.holidays.maxOfOrNull { it.end } ?: today)   // S20

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hits.isEmpty()) MaterialTheme.colorScheme.surfaceVariant
            else if (dark) Color(0xFF5A4A20) else Color(0xFFFFF3D6)
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (stale) {
                Text("假期数据待更新（当前数据仅覆盖到 2026-2027 学年）",   // S20
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface)
            } else if (hits.isEmpty()) {
                if (makeups.isNotEmpty()) {
                    Text(
                        "⚠️ 本周调休上课：" + makeups.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else if (todayMakeup != null) {
                    Text(
                        "⚠️ 今天${todayMakeup.name.removePrefix("调休上课")}！",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
                    "假期/调休依据学校校历" + if (todayHoliday != null) " · 今天【${todayHoliday.name}】🎉" else "",
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
    val context = LocalContext.current
    val editing = initial != null
    val maxP = PeriodTable.periodsPerDay(context).coerceIn(1, 24)       // S10：下界 1
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var teacher by rememberSaveable { mutableStateOf(initial?.teacher ?: "") }
    var location by rememberSaveable { mutableStateOf(initial?.location ?: "") }
    // V4-20：slots 旋转不丢（自定义 Saver）
    val slots = rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver<MutableList<Slot>, String>(
            save = { list -> list.map { "${it.weekday}:${it.start}:${it.end}" } },
            restore = { strs ->
                mutableStateListOf(*(if (strs.isEmpty()) arrayOf(Slot(1, 1, minOf(2, maxP)))
                else strs.map {
                    val a = it.split(":"); Slot(a[0].toInt(), a[1].toInt(), a[2].toInt())
                }.toTypedArray()))
            },
        ),
    ) {
        mutableStateListOf(
            if (initial != null) Slot(initial.weekday, initial.startPeriod, initial.endPeriod)
            else Slot(1, 1, minOf(2, maxP))                              // S10：默认时段夹紧
        )
    }
    var weeksText by rememberSaveable {
        mutableStateOf(
            if (initial != null) ScheduleStore.compactWeeks(initial.weeks) else "1-16"
        )
    }
    var parity by rememberSaveable { mutableIntStateOf(0) }
    var errorText by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "✏️ 编辑课程" else "✏️ 添加课程") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .imePadding()                                        // S16
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    name, { name = it; errorText = "" },                 // S14
                    Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("课程名（必填）") },
                    isError = errorText.contains("课程名"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(teacher, { teacher = it }, Modifier.weight(1f),
                        singleLine = true, label = { Text("老师") })
                    OutlinedTextField(location, { location = it }, Modifier.weight(1f),
                        singleLine = true, label = { Text("教室") })
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
                    onClick = { slots.add(Slot(1, 1, minOf(2, maxP))) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("＋ 添加上课时间") }

                Text("📅 周次（支持分段，如 2-5,7-8）", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                OutlinedTextField(
                    weeksText, { weeksText = it; errorText = "" },        // S14
                    Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("周次范围") },
                    isError = errorText.contains("周次"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部周", "单周", "双周").forEachIndexed { i, t ->
                        FilterChip(selected = parity == i, onClick = { parity = i }, label = { Text(t) })
                    }
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                if (errorText.isNotBlank()) {                             // S14：错误在按钮上方
                    Text(errorText, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorText = "请填写课程名（必填）"
                            return@Button
                        }
                        // S9：空白=没填才兜底；非空但解析不出必须报错
                        val parsed = ScheduleStore.parseWeeks(weeksText)
                        val filtered = parsed.filter { w ->
                            when (parity) {
                                1 -> w % 2 == 1
                                2 -> w % 2 == 0
                                else -> true
                            }
                        }.toSet()
                        val weeks = when {
                            filtered.isNotEmpty() -> filtered
                            weeksText.isBlank() && parity == 0 -> (1..16).toSet()
                            weeksText.isNotBlank() && parsed.isEmpty() -> {
                                errorText = "周次解析不出内容，请用 1-16 或 2-5,7-8 这类格式"
                                return@Button
                            }
                            else -> {
                                errorText = "周次与单/双周设置冲突（例如填了奇数周却选了双周），请调整"
                                return@Button
                            }
                        }
                        onSave(
                            slots.map { s ->
                                Course(
                                    name = name.trim(),
                                    teacher = teacher.trim(),
                                    location = location.trim(),
                                    weekday = s.weekday,
                                    startPeriod = s.start,
                                    endPeriod = s.end.coerceIn(s.start, maxP),   // S10
                                    weeks = weeks,
                                )
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
