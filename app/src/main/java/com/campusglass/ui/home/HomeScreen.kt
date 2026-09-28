package com.campusglass.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campusglass.ui.widgets.ScreenHeader

/** 农大常用网站（已核实可访问） */
private val schoolSites = listOf(
    "福建农林大学官网" to "https://www.fafu.edu.cn/",
    "金山学院官网" to "http://jsxy.fafu.edu.cn/",
    "金山学院教务系统（正方）" to "http://jsxyjwgl.fafu.edu.cn/",
    "福农大教务管理系统" to "http://jwgl.fafu.edu.cn/",
)

/** 首页：整体居中 + 常用网站直达 */
@Composable
fun HomeScreen(onGoto: (String) -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("校园助手", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Text(
                    "FAFUer 专用",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "取件码快捷跳转 · 快递查询 · 手动课表 · 南平校区公交",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
            }
        }

        // 快捷功能
        item {
            Button(onClick = { onGoto("pickup") }, modifier = Modifier.fillMaxWidth()) {
                Text("📦 取件码 · 快递查询")
            }
        }
        item {
            OutlinedButton(onClick = { onGoto("schedule") }, modifier = Modifier.fillMaxWidth()) {
                Text("📚 课表 · 手动添加")
            }
        }
        item {
            OutlinedButton(onClick = { onGoto("bus") }, modifier = Modifier.fillMaxWidth()) {
                Text("🚌 公交 · 南平校区")
            }
        }
        item {
            OutlinedButton(onClick = { onGoto("about") }, modifier = Modifier.fillMaxWidth()) {
                Text("⚙️ 设置 · 关于")
            }
        }

        // ---- 农大资讯 / 常用网站 ----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏫 农大资讯 · 常用网站", style = MaterialTheme.typography.titleMedium)
                    schoolSites.forEach { (name, url) ->
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(name) }
                    }
                    Text(
                        "点击在浏览器打开；网站均可正常访问（已核实）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}
