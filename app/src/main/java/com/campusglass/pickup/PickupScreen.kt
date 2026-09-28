package com.campusglass.pickup

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.campusglass.ui.widgets.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 取件页：① 支付宝·菜鸟 ② 拼多多身份码（自动进入） ③ 快递单号查询（API 直出 + 历史）。
 */
@Composable
fun PickupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("express", Context.MODE_PRIVATE) }

    var trackingNo by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExpressApi.Result?>(null) }
    var history by remember {
        mutableStateOf(prefs.getStringSet("history", emptySet())?.toList().orEmpty())
    }
    var showWxDialog by remember { mutableStateOf(false) }

    fun doQuery(nu: String) {
        if (nu.isBlank()) {
            Toast.makeText(context, "先输入快递单号", Toast.LENGTH_SHORT).show()
            return
        }
        val h = (listOf(nu) + history.filter { it != nu }).take(10)
        history = h
        prefs.edit().putStringSet("history", h.toSet()).apply()
        loading = true
        result = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { ExpressApi.query(nu) }
            result = r
            loading = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("取件码 · 快递") }

        // ---- ① 支付宝 · 菜鸟 ----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
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

        // ---- ② 拼多多身份码（自动进入）----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("② 拼多多 · 身份码/包裹页", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "一键自动进入（App 内直接打开身份码/包裹页，无需手动操作）；" +
                            "失败时可选复制链接去微信打开。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Button(
                        onClick = { showWxDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🚀 自动进入身份码页（推荐）") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { AppJump.openPddWeChatPackage(context) },
                            modifier = Modifier.weight(1f),
                        ) { Text("复制链接去微信") }
                        OutlinedButton(
                            onClick = { AppJump.openPddWeChatInBrowser(context) },
                            modifier = Modifier.weight(1f),
                        ) { Text("浏览器打开") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { AppJump.openPddPersonal(context) },
                            modifier = Modifier.weight(1f),
                        ) { Text("拼多多个人中心") }
                        OutlinedButton(
                            onClick = { PddLauncher.openApp(context, AppJump.PDD_PKG) },
                            modifier = Modifier.weight(1f),
                        ) { Text("仅打开拼多多") }
                    }
                    Text(
                        "身份码路径备忘：个人中心 → 多多买菜 → 自提服务 → 我的身份码（K 开头取件码 = 亮身份码扫码出库）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }

        // ---- ③ 快递单号查询 ----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
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
                            modifier = Modifier.weight(1f),
                        ) { Text("查询物流") }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                                val text = cm.primaryClip?.getItemAt(0)
                                    ?.coerceToText(context)?.toString().orEmpty()
                                val nu = Regex("[A-Za-z0-9]{8,}").find(text.replace(" ", ""))?.value.orEmpty()
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

                    // 历史记录
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

    if (showWxDialog) {
        WeChatPackageDialog(onDismiss = { showWxDialog = false })
    }
}

/**
 * 微信身份码自动进入：App 内嵌浏览器以微信 UA 打开 OAuth 链接，
 * 自动走完微信授权 → 拼多多驿站包裹/身份码页，无需用户手动粘贴。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WeChatPackageDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val webViewRef = remember { mutableStateOf<WebView?>(null) }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "拼多多身份码 · 自动进入",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { webViewRef.value?.reload() }) { Text("刷新") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 12; Mobile) MicroMessenger/8.0.42.2460(0x28002A35) " +
                                    "WeChat/arm64 NetType/WIFI Language/zh_CN"
                            webViewClient = WebViewClient()
                            loadUrl(AppJump.PDD_WECHAT_PACKAGE_URL)
                            webViewRef.value = this
                        }
                    },
                )
            }
        }
    }
}
