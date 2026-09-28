package com.campusglass.ui.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/**
 * 亚克力（Acrylic）模糊材质 —— 底部悬浮栏用。
 * 背景模糊来自开源库 Haze（Chris Banes，Apache-2.0）。
 */

@Composable
fun rememberGlassState(): HazeState = rememberHazeState()

/** 标记"玻璃背后的背景层" */
fun Modifier.glassBackground(state: HazeState): Modifier = this.hazeSource(state)

/** 亚克力模糊修饰符：模糊 + 半透明着色 */
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
        .clip(shape)
        .hazeBlur(HazeInput.Backdrop(state), style)
}
