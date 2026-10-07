package com.campusglass.pickup

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 快递查询 v5（审查 E1-E12 修复）：
 * 历史读写统一 history2（复活 bug 根治）· 查询可取消/防串号 · 失败样式 · 结果置顶 · 危险操作确认。
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
    var showClearConfirm by remember { mutableStateOf(false) }         // E9

    // E1：读写统一 history2；一次性迁移旧键
    var history by remember {
        mutableStateOf(
            if (prefs.contains("history2")) {
                prefs.getString("history2", "")?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
            } else {
                val old = prefs.getStringSet("history", emptySet())?.toList().orEmpty()
                prefs.edit().putString("history2", old.joinToString("\n")).remove("history").apply()
                old
            }
        )
    }

    fun saveHistory(list: List<String>) {
        history = list
        prefs.edit().putString("history2", list.joinToString("\n")).apply()   // E1
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
        saveHistory((listOf(nu) + history.filter { !it.equals(nu, true) }).take(10))
        loading = true
        result = null
        resultNo = nu
        queryJob = scope.launch {
            val r = try {
                withContext(Dispatchers.IO) { ExpressApi.query(nu, force) }
            } catch (e: CancellationException) {
                return@launch                                             // E2：取消直接退出
            }
            if (resultNo == nu) {                                         // E2：防串号
                result = r
                loading = false
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(result) { if (result != null) listState.animateScrollToItem(0) }   // E8

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
                    Text("正在查询…", style = MaterialTheme.typography.bodySmall)
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
                                    "运单号：$resultNo　·　共 ${r.traces.size} 条轨迹（全部显示）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                TextButton(onClick = { doQuery(resultNo, force = true) }) {   // E12
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
                        } else {
                            r.traces.forEachIndexed { i, t ->
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(t.time, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                    Text(t.context, style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)
                                }
                                if (i != r.traces.size - 1) HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        // ---- 历史（E9：删除有描述；清空有确认） ----
        if (history.isNotEmpty()) {
            item {
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
                        history.forEach { nu ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = {
                                    trackingNo = nu
                                    doQuery(nu)
                                }) { Text(nu) }
                                IconButton(
                                    onClick = { saveHistory(history - nu) },
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除单号 $nu",          // E9
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
