package com.campusglass.ui.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campusglass.ui.glass.AcrylicCard
import kotlinx.coroutines.launch

/** 农大常用网站（已核实可访问） */
private val schoolSites = listOf(
    "🏫 金山学院官网" to "http://jsxy.fafu.edu.cn/",
    "📝 金山学院教务系统（正方）" to "http://jsxyjwgl.fafu.edu.cn/",
    "🎓 福建农林大学官网" to "https://www.fafu.edu.cn/",
    "📚 福农大教务管理系统" to "http://jwgl.fafu.edu.cn/",
)

/**
 * 快捷打开数字FAFU（华为云 WeLink 白牌）。
 * Android 11+ 需 Manifest <queries> 声明包名才可见；再加查活动 + scheme 三级兜底。
 */
private fun openDigitalFafuApp(context: android.content.Context): Boolean {
    val pkg = "cn.edu.fafu.iportal"
    val pm = context.packageManager
    // 1) 标准启动
    runCatching {
        pm.getLaunchIntentForPackage(pkg)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
    }
    // 2) 机型 getLaunchIntentForPackage 失灵时：查 MAIN 活动显式拉起
    runCatching {
        val q = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN).setPackage(pkg), 0
        )
        q.firstOrNull()?.let {
            context.startActivity(
                android.content.Intent().setComponent(
                    android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        }
    }
    // 3) WeLink 系常见 scheme 探测
    for (uri in listOf("iportal://", "iportal://main", "cloudlink://", "welink://")) {
        val ok = runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                    .setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (ok) return true
    }
    return false
}

/** 课表查询链接（复制到微信打开） */
private const val JWC_SCHEDULE_URL =
    "http://jsxyjwc.fafu.edu.cn/index.html?code=021bBGFa1R7PuM0hQoHa1xvmph3bBGFd&state=STATE#/"

/** 请假模板（点击复制） */
private const val LEAVE_TEMPLATE = """x导你好！我是202x级xxx专业1班xxx的家长
学生姓名：xxx
请假时间：2026年4月3日xx点～2026年4月6日xx点xx分
请假原因：xx
具体去向地址：xx省xx市xx区xx街道x号xx室
请假外出期间也将配合学校做好孩子安全教育，请假结束按时返校！"""

/**
 * 首页（v4.2）三行布局：
 * 1️⃣ 每日一句 → 2️⃣ 数字FAFU（打卡/请假模板）→ 3️⃣ 常用官网
 */
@Composable
fun HomeScreen(onGoto: (String) -> Unit) {
    val context = LocalContext.current

    // A6：只有当天没拉过才刷新
    var quote by remember { mutableStateOf(com.campusglass.home.Hitokoto.cached(context)) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (com.campusglass.home.Hitokoto.needsFetch(context) || quote == null) {
            quote = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.campusglass.home.Hitokoto.fetchToday(context)
                    ?: com.campusglass.home.Hitokoto.cached(context)
            }
        }
    }

    fun copyText(label: String, text: String) {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制到剪贴板 ✅", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Text(
                "首页",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

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

                // ===== 1️⃣ 每日一句 =====
                AcrylicCard(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                        .clickable {
                            if (quote != null) {
                                copyText("每日一句", "「${quote!!.text}」 —— ${quote!!.from}")
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
                                textAlign = TextAlign.Start,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "—— ${quote!!.from}",
                                Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Text(
                                "点一下复制",
                                Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            )
                        } else {
                            Text(
                                "获取失败，稍后再试（点卡片可重试）",
                                Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val q = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.campusglass.home.Hitokoto.fetchNew(context)
                                    }
                                    if (q != null) quote = q
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("⟳ 换一句", style = MaterialTheme.typography.labelSmall) }
                    }
                }

                // ===== 2️⃣ 数字FAFU =====
                AcrylicCard(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "📱 数字FAFU",
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall,
                        )

                        // 快捷打开 App（已装直接开；未装引导去下载）
                        Button(
                            onClick = {
                                if (!openDigitalFafuApp(context)) {
                                    Toast.makeText(
                                        context,
                                        "未能拉起数字FAFU（可能未安装），已打开下载页面 ⬇️",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://m.fafu.edu.cn/"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("📲 快捷打开 数字FAFU") }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://m.fafu.edu.cn/"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("⬇️ 下载地址（m.fafu.edu.cn）") }

                        HorizontalDivider()

                        // 查看课表（复制到微信打开）
                        Text("📅 查看课表", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { copyText("课表链接", JWC_SCHEDULE_URL) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("📋 复制课表链接（去微信打开）") }
                        Text(
                            JWC_SCHEDULE_URL,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 2,
                        )
                        Text(
                            "复制到微信再点击打开查看课表，或者选择下方教务系统查看 👇",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )

                        HorizontalDivider()

                        // 打卡模块
                        Text("🌙 打卡模块", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text(
                            "本校正常会在 21 点后进行晚归签到，请及时进入「数字FAFU → 主页 → 学生管理」内进行签到 ✅",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "⚠️ 注意！！！若更换手机，需和辅导员联系在后台更新主设备，否则无法通过认证、无法签到！",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )

                        HorizontalDivider()

                        // 请假模块
                        Text("📝 请假模块", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text(
                            "👇 请假模板（点一下复制）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable { copyText("请假模板", LEAVE_TEMPLATE) },
                        ) {
                            Text(
                                LEAVE_TEMPLATE,
                                Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            "📌 短信自行发送至辅导员，并截图提交至「数字FAFU → 学生管理 → 请假申请材料」中；返校后请及时销假 🏠",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }

                // ===== 3️⃣ 常用官网 =====
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
}
