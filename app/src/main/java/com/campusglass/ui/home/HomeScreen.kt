package com.campusglass.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.campusglass.ui.glass.AcrylicCard
import com.campusglass.ui.widgets.ScreenHeader

/** 农大常用网站（已核实可访问） */
private val schoolSites = listOf(
    "🏫 金山学院官网" to "http://jsxy.fafu.edu.cn/",
    "📝 金山学院教务系统（正方）" to "http://jsxyjwgl.fafu.edu.cn/",
    "🎓 福建农林大学官网" to "https://www.fafu.edu.cn/",
    "📚 福农大教务管理系统" to "http://jwgl.fafu.edu.cn/",
)

/**
 * 首页（v3 稳定版）：仅保留常用官网，整体居中。
 */
@Composable
fun HomeScreen(onGoto: (String) -> Unit) {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        // 标题保留
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            ScreenHeader("首页")
        }
        // 官网区域整体居中（占据剩余空间）
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
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
        }
    }
}
