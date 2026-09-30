package com.campusglass.ui.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusglass.BuildConfig
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
 * 首页（v3.3）：新增「这是什么 App」的介绍卡片 + 三个快捷入口，
 * 剩余空间放常用官网；整页可滚动（旧版内容不可滚动，小屏/大字号下会被裁掉）。
 */
@Composable
fun HomeScreen(onGoto: (String) -> Unit) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "没有可打开该链接的应用（$url）", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("首页")

        // ---- 介绍 ----
        AcrylicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "校园助手 · FAFUer 专用",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "为福建农林大学（金山 / 南平校区）同学准备的小工具集合：",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "① 取件码快捷跳转：一键拉起支付宝·菜鸟取件码 / 拼多多身份码\n" +
                        "② 快递单号查询：自动识别快递公司，App 内直出物流轨迹\n" +
                        "③ 手动课表：周日起算、分段周次、跨节占格、节假日提示\n" +
                        "④ 南平校区公交：103/105/107/K2/K1 站点与时刻参考",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Text(
                    "免费开源 · 无广告 · 不收集账号密码；课表与查询历史只存在本机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    "当前版本 v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ---- 快捷入口 ----
        AcrylicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("⚡ 快捷入口", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onGoto("pickup") }, modifier = Modifier.weight(1f)) {
                        Text("取件码")
                    }
                    OutlinedButton(onClick = { onGoto("schedule") }, modifier = Modifier.weight(1f)) {
                        Text("课表")
                    }
                    OutlinedButton(onClick = { onGoto("bus") }, modifier = Modifier.weight(1f)) {
                        Text("公交")
                    }
                }
            }
        }

        // ---- 常用官网 ----
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
                        onClick = { openUrl(url) },
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

        Spacer(Modifier.height(4.dp))
    }
}
