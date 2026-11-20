package com.campusglass.schedule

import android.graphics.BitmapFactory
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 教务课表导入页（v5.7）。
 *
 * 两种方式，都不需要用户离开 App：
 *  ① 账号登录：App 直接用 HttpURLConnection 走正方登录流程（验证码手动输入），抓 xskbcx.aspx。
 *  ② 网页登录：内嵌 WebView 让用户自己登录并进入课表页，点一下「抓取当前页」把 DOM 交回来解析。
 *
 * 两种方式的产物都是课表页 HTML，解析统一交给 [ZfsoftParser]，
 * 所以解析器只要对一处，两种入口同时受益。
 */
@Composable
fun ZfsoftImportScreen(
    onBack: () -> Unit,
    onResult: (ZfsoftParser.ParseResult, String) -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 2.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "从教务系统导入课表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("账号登录") })
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("网页登录") })
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            when (mode) {
                0 -> AccountMode(onResult)
                else -> WebMode(onResult)
            }
        }
    }
}

/** 方式一：账号直接登录（原生 HTTP，不经过 WebView） */
@Composable
private fun AccountMode(onResult: (ZfsoftParser.ParseResult, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { ZfCredentials.load(context) }

    var siteIdx by rememberSaveable {
        mutableIntStateOf((saved?.siteIndex ?: 0).coerceIn(0, ZfsoftClient.SITES.size - 1))
    }
    var studentId by rememberSaveable { mutableStateOf(saved?.studentId.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(saved?.password.orEmpty()) }
    var rememberMe by rememberSaveable { mutableStateOf(saved != null) }
    var code by rememberSaveable { mutableStateOf("") }
    var captcha by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    // 会话要跨「换验证码」保留：正方靠 URL 里的会话前缀维持登录态，重建会话等于重开一次登录
    var session by remember { mutableStateOf(ZfsoftClient.Session(ZfsoftClient.SITES[siteIdx])) }

    fun refreshCaptcha() {
        busy = true
        error = ""
        status = "正在连接教务系统…"
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { session.loadLoginPage() }
                val bmp = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
                captcha = bmp
                code = ""
                status = ""
                if (bmp == null) error = "验证码图片没解开，点图片重试一次"
            } catch (e: ZfsoftException) {
                error = e.message ?: "连接教务系统失败"
                status = ""
            } catch (e: Exception) {
                error = "连不上教务系统：校外访问可能需要连校园网 / 学校 VPN"
                status = ""
            } finally {
                busy = false
            }
        }
    }

    // 首次进入、切换站点 → 重开一个会话并取验证码
    LaunchedEffect(siteIdx) {
        session = ZfsoftClient.Session(ZfsoftClient.SITES[siteIdx])
        refreshCaptcha()
    }

    fun submit() {
        if (busy) return
        if (studentId.isBlank() || password.isBlank() || code.isBlank()) {
            error = "学号、密码、验证码都要填"
            return
        }
        busy = true
        error = ""
        status = "正在登录…"
        scope.launch {
            try {
                val name = withContext(Dispatchers.IO) { session.login(studentId, password, code) }
                status = "登录成功，正在读取课表…"
                val html = withContext(Dispatchers.IO) { session.fetchScheduleHtml(studentId, name) }
                val parsed = withContext(Dispatchers.Default) { ZfsoftParser.parse(html) }
                if (parsed.courses.isEmpty()) {
                    error = parsed.warnings.firstOrNull() ?: "课表页里没解析到课程"
                    status = ""
                    busy = false
                    refreshCaptcha()
                    return@launch
                }
                if (rememberMe) {
                    ZfCredentials.save(context, studentId.trim(), password, siteIdx)
                } else {
                    ZfCredentials.clear(context)
                }
                status = ""
                busy = false
                onResult(parsed, "账号登录")
            } catch (e: ZfsoftException) {
                error = e.message ?: "抓取失败"
                status = ""
                busy = false
                refreshCaptcha()   // 验证码一次性，失败后必须换一张
            } catch (e: Exception) {
                error = "抓取失败：" + (e.message ?: "网络异常")
                status = ""
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "用授课教师的那个教务账号直接登录。App 只在本机读取课表数据，不上传任何账号信息。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZfsoftClient.SITES.forEachIndexed { i, site ->
                FilterChip(
                    selected = siteIdx == i,
                    onClick = { if (!busy) siteIdx = i },
                    label = { Text(site.label) },
                )
            }
        }
        OutlinedTextField(
            value = studentId,
            onValueChange = { studentId = it.trim() },
            label = { Text("学号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter { c -> c.isLetterOrDigit() }.take(6) },
                label = { Text("验证码") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(width = 108.dp, height = 44.dp)
                    .clickable(enabled = !busy) { refreshCaptcha() },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = captcha
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "验证码，点一下换一张",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(width = 108.dp, height = 44.dp),
                    )
                } else {
                    Text(
                        if (busy) "加载中…" else "点这里加载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(checked = rememberMe, onCheckedChange = { rememberMe = it })
            Column(Modifier.weight(1f)) {
                Text("记住账号密码", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "用系统 Keystore 加密存在本机（换机 / 卸载即失效）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("登录并导入课表")
            }
            OutlinedButton(onClick = { refreshCaptcha() }, enabled = !busy) { Text("换验证码") }
        }
        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    status.ifBlank { "处理中…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (error.isNotBlank()) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "导入后是「预览 → 选择合并或替换」，不会直接覆盖现有课表。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 方式二：内嵌 WebView 自己登录，抓当前页 DOM（不碰学生的密码） */
@Composable
private fun WebMode(onResult: (ZfsoftParser.ParseResult, String) -> Unit) {
    var siteIdx by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    // 不用 State 存 WebView：它只给按钮用，不需要触发重组，也更不容易踩快照写入限制
    val webViewRef = remember { java.util.concurrent.atomic.AtomicReference<WebView?>(null) }

    fun grab() {
        val wv = webViewRef.get()
        if (wv == null) {
            error = "网页还没加载好，稍等一下再点"
            return
        }
        busy = true
        error = ""
        wv.evaluateJavascript(
            "(function(){try{return document.documentElement.outerHTML;}catch(e){return '';}})()",
        ) { raw ->
            val html = decodeJsString(raw)
            val parsed = ZfsoftParser.parse(html)
            busy = false
            if (parsed.courses.isEmpty()) {
                error = parsed.warnings.firstOrNull()
                    ?: "当前页面里没有课表，请先登录并进入「学生个人课表」页面"
            } else {
                onResult(parsed, "网页登录")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "① 在下面的网页里登录教务系统　② 打开「学生个人课表 / 信息查询 → 学生课表」　" +
                "③ 点最下面的按钮把当前页抓回来解析。整个过程 App 不接触你的密码。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZfsoftClient.SITES.forEachIndexed { i, site ->
                FilterChip(
                    selected = siteIdx == i,
                    onClick = {
                        if (busy) return@FilterChip
                        siteIdx = i
                        error = ""
                        webViewRef.get()?.loadUrl(site.base + "/")
                    },
                    label = { Text(site.label) },
                )
            }
            TextButton(onClick = { webViewRef.get()?.reload() }) { Text("刷新") }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(ZfsoftClient.SITES[siteIdx].base + "/")
                        webViewRef.set(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (error.isNotBlank()) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { grab() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("我已登录，抓取当前页课表")
                }
            }
        }
    }
}

/** evaluateJavascript 的回调值是 JSON 字符串字面量，这里把它还原成真正的 HTML 文本 */
private fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching { org.json.JSONArray("[" + raw + "]").getString(0) }.getOrDefault("")
}
