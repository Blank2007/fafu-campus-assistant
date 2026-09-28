package com.campusglass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 玻璃底色常量（历史遗留保留）
val GlassTintLight = Color.White.copy(alpha = 0.32f)
val GlassEdgeHighlight = Color.White.copy(alpha = 0.85f)
val GlassEdgeShadow = Color.Black.copy(alpha = 0.30f)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BFF),
    secondary = Color(0xFF2AA88C),
    tertiary = Color(0xFFE0803A),
    surface = Color(0xFFF7F9FF),
    background = Color(0xFFEEF2FC),
)

/**
 * 全局主题：强制浅色 + 可选主题色（设置页可改，0=跟随系统动态取色）。
 */
@Composable
fun CampusGlassTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val idx = ThemePrefs.themeColorIndex.value
    val colorScheme = when {
        idx == 0 && dynamicColor -> dynamicLightColorScheme(context)
        idx == 0 -> LightColors
        else -> LightColors.copy(primary = ThemePrefs.THEME_COLORS[idx].second)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
