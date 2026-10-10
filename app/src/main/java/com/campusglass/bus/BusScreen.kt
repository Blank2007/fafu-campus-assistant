package com.campusglass.bus
import androidx.compose.runtime.saveable.rememberSaveable

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusglass.ui.glass.AcrylicCard
import com.campusglass.ui.widgets.ScreenHeader

/**
 * 附近公交：目的地筛选（建发悦城 / 万达广场 / 动车站）+ 点击线路弹窗看时刻表与路线。
 */
@Composable
fun BusScreen() {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("全部") }
    var detailName by rememberSaveable { mutableStateOf("") }   // V4-19：存线路名（可保存）
    val detail = BusData.routes.firstOrNull { it.name == detailName }

    val shown = if (filter == "全部") BusData.routes
    else BusData.routes.filter { filter in it.destTags }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("附近公交 · 南平校区")

        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (listOf("全部") + BusData.DESTINATIONS).forEach { d ->
                FilterChip(selected = filter == d, onClick = { filter = d }, label = { Text(d) })
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shown) { route ->
                AcrylicCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { detailName = route.name },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(route.name, style = MaterialTheme.typography.titleMedium)
                        Text(route.endpoints, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "🚏 去程 ${route.upSched.label}：首班 ${orUnknown(route.upSched.first)} · 末班 ${orUnknown(route.upSched.last)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "🔄 返程 " + (
                                route.downSched?.let {
                                    "${it.label}：首班 ${orUnknown(it.first)} · 末班 ${orUnknown(it.last)}"
                                } ?: "时刻未公开，以站牌为准"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(route.fare, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "点击查看时刻表与路线 →",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("没有符合「$filter」的线路", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { filter = "全部" }) { Text("清除筛选") }
                    }
                }
            }

            item {
                Text(
                    BusData.SOURCE_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }

    detail?.let { route ->
        AlertDialog(
            onDismissRequest = { detailName = "" },
            title = { Text(route.name) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(route.endpoints, style = MaterialTheme.typography.bodyMedium)
                    Text(route.fare, style = MaterialTheme.typography.bodySmall)

                    DirSection("🚏 去程", route.upSched)
                    route.downSched?.let { DirSection("🔄 返程", it) }
                        ?: Text(
                            "🔄 返程：时刻未公开，以站牌/「掌上公交」为准",
                            style = MaterialTheme.typography.bodySmall,
                        )

                    if (route.upStops.isNotEmpty()) {
                        Text("停靠站点", style = MaterialTheme.typography.titleSmall)
                        StopsLine("去程", route.upStops, route.downStops)      // V4-18
                        StopsLine("返程", route.downStops, route.upStops)      // V4-18
                        Text(
                            "注：双向站点存在差异（部分站仅单向停靠），以站牌为准",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    if (route.note.isNotBlank()) {
                        Text(route.note, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "具体班次与实时到站见「掌上公交」APP · 服务热线 ${BusData.SERVICE_HOTLINE}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { detailName = "" }) { Text("关闭") }
            },
        )
    }
}

/** 单方向发车计划区块：标题 + 首末班 + 时刻格 */
@Composable
private fun DirSection(tag: String, sched: com.campusglass.bus.DirSchedule) {
    Text("$tag ${sched.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(
        "首班 ${orUnknown(sched.first)} · 末班 ${orUnknown(sched.last)} · ${sched.intervalNote}" +
            if (sched.timeOfficial || sched.intervalNote.contains("推算")) "" else "（推算）",
        style = MaterialTheme.typography.bodySmall,
    )
    if (sched.note.isNotBlank()) {
        Text(sched.note, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
    val times = BusData.departureTimes(sched)
    if (times.isEmpty()) {
        Text(
            "该方向无公开固定时刻表，建议「掌上公交」看实时到站。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    } else {
        Text(
            when {
                sched.fixedTimes != null -> "官方固定班次："
                sched.timeOfficial && sched.peakIntervalMin != null -> "早高峰为官方时刻，其余按间隔推算："
                sched.timeOfficial -> "官方运营时段："
                else -> "按发车间隔推算（以站牌为准）："
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        TimeGrid(times)
        if (BusData.lastIsStamped(sched, times)) {
            Text(
                "注：${times.last()} 为标称末班（总站发车，仅此时刻），与前一班间隔较短",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

private fun orUnknown(v: String): String = v.ifBlank { "以站牌为准" }

/** 发车时刻分时段排版：上午/下午/晚间三段小格子 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeGrid(times: List<String>) {
    val groups = listOf(
        "上午" to times.filter { it < "12:00" },
        "下午" to times.filter { it >= "12:00" && it < "18:00" },
        "晚间" to times.filter { it >= "18:00" },
    )
    groups.forEach { (label, list) ->
        if (list.isEmpty()) return@forEach
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            list.forEach { t ->
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        t,
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/** 站点列表：一行一个站点（校区站高亮） */
@Composable
private fun StopsLine(label: String, stops: List<String>, other: List<String> = emptyList()) {
    if (stops.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        stops.forEachIndexed { i, s ->
            val campus = s == BusData.CAMPUS_STOP
            val oneWay = other.isNotEmpty() && other.none { it == s }   // V4-18：单向站标注
            Text(
                "${i + 1}. $s" +
                    (if (campus) " ★ 校区站" else "") +
                    (if (oneWay) if (label == "去程") "（仅去程）" else "（仅返程）" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (campus) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (campus) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
