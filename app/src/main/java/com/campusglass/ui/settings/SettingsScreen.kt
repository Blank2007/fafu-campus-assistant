package com.campusglass.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusglass.BuildConfig
import com.campusglass.schedule.PeriodTable
import com.campusglass.schedule.ScheduleStore
import com.campusglass.ui.glass.AcrylicCard
import com.campusglass.ui.glass.isAppInDarkTheme
import com.campusglass.ui.theme.ThemePrefs
import com.campusglass.ui.theme.adjustColorStyle
import com.campusglass.ui.widgets.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class Credit(val name: String, val author: String, val url: String, val license: String, val usage: String)

private const val GITHUB_HOME = "https://github.com/Blank2007"
private const val RELEASES_URL = "https://github.com/Blank2007/fafu-campus-assistant/releases"

private val credits = listOf(
    Credit("WakeUp 课程表（WakeupSchedule_Kotlin）", "YZune", "https://github.com/YZune/WakeUpSchedule", "Apache-2.0", "间接参考：周课表格 UI 设计思路（未复制源码）"),
    Credit("ling_QuickShortcut（快递身份码快捷方式）", "qinyuanxin132", "https://github.com/qinyuanxin132/ling_QuickShortcut", "MIT", "间接参考：拼多多【身份码】跳转思路"),
    Credit("拼多多 Scheme 公开资料", "社区整理（CSDN）", "https://blog.csdn.net/weixin_48141487/article/details/140077257", "公开资料 / 合理引用", "间接参考：拼多多页面拉起路径"),
    Credit("支付宝菜鸟小程序取件码", "V2EX 社区实测", "https://v2ex.com/t/1002900", "公开资料 / 合理引用", "取件码直达：alipays 小程序 appId"),
    Credit("建阳公交线路通告", "武夷发展集团·建阳区公交公司（大武夷新闻网）", "https://www.greatwuyi.com/guangg/content/202508/27/c1555764.html", "公开资讯 / 合理引用", "公交数据：103/105/107 路站点与时刻"),
    Credit("无敌电动公交数据", "modiauto.com.cn", "https://www.modiauto.com.cn/cx/bus_142302.html", "公开资讯 / 合理引用", "公交发车时刻核实（2026-08/09）"),
    Credit("Haze（亚克力模糊）", "Chris Banes", "https://github.com/chrisbanes/haze", "Apache-2.0", "直接依赖：亚克力模糊材质"),
    Credit("快递100", "深圳前海百递网络", "https://www.kuaidi100.com", "平台服务（查询接口）", "快递物流轨迹查询接口"),
    Credit("Jetpack Compose / AndroidX", "Google & AOSP", "https://android.googlesource.com/platform/frameworks/support", "Apache-2.0", "直接依赖：Material 3 UI、原生动画"),
    Credit("Kotlin", "JetBrains", "https://github.com/JetBrains/kotlin", "Apache-2.0", "直接依赖：开发语言"),
)

private val changelog = listOf(
    "v3.3.zilyf · 2026-10-01 02:10" to "全面修 bug：① 快递「查无结果」不再显示成「已签收」（接口对查不到的单号同样返回 status=200/state=3，真值标记是 condition=F00）；② 自定义主题色不再变成全透明、也不再在重启后丢失（旧版 alpha 缺失 + 持久化打包值/ARGB 混用）；③ 公交末班车补全（旧版最后一班总早于标称末班）、上下行站点表互逆校正；④ 打印/清理死代码：拼多多/支付宝跳转失败会有明确提示，Activity 探测真正生效；⑤ 编辑课程新增的时段不再被丢弃，周次冲突会提示而不是静默变成全周；⑥ 全 App 键盘不再遮挡输入框，对话框可滚动不再被顶出屏幕；⑦ 状态栏图标跟随 App 深浅色，深色模式启动不再闪白；⑧ 顶栏/卡片配色统一、chip 行可换行、开关靠右、删除操作二次确认",
    "v3.2.zilyf · 2026-10-01 01:15" to "APP 图标重新设计（渐变+学士帽自适应图标）；官网加小图标；关于页 GitHub 头像（点击可访问）；全量盘查：周次编辑格式修复、越界课程保护、安全性检查",
    "v3.1.zilyf · 2026-10-01 00:35" to "首页保留「首页」标题；官网卡片整体居中；版本号 v3.1.zilyf",
    "v3.0.zhy · 2026-10-01 00:25" to "【稳定版】首页精简：仅保留常用官网并整体居中",
    "v2.24 · 2026-10-01 00:15" to "节数输入框修复（文本与状态分离）：可删空重输、即时刷新，节数 24 可随意调回；点芯片同步文本框",
    "v2.23 · 2026-09-30 23:55" to "亚克力背板圆角修复（模糊层纳入圆角裁切）；每天上课节数真自定义 1-24（根治超 12/11 被自动截断、设置无效的上限 bug）；作息行数跟随节数",
    "v2.22 · 2026-09-30 00:55" to "底栏整体重建（自定义胶囊模型，描边内嵌绘制不断边）；底部黑块根治（系统导航栏对比度遮罩）；每天节数可自定义到 24；七彩推荐色修偏色（标准零漂移）+ 改滑动不截断",
    "v2.21 · 2026-09-30 00:35" to "课程可编辑；连上多节跨行占满多格（冲突左右分栏）；每天上课节数可自定义（选项改为滑动不受截断）；主题色新增小米「色彩风格」（标准/鲜艳/柔和）+ 色温；底栏边框重构（消除模糊毛边+发丝描边）",
    "v2.20 · 2026-09-29 23:55" to "修复添加课程里周几/节次无法选择的 bug（状态不刷新 + 芯片溢出）：改为可观察状态 + 横向滑动选择",
    "v2.19 · 2026-09-29 22:55" to "课表从零重写（修复周几错位等全部旧问题，间距优化）；深色模式黑字问题全局根治（文字颜色统一切题）；检查更新修复（GitHub API 需 User-Agent 导致 403）",
    "v2.18 · 2026-09-29 00:35" to "深色三态（跟随系统/浅色/深色）；课表 bug 修复（周几映射错位/节假日标记找回/学期起始入口）；底栏下拉自动隐藏；检查更新；背景按屏幕尺寸裁切（居中/顶部/底部/适应宽度）并即时刷新；全局亚克力开关；主题色 7 推荐+系统+调色盘自定义；「取件」更名「快递」并与课表换位",
    "v2.17 · 2026-09-28 23:59" to "课表大改版：周日起算、一节一行、教师行、分段周次、多时段、高对比色块、添加界面重做；节次时间/每天节数自定义；全局亚克力；安全区适配；拼多多入口精简",
    "v2.16 · 2026-09-28 23:35" to "深色模式开关；悬浮亚克力底栏；异形屏适配；公交站点竖排；快递多公司兼容；微信身份码自动进入；背景只留自定义；个性化；日志精确到分钟",
    "v2.15 · 2026-09-28 23:00" to "路线图改地图截图；首页农大常用网站；设置页（自定义背景/主题色）",
    "v2.14 · 2026-09-28 00:29" to "公交路线图内置；发车时刻分时段排版；保留邮箱仅不公开 QQ 号",
    "v2.13 · 2026-09-28 00:16" to "移除 QQ 号与 QQ 邮箱（隐私）；含 v2.12 全部更新",
    "v2.12 · 2026-09-27 23:56" to "拼多多身份码微信入口；课表学期校准；公交时刻核实补齐",
    "v2.11 · 2026-09-27 00:16" to "快递查询历史记录；关于页作者信息与代码归属",
    "v2.10 · 2026-09-27 00:00" to "快递查询 API 直出物流轨迹",
    "v2.9 · 2026-09-26 23:30" to "快递 App 内显示；读取剪贴板填单号",
    "v2.8 · 2026-09-26 23:22" to "公交去高德；UI 过渡动画；版本日志折叠",
    "v2.7 · 2026-09-26 23:11" to "关于页精简；电商只留菜鸟/拼多多；快递查询上线",
    "v2.2-v2.6 · 2026-09-26 21:45~23:05" to "底部功能栏/公交筛选/课表手动模式/检测权限化等（详见 CHANGELOG.md）",
    "v1.x-v2.1 · 2026-09-25 23:45 起" to "初版到原生 Material 化（历史版本，详见 CHANGELOG.md）",
)

/**
 * 设置页。
 *
 * v3.3 修复：
 *  - 调色盘：RGB 组合补齐 alpha（旧版 `Color(0x00RRGGBB)` 是全透明，确认后全局主色消失）；
 *    自定义色只保存「原始色」，色彩风格/色温在渲染时统一应用一次（旧版会叠加两次导致颜色漂移）；
 *    「取消」不再偷偷保存色彩风格/色温；
 *  - 自定义色持久化改用 ARGB Int（旧版写入打包 ULong、读取按 ARGB 解释，重启必然丢）；
 *  - 系统色点显示真实动态取色，不再显示“当前生效色”造成误导；
 *  - 三个 chip 行改为可换行；开关行补 fillMaxWidth（否则 SpaceBetween 无效、开关挤在文字后面）；
 *  - 对话框高度改为 heightIn + 可滚动 + imePadding；节次时间与学期起始日做输入校验并给出错误提示；
 *    新增「恢复默认作息」；
 *  - 检查更新：版本号按段比较（旧版仅字符串相等，连更旧的 tag 也会提示“发现新版本”），
 *    区分 404/403/网络错误，连接用后 disconnect；
 *  - 所有 startActivity 失败时给出提示；恢复默认背景会删除已保存的图片文件。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<String?>(null) }
    var updateHasNew by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    val dark = isAppInDarkTheme()
    // 注意：不要在 remember{} 里调用 MaterialTheme / dynamicXxxColorScheme（那是 @Composable 调用）
    val systemColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        MaterialTheme.colorScheme.primary
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "没有可打开该链接的应用", Toast.LENGTH_SHORT).show()
        }
    }

    val pickBgImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // v3.3：降采样后再保存，避免把几十 MB 原图直接存进私有目录
                val ok = withContext(Dispatchers.IO) { saveBackgroundImage(context, uri) }
                Toast.makeText(
                    context,
                    if (ok) "背景已更换 ✅（可选裁切方式）" else "背景设置失败，请换一张图片试试",
                    Toast.LENGTH_SHORT,
                ).show()
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

        // ---- 主题模式（三态）----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🌙 深色模式", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemePrefs.ThemeMode.entries.forEach { m ->
                            FilterChip(
                                selected = ThemePrefs.themeMode.value == m,
                                onClick = { ThemePrefs.setThemeMode(context, m) },
                                label = {
                                    Text(
                                        when (m) {
                                            ThemePrefs.ThemeMode.SYSTEM -> "跟随系统"
                                            ThemePrefs.ThemeMode.LIGHT -> "浅色"
                                            ThemePrefs.ThemeMode.DARK -> "深色"
                                        }
                                    )
                                },
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { ThemePrefs.setAcrylic(context, !ThemePrefs.acrylicEnabled.value) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("全局亚克力效果（需自定义背景）", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = ThemePrefs.acrylicEnabled.value,
                            onCheckedChange = { ThemePrefs.setAcrylic(context, it) },
                        )
                    }
                }
            }
        }

        // ---- 背景 + 裁切 ----
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
                        ) { Text("选择背景图") }
                        OutlinedButton(
                            onClick = {
                                // v3.3：同时删掉已保存的图片文件，不再留下多 MB 残留
                                runCatching { File(context.filesDir, "bg_image.jpg").delete() }
                                ThemePrefs.setBgImage(context, "")
                                Toast.makeText(context, "已恢复默认背景", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("恢复默认") }
                    }
                    Text("裁切方式（按屏幕尺寸）", style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemePrefs.BgCrop.entries.forEach { c ->
                            FilterChip(
                                selected = ThemePrefs.bgCropMode.value == c,
                                onClick = { ThemePrefs.setBgCrop(context, c) },
                                label = {
                                    Text(
                                        when (c) {
                                            ThemePrefs.BgCrop.CENTER -> "居中"
                                            ThemePrefs.BgCrop.TOP -> "顶部"
                                            ThemePrefs.BgCrop.BOTTOM -> "底部"
                                            ThemePrefs.BgCrop.FIT_WIDTH -> "适应宽度"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        "已按当前设备屏幕比例裁切铺满，立即生效（自动纠正如竖拍照片方向）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // ---- 主题色：推荐 + 系统 + 调色盘 ----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🎯 主题色", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            // v3.3：显示真正的“系统动态取色”，而不是当前生效色
                            ColorDot(
                                color = systemColor,
                                label = "系统",
                                selected = ThemePrefs.themeColorIndex.value == 0,
                            ) { ThemePrefs.setColorIndex(context, 0) }
                        }
                        // 7 推荐色（显示色=实际生效色，防偏色）
                        items(ThemePrefs.THEME_COLORS.size) { i ->
                            val (label, color) = ThemePrefs.THEME_COLORS[i]
                            ColorDot(
                                color = adjustColorStyle(color),
                                label = label,
                                selected = ThemePrefs.themeColorIndex.value == i + 1,
                            ) { ThemePrefs.setColorIndex(context, i + 1) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showColorPicker = true }) {
                            Text("🎨 调色盘自定义" + if (ThemePrefs.themeColorIndex.value == 8) "（当前生效）" else "")
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("小", "中", "大").forEachIndexed { i, t ->
                            FilterChip(
                                selected = ThemePrefs.scheduleFontScale.value == i,
                                onClick = { ThemePrefs.setFontScale(context, i) },
                                label = { Text(t) },
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { ThemePrefs.setShowWeekend(context, !ThemePrefs.showWeekend.value) },
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
                    ) { Text("⏰ 节次时间设置 / 每天上课节数 / 学期起始日") }
                }
            }
        }

        // ---- 关于 + 检查更新 ----
        item {
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            painter = painterResource(com.campusglass.R.drawable.github_avatar),
                            contentDescription = "GitHub 头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { openUrl(GITHUB_HOME) },
                        )
                        Column {
                            Text(
                                "校园助手-FAFUer专用 v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "GitHub · Void_Blank（点击头像可访问）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        "取件码快捷跳转（支付宝·菜鸟 / 拼多多）· 快递单号查询 · 手动课表 · 南平校区公交",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "全部代码由 AI 助手（Mimo v2.6 / OpenClaw 编程助手）独立编写；" +
                            "需求设计、提示词与测试反馈由 Void_Blank 提供。人出想法，AI 写代码。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            checking = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { checkUpdate() }
                                updateInfo = r.message
                                updateHasNew = r.hasNew
                                checking = false
                            }
                        },
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (checking) "正在检查更新…" else "🔄 检查更新") }
                    TextButton(onClick = { openUrl(RELEASES_URL) }) {
                        Text("打开下载页（GitHub Releases）")
                    }
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
                AcrylicCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("📋 版本修改日志", style = MaterialTheme.typography.titleMedium)
                    }
                }
                changelog.first().let { (ver, desc) ->
                    AcrylicCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("$ver（当前版本）", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
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
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("🙏 致谢（借用 / 参考的开源项目与数据来源）", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        items(credits) { c ->
            AcrylicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text("作者：${c.author} · 许可：${c.license}", style = MaterialTheme.typography.bodySmall)
                    Text(c.usage, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { openUrl(c.url) }) {
                        Text(c.url, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showTimeDialog) {
        PeriodTimesDialog(onDismiss = { showTimeDialog = false })
    }
    if (showColorPicker) {
        ColorPickerDialog(onDismiss = { showColorPicker = false })
    }
    updateInfo?.let { msg ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("检查更新") },
            text = { Text(msg) },
            confirmButton = {
                if (updateHasNew) {
                    TextButton(onClick = { openUrl(RELEASES_URL) }) { Text("去下载") }
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("关闭") }
            },
        )
    }
}

/** 把选中的图片降采样后保存到应用私有目录，返回是否成功 */
private fun saveBackgroundImage(context: Context, uri: Uri): Boolean = runCatching {
    val file = File(context.filesDir, "bg_image.jpg")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

    val maxSide = 2560
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2

    val decoded = context.contentResolver.openInputStream(uri)
        ?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: return false
    file.outputStream().use { out -> decoded.compress(Bitmap.CompressFormat.JPEG, 88, out) }
    decoded.recycle()
    ThemePrefs.setBgImage(context, file.absolutePath)
    true
}.getOrDefault(false)

private data class UpdateCheck(val message: String, val hasNew: Boolean)

/** 版本比较：3.3.zilyf / 3.2.1 这类串按段比较，数字段按数值、非数字段按字符串 */
private fun isNewerVersion(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.', '-', '_').filter { it.isNotEmpty() }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrNull(i) ?: return false
        val y = b.getOrNull(i) ?: return true
        val nx = x.toIntOrNull()
        val ny = y.toIntOrNull()
        val cmp = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y, ignoreCase = true)
        if (cmp != 0) return cmp > 0
    }
    return false
}

/**
 * 检查更新：读 GitHub Releases 最新版（带 User-Agent + 重试）。
 * v3.3：按版本号比较大小（旧版只要字符串不相等就说“发现新版本”，连更旧的 tag 也会提示更新），
 * 并区分 404 / 403 / 网络错误。
 */
private fun checkUpdate(): UpdateCheck {
    val url = "https://api.github.com/repos/Blank2007/fafu-campus-assistant/releases/latest"
    var lastErr = ""
    repeat(2) {
        val r = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 12_000
                conn.readTimeout = 12_000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "FAFU-Campus-Assistant/${BuildConfig.VERSION_NAME}")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val code = conn.responseCode
                if (code == 404) {
                    return UpdateCheck("没有找到发布信息（仓库或 Release 可能已被删除）", false)
                }
                if (code == 403) {
                    return UpdateCheck("被 GitHub 限流了（HTTP 403），请稍后再试", false)
                }
                if (code != 200) {
                    return UpdateCheck("检查失败：HTTP $code（可点「打开下载页」查看）", false)
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val latest = json.optString("tag_name", "").removePrefix("v")
                val current = BuildConfig.VERSION_NAME
                return when {
                    latest.isBlank() ->
                        UpdateCheck("没获取到版本信息，请稍后再试", false)
                    isNewerVersion(latest, current) ->
                        UpdateCheck("发现新版本 v$latest（当前 v$current）！点「去下载」更新", true)
                    isNewerVersion(current, latest) ->
                        UpdateCheck("当前版本 v$current 比线上最新版 v$latest 还新（可能是本地开发版）", false)
                    else ->
                        UpdateCheck("已是最新版本（v$current）✅", false)
                }
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        if (r.isSuccess) return r.getOrThrow()
        lastErr = r.exceptionOrNull()?.message ?: "网络异常"
    }
    return UpdateCheck("检查失败：$lastErr（可能是网络不畅，可直接点「打开下载页」查看）", false)
}

@Composable
private fun ColorDot(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * 调色盘：色彩风格（小米风格）+ 色温 + RGB 自定义。
 * v3.3：RGB 组合补 alpha（旧版是全透明色）；只保存原始色，渲染时统一应用一次；
 *       色彩风格/色温改为点「使用此色」才生效（点取消不再偷偷保存）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val baseArgb = ThemePrefs.customColor.value.toArgb()
    var r by remember { mutableFloatStateOf(((baseArgb shr 16) and 0xFF).toFloat()) }
    var g by remember { mutableFloatStateOf(((baseArgb shr 8) and 0xFF).toFloat()) }
    var b by remember { mutableFloatStateOf((baseArgb and 0xFF).toFloat()) }
    var styleState by remember { mutableIntStateOf(ThemePrefs.colorStyle.value) }
    var tempState by remember { mutableFloatStateOf(ThemePrefs.colorTemp.value) }

    // 不透明原始色：0xFFRRGGBB（旧版少了 alpha 字节 → Color(Int) 得到全透明色）
    val raw = Color(
        (0xFF shl 24) or
            (r.toInt().coerceIn(0, 255) shl 16) or
            (g.toInt().coerceIn(0, 255) shl 8) or
            b.toInt().coerceIn(0, 255)
    )
    val preview = adjustColorStyle(raw, styleState, tempState)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎨 调色盘") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(CircleShape)
                        .background(preview)
                )
                Text("色彩风格（像小米色彩风格）", style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("标准", "鲜艳", "柔和").forEachIndexed { i, t ->
                        FilterChip(
                            selected = styleState == i,
                            onClick = { styleState = i },
                            label = { Text(t) },
                        )
                    }
                }
                Text(
                    "色温（冷 ${if (tempState < 0) "❄" else if (tempState > 0) "🔥" else "—"} 暖）",
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = tempState,
                    onValueChange = { tempState = it },
                    valueRange = -20f..20f,
                )
                HorizontalDivider()
                Text("红 ${r.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = r, onValueChange = { r = it }, valueRange = 0f..255f)
                Text("绿 ${g.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = g, onValueChange = { g = it }, valueRange = 0f..255f)
                Text("蓝 ${b.toInt()}", style = MaterialTheme.typography.labelSmall)
                Slider(value = b, onValueChange = { b = it }, valueRange = 0f..255f)
                Text(
                    "预览色：#" + preview.toArgb().toUInt().toString(16).uppercase().takeLast(6) +
                        "（原始色 #" + raw.toArgb().toUInt().toString(16).uppercase().takeLast(6) + "）",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                ThemePrefs.setColorStyle(context, styleState)
                ThemePrefs.setColorTemp(context, tempState)
                ThemePrefs.setCustomColor(context, raw)   // 只存原始色，渲染时应用一次风格/色温
                onDismiss()
            }) { Text("使用此色") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 节次时间 / 每天节数 / 学期起始日 */
@Composable
private fun PeriodTimesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val times = remember { mutableStateOf(PeriodTable.all(context)) }
    var perDayState by remember { mutableIntStateOf(PeriodTable.periodsPerDay(context)) }
    var perDayText by remember { mutableStateOf(PeriodTable.periodsPerDay(context).toString()) }   // 文本与状态分离，支持清空重输
    var termStartText by remember { mutableStateOf(ScheduleStore.termStart(context).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏰ 节次与学期设置") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)     // 旧版写死 460dp，横屏/大字号下按钮会被顶出屏幕
                    .imePadding()               // 键盘不再遮挡输入框
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("每天上课节数（1-24，可自定义）", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..24).toList()) { n ->
                        FilterChip(
                            selected = perDayState == n,
                            onClick = {
                                perDayState = n
                                perDayText = n.toString()
                                error = null
                            },
                            label = { Text("$n") },
                        )
                    }
                }
                OutlinedTextField(
                    value = perDayText,
                    onValueChange = { v ->
                        perDayText = v.filter { it.isDigit() }.take(2)   // 可删空、可重输，即时刷新
                        perDayText.toIntOrNull()?.let { perDayState = it.coerceIn(1, 24) }
                    },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("自定义每天节数（1-24）") },
                )
                OutlinedTextField(
                    termStartText, {
                        termStartText = it
                        error = null
                    },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error?.contains("学期") == true,
                    label = { Text("学期起始日（第一周的周日，如 2026-08-30）") },
                )
                HorizontalDivider()
                times.value.take(perDayState).forEachIndexed { i, t ->
                    val start = remember(t) { mutableStateOf(t.substringBefore("-")) }
                    val end = remember(t) { mutableStateOf(t.substringAfter("-")) }
                    Text("第${i + 1}节", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(start.value, { v: String ->
                            start.value = v
                            times.value = times.value.toMutableList().also { it[i] = "${start.value}-${end.value}" }
                        }, Modifier.weight(1f), singleLine = true,
                            isError = !PeriodTable.isValidTime(start.value),
                            label = { Text("上课") })
                        OutlinedTextField(end.value, { v: String ->
                            end.value = v
                            times.value = times.value.toMutableList().also { it[i] = "${start.value}-${end.value}" }
                        }, Modifier.weight(1f), singleLine = true,
                            isError = !PeriodTable.isValidTime(end.value),
                            label = { Text("下课") })
                    }
                }
                OutlinedButton(
                    onClick = {
                        PeriodTable.reset(context)
                        times.value = PeriodTable.all(context)
                        perDayState = PeriodTable.periodsPerDay(context)
                        perDayText = perDayState.toString()
                        error = null
                        Toast.makeText(context, "已恢复默认作息时间", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("恢复默认作息时间") }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val invalid = times.value.take(perDayState).filterNot { PeriodTable.isValidRange(it) }
                if (invalid.isNotEmpty()) {
                    error = "作息时间格式不对（应形如 08:00-08:45）：${invalid.joinToString("、")}"
                    return@Button
                }
                val term = parseDate(termStartText)
                if (term == null) {
                    error = "学期起始日格式不对，请填 2026-08-30 这样的日期"
                    return@Button
                }
                PeriodTable.save(context, times.value)
                PeriodTable.setPeriodsPerDay(
                    context,
                    perDayText.toIntOrNull()?.coerceIn(1, 24) ?: perDayState,
                )
                ScheduleStore.setTermStart(context, term)
                Toast.makeText(context, "已保存节次与学期设置", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 宽松日期解析：接受 2026-08-30 / 2026-8-30 / 2026/8/30 / 2026.8.30 */
private fun parseDate(input: String): LocalDate? {
    val s = input.trim().replace('/', '-').replace('.', '-')
    if (s.isEmpty()) return null
    return runCatching { LocalDate.parse(s) }.getOrElse {
        runCatching { LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-M-d")) }.getOrNull()
    }
}
