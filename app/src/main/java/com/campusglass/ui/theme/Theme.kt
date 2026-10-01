package com.campusglass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BFF),
    secondary = Color(0xFF2AA88C),
    tertiary = Color(0xFFE0803A),
    surface = Color(0xFFF7F9FF),
    background = Color(0xFFEEF2FC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    secondary = Color(0xFF7FDCCB),
    tertiary = Color(0xFFFFC08A),
    surface = Color(0xFF171A24),
    background = Color(0xFF10121A),
)

/**
 * 全局主题：
 * - 深色三态：跟随系统 / 浅色 / 深色（所有文字/标题颜色随主题切换）
 * - 主题色：系统动态取色(0) / 7 推荐色(1..7) / 自定义调色盘(8)
 */
@Composable
fun CampusGlassTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (ThemePrefs.themeMode.value) {
        ThemePrefs.ThemeMode.DARK -> true
        ThemePrefs.ThemeMode.LIGHT -> false
        ThemePrefs.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val idx = ThemePrefs.themeColorIndex.value
    val primary = when {
        idx == 0 -> null                       // 系统主题色（动态）
        idx <= ThemePrefs.THEME_COLORS.size -> ThemePrefs.THEME_COLORS[idx - 1].second
        else -> ThemePrefs.customColor.value   // 自定义
    }?.let { adjustColorStyle(it) }            // 色彩风格 + 色温

    val colorScheme = when {
        primary == null && dynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        primary == null ->
            if (dark) DarkColors else LightColors
        dark -> DarkColors.copy(primary = primary)
        else -> LightColors.copy(primary = primary)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** 色彩风格（标准/鲜艳/柔和）+ 色温（冷	o 暖）；style/temp 可临时传入用于预览（SET-1） */
fun adjustColorStyle(
    c: Color,
    style: Int = ThemePrefs.colorStyle.value,
    temp: Float = ThemePrefs.colorTemp.value,
): Color {
    if (style == 0 && temp == 0f) return c  // 标准+中性：零漂移，防偏色
    val hs = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hs)
    hs[1] = (hs[1] * when (style) {
        1 -> 1.35f   // 鲜艳
        2 -> 0.55f   // 柔和
        else -> 1f   // 标准
    }).coerceIn(0f, 1f)
    hs[0] = (hs[0] + temp * 1.2f + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hs))
}
