package com.campusglass.bus

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
 *
 * v3.3 修复：
 *  - 筛选/弹窗状态用 rememberSaveable 保存（旋转屏幕、切 Tab 回来不再丢）；
 *  - 筛选行改为可换行的 FlowRow（窄屏/大字号下最后一个 chip 不再被裁掉）；
 *  - 时刻表补上标称末班，并在下方说明“末班已包含、班次按间隔生成”；
 *  - 不再把「（推算）」重复追加到已经写明来源的文案上；
 *  - 弹窗副标题按线路数据判断（固定班次 / 早高峰官方 / 全部推算），不再一律写“早高峰为官方时刻”；
 *  - 站点列表改为带序号的文本节点（旧版把「↓」当成独立文本，读屏时一个方向要念两倍长度）；
 *  - 筛选结果为空时给出提示。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusScreen() {
    var filter by rememberSaveable { mutableStateOf("全部") }
    var detailName by rememberSaveable { mutableStateOf<String?>(null) }

    val shown = if (filter == "全部") BusData.routes
    else BusData.routes.filter { filter in it.destTags }
    val detail = detailName?.let { name -> BusData.routes.firstOrNull { it.name == name } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("附近公交 · 南平校区")

        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            if (shown.isEmpty()) {
                item {
                    Text(
                        "没有开往「$filter」的线路，点上面的「全部」查看所有线路。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

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
                            "首班 ${route.firstDeparture} · 末班 ${route.lastDeparture} · ${route.fare}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            route.intervalNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Text(
                            "点击查看时刻表与路线 →",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
        val times = BusData.departureTimes(route)
        AlertDialog(
            onDismissRequest = { detailName = null },
            title = { Text(route.name) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(route.endpoints, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "首班 ${route.firstDeparture} · 末班 ${route.lastDeparture} · ${route.fare}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        route.intervalNote + sourceTag(route),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text("发车时刻", style = MaterialTheme.typography.titleSmall)
                    if (times.isEmpty()) {
                        Text(
                            "该线路没有可推算的固定间隔（${route.intervalNote}），" +
                                "建议用「掌上公交」APP 看实时到站。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            timeCaption(route),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        TimeGrid(times)
                        Text(
                            "共 ${times.size} 班，已含标称末班 ${route.lastDeparture}；" +
                                "按间隔生成的时刻仅供参考，以站牌为准。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }

                    if (route.upStops.isNotEmpty()) {
                        Text("停靠站点", style = MaterialTheme.typography.titleSmall)
                        Text(
                            BusData.STOPS_NOTE,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                        StopsLine("上行（去程）", route.upStops)
                        StopsLine("下行（回程）", route.down)
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
                TextButton(onClick = { detailName = null }) { Text("关闭") }
            },
        )
    }
}

/**
 * 来源标注：文案里已经写了「推算/官方/固定/核实」就不再追加，避免出现
 * 「约 15-19 分钟一班（推算）（推算）」这种重复。
 */
private fun sourceTag(route: BusRoute): String = when {
    route.fixedTimes?.isNotEmpty() == true -> ""
    route.timeOfficial -> ""
    else -> "（推算，以站牌为准）"
}

/** 时刻表副标题：按数据判断来源，而不是只看 timeOfficial */
private fun timeCaption(route: BusRoute): String = when {
    route.fixedTimes?.isNotEmpty() == true -> "固定班次（官方核实时刻）："
    route.peakIntervalMin != null -> "早高峰为官方时刻，其余按间隔推算（以站牌为准）："
    else -> "按发车间隔推算（以站牌为准）："
}

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

/** 站点列表：一行一个带序号的站点（校区站高亮），不用装饰性箭头占文本节点 */
@Composable
private fun StopsLine(label: String, stops: List<String>) {
    if (stops.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        stops.forEachIndexed { i, s ->
            val highlight = s == BusData.CAMPUS_STOP
            Text(
                text = "${i + 1}. $s" + if (highlight) "　★ 校区站" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
