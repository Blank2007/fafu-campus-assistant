package com.campusglass.ui.theme
import androidx.compose.runtime.mutableFloatStateOf

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * 外观与个性化偏好（设置页可改，持久化）：
 * 主题模式（跟随系统/浅色/深色）· 主题色（7推荐+系统+自定义调色盘）·
 * 亚克力开关 · 自定义背景（含裁切模式）· 课表字号/周末列。
 */
object ThemePrefs {

    enum class ThemeMode { SYSTEM, LIGHT, DARK }
    enum class BgCrop { CENTER, TOP, BOTTOM, FIT_WIDTH }

    val themeMode = mutableStateOf(ThemeMode.SYSTEM)   // 深色模式三态
    val acrylicEnabled = mutableStateOf(true)          // 全局亚克力开关

    /** 0=系统主题色(动态) 1..7=推荐色 8=自定义(调色盘) */
    val themeColorIndex = mutableIntStateOf(0)
    val customColor = mutableStateOf(Color(0xFF3B5BFF))
    val colorStyle = mutableIntStateOf(0)          // 色彩风格：0 标准 / 1 鲜艳 / 2 柔和（小米色彩风格风格）
    val colorTemp = mutableFloatStateOf(0f)        // 色温：-20(冷) ~ +20(暖)

    val bgImagePath = mutableStateOf("")
    val bgCropMode = mutableStateOf(BgCrop.CENTER)
    val bgNonce = mutableIntStateOf(0)                 // 换图时+1，强制刷新缓存

    val scheduleFontScale = mutableIntStateOf(1)       // 0小 1中 2大
    val showWeekend = mutableStateOf(true)

    val THEME_COLORS = listOf(
        "蓝" to Color(0xFF2962FF),
        "紫" to Color(0xFF7C4DFF),
        "绿" to Color(0xFF00897B),
        "橙" to Color(0xFFEF6C00),
        "红" to Color(0xFFD32F2F),
        "青" to Color(0xFF0097A7),
        "粉" to Color(0xFFE91E63),
    )

    private fun prefs(c: Context) = c.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        themeMode.value = runCatching {
            ThemeMode.valueOf(p.getString("themeMode", "SYSTEM") ?: "SYSTEM")
        }.getOrDefault(ThemeMode.SYSTEM)
        acrylicEnabled.value = p.getBoolean("acrylic", true)
        themeColorIndex.value = p.getInt("colorIdx", 0).coerceIn(0, 8)
        customColor.value = Color(p.getLong("customColor", 0xFF3B5BFF))
        colorStyle.value = p.getInt("colorStyle", 0).coerceIn(0, 2)
        colorTemp.value = p.getFloat("colorTemp", 0f).coerceIn(-20f, 20f)
        bgImagePath.value = p.getString("bgImage", "") ?: ""
        bgCropMode.value = runCatching {
            BgCrop.valueOf(p.getString("bgCrop", "CENTER") ?: "CENTER")
        }.getOrDefault(BgCrop.CENTER)
        scheduleFontScale.value = p.getInt("fontScale", 1).coerceIn(0, 2)
        showWeekend.value = p.getBoolean("showWeekend", true)
    }

    fun setThemeMode(c: Context, m: ThemeMode) {
        themeMode.value = m
        prefs(c).edit().putString("themeMode", m.name).apply()
    }

    fun setAcrylic(c: Context, on: Boolean) {
        acrylicEnabled.value = on
        prefs(c).edit().putBoolean("acrylic", on).apply()
    }

    fun setColorIndex(c: Context, idx: Int) {
        themeColorIndex.value = idx.coerceIn(0, 8)
        prefs(c).edit().putInt("colorIdx", themeColorIndex.value).apply()
    }

    fun setCustomColor(c: Context, color: Color) {
        customColor.value = color
        themeColorIndex.value = 8
        prefs(c).edit()
            .putLong("customColor", color.value.toLong())
            .putInt("colorIdx", 8)
            .apply()
    }

    fun setBgImage(c: Context, path: String) {
        bgImagePath.value = path
        bgNonce.value = bgNonce.value + 1
        prefs(c).edit().putString("bgImage", path).apply()
    }

    fun setBgCrop(c: Context, crop: BgCrop) {
        bgCropMode.value = crop
        bgNonce.value = bgNonce.value + 1
        prefs(c).edit().putString("bgCrop", crop.name).apply()
    }

    fun setFontScale(c: Context, v: Int) {
        scheduleFontScale.value = v.coerceIn(0, 2)
        prefs(c).edit().putInt("fontScale", scheduleFontScale.value).apply()
    }

    fun setColorStyle(c: Context, v: Int) {
        colorStyle.value = v.coerceIn(0, 2)
        prefs(c).edit().putInt("colorStyle", colorStyle.value).apply()
    }

    fun setColorTemp(c: Context, v: Float) {
        colorTemp.value = v.coerceIn(-20f, 20f)
        prefs(c).edit().putFloat("colorTemp", colorTemp.value).apply()
    }

    fun setShowWeekend(c: Context, on: Boolean) {
        showWeekend.value = on
        prefs(c).edit().putBoolean("showWeekend", on).apply()
    }
}
