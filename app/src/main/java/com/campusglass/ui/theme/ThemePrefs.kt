package com.campusglass.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * 外观与个性化偏好（设置页可改，持久化）：
 * 深色模式开关 · 主题色 · 自定义背景图 · 课表字号 · 课表是否显示周末。
 */
object ThemePrefs {

    val darkMode = mutableStateOf(false)          // 深色模式（用户开关）
    val themeColorIndex = mutableStateOf(0)       // 0 = 跟随系统取色
    val bgImagePath = mutableStateOf("")          // 自定义背景图（空 = 默认）
    val scheduleFontScale = mutableStateOf(1)     // 课表字号：0小 1中 2大
    val showWeekend = mutableStateOf(true)        // 课表显示周六周日列

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
        darkMode.value = p.getBoolean("dark", false)
        themeColorIndex.value = p.getInt("colorIdx", 0).coerceIn(0, THEME_COLORS.size - 1)
        bgImagePath.value = p.getString("bgImage", "") ?: ""
        scheduleFontScale.value = p.getInt("fontScale", 1).coerceIn(0, 2)
        showWeekend.value = p.getBoolean("showWeekend", true)
    }

    fun setDark(c: Context, on: Boolean) {
        darkMode.value = on
        prefs(c).edit().putBoolean("dark", on).apply()
    }

    fun setColor(c: Context, idx: Int) {
        themeColorIndex.value = idx
        prefs(c).edit().putInt("colorIdx", idx).apply()
    }

    fun setBgImage(c: Context, path: String) {
        bgImagePath.value = path
        prefs(c).edit().putString("bgImage", path).apply()
    }

    fun setFontScale(c: Context, v: Int) {
        scheduleFontScale.value = v.coerceIn(0, 2)
        prefs(c).edit().putInt("fontScale", scheduleFontScale.value).apply()
    }

    fun setShowWeekend(c: Context, on: Boolean) {
        showWeekend.value = on
        prefs(c).edit().putBoolean("showWeekend", on).apply()
    }
}
