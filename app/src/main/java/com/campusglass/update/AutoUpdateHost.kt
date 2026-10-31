package com.campusglass.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import com.campusglass.ui.motion.motionSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusglass.ui.settings.UpdateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v4.19：启动时自动检查更新的宿主。
 *
 * 行为（由 [UpdatePolicy.mode] 决定）：
 *  - OFF       → 什么都不做
 *  - WHEN_OPEN → 检查；发现新版本自动弹出更新弹窗（等于用户每次打开都被告知）
 *  - SILENT    → 检查；发现新版本从**底部**滑出一条横幅（不打断、不遮内容）
 *
 * ⚠️ 位置刻意放在**底部**（v4.19 修正）：
 * 原先画在屏幕顶部，而本页是 edge-to-edge（`enableEdgeToEdge`），
 * 顶部内容会被状态栏盖住 —— 提示条文字正好被状态栏挡了。
 * 参照 Chrome / Telegram 等开源软件的做法改为底部横幅，并显式加导航栏 inset，
 * 避免被手势条 / 三键导航挡住。
 *
 * 其他细节：
 *  - 每次「冷启动」最多查一次（remember 随 Activity 重建重置）
 *  - 节流：距上次检查不足 [UpdatePolicy.THROTTLE_MS] 时跳过，避免频繁冷启反复请求
 *  - 已点「忽略」的版本不再打扰；用户手动检查（设置页）不受跳过影响
 *  - 网络失败静默丢弃，不打扰用户（自动检查不该报错）
 */
@Composable
fun AutoUpdateHost() {
    val context = LocalContext.current
    // 更新横幅和底栏共用一套动效令牌（用户可在设置里调速度 / 减少动效）
    val m = motionSpec()
    var result by remember { mutableStateOf<UpdateCheck?>(null) }
    var bannerDismissed by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!UpdatePolicy.shouldCheckNow()) return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { runCatching { checkUpdate() }.getOrNull() }
            ?: return@LaunchedEffect
        UpdatePolicy.markChecked(context)
        val tag = r.newTag ?: return@LaunchedEffect          // 无新版本 / 出错 → 静默
        if (UpdatePolicy.isSkipped(tag)) return@LaunchedEffect
        result = r
        if (UpdatePolicy.mode == UpdatePolicy.Mode.WHEN_OPEN) showDialog = true
    }

    val r = result
    if (r == null) return

    // 弹窗模式（WHEN_OPEN）：自动弹更新弹窗
    if (showDialog) {
        UpdateDialog(
            onDismiss = {
                showDialog = false
                // 用户关掉即视为「这次不装」，记住该版本避免反复弹（新版仍会提示）
                r.newTag?.let { UpdatePolicy.skipTag(context, it) }
            },
            prefetched = r,
        )
    }

    // 静默模式：底部横幅（仿 Chrome / Telegram），不抢焦点、不遮内容
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !bannerDismissed,
            modifier = Modifier.align(Alignment.BottomCenter),
            // 和底栏隐藏/出现同一套弹簧：从下方带阻尼顶上来，消失时快速退下
            enter = m.barEnter,
            exit = m.barExit,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .widthIn(max = 520.dp),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "发现新版本 v${r.newTag}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "点「去更新」查看更新日志并安装",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    TextButton(onClick = { showDialog = true }) { Text("去更新") }
                    TextButton(onClick = {
                        bannerDismissed = true
                        r.newTag?.let { UpdatePolicy.skipTag(context, it) }
                    }) { Text("忽略") }
                }
            }
        }
    }
}
