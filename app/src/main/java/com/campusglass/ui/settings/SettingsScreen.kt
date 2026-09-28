package com.campusglass.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusglass.BuildConfig
import com.campusglass.ui.theme.ThemePrefs
import com.campusglass.ui.widgets.ScreenHeader
import java.io.File

private data class Credit(val name: String, val author: String, val url: String, val license: String, val usage: String)

private val credits = listOf(
    Credit("WakeUp 课程表（WakeupSchedule_Kotlin）", "YZune", "https://github.com/YZune/WakeUpSchedule", "Apache-2.0", "间接参考：周课表格 UI 设计思路（未复制源码）"),
    Credit("ling_QuickShortcut（快递身份码快捷方式）", "qinyuanxin132", "https://github.com/qinyuanxin132/ling_QuickShortcut", "MIT", "间接参考：拼多多【身份码】跳转思路（未复制源码）"),
    Credit("拼多多 Scheme 公开资料", "社区整理（CSDN）", "https://blog.csdn.net/weixin_48141487/article/details/140077257", "公开资料 / 合理引用", "间接参考：拼多多页面拉起路径"),
    Credit("支付宝菜鸟小程序取件码", "V2EX 社区实测", "https://v2ex.com/t/1002900", "公开资料 / 合理引用", "取件码直达：alipays 小程序 appId"),
    Credit("建阳公交线路通告", "武夷发展集团·建阳区公交公司（大武夷新闻网）", "https://www.greatwuyi.com/guangg/content/202508/27/c1555764.html", "公开资讯 / 合理引用", "公交数据：103/105/107 路站点与时刻"),
    Credit("无敌电动公交数据", "modiauto.com.cn", "https://www.modiauto.com.cn/cx/bus_142302.html", "公开资讯 / 合理引用", "公交路线图截图与发车时刻核实（2026-08/09）"),
    Credit("快递100", "深圳前海百递网络", "https://www.kuaidi100.com", "平台服务（查询接口）", "快递物流轨迹查询接口"),
    Credit("Jetpack Compose / AndroidX", "Google & AOSP", "https://android.googlesource.com/platform/frameworks/support", "Apache-2.0", "直接依赖：Material 3 UI、原生动画"),
    Credit("Kotlin", "JetBrains", "https://github.com/JetBrains/kotlin", "Apache-2.0", "直接依赖：开发语言"),
)

private val changelog = listOf(
    "v2.15" to "路线图改为真实地图截图；首页新增农大常用网站；新增设置页（自定义背景/主题色），关于内容并入设置页",
    "v2.14" to "公交路线图内置；发车时刻分时段排版；保留邮箱仅不公开 QQ 号",
    "v2.13" to "移除 QQ 号与 QQ 邮箱（隐私保护）；含 v2.12 全部更新",
    "v2.12" to "拼多多身份码微信入口（含驿站点）；课表学期校准（9/28=第五周）；公交时刻核实补齐",
    "v2.11" to "快递查询历史记录；关于页新增作者信息与代码归属说明",
    "v2.10" to "快递查询 API 直出物流轨迹",
    "v2.9" to "快递 App 内显示；读取剪贴板快捷填单号",
    "v2.8" to "公交去高德；UI 过渡动画；版本日志折叠",
    "v2.7" to "关于页精简；电商只留菜鸟/拼多多；快递单号查询上线",
    "v2.2-v2.6" to "底部功能栏/公交时刻筛选/课表手动模式/应用检测权限化等（详见 CHANGELOG.md）",
    "v1.x-v2.1" to "初版到原生 Material 化（历史版本，详见 CHANGELOG.md）",
)

/** 设置页：外观自定义 + 关于/致谢/版本日志 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val pickBgImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val file = File(context.filesDir, "bg_image.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                ThemePrefs.setBgImage(context, file.absolutePath)
                ThemePrefs.setBgMode(context, ThemePrefs.BgMode.IMAGE)
                Toast.makeText(context, "背景已更换 ✅", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "背景设置失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("设置") }

        // ---- 外观：背景 ----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🎨 软件背景", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePrefs.BgMode.entries.take(4).forEach { m ->
                            FilterChip(
                                selected = ThemePrefs.bgMode.value == m,
                                onClick = { ThemePrefs.setBgMode(context, m) },
                                label = { Text(m.label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = ThemePrefs.bgMode.value == ThemePrefs.BgMode.IMAGE,
                            onClick = {
                                if (ThemePrefs.bgImagePath.value.isBlank()) {
                                    pickBgImage.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else {
                                    ThemePrefs.setBgMode(context, ThemePrefs.BgMode.IMAGE)
                                }
                            },
                            label = { Text("自定义图片") },
                        )
                        OutlinedButton(onClick = {
                            pickBgImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) { Text("选择图片…") }
                    }
                    Text(
                        "自定义图片会裁切铺满全屏，随时可切回默认。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // ---- 外观：主题色 ----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🎯 主题色", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemePrefs.THEME_COLORS.forEachIndexed { i, (label, color) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { ThemePrefs.setColor(context, i) },
                            ) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (ThemePrefs.themeColorIndex.value == i)
                                                Modifier.background(
                                                    Color.Transparent, CircleShape
                                                )
                                            else Modifier
                                        )
                                )
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // ---- 关于本项目 ----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "校园助手-FAFUer专用 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "取件码快捷跳转（支付宝·菜鸟 / 拼多多）· 快递单号查询 · 手动课表 · 南平校区公交",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "全部代码由 AI 助手（Mimo v2.6 / OpenClaw 编程助手）独立编写；" +
                            "需求设计、功能构想、提示词与测试反馈由 Void_Blank 提供。人出想法，AI 写代码。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // ---- 联系 ----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📮 联系 · 交流 · 反馈", style = MaterialTheme.typography.titleMedium)
                    Text("GitHub：Void_Blank（Blank2007）", style = MaterialTheme.typography.bodyMedium)
                    Text("邮箱：1553008865@qq.com", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Blank2007/fafu-campus-assistant"))
                            )
                        }
                    }) { Text("开源仓库：Blank2007/fafu-campus-assistant") }
                    Text(
                        "非常欢迎交流与反馈！不管是发现 Bug、想要新功能，还是想聊聊实现，" +
                            "都欢迎通过 GitHub 或邮件找我。你的每条建议都可能出现在下一个版本里 🚀",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ---- 版本日志 ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📋 版本修改日志", style = MaterialTheme.typography.titleMedium)
                changelog.first().let { (ver, desc) ->
                    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "$ver（当前版本）",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(desc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起历史版本 ▴" else "查看历史版本修改 ▾")
                }
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        changelog.drop(1).forEach { (ver, desc) ->
                            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(ver, style = MaterialTheme.typography.titleSmall)
                                    Text(desc, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 致谢 ----
        item {
            Text("🙏 致谢（借用 / 参考的开源项目与数据来源）", style = MaterialTheme.typography.titleMedium)
        }
        items(credits) { c ->
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text("作者：${c.author} · 许可：${c.license}", style = MaterialTheme.typography.bodySmall)
                    Text(c.usage, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url)))
                        }
                    }) { Text(c.url, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("致谢声明（详细）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. 直接依赖：Jetpack Compose / AndroidX、Kotlin —— Apache-2.0，版权归 Google、JetBrains，分发时保留原始许可证与署名。\n\n" +
                            "2. 间接参考：WakeUp 课程表、ling_QuickShortcut 等 —— 仅借鉴公开设计思路与跳转方案，未复制源码，著作权归原作者所有。\n\n" +
                            "3. 公开资料与数据：公交数据引自建阳公交官方通告与无敌电动公开数据（路线图截自其公开线路图，如有异议请联系撤下）；" +
                            "快递轨迹由快递100 提供查询接口；拼多多/支付宝跳转路径引自社区公开资料。\n\n" +
                            "4. 本项目免费开源，仅供学习交流使用。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
