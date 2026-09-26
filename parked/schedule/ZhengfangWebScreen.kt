package com.campusglass.schedule

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.campusglass.ui.widgets.ScreenHeader

/** 福建农林大学金山学院 正方教务（旧版）学生课表入口 */
private const val DEFAULT_URL = "http://jsxyjwgl.fafu.edu.cn/jsxsd/xskbcx.aspx"

/**
 * 正方教务网页导入：内置 WebView（IE 兼容 UA）打开课表页，
 * 登录进"学生课表"后一键抓取 HTML → ZhengfangParser 解析 → 保存。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ZhengfangWebScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(DEFAULT_URL) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("正方教务 · 网页抓取", onBack)

        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("教务系统课表页地址") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { webView?.loadUrl(url) },
                        modifier = Modifier.weight(1f),
                    ) { Text("前往") }
                    Button(
                        onClick = {
                            webView?.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                val html = raw?.trim('"')
                                    ?.replace("\\n", "\n")
                                    ?.replace("\\\"", "\"")
                                    ?.replace("\\/", "/")
                                    ?.replace("\\t", "\t")
                                    ?.replace("&nbsp;", " ")
                                    .orEmpty()
                                val r = ZhengfangParser.parse(html)
                                if (r.courses.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        r.warnings.firstOrNull() ?: "解析失败，请先登录并打开“学生课表”页",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    ScheduleStore.saveCourses(context, r.courses)
                                    Toast.makeText(
                                        context,
                                        "抓取成功：${r.courses.size} 条课程",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    onBack()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("抓取并解析课表") }
                }
                Text(
                    "默认地址为金山学院正方教务旧版课表入口（jsxyjwgl.fafu.edu.cn）；" +
                        "若学校已升级新版正方，把地址换成你课表页 URL 再抓取即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        Card(
            Modifier
                .fillMaxWidth()
                .height(460.dp),
            elevation = CardDefaults.cardElevation(2.dp),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // 老 IE 兼容 UA：正方这类老页面按 UA 分流，报 IE 渲染完整可点版
                        settings.userAgentString =
                            "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.1; Trident/4.0)"
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.textZoom = 100
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        setInitialScale(140)
                        webViewClient = WebViewClient()
                        loadUrl(DEFAULT_URL)
                        webView = this
                    }
                },
            )
        }
    }
}
