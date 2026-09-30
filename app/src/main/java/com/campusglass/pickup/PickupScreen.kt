package com.campusglass.pickup

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.campusglass.ui.glass.AcrylicCard
import com.campusglass.ui.widgets.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 取件页：① 支付宝·菜鸟 ② 拼多多（首页/个人中心） ③ 快递单号查询。
 *
 * v3.3 修复：
 *  - 查询结果与单号绑定：快速连查两个单号不会再显示错人的结果；
 *  - 查询中按钮禁用，避免连点触发接口限流；
 *  - 键盘弹出不再遮挡输入框与结果（imePadding）；
 *  - 剪贴板只认“像单号”的串，网址/手机号不再被当成单号“填入成功”；
 *  - 历史记录改为有序保存（重启后不再乱序），清空前二次确认；
 *  - 失败原因区分网络异常 / 未查到 / 识别不出公司（配合 ExpressApi 的 Kind）。
 */

private const val PREFS_NAME = "express"
private const val KEY_HISTORY = "historyLines"
private const val KEY_HISTORY_LEGACY = "history"
private const val HISTORY_LIMIT = 10

/** 读取历史（有序）；自动迁移旧版的 StringSet 存储 */
private fun readHistory(prefs: SharedPreferences): List<String> {
    val lines = prefs.getString(KEY_HISTORY, null)
    if (lines != null) {
        return lines.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(HISTORY_LIMIT)
    }
    val legacy = prefs.getStringSet(KEY_HISTORY_LEGACY, emptySet())?.toList().orEmpty()
    if (legacy.isNotEmpty()) writeHistory(prefs, legacy)
    return legacy
}

/** 历史列表永远用字符串按行保存，保证“最新在前”的顺序在重启后依然成立 */
private fun writeHistory(prefs: SharedPreferences, list: List<String>) {
    prefs.edit().putString(KEY_HISTORY, list.take(HISTORY_LIMIT).joinToString("\n")).apply()
}

/** 从任意文本中提取「像快递单号」的串；不做模糊兜底，避免把网址/手机号当单号 */
internal fun extractTrackingNumber(text: String): String? {
    val compact = text.replace(Regex("[\\s\\u00A0\\u3000]+"), "")
    if (compact.isEmpty()) return null
    return Regex("[A-Za-z]{0,3}\\d{8,20}").findAll(compact)
        .map { it.value }
        .firstOrNull { candidate ->
            val digits = candidate.count { it.isDigit() }
            // 排除 11 位手机号（1 开头且全是数字）
            digits >= 8 && !(candidate.length == 11 && candidate.startsWith("1") && candidate.all { it.isDigit() })
        }
}

private fun readClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return runCatching { clip.getItemAt(0).coerceToText(context)?.toString() }.getOrNull()
}

@Composable
fun PickupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var trackingNo by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExpressApi.Result?>(null) }
    var history by remember { mutableStateOf(readHistory(prefs)) }
    var confirmClear by remember { mutableStateOf(false) }
    // 查询序号：只有最后一次查询的结果允许写回，杜绝“查 A 显示 B”
    var querySeq by remember { mutableIntStateOf(0) }

    fun doQuery(nu: String) {
        val number = nu.trim()
        if (number.isBlank()) {
            Toast.makeText(context, "先输入快递单号", Toast.LENGTH_SHORT).show()
            return
        }
        val mySeq = querySeq + 1
        querySeq = mySeq
        val h = (listOf(number) + history.filterNot { it.equals(number, ignoreCase = true) }).take(HISTORY_LIMIT)
        history = h
        writeHistory(prefs, h)
        loading = true
        result = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { ExpressApi.query(number) }
            if (mySeq == querySeq) {
                result = r
                loading = false
            }
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("取件码 · 快递") }

        // ---- ① 支付宝 · 菜鸟 ----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("① 菜鸟取件码 · 支付宝", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "走支付宝里的菜鸟小程序（不用装菜鸟 App），打开即到取件码/待取包裹页。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Button(
                        onClick = { AppJump.openAlipayCainiao(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("立即拉起支付宝·菜鸟取件码") }
                }
            }
        }

        // ---- ② 拼多多（首页 / 个人中心）----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("② 拼多多", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "身份码路径：个人中心 → 多多买菜 → 自提服务 → 我的身份码\n" +
                            "（K 开头取件码 = 亮身份码扫码出库）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { AppJump.openPddHome(context) },
                            modifier = Modifier.weight(1f),
                        ) { Text("拼多多首页") }
                        Button(
                            onClick = { AppJump.openPddPersonal(context) },
                            modifier = Modifier.weight(1f),
                        ) { Text("拼多多个人中心") }
                    }
                }
            }
        }

        // ---- ③ 快递单号查询 ----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("③ 快递单号查询", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = trackingNo,
                        onValueChange = {
                            trackingNo = it.trim()
                            if (result != null && result?.number != trackingNo) result = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("粘贴或输入快递单号") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { doQuery(trackingNo) },
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                        ) { Text("查询物流") }
                        OutlinedButton(
                            onClick = {
                                val nu = readClipboardText(context)?.let { extractTrackingNumber(it) }
                                if (nu.isNullOrBlank()) {
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )

                    if (history.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("查询历史", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { confirmClear = true }) { Text("清空") }
                        }
                        history.forEach { nu ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { trackingNo = nu; doQuery(nu) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("📦 $nu", style = MaterialTheme.typography.bodySmall) }
                                IconButton(onClick = {
                                    val h = history.filterNot { it.equals(nu, ignoreCase = true) }
                                    history = h
                                    writeHistory(prefs, h)
                                }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除单号 $nu",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }

                    if (loading) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("正在查询…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    result?.let { r ->
                        HorizontalDivider()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r.comName, style = MaterialTheme.typography.titleSmall)
                            if (r.kind == ExpressApi.Kind.OK) {
                                Text(
                                    r.stateText,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (r.number.isNotBlank()) {
                            Text(
                                "单号：${r.number}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        if (r.traces.isEmpty()) {
                            Text(
                                r.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.kind == ExpressApi.Kind.NETWORK) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                },
                            )
                        } else {
                            r.traces.forEachIndexed { i, t ->
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        t.time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        t.context,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                                if (i != r.traces.size - 1) HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空查询历史") },
            text = { Text("将删除全部已保存的单号（共 ${history.size} 条），删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    history = emptyList()
                    prefs.edit().remove(KEY_HISTORY).remove(KEY_HISTORY_LEGACY).apply()
                    confirmClear = false
                }) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}
