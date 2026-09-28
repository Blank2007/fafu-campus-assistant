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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusglass.BuildConfig
import com.campusglass.schedule.PeriodTable
import com.campusglass.ui.theme.ThemePrefs
import com.campusglass.ui.glass.AcrylicCard
import com.campusglass.ui.widgets.ScreenHeader
import java.io.File

private data class Credit(val name: String, val author: String, val url: String, val license: String, val usage: String)

private val credits = listOf(
    Credit("WakeUp 课程表（WakeupSchedule_Kotlin）", "YZune", "https://github.com/YZune/WakeUpSchedule", "Apache-2.0", "间接参考：周课表格 UI 设计思路（未复制源码）"),
    Credit("ling_QuickShortcut（快递身份码快捷方式）", "qinyuanxin132", "https://github.com/qinyuanxin132/ling_QuickShortcut", "MIT", "间接参考：拼多多【身份码】跳转思路"),
    Credit("拼多多 Scheme 公开资料", "社区整理（CSDN）", "https://blog.csdn.net/weixin_48141487/article/details/140077257", "公开资料 / 合理引用", "间接参考：拼多多页面拉起路径"),
    Credit("支付宝菜鸟小程序取件码", "V2EX 社区实测", "https://v2ex.com/t/1002900", "公开资料 / 合理引用", "取件码直达：alipays 小程序 appId"),
    Credit("建阳公交线路通告", "武夷发展集团·建阳区公交公司（大武夷新闻网）", "https://www.greatwuyi.com/guangg/content/202508/27/c1555764.html", "公开资讯 / 合理引用", "公交数据：103/105/107 路站点与时刻"),
    Credit("无敌电动公交数据", "modiauto.com.cn", "https://www.modiauto.com.cn/cx/bus_142302.html", "公开资讯 / 合理引用", "公交发车时刻核实（2026-08/09）"),
    Credit("Haze（亚克力模糊）", "Chris Banes", "https://github.com/chrisbanes/haze", "Apache-2.0", "直接依赖：底部悬浮栏亚克力模糊材质"),
    Credit("快递100", "深圳前海百递网络", "https://www.kuaidi100.com", "平台服务（查询接口）", "快递物流轨迹查询接口"),
    Credit("Jetpack Compose / AndroidX", "Google & AOSP", "https://android.googlesource.com/platform/frameworks/support", "Apache-2.0", "直接依赖：Material 3 UI、原生动画"),
    Credit("Kotlin", "JetBrains", "https://github.com/JetBrains/kotlin", "Apache-2.0", "直接依赖：开发语言"),
)

/** 版本修改日志（精确到分钟） */
private val changelog = listOf(
    "v2.17 · 2026-09-28 23:59" to "课表大改版：周日起算、一节一行、第三行显示教师、分段周次（2-5,7-8）、同名课多时段、高对比色块、添加界面重做；节次时间/每天节数可自定义；全局亚克力（仅自定义背景时启用）；底部悬浮栏边距适配圆角与手势条；拼多多只留首页/个人中心",
    "v2.16 · 2026-09-28 23:35" to "深色模式开关；悬浮亚克力底栏；异形屏全屏背景适配；公交站点竖排；快递多公司兼容并标注支持范围；微信身份码自动进入；背景只留自定义；个性化（课表字号/显示周末）；日志精确到分钟",
    "v2.15 · 2026-09-28 23:00" to "路线图改地图截图；首页农大常用网站；设置页（自定义背景/主题色）",
    "v2.14 · 2026-09-28 00:29" to "公交路线图内置；发车时刻分时段排版；保留邮箱仅不公开 QQ 号",
    "v2.13 · 2026-09-28 00:16" to "移除 QQ 号与 QQ 邮箱（隐私）；含 v2.12 全部更新",
    "v2.12 · 2026-09-27 23:56" to "拼多多身份码微信入口（含驿站点）；课表学期校准（9/28=第五周）；公交时刻核实补齐",
    "v2.11 · 2026-09-27 00:16" to "快递查询历史记录；关于页新增作者信息与代码归属说明",
    "v2.10 · 2026-09-27 00:00" to "快递查询 API 直出物流轨迹",
    "v2.9 · 2026-09-26 23:30" to "快递 App 内显示；读取剪贴板快捷填单号",
    "v2.8 · 2026-09-26 23:22" to "公交去高德改掌上公交提示；UI 过渡动画；版本日志折叠",
    "v2.7 · 2026-09-26 23:11" to "关于页精简；电商只留菜鸟/拼多多；快递单号查询上线",
    "v2.2-v2.6 · 2026-09-26 21:45~23:05" to "底部功能栏/公交时刻筛选/课表手动模式/应用检测权限化等（详见 CHANGELOG.md）",
    "v1.x-v2.1 · 2026-09-25 23:45 起" to "初版到原生 Material 化（历史版本，详见 CHANGELOG.md）",
)

/** 设置页：外观 / 个性化 / 关于 / 致谢 / 版本日志 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }

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

        // ---- 深色模式 ----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("🌙 深色模式", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "开启后全局使用深色配色（当前为可选开关，按需启用）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = ThemePrefs.darkMode.value,
                        onCheckedChange = { ThemePrefs.setDark(context, it) },
                    )
                }
            }
        }

        // ---- 背景（只保留自定义）----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🎨 软件背景", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                pickBgImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("选择自定义背景图") }
                        OutlinedButton(
                            onClick = {
                                ThemePrefs.setBgImage(context, "")
                                Toast.makeText(context, "已恢复默认背景", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("恢复默认") }
                    }
                    Text(
                        if (ThemePrefs.bgImagePath.value.isBlank()) "当前：默认背景"
                        else "当前：自定义图片（裁切铺满全屏，含异形屏）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // ---- 主题色 ----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
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
                                )
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (ThemePrefs.themeColorIndex.value == i) FontWeight.Bold
                                    else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- 个性化 ----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⚙️ 个性化", style = MaterialTheme.typography.titleMedium)
                    Text("课表字号", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("小", "中", "大").forEachIndexed { i, t ->
                            FilterChip(
                                selected = ThemePrefs.scheduleFontScale.value == i,
                                onClick = { ThemePrefs.setFontScale(context, i) },
                                label = { Text(t) },
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("课表显示周六/周日", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = ThemePrefs.showWeekend.value,
                            onCheckedChange = { ThemePrefs.setShowWeekend(context, it) },
                        )
                    }
                    OutlinedButton(
                        onClick = { showTimeDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("⏰ 节次时间设置 / 每天上课节数") }
                    Text(
                        "更多个性化持续增加中（欢迎提建议）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }

        // ---- 关于本项目 ----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
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
            AcrylicCard(Modifier.fillMaxWidth()) {
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
                    AcrylicCard(Modifier.fillMaxWidth()) {
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
                            AcrylicCard(Modifier.fillMaxWidth()) {
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
            AcrylicCard(Modifier.fillMaxWidth()) {
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
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("致谢声明（详细）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. 直接依赖：Haze、Jetpack Compose / AndroidX、Kotlin —— 各自许可证见上表，分发时保留原始许可证与署名。\n\n" +
                            "2. 间接参考：WakeUp 课程表、ling_QuickShortcut 等 —— 仅借鉴公开思路，未复制源码，著作权归原作者所有。\n\n" +
                            "3. 公开资料与数据：公交数据引自建阳公交官方通告与无敌电动公开数据；快递轨迹由快递100 提供查询接口；" +
                            "拼多多/支付宝跳转路径引自社区公开资料。\n\n" +
                            "4. 本项目免费开源，仅供学习交流使用。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showTimeDialog) {
        PeriodTimesDialog(onDismiss = { showTimeDialog = false })
    }
}

/** 节次时间设置：每节上课/结束时间可改 + 每天节数自定义 */
@Composable
private fun PeriodTimesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val times = remember { mutableStateOf(PeriodTable.all(context)) }
    val perDay by remember { mutableIntStateOf(PeriodTable.periodsPerDay(context)) }
    var perDayState by remember { mutableIntStateOf(perDay) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏰ 节次时间设置") },
        text = {
            Column(
                Modifier
                    .height(440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("每天上课节数", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (4..11).forEach { n ->
                        FilterChip(
                            selected = perDayState == n,
                            onClick = { perDayState = n },
                            label = { Text("$n") },
                        )
                    }
                }
                HorizontalDivider()
                times.value.forEachIndexed { i, t ->
                    val start = remember(t) { mutableStateOf(t.substringBefore("-")) }
                    val end = remember(t) { mutableStateOf(t.substringAfter("-")) }
                    Text("第${i + 1}节", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(start.value, { v: String ->
                            start.value = v
                            times.value = times.value.toMutableList().also { it[i] = "${start.value}-${end.value}" }
                        }, Modifier.weight(1f), singleLine = true, label = { Text("上课") })
                        OutlinedTextField(end.value, { v: String ->
                            end.value = v
                            times.value = times.value.toMutableList().also { it[i] = "${start.value}-${end.value}" }
                        }, Modifier.weight(1f), singleLine = true, label = { Text("下课") })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                PeriodTable.save(context, times.value)
                PeriodTable.setPeriodsPerDay(context, perDayState)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
