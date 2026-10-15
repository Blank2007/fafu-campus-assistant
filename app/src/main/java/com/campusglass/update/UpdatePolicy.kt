package com.campusglass.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 启动时自动检查更新的用户偏好（设置页可改，持久化）。
 *
 * 三种模式：
 *  - OFF      从不自动检查（仍可在设置页手动点「检查更新」）
 *  - WHEN_OPEN 每次打开 App 检查，发现新版本**弹窗**（默认）
 *  - SILENT   每次打开 App 检查，但**不弹窗**，只在首页顶部出一张可关闭的提示条
 *
 * 另外记录：
 *  - lastCheckMs：上次检查时间（用于节流，避免频繁冷启动反复请求 GitHub）
 *  - skippedTag：用户选择「本次跳过」的版本号（同一版本不再重复打扰）
 *
 * ⚠️ 状态一律用 Compose 的 [mutableStateOf] 暴露：
 * v4.18 初版用的是普通 `@Volatile var` 字段，Compose 在首次组合时读一次就缓存下来，
 * 点开关**界面不会刷新**（要退出设置页再进来才变）。改用 Compose 状态后点击即时生效。
 */
object UpdatePolicy {

    enum class Mode { OFF, WHEN_OPEN, SILENT }

    /** 自动检查节流窗口：10 分钟内重复冷启动不再请求（GitHub 未登录限流 60 次/小时） */
    const val THROTTLE_MS = 10 * 60 * 1000L

    private fun prefs(c: Context) = c.getSharedPreferences("update", Context.MODE_PRIVATE)

    /** 当前模式（Compose 可观察，改动即时触发重组） */
    var mode by mutableStateOf(Mode.WHEN_OPEN)
        private set

    /** 上次检查时间（Compose 可观察；用 Long 专用状态避免装箱） */
    var lastCheckMs by mutableLongStateOf(0L)
        private set

    /** 已跳过的版本号（Compose 可观察，清除后文案即时消失） */
    var skippedTag by mutableStateOf("")
        private set

    /** App 启动时载入（MainActivity.onCreate 调用，早于任何检查） */
    fun load(c: Context) {
        val p = prefs(c)
        mode = runCatching {
            Mode.valueOf(p.getString("autoCheckMode", Mode.WHEN_OPEN.name) ?: Mode.WHEN_OPEN.name)
        }.getOrDefault(Mode.WHEN_OPEN)
        lastCheckMs = p.getLong("lastCheckMs", 0L)
        skippedTag = p.getString("skippedTag", "") ?: ""
    }

    fun setMode(c: Context, m: Mode) {
        mode = m
        prefs(c).edit().putString("autoCheckMode", m.name).apply()
    }

    fun markChecked(c: Context, at: Long = System.currentTimeMillis()) {
        lastCheckMs = at
        prefs(c).edit().putLong("lastCheckMs", at).apply()
    }

    /** 用户点「本次跳过」：记住该版本，之后同一版本不再提示（新版本来了仍会提示） */
    fun skipTag(c: Context, tag: String) {
        skippedTag = tag
        prefs(c).edit().putString("skippedTag", tag).apply()
    }

    fun clearSkipped(c: Context) {
        skippedTag = ""
        prefs(c).edit().remove("skippedTag").apply()
    }

    /**
     * 是否允许此刻发起自动检查。
     * OFF 或处于节流窗口内 → false。
     */
    fun shouldCheckNow(now: Long = System.currentTimeMillis()): Boolean {
        if (mode == Mode.OFF) return false
        if (lastCheckMs > 0 && now - lastCheckMs < THROTTLE_MS) return false
        return true
    }

    /** 该 tag 是否已被用户跳过 */
    fun isSkipped(tag: String): Boolean = tag.isNotBlank() && tag == skippedTag

    /** 设置页展示用文案 */
    fun label(m: Mode): String = when (m) {
        Mode.OFF -> "从不（仅手动检查）"
        Mode.WHEN_OPEN -> "每次打开都检查（弹窗提示）"
        Mode.SILENT -> "每次打开都检查（静默，仅提示条）"
    }
}
