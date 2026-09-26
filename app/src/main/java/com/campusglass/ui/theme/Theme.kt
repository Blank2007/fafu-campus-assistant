package com.campusglass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 玻璃底色（配合极光背景使用）——强制浅色模式
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
 * 全局主题：按需求**强制浅色**（不提供深色模式）。
 * Material Design 3 配色，Android 12+ 取动态取色（仅浅色）。
 */
@Composable
fun CampusGlassTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor) {
        dynamicLightColorScheme(context)
    } else {
        LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
