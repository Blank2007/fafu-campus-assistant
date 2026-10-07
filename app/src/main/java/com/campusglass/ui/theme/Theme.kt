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
    // T5：补全角色（不再落到 M3 偏紫灰基线）
    onSurface = Color(0xFF1A1C22),
    onBackground = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFE3E8F5),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFFB6C0D4),
    outlineVariant = Color(0xFFD4DBE8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5FD),
    surfaceContainer = Color(0xFFECF0FA),
    surfaceContainerHigh = Color(0xFFE6EBF6),
    surfaceContainerHighest = Color(0xFFE0E6F2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    secondary = Color(0xFF7FDCCB),
    tertiary = Color(0xFFFFC08A),
    surface = Color(0xFF171A24),
    background = Color(0xFF10121A),
    // T5：补全角色
    onSurface = Color(0xFFE6E8F0),
    onBackground = Color(0xFFE6E8F0),
    surfaceVariant = Color(0xFF2A2E3C),
    onSurfaceVariant = Color(0xFFC2C7D5),
    outline = Color(0xFF5A6275),
    outlineVariant = Color(0xFF3A4052),
    surfaceContainerLowest = Color(0xFF0B0D14),
    surfaceContainerLow = Color(0xFF12141E),
    surfaceContainer = Color(0xFF171A26),
    surfaceContainerHigh = Color(0xFF1D2130),
    surfaceContainerHighest = Color(0xFF242838),
)

/** T6：由主色旋转色相派生次要/强调色（避免选粉后青绿撞色） */
private fun rotateHue(c: Color, deg: Float): Color {
    val hs = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hs)
    hs[0] = (hs[0] + deg + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hs))
}

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
        dark -> DarkColors.copy(
            primary = primary,
            secondary = rotateHue(primary, 45f),      // T6
            tertiary = rotateHue(primary, 90f),
        )
        else -> LightColors.copy(
            primary = primary,
            secondary = rotateHue(primary, 45f),      // T6
            tertiary = rotateHue(primary, 90f),
        )
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
