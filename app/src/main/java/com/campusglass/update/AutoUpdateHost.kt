package com.campusglass.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.campusglass.ui.settings.UpdateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v4.18：启动时自动检查更新的宿主。
 *
 * 行为（由 [UpdatePolicy.mode] 决定）：
 *  - OFF       → 什么都不做
 *  - WHEN_OPEN → 检查；发现新版本自动弹出更新弹窗（等于用户每次打开都被告知）
 *  - SILENT    → 检查；发现新版本只在页面顶部出提示条，点了才打开弹窗
 *
 * 细节：
 *  - 每次「冷启动」最多查一次（remember 在 Activity 重建时由 MainActivity 的 onCreate 重置）
 *  - 节流：距上次检查不足 [UpdatePolicy.THROTTLE_MS] 时跳过，避免频繁切后台/冷启反复请求
 *  - 已点「本次跳过」的版本不再打扰；用户手动点的检查（设置页）不受跳过影响
 *  - 网络失败静默丢弃，不打扰用户（自动检查不该报错）
 */
@Composable
fun AutoUpdateHost() {
    val context = LocalContext.current
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

    if (showDialog) {
        UpdateDialog(
            onDismiss = {
                showDialog = false
                // 弹窗模式：用户关掉即视为「这次不装」，记住该版本避免反复弹（新版仍会提示）
                r.newTag?.let { UpdatePolicy.skipTag(context, it) }
            },
            prefetched = r,
        )
    }

    // 静默模式：顶部提示条（也可在弹窗被跳过的情况下作为回退入口）
    AnimatedVisibility(
        visible = !bannerDismissed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.fillMaxWidth(0.72f)) {
                    Text(
                        "发现新版本 v${r.newTag}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "点右侧「去更新」查看更新日志并安装",
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
