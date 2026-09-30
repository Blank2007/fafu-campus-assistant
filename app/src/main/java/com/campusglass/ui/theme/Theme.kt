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

/**
 * v3.3：补齐容器/描边等颜色角色。
 * 旧版只覆盖 primary/secondary/tertiary/surface/background，其余角色（surfaceVariant、
 * surfaceContainer*、outline…）全部落到 Material3 默认的紫调调色板，导致 Card/AlertDialog/
 * FilterChip 的底色与「蓝调」背景不是一套色（深色模式下尤其明显）。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BFF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2AA88C),
    tertiary = Color(0xFFE0803A),
    background = Color(0xFFEEF2FC),
    onBackground = Color(0xFF1A1C22),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFE2E8F6),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF747A88),
    outlineVariant = Color(0xFFC6CCDA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5FD),
    surfaceContainer = Color(0xFFEDF1FA),
    surfaceContainerHigh = Color(0xFFE7ECF7),
    surfaceContainerHighest = Color(0xFFE1E7F4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF12203F),
    secondary = Color(0xFF7FDCCB),
    tertiary = Color(0xFFFFC08A),
    background = Color(0xFF10121A),
    onBackground = Color(0xFFE3E6EF),
    surface = Color(0xFF171A24),
    onSurface = Color(0xFFE3E6EF),
    surfaceVariant = Color(0xFF3A3F4B),
    onSurfaceVariant = Color(0xFFC3C7D2),
    outline = Color(0xFF8E929E),
    outlineVariant = Color(0xFF44485A),
    surfaceContainerLowest = Color(0xFF0C0E15),
    surfaceContainerLow = Color(0xFF1B1F2A),
    surfaceContainer = Color(0xFF20242F),
    surfaceContainerHigh = Color(0xFF262B37),
    surfaceContainerHighest = Color(0xFF2C313D),
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

/**
 * 色彩风格（标准/鲜艳/柔和）+ 色温（冷 ↔ 暖）→ 小米「色彩风格」式调整。
 * style/temp 可显式传入，便于调色盘在不改动全局设置的前提下预览效果。
 */
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
