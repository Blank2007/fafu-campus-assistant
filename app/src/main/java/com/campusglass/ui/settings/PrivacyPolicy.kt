package com.campusglass.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 隐私政策与数据使用协议（B-4 · 审查 v4.10 修复）。
 * - 首次启动强制同意（PrivacyConsentDialog）
 * - 设置→关于 提供查看入口（PrivacyPolicyDialog）
 * - 仓库 PRIVACY.md 与此处文案保持一致（改动请同步）
 *
 * 生效日期：2026-10-11 · 适用版本：v4.11.sljzy 及以后
 */
object PrivacyPrefs {
    private const val PREFS = "privacy"
    private const val KEY_AGREED = "agreed_v1"

    fun agreed(context: android.content.Context): Boolean =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_AGREED, false)

    fun setAgreed(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AGREED, true).apply()
    }
}

/** 协议正文（段落标题 to 内容），与仓库 PRIVACY.md 同步 */
val PRIVACY_SECTIONS: List<Pair<String, String>> = listOf(
    "一、概述" to
        "「校园助手-FAFUer专用」（以下简称“本应用”）由 Void_Blank 发起设计、由 AI 助手编写，" +
        "是一款面向福建农林大学（含金山学院）同学的离线优先校园工具。" +
        "本应用尊重并保护你的隐私。本协议说明本应用会用到哪些数据、这些数据去哪里、以及我们不会做什么。" +
        "继续使用即表示你已阅读并同意本协议。",
    "二、本应用会用到的数据" to
        "1. 课表与课程信息：由你手动录入，仅保存在本机（应用私有存储），不上传任何服务器。\n" +
        "2. 快递单号：仅在你主动点击“查询”时，为查询物流而发送给快递查询服务（快递100）。\n" +
        "3. 剪贴板内容：仅在你主动点击“读取剪贴板”按钮时读取一次，用于自动提取快递单号；" +
        "不后台读取、不保存剪贴板原文、更不会上传。\n" +
        "4. 取件码 / 快捷跳转内容：仅在本机解析，不联网传输。\n" +
        "5. 主题、背景、学期设置等偏好：仅保存在本机。",
    "三、数据去向（第三方服务）" to
        "为实现功能，本应用仅与以下三个外部服务通信，且不向它们发送你的姓名、学号、手机号等身份信息：\n" +
        "1. 快递100（kuaidi100.com）：接收你主动查询的快递单号，返回物流轨迹。\n" +
        "2. 一言（hitokoto.cn）：获取“每日一句”诗词文案。\n" +
        "3. GitHub（api.github.com / github.com）：检查新版本并下载更新包。\n" +
        "以上服务各有其隐私政策，本应用无法控制其行为，请以其官方说明为准。",
    "四、我们不会做什么" to
        "1. 不收集定位、通讯录、短信、通话记录、相机、麦克风或设备标识符。\n" +
        "2. 不含任何广告、统计或推送 SDK。\n" +
        "3. 不在后台读取剪贴板。\n" +
        "4. 不上传你的课表、偏好或任何本地数据。\n" +
        "5. 不将你的信息出售、共享给任何第三方用于营销。",
    "五、数据存储与安全" to
        "1. 本地数据保存在应用私有目录，卸载应用即一并清除；系统云备份已关闭（allowBackup=false），" +
        "课表不会被备份提取。\n" +
        "2. 应用更新包在安装前会校验 SHA-256 摘要与 Ed25519 数字签名（v4.11 起），" +
        "校验不通过不会安装。\n" +
        "3. 网络请求均使用 HTTPS（快递查询、一言、GitHub）。",
    "六、未成年人说明" to
        "本应用主要面向在校学生。如果你是未成年人，请在监护人指导下阅读本协议并决定是否使用。",
    "七、协议更新" to
        "本协议如有更新，将在“设置 → 版本修改日志”中说明；涉及数据使用的重大变更将重新征求你的同意。",
    "八、联系我们" to
        "对本协议或数据使用有任何疑问，欢迎通过 GitHub Issues（Blank2007/fafu-campus-assistant）" +
        "或邮箱 1553008865@qq.com 联系。你的反馈会让这个小工具变得更好。",
)

/** 协议正文（只读，可滚动） */
@Composable
fun PrivacyPolicyContent(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
    ) {
        PRIVACY_SECTIONS.forEach { (title, body) ->
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * 首次启动强制同意弹窗：未同意不可使用。
 * onAgree=同意并继续；onExit=不同意并退出。
 */
@Composable
fun PrivacyConsentDialog(onAgree: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* 强制选择，不允许点外面关闭 */ },
        title = { Text("📜 隐私政策与数据使用协议") },
        text = {
            Column {
                Text(
                    "欢迎使用「校园助手-FAFUer专用」！使用前请阅读并同意隐私政策：",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "要点：课表等数据只存本机、不上传；剪贴板仅在你点“读取剪贴板”时读取一次；" +
                        "联网仅用于 快递查询（快递100）/ 每日一句（一言）/ 检查更新（GitHub）；" +
                        "无广告、无统计、不收集个人身份信息。",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                PrivacyPolicyContent(Modifier.heightIn(max = 260.dp))
            }
        },
        confirmButton = {
            Button(onClick = onAgree) { Text("同意并继续") }
        },
        dismissButton = {
            TextButton(onClick = onExit) { Text("不同意并退出") }
        },
    )
}

/** 关于页查看入口（只读） */
@Composable
fun PrivacyPolicyDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("📜 隐私政策与数据使用协议") },
        text = {
            PrivacyPolicyContent(Modifier.heightIn(max = 420.dp))
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        },
    )
}
