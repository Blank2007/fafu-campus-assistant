package com.campusglass.ui.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.campusglass.ui.theme.ThemePrefs
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.border

/**
 * 亚克力（Acrylic）材质体系。
 * - 有自定义背景图时：全局卡片/栏位使用亚克力模糊（看得见背后图片）
 * - 无自定义背景（默认纯色）：自动退回普通不透明卡片（默认不用亚克力）
 */

val LocalGlass = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun rememberGlassState(): HazeState = rememberHazeState()

/** 标记"玻璃背后的背景层" */
fun Modifier.glassBackground(state: HazeState): Modifier = this.hazeSource(state)

/** 亚克力模糊修饰符 */
fun Modifier.acrylic(
    state: HazeState,
    dark: Boolean,
    blurRadius: Dp = 26.dp,
    shape: Shape = RoundedCornerShape(26.dp),
): Modifier {
    val tint = if (dark) Color(0x9920222C) else Color(0xCCFFFFFF)
    val style = HazeBlurStyle {
        blurEnabled(true)
        blurRadius(blurRadius)
        noiseFactor(0.05f)
        colorEffects(listOf(HazeColorEffect.tint(tint)))
    }
    return this
        .hazeBlur(HazeInput.Backdrop(state), style)   // 先模糊
        .clip(shape)                                   // 后裁切：模糊不溢出圆角，消除毛边框
}

/** 全局卡片：有自定义背景时亚克力，否则普通卡片 */
@Composable
fun AcrylicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit,
) {
    val glass = LocalGlass.current
    val customBg = ThemePrefs.bgImagePath.value.isNotBlank()
    val dark = when (ThemePrefs.themeMode.value) {
        ThemePrefs.ThemeMode.DARK -> true
        ThemePrefs.ThemeMode.LIGHT -> false
        ThemePrefs.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    if (glass != null && customBg && ThemePrefs.acrylicEnabled.value) {
        Surface(
            modifier = modifier.acrylic(glass, dark, blurRadius = 22.dp, shape = shape),
            shape = shape,
            color = Color.Transparent,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            elevation = CardDefaults.cardElevation(2.dp),
        ) { content() }
    }
}
