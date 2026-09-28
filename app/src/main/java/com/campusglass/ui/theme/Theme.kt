package com.campusglass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
 * 全局主题：深色模式开关（设置页） + 可选主题色（0=跟随系统动态取色）。
 */
@Composable
fun CampusGlassTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = ThemePrefs.darkMode.value
    val idx = ThemePrefs.themeColorIndex.value
    val colorScheme = when {
        idx == 0 && dynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        idx == 0 ->
            if (dark) DarkColors else LightColors
        dark ->
            DarkColors.copy(primary = ThemePrefs.THEME_COLORS[idx].second)
        else ->
            LightColors.copy(primary = ThemePrefs.THEME_COLORS[idx].second)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
