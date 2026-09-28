package com.campusglass.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * 外观偏好：软件背景 + 主题色（设置页里改，持久化保存）。
 */
object ThemePrefs {

    enum class BgMode(val label: String) {
        DEFAULT("默认"),
        AURORA("极光渐变"),
        SKY("浅蓝"),
        MINT("浅绿"),
        IMAGE("自定义图片"),
    }

    /** 0 = 跟随系统取色（动态色） */
    val themeColorIndex = mutableStateOf(0)
    val bgMode = mutableStateOf(BgMode.DEFAULT)
    val bgImagePath = mutableStateOf("")

    val THEME_COLORS = listOf(
        "跟随系统" to Color(0xFF3B5BFF),
        "蓝" to Color(0xFF2962FF),
        "紫" to Color(0xFF7C4DFF),
        "绿" to Color(0xFF00897B),
        "橙" to Color(0xFFEF6C00),
        "红" to Color(0xFFD32F2F),
        "青" to Color(0xFF0097A7),
    )

    private fun prefs(c: Context) = c.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        themeColorIndex.value = p.getInt("colorIdx", 0).coerceIn(0, THEME_COLORS.size - 1)
        bgMode.value = runCatching { BgMode.valueOf(p.getString("bgMode", "DEFAULT") ?: "DEFAULT") }
            .getOrDefault(BgMode.DEFAULT)
        bgImagePath.value = p.getString("bgImage", "") ?: ""
    }

    fun setColor(c: Context, idx: Int) {
        themeColorIndex.value = idx
        prefs(c).edit().putInt("colorIdx", idx).apply()
    }

    fun setBgMode(c: Context, mode: BgMode) {
        bgMode.value = mode
        prefs(c).edit().putString("bgMode", mode.name).apply()
    }

    fun setBgImage(c: Context, path: String) {
        bgImagePath.value = path
        prefs(c).edit().putString("bgImage", path).apply()
    }
}
