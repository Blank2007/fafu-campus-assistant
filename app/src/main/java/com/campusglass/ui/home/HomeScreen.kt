package com.campusglass.ui.home
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campusglass.ui.glass.AcrylicCard

/** 农大常用网站（已核实可访问） */
private val schoolSites = listOf(
    "🏫 金山学院官网" to "http://jsxy.fafu.edu.cn/",
    "📝 金山学院教务系统（正方）" to "http://jsxyjwgl.fafu.edu.cn/",
    "🎓 福建农林大学官网" to "https://www.fafu.edu.cn/",
    "📚 福农大教务管理系统" to "http://jwgl.fafu.edu.cn/",
)

/**
 * 首页（v3.9）：「首页」标题 + 居中板块（官网卡片 + 每日一句卡片）。
 * 每日一句每次打开自动刷新；点击复制。
 */
@Composable
fun HomeScreen(onGoto: (String) -> Unit) {
    val context = LocalContext.current

    // A6：只有当天没拉过才刷新（不再每次进首页都强刷）
    var quote by remember { mutableStateOf(com.campusglass.home.Hitokoto.cached(context)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (com.campusglass.home.Hitokoto.needsFetch(context) || quote == null) {
            quote = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.campusglass.home.Hitokoto.fetchToday(context)
                    ?: com.campusglass.home.Hitokoto.cached(context)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 标题保留
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Text(
                "首页",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 板块整体居中（内容超高时可滚动）
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 官网卡片
                AcrylicCard(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🏫 常用官网", style = MaterialTheme.typography.titleMedium)
                        schoolSites.forEach { (name, url) ->
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(name)
                                    Text(
                                        url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                        Text(
                            "点击在浏览器打开（已核实可访问）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }

                // 每日一句卡片：标题居中 · 正文靠左 · 来源靠右 · 提示居中 · 点击复制
                AcrylicCard(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                        .clickable {
                            if (quote != null) {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "每日一句", "「${quote!!.text}」 —— ${quote!!.from}"
                                    )
                                )
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        },
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "📜 每日一句",
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (quote != null) {
                            Text(
                                "「${quote!!.text}」",
                                Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,          // 正文靠左
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "—— ${quote!!.from}",
                                Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,            // 来源另起一行靠右
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Text(
                                "点一下复制",
                                Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,         // 提示居中
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    val q = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.campusglass.home.Hitokoto.fetchNew(context)
                                    }
                                    if (q != null) quote = q
                                }
                            }) {
                                Text("⟳ 换一句", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Text(
                                "获取失败，稍后再试（点卡片可重试）",
                                Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}
