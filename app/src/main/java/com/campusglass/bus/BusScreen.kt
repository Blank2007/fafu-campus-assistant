package com.campusglass.bus

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.campusglass.ui.widgets.ScreenHeader

/**
 * 附近公交：目的地筛选（建发悦城 / 万达广场 / 动车站）+ 点击线路弹窗看时刻表与路线。
 */
@Composable
fun BusScreen() {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("全部") }
    var detail by remember { mutableStateOf<BusRoute?>(null) }

    val shown = if (filter == "全部") BusData.routes
    else BusData.routes.filter { filter in it.destTags }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("附近公交 · 南平校区")

        Row(
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
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { detail = route },
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(route.name, style = MaterialTheme.typography.titleMedium)
                        Text(route.endpoints, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "首班 ${route.firstDeparture} · 末班 ${route.lastDeparture} · ${route.fare}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            route.intervalNote + if (route.timeOfficial) "" else "（推算）",
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
            onDismissRequest = { detail = null },
            title = { Text(route.name) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(route.endpoints, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "首班 ${route.firstDeparture} · 末班 ${route.lastDeparture} · ${route.fare}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(route.intervalNote, style = MaterialTheme.typography.bodySmall)

                    Text("发车时刻", style = MaterialTheme.typography.titleSmall)
                    if (times.isEmpty()) {
                        Text(
                            "该线路发车间隔波动较大（${route.intervalNote}），建议用「掌上公交」APP 看实时到站。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            if (route.timeOfficial) "早高峰为官方时刻，其余按间隔推算：" else "按发车间隔推算（以站牌为准）：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Text(
                            times.chunked(6).joinToString("\n") { it.joinToString("  ") },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (route.upStops.isNotEmpty()) {
                        Text("停靠站点", style = MaterialTheme.typography.titleSmall)
                        StopsLine("上行", route.upStops)
                        StopsLine("下行", route.downStops)
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
                TextButton(onClick = { detail = null }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun StopsLine(label: String, stops: List<String>) {
    if (stops.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Text(
        buildAnnotatedString {
            stops.forEachIndexed { i, s ->
                if (s == BusData.CAMPUS_STOP) {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                        append("【$s】")
                    }
                } else {
                    append(s)
                }
                if (i != stops.size - 1) append(" → ")
            }
        },
        style = MaterialTheme.typography.bodySmall,
    )
}
