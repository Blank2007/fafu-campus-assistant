package com.campusglass.pickup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.campusglass.ui.motion.motionSpec
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 轨迹超过这个条数就先折叠，只显示最近的几条 */
private const val COLLAPSE_AT = 3

/**
 * 快递查询 v6（v4.15）：
 * 历史结构化（单号+公司+状态+时间，可只看未签收）· 长轨迹折叠 · 复制单号 · 失败可重试 ·
 * 输入时离线识别快递公司 · 修掉"取消查询后 loading 被旧任务抹掉"的竞态。
 */
@Composable
fun PickupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("express", Context.MODE_PRIVATE) }

    var trackingNo by rememberSaveable { mutableStateOf("") }          // E11
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExpressApi.Result?>(null) }
    var resultNo by rememberSaveable { mutableStateOf("") }
    var queryJob by remember { mutableStateOf<Job?>(null) }            // E2：可取消
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }   // E9/V4-20
    var expanded by rememberSaveable { mutableStateOf(false) }           // v4.15：轨迹展开
    var onlyUnsigned by rememberSaveable { mutableStateOf(false) }       // v4.15：只看未签收
    var showSupported by rememberSaveable { mutableStateOf(false) }      // v4.16：支持清单展开

    // E1：读写统一 history2；一次性迁移旧键。v4.15 起同一条记录里还存公司与状态。
    var history by remember {
        mutableStateOf(
            if (prefs.contains("history2")) {
                ExpressHistory.parse(prefs.getString("history2", ""))
            } else {
                val old = prefs.getStringSet("history", emptySet())?.toList().orEmpty()
                val entries = old.map { ExpressHistory.Entry(it) }
                prefs.edit().putString("history2", ExpressHistory.encode(entries)).remove("history").apply()
                entries
            }
        )
    }

    fun saveHistory(list: List<ExpressHistory.Entry>) {
        history = list
        prefs.edit().putString("history2", ExpressHistory.encode(list)).apply()   // E1
    }

    fun doQuery(nuRaw: String, force: Boolean = false) {
        val nu = ExpressApi.normalize(nuRaw)
        if (nu.isBlank()) {
            Toast.makeText(context, "先输入快递单号", Toast.LENGTH_SHORT).show()
            return
        }
        if (loading) {
            Toast.makeText(context, "上一个查询还在进行中，已为你取消并重新查询", Toast.LENGTH_SHORT).show()
        }
        queryJob?.cancel()                                                 // E2：取消上一个
        // v4.15：先置顶入历史（公司与状态留空 → upsert 会保留上次查到的值，不会冲掉）
        saveHistory(ExpressHistory.upsert(history, ExpressHistory.Entry(nu = nu, time = System.currentTimeMillis())))
        loading = true
        resultNo = nu
        queryJob = scope.launch {
            val r = try {
                withContext(Dispatchers.IO) { ExpressApi.query(nu, force) }
            } catch (e: CancellationException) {
                // v4.15 修竞态：这里**不能**把 loading 置回 false。
                // 被取消的旧任务总是晚于新任务执行到这一句（新任务在同一个主线程 tick 里
                // 已经设好 loading=true），一置就正好把新任务的"查询中"状态抹掉，
                // 表现为转圈提前消失、按钮提前可用。取消按钮那条路径会自己重置 loading。
                return@launch
            }
            if (resultNo == nu) {                                         // E2：防串号
                result = r
                loading = false
                // v4.15：成功时把公司与状态回填到历史（失败则保留原值，不写入误导信息）
                saveHistory(
                    ExpressHistory.upsert(
                        history,
                        ExpressHistory.Entry(
                            nu = nu,
                            com = if (r.ok) r.comName else "",
                            state = if (r.ok) r.stateText else "",
                            time = System.currentTimeMillis(),
                        ),
                    )
                )
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(result) {
        expanded = false                                                  // v4.15：新结果默认折叠
        if (result != null) listState.animateScrollToItem(0)               // E8
    }

    // v4.15：输入时离线识别候选公司（不发网络请求），减少无效查询
    val guessed = remember(trackingNo) { ExpressApi.guessCompanies(trackingNo) }

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = trackingNo,
                    onValueChange = { trackingNo = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("粘贴或输入快递单号") },
                )
                // v4.15：离线识别候选公司（不发网络请求），减少无效查询
                if (guessed.isNotEmpty()) {
                    Text(
                        "识别为：${guessed.joinToString("、")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { doQuery(trackingNo) },
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (loading) "查询中…" else "查询物流") }
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = runCatching {
                                cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                            }.getOrNull().orEmpty()                                   // E12：防越界
                            val cleaned = ExpressApi.normalize(text)
                            val nu = Regex("[A-Za-z0-9]{8,}").findAll(cleaned)
                                .map { it.value }.maxByOrNull { it.length }.orEmpty()
                            if (nu.isEmpty()) {
                                Toast.makeText(context, "剪贴板里没找到快递单号", Toast.LENGTH_SHORT).show()
                            } else {
                                trackingNo = nu
                                Toast.makeText(context, "已填入单号：$nu", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("读取剪贴板") }
                }
                Text(
                    ExpressApi.SUPPORTED_HINT,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                // v4.16：把「支持哪些快递」直接摆在说明旁边（默认收起，点开看全）
                // 动效：箭头旋转 90°（▸→▾ 的旋转比换字符顺滑，也和设置页致谢箭头一致）；
                //       清单用展开/收起动画，不再是点击瞬间"蹦"出一大段文字。
                val m = motionSpec()
                val caretRot by animateFloatAsState(
                    targetValue = if (showSupported) 90f else 0f,
                    animationSpec = m.fluid,
                    label = "supportedCaret",
                )
                TextButton(
                    onClick = { showSupported = !showSupported },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        if (showSupported) "收起支持清单（${ExpressApi.SUPPORTED_COURIERS.size} 家）"
                        else "支持哪些快递？",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "▸",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 3.dp).rotate(caretRot),
                    )
                }
                AnimatedVisibility(
                    visible = showSupported,
                    enter = m.revealEnter,
                    exit = m.revealExit,
                ) {
                    Text(
                        ExpressApi.SUPPORTED_COURIERS.joinToString("、"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // ---- 查询中（E2：含取消） ----
        if (loading) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("正在查询…（免费源偶发查不到，会自动重试）", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        queryJob?.cancel()
                        loading = false
                        Toast.makeText(context, "已取消查询", Toast.LENGTH_SHORT).show()
                    }) { Text("取消") }
                }
            }
        }

        // ---- 结果卡（E8：置于历史上方） ----
        result?.let { r ->
            item {
                val fail = !r.ok                                             // E5：失败样式
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (fail) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (fail) "❌ ${r.comName}" else r.comName,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (fail) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            if (!fail) {
                                Text(
                                    r.stateText,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (!fail) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "运单号：$resultNo　·　共 ${r.traces.size} 条轨迹",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,          // v4.15：长单号不撑破行
                                )
                                TextButton(onClick = { copyToClipboard(context, resultNo) }) {   // v4.15
                                    Text("复制单号", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { doQuery(resultNo, force = true) }) {      // E12
                                    Text("忽略缓存重查", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (r.traces.isEmpty()) {
                            Text(
                                r.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (fail) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            if (fail) {
                                // v4.15：失败不再只能干瞪眼，直接给个重试
                                // v4.16：再加「去快递100 网页查」——免费数据源没收录该单号时，
                                //        网页端能看到官方结果（也能订阅后续更新），是目前唯一可靠的兜底
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { doQuery(resultNo, force = true) }) {
                                        Text("重试")
                                    }
                                    OutlinedButton(onClick = { openKuaidi100Web(context, resultNo) }) {
                                        Text("去快递100 网页查")
                                    }
                                }
                            }
                        } else {
                            // v4.15：轨迹长时先折叠，只显示最近 COLLAPSE_AT 条
                            // 动效：折叠部分改用 AnimatedVisibility 包住"多出来的那几条"，
                            //       展开时从 0 高度长出来，收起时向上收拢。
                            //       原来用 take() 直接增删列表 → 高度瞬间跳变，看不到任何过渡。
                            val m = motionSpec()
                            val collapsible = r.traces.size > COLLAPSE_AT
                            val head = if (collapsible) r.traces.take(COLLAPSE_AT) else r.traces
                            val extra = if (collapsible) r.traces.drop(COLLAPSE_AT) else emptyList()
                            head.forEachIndexed { i, t ->
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(t.time, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                    Text(t.context, style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)
                                }
                                if (i != head.lastIndex) HorizontalDivider()
                            }
                            AnimatedVisibility(
                                visible = expanded,
                                enter = m.revealEnter,
                                exit = m.revealExit,
                            ) {
                                Column {
                                    if (extra.isNotEmpty()) HorizontalDivider()
                                    extra.forEachIndexed { j, t ->
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(t.time, style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary)
                                            Text(t.context, style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (j != extra.lastIndex) HorizontalDivider()
                                    }
                                }
                            }
                            if (collapsible) {
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(
                                        if (expanded) "收起轨迹"
                                        else "展开全部 ${r.traces.size} 条轨迹",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 历史（E9：删除有描述；清空有确认。v4.15：带公司/状态/时间 + 只看未签收） ----
        if (history.isNotEmpty()) {
            item {
                val now = System.currentTimeMillis()
                val shownHistory = if (onlyUnsigned) history.filter { !it.signed } else history
                val unsignedCount = history.count { !it.signed }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("查询历史", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { showClearConfirm = true }) { Text("清空") }   // E9
                        }
                        if (unsignedCount in 1 until history.size) {                 // 有已签收的才值得筛
                            TextButton(onClick = { onlyUnsigned = !onlyUnsigned }) {
                                Text(
                                    if (onlyUnsigned) "✔ 只看未签收（$unsignedCount）"
                                    else "只看未签收（$unsignedCount）",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (shownHistory.isEmpty()) {
                            Text(
                                "都签收啦 🎉",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        shownHistory.forEach { e ->
                            val sub = ExpressHistory.subtitle(e, now)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f).clickable {
                                        trackingNo = e.nu
                                        doQuery(e.nu)
                                    }.padding(vertical = 4.dp),
                                ) {
                                    Text(
                                        if (e.signed) "✔ ${e.nu}" else e.nu,             // 已签收一眼可见
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (sub.isNotBlank()) {
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { saveHistory(history - e) },
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除单号 ${e.nu}",          // E9
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 取件快捷入口 ----
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📦 取件码快捷入口", style = MaterialTheme.typography.titleSmall)
                    Button(
                        onClick = {
                            // 支付宝·菜鸟小程序（直达取件码）
                            val uri = "alipays://platformapi/startapp?appId=2021001141626787"
                            val ok = runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(uri))
                                        .setPackage(AppJump.ALIPAY_PKG)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.isSuccess
                            if (!ok) {
                                Toast.makeText(
                                    context, "未能直达菜鸟小程序，已打开支付宝首页，请手动搜索「菜鸟」",  // E10
                                    Toast.LENGTH_LONG,
                                ).show()
                                AppJump.openAppFallback(context, AppJump.ALIPAY_PKG)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("支付宝 · 菜鸟取件码") }
                    OutlinedButton(
                        onClick = { AppJump.openPddPersonal(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("拼多多 · 个人中心（我的身份码）") }
                    OutlinedButton(
                        onClick = { AppJump.openPddHome(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("拼多多 · 首页") }
                    Text(
                        "按需求不拉起菜鸟 App，走支付宝小程序；拼多多身份码入口：个人中心→多多买菜→自提服务",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }

    // E9：清空确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空查询历史？") },
            text = { Text("将删除全部 ${history.size} 条历史记录，确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    saveHistory(emptyList())
                    showClearConfirm = false
                }) { Text("确定清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } },
        )
    }
}

/**
 * 复制到剪贴板。
 * Android 13（API 33）起系统会自己弹一个"已复制"提示，我们再弹一次就变成两条，所以低版本才自己弹。
 */
private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("快递单号", text))
    }.onFailure {
        Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "已复制单号", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 用系统浏览器打开快递100 的结果页（v4.16）。
 *
 * 为什么要它：免费 JSON 接口只覆盖它自己收录的数据，遇到没收录的单号一律回"查无结果"。
 * 网页端能看到官方结果、也能订阅后续更新，所以作为兜底入口给用户。
 * 用浏览器而不是 WebView，避免把 Cookie/UA 折腾进 App（也符合项目"零后端、少权限"的取向）。
 */
private fun openKuaidi100Web(context: Context, nu: String) {
    val url = "https://m.kuaidi100.com/result.jsp?nu=" +
        java.net.URLEncoder.encode(nu, "UTF-8")
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "没能打开浏览器", Toast.LENGTH_SHORT).show()
    }
}
