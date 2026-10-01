package com.campusglass.pickup
import androidx.compose.foundation.layout.imePadding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
 */
@Composable
fun PickupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("express", Context.MODE_PRIVATE) }

    var trackingNo by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExpressApi.Result?>(null) }
    var resultNo by remember { mutableStateOf("") }   // PK-2：结果绑定单号，防串号
    var history by remember {
        // PK-6：有序存储（旧 Set 顺序随机）+ 不区分大小写去重
        mutableStateOf(
            prefs.getString("history2", "")?.split("\n")?.filter { it.isNotBlank() }
                ?: prefs.getStringSet("history", emptySet())?.toList().orEmpty()
        )
    }

    fun doQuery(nuRaw: String) {
        if (loading) return                      // PK-2：查询中禁止重入
        val nu = ExpressApi.normalize(nuRaw)
        if (nu.isBlank()) {
            Toast.makeText(context, "先输入快递单号", Toast.LENGTH_SHORT).show()
            return
        }
        val h = (listOf(nu) + history.filter { !it.equals(nu, true) }).take(10)
        history = h
        prefs.edit().putString("history2", h.joinToString("\n")).apply()
        loading = true
        result = null
        resultNo = nu
        scope.launch {
            val r = withContext(Dispatchers.IO) { ExpressApi.query(nu) }
            if (resultNo == nu) {                // PK-2：只展示最后发起的那次查询
                result = r
                loading = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),   // UI-4：键盘不遮输入框/结果
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
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("alipays://platformapi/startapp?appId=2021001141626787")
                                    ).setPackage(AppJump.ALIPAY_PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure {
                                PddLauncher.openApp(context, AppJump.ALIPAY_PKG)
                            }
                        },
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
                        onValueChange = { trackingNo = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("粘贴或输入快递单号") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { doQuery(trackingNo) },
                            enabled = !loading,          // PK-2：查询中禁用
                            modifier = Modifier.weight(1f),
                        ) { Text(if (loading) "查询中…" else "查询物流") }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                                val text = cm.primaryClip?.getItemAt(0)
                                    ?.coerceToText(context)?.toString().orEmpty()
                                // PK-5：归一化 + 取最长字母数字段（不再把网址当单号）
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
                            TextButton(onClick = {
                                history = emptyList()
                                prefs.edit().remove("history").apply()
                            }) { Text("清空") }
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
                                TextButton(onClick = {
                                    val h = history.filter { it != nu }
                                    history = h
                                    prefs.edit().putStringSet("history", h.toSet()).apply()
                                }) { Text("✕") }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(r.comName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                r.stateText,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (r.traces.isEmpty()) {
                            Text(r.message, style = MaterialTheme.typography.bodySmall)
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
    }
}
