package com.campusglass.bus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.campusglass.ui.glass.AcrylicCard
import com.campusglass.ui.widgets.ScreenHeader
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * 附近公交（v6.0 重写）。
 *
 * 从「线路资料库」改成「从校区出发的出行助手」：
 * 1. 主列表按**目的地场景**分组（去万达 / 去市区 / 去高铁站…），而不是按线路编号堆
 * 2. 每张卡直接给结论：几站、多久、下一班大概几点
 * 3. 不经校区的线路（K3/K1/建阳1路）保留但**标灰**，并写明怎么换乘
 * 4. 详情页可以**切换方向**（从校区出发 / 回校区），默认只看从校区出发那一程
 */
@Composable
fun BusScreen() {
    var filter by rememberSaveable { mutableStateOf("") }      // "" = 全部
    var detailName by rememberSaveable { mutableStateOf("") }
    val detail = BusData.routes.firstOrNull { it.name == detailName }

    // 「下一班」要跟着时间走：每 30 秒刷新一次当前时刻
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000)
        }
    }

    val scenes = if (filter.isBlank()) BusData.activeSceneIds() else listOf(filter)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("附近公交 · 南平校区")

        // 目的地筛选：只列「真的有线路到达」的场景，避免点进去空空如也
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = filter.isBlank(),
                    onClick = { filter = "" },
                    label = { Text("全部") },
                )
            }
            items(BusData.activeSceneIds()) { id ->
                val s = BusData.SCENES.first { it.id == id }
                FilterChip(
                    selected = filter == id,
                    onClick = { filter = if (filter == id) "" else id },
                    label = { Text(s.title.substringBefore("（")) },
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            scenes.forEach { sceneId ->
                val scene = BusData.SCENES.first { it.id == sceneId }
                val list = BusData.routesForScene(sceneId)

                item(key = "h-$sceneId") { SceneHeader(scene) }
                items(list, key = { "$sceneId-${it.name}" }) { route ->
                    RouteCard(
                        route = route,
                        scene = scene,
                        now = now,
                        onClick = { detailName = route.name },
                    )
                }
            }

            item {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    Text(
                        BusData.SOURCE_NOTE,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }

    detail?.let { route ->
        RouteDetailPage(route = route, now = now, onClose = { detailName = "" })
    }
}

// ───────────────────────────── 场景标题 ─────────────────────────────

@Composable
private fun SceneHeader(scene: Scene) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(scene.emoji, style = MaterialTheme.typography.titleMedium)
        Column {
            Text(scene.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                scene.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

// ───────────────────────────── 线路卡片 ─────────────────────────────

@Composable
private fun RouteCard(
    route: BusRoute,
    scene: Scene,
    now: LocalTime,
    onClick: () -> Unit,
) {
    val d = route.out
    val next = remember(now, route.name) { estimateNext(d, now, 3) }
    val stops = remember(route.name, scene.id) { stopsFromCampus(d, scene.targetStop) }
    val dim = if (route.servesCampus) 1f else 0.5f

    AcrylicCard(
        Modifier
            .fillMaxWidth()
            .alpha(dim)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    route.shortName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (route.servesCampus) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                val sub = route.name.substringAfter("（", "").removeSuffix("）")
                if (sub.isNotBlank()) {
                    Text(
                        "  $sub",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!route.servesCampus) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            "不经校区",
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // 结论行：票价 + 几站 + 下一班
            Text(
                buildString {
                    append(route.fare)
                    if (stops != null) append(" · 到").append(scene.targetStop).append(" ").append(stops).append(" 站")
                    append(" · ")
                    append(
                        d.intervalMin?.let { "约 $it 分钟一班" }
                            ?: if (d.fixedTimes != null) "固定班次" else "班次不定"
                    )
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                "🚏 从校区上车 · " + if (d.first.isBlank()) "时刻以校区通知为准"
                else "首班 ${d.first} / 末班 ${d.last}（起点站发车）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )

            NextDepartureLine(d, now, next)

            // 目标站不在这条线路上 → 明确说清楚要换乘，别让人以为坐上去就到
            if (stops == null && scene.transferNote.isNotBlank()) {
                Text(
                    "🔀 " + scene.transferNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                )
            }

            if (!route.servesCampus) {
                Text(
                    route.out.note.ifBlank { "需换乘前往" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** 「下一班 约 08:05 · 08:20 · 08:35」/「今日末班已过」 */
@Composable
private fun NextDepartureLine(d: Direction, now: LocalTime, next: List<String>) {
    when {
        d.first.isBlank() -> Text(
            "🕐 无固定时刻表，按校区通知发车",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )

        next.isEmpty() -> Text(
            // 末班已过时，把明日首班也告诉用户，而不是只丢一句「没车了」
            "🌙 今日末班已过（末班 ${d.last}）· 明日首班 ${d.first}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )

        else -> Text(
            "🕐 下一班约 " + next.joinToString(" · ") + "（估算，非实时）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
        )
    }
}

// ───────────────────────────── 线路详情（全屏二级页）─────────────────────────────

@Composable
private fun RouteDetailPage(route: BusRoute, now: LocalTime, onClose: () -> Unit) {
    // 默认看「从校区出发」那一程 —— 学生绝大多数时候只关心这个方向
    var showOut by rememberSaveable(route.name) { mutableStateOf(true) }
    val d = if (showOut) route.out else route.back

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                // 顶部栏
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(route.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            route.endpoints,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    TextButton(onClick = onClose) { Text("关闭") }
                }
                HorizontalDivider()

                // 方向切换
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = showOut,
                        onClick = { showOut = true },
                        label = { Text("从校区出发") },
                    )
                    FilterChip(
                        selected = !showOut,
                        onClick = { showOut = false },
                        label = { Text("回校区") },
                    )
                }

                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        AcrylicCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    d.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (d.first.isBlank()) "首末班未公开，按校区通知发车"
                                    else "首班 ${d.first} · 末班 ${d.last} · 间隔约 ${d.intervalMin ?: "?"} 分钟",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                NextDepartureLine(d, now, estimateNext(d, now, 3))
                                if (!d.official) {
                                    Text(
                                        "⚠️ 官方未单列该方向时刻，此处为参考信息",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    )
                                }
                                Text(
                                    "💰 " + route.fare,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (d.note.isNotBlank()) {
                                    Text(
                                        d.note,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    )
                                }
                            }
                        }
                    }

                    if (d.stops.isNotEmpty()) {
                        item {
                            Text(
                                "🚏 途经 ${d.stops.size} 站",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        item { StopTimeline(d.stops) }
                    } else {
                        item {
                            Text(
                                "站点明细未公开，以站牌为准",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }

                    if (route.note.isNotBlank()) {
                        item {
                            Text(
                                "💡 " + route.note,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }

                    item {
                        Text(
                            "数据来源：建阳公交官方公告（2026-10 核对）· 实时到站请用「掌上公交」APP · " +
                                "服务热线 ${BusData.SERVICE_HOTLINE}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── 站点时间轴 ─────────────────────────────

/**
 * 竖向站点时间轴。校区站高亮加粗，起点/终点用实心大点。
 * 比原来的「1. xxx 2. xxx」纯文本列表好扫得多。
 */
@Composable
private fun StopTimeline(stops: List<String>) {
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val dot = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val accent = MaterialTheme.colorScheme.primary

    Column {
        stops.forEachIndexed { i, name ->
            val isCampus = name == BusData.CAMPUS_STOP
            val isEnd = i == 0 || i == stops.lastIndex
            val color = if (isCampus) accent else MaterialTheme.colorScheme.onSurface

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 左侧：上段线 + 圆点 + 下段线
                Column(
                    Modifier.width(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(7.dp)
                            .background(if (i == 0) Color.Transparent else line),
                    )
                    Box(
                        Modifier
                            .size(if (isCampus || isEnd) 9.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isCampus) accent else dot),
                    )
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(7.dp)
                            .background(if (i == stops.lastIndex) Color.Transparent else line),
                    )
                }
                Text(
                    name + if (isCampus) "  ← 校区站" else "",
                    style = if (isCampus) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodySmall,
                    fontWeight = if (isCampus) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCampus) accent else color,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                )
            }
        }
    }
}
