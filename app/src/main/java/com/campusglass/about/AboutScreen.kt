package com.campusglass.about

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusglass.BuildConfig
import com.campusglass.ui.widgets.ScreenHeader

private data class Credit(
    val name: String,
    val author: String,
    val url: String,
    val license: String,
    val usage: String,
)

private val credits = listOf(
    Credit(
        "WakeUp 课程表（WakeupSchedule_Kotlin）", "YZune",
        "https://github.com/YZune/WakeUpSchedule", "Apache-2.0",
        "间接参考：周课表格 UI 设计思路（未复制源码）",
    ),
    Credit(
        "ling_QuickShortcut（快递身份码快捷方式）", "qinyuanxin132",
        "https://github.com/qinyuanxin132/ling_QuickShortcut", "MIT",
        "间接参考：拼多多【身份码】跳转思路（未复制源码）",
    ),
    Credit(
        "拼多多 Scheme 公开资料", "社区整理（CSDN）",
        "https://blog.csdn.net/weixin_48141487/article/details/140077257", "公开资料 / 合理引用",
        "间接参考：拼多多页面拉起路径",
    ),
    Credit(
        "支付宝菜鸟小程序取件码", "V2EX 社区实测",
        "https://v2ex.com/t/1002900", "公开资料 / 合理引用",
        "取件码直达：alipays 小程序 appId=2021001141626787",
    ),
    Credit(
        "建阳公交线路通告", "武夷发展集团·建阳区公交公司（大武夷新闻网）",
        "https://www.greatwuyi.com/guangg/content/202508/27/c1555764.html", "公开资讯 / 合理引用",
        "公交数据：103/105/107 路站点与时刻",
    ),
    Credit(
        "快递100", "深圳前海百递网络",
        "https://www.kuaidi100.com", "平台服务（查询接口）",
        "快递物流轨迹查询接口",
    ),
    Credit(
        "Jetpack Compose / AndroidX", "Google & AOSP",
        "https://android.googlesource.com/platform/frameworks/support", "Apache-2.0",
        "直接依赖：Material 3 UI、原生动画",
    ),
    Credit(
        "Kotlin", "JetBrains",
        "https://github.com/JetBrains/kotlin", "Apache-2.0",
        "直接依赖：开发语言",
    ),
)

/** 版本修改日志 */
private val changelog = listOf(
    "v2.11" to "快递查询新增历史记录（点击重查/单条删除/清空）；关于页新增作者 GitHub/QQ/邮箱、代码归属说明与欢迎反馈",
    "v2.10" to "快递查询改为 API 直出物流轨迹（在页面下方直接显示结果，不再用内置浏览器）",
    "v2.9" to "快递查询 App 内显示；「复制单号」换成「读取剪贴板」快捷填单号",
    "v2.8" to "公交弹窗去掉高德查询改为提示掌上公交；UI 全局过渡动画；版本日志只显示当前版（历史可展开）",
    "v2.7" to "关于页精简；电商只保留菜鸟/拼多多；新增快递单号查询；版本号动态显示 + 修改日志",
    "v2.6" to "购物平台直达订单/个人中心（Activity 自动探测）；课表周几下显示日期",
    "v2.5" to "应用检测权限化（QUERY_ALL_PACKAGES）；手动检测/重新检测按钮 + 缓存",
    "v2.4" to "课表：起止周 + 单双周、节假日标记；取件页重排（菜鸟①、拼多多②）",
    "v2.3" to "公交高德跳线路图；课表添加入口改浮动按钮",
    "v2.2" to "底部功能栏；首页居中；公交时刻点+目的地筛选；课表手动添加回归",
    "v2.1" to "课表模块暂时下线（源码保留）",
    "v2.0" to "原生 Material Design；更名「校园助手-FAFUer专用」；体积极限压缩；新增南平校区公交",
    "v1.x" to "初版：液态玻璃 UI、取件码跳转、正方教务课表、校园地图（历史版本）",
)

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("关于") }

        // ---- 项目简介 ----
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
                        "系统要求 Android 15+（兼容 12~17）· 原生 Material Design 3 · 安装包约 2MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
        }

        // ---- 作者与代码归属（写清楚）----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✍️ 作者与代码归属", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "全部代码由 AI 助手（Mimo v2.6 / OpenClaw 编程助手）独立编写完成；" +
                            "项目的需求设计、功能构想、提示词（Prompt）与全部测试反馈由 Void_Blank 提供。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "简单说：人出想法，AI 写代码 —— 这是一个人 + AI 协作完成的开源项目。",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ---- 联系方式 ----
        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📮 联系作者 · 交流反馈", style = MaterialTheme.typography.titleMedium)
                    Text("GitHub：Void_Blank（Blank2007）", style = MaterialTheme.typography.bodyMedium)
                    Text("QQ：1553008865", style = MaterialTheme.typography.bodyMedium)
                    Text("邮箱：1553008865@qq.com", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Blank2007/fafu-campus-assistant"))
                            )
                        }
                    }) { Text("开源仓库：github.com/Blank2007/fafu-campus-assistant") }
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Blank2007"))
                            )
                        }
                    }) { Text("作者主页：github.com/Blank2007") }
                    Text(
                        "非常欢迎交流与反馈！不管是发现 Bug、想要新功能，还是对代码实现好奇想聊聊，" +
                            "都欢迎通过上面任意方式找我。你的每一条建议，都可能出现在下一个版本的更新日志里 🚀",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ---- 版本日志 ----
        item {
            var expanded by remember { mutableStateOf(false) }
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
                    Text("作者：${c.author}", style = MaterialTheme.typography.bodySmall)
                    Text("许可：${c.license} · ${c.usage}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url)))
                        }
                    }) {
                        Text(c.url, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("致谢声明（详细）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. 直接依赖：Jetpack Compose / AndroidX、Kotlin —— Apache-2.0，版权归 Google、JetBrains 所有，随本项目分发时保留其原始许可证与署名。\n\n" +
                            "2. 间接参考：WakeUp 课程表、ling_QuickShortcut 等 —— 仅借鉴公开的设计思路与跳转方案，" +
                            "未复制其任何源码，不存在许可证传染；其著作权归原作者所有。\n\n" +
                            "3. 公开资料引用：拼多多 scheme 合集（CSDN 社区整理）、支付宝菜鸟小程序 appId（V2EX 社区实测）—— " +
                            "出处已在上表列出，如有异议请联系我撤下。\n\n" +
                            "4. 数据与平台服务：公交数据引自建阳公交官方公开通告（大武夷新闻网），时刻如有调整以站牌为准；" +
                            "快递物流轨迹由快递100 提供查询接口，仅作个人查询展示用途。\n\n" +
                            "5. 本项目仅供学习交流使用，免费开源；若它帮到了你，欢迎 star 仓库或告诉我你的想法。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
