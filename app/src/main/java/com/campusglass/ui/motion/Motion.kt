package com.campusglass.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * 动效令牌（v5.5 起真正生效的唯一动效来源，且**由用户决定手感**）。
 *
 * 手感目标：HyperOS / Material 3 的"流体"动效——偏低阻尼 + 中等刚度的弹簧，
 * 位移用弹簧、透明度用短 tween，跟手（按下就动）而不是等动画播完。
 *
 * 约定（改界面时照抄，别再手写毫秒）：
 * 1. 位移 / 尺寸 / 缩放 → 弹簧：[MotionSpec.fluid]、[MotionSpec.press]、[MotionSpec.navigate]
 * 2. 透明度 / 颜色 → tween：[MotionSpec.fadeThrough]、[MotionSpec.quick]
 * 3. 页面切换统一用 [MotionSpec.tabEnter] / [MotionSpec.tabExit]（fade-through，不做横向滑动）
 * 4. 展开 / 收起统一用 [MotionSpec.revealEnter] / [MotionSpec.revealExit]
 * 5. 可点卡片 / 按钮统一用 [pressable]（按下缩到 0.96 + 松手弹回）
 *
 * 取值来自 [MotionPrefs]（设置页可调），所以**不要在 composable 外缓存**：
 * 每次组合用 `val m = motionSpec()` 现取，用户改完设置立刻生效。
 *
 * 系统"动画时长缩放"（开发者选项 / 无障碍）由 Compose 的 MotionDurationScale 叠加缩放；
 * 用户打开「减少动效」时，本文件会把位移/缩放全部换成长度≈0 的淡入淡出。
 */
@Immutable
data class MotionSpec(
    /** 通用位移 / 缩放弹簧 */
    val fluid: SpringSpec<Float>,
    /** 按压松手回弹 */
    val press: SpringSpec<Float>,
    /** 大块内容位移（详情页进出） */
    val navigate: SpringSpec<Float>,
    /** 整块内容淡入淡出 */
    val fadeThrough: TweenSpec<Float>,
    /** 短促微动效 */
    val quick: TweenSpec<Float>,
    val tabEnter: EnterTransition,
    val tabExit: ExitTransition,
    val barEnter: EnterTransition,
    val barExit: ExitTransition,
    val revealEnter: EnterTransition,
    val revealExit: ExitTransition,
    /** 按压反馈是否启用（「关闭」或「减少动效」时为 false） */
    val pressEnabled: Boolean,
    /** 按压强度倍率，用于把各处的 0.96 / 0.93 放大或缩小 */
    val pressStrength: Float,
    /** 减少动效：装饰性动画（课表 Crossfade、FAB 缩放、色点放大）应由调用方跳过 */
    val reduceMotion: Boolean,
    /** 底栏是否随内容下滑自动隐藏 */
    val barAutoHide: Boolean,
)

/** 各档速度下统一的时长换算：慢 = 更长，快 = 更短，并夹在 40..1200 ms 之间 */
internal fun dur(base: Int, speed: Float): Int = (base / speed).roundToInt().coerceIn(40, 1200)

/** 弹簧刚度随速度平方缩放（时间缩短一半 = 刚度 ×4） */
private fun stiffness(base: Float, speed: Float): Float =
    (base * speed * speed).coerceIn(40f, 6000f)

/**
 * 按用户偏好构造一组令牌。纯函数、无副作用，方便预览与单测。
 *
 * @param speed 播放速度倍率（0.55 / 0.8 / 1.0 / 1.35）
 * @param pressStrength 按压强度（0 关闭 / 1 标准 / 1.8 明显）
 * @param tabFade Tab 切换是否做淡入淡出（false = 瞬切）
 * @param reduceMotion 减少动效（无障碍）
 * @param barAutoHide 底栏是否随下滑隐藏
 */
fun buildMotionSpec(
    speed: Float = 1f,
    pressStrength: Float = 1f,
    tabFade: Boolean = true,
    reduceMotion: Boolean = false,
    barAutoHide: Boolean = true,
): MotionSpec {
    val s = speed.coerceIn(0.3f, 3f)

    // 减少动效：所有过渡退化成 100ms 内的淡入淡出，不位移、不缩放、不回弹
    if (reduceMotion) {
        val fast = tween<Float>(durationMillis = 90, easing = FastOutSlowInEasing)
        return MotionSpec(
            fluid = spring(dampingRatio = 1f, stiffness = 1200f),
            press = spring(dampingRatio = 1f, stiffness = 1200f),
            navigate = spring(dampingRatio = 1f, stiffness = 1200f),
            fadeThrough = fast,
            quick = tween<Float>(durationMillis = 80, easing = FastOutSlowInEasing),
            tabEnter = fadeIn(tween(dur(110, s), easing = FastOutSlowInEasing)),
            tabExit = fadeOut(tween(dur(80, s), easing = FastOutSlowInEasing)),
            barEnter = fadeIn(tween(dur(100, s), easing = FastOutSlowInEasing)),
            barExit = fadeOut(tween(dur(80, s), easing = FastOutSlowInEasing)),
            revealEnter = fadeIn(tween(dur(100, s), easing = FastOutSlowInEasing)),
            revealExit = fadeOut(tween(dur(80, s), easing = FastOutSlowInEasing)),
            pressEnabled = false,
            pressStrength = 0f,
            reduceMotion = true,
            barAutoHide = barAutoHide,
        )
    }

    return MotionSpec(
        fluid = spring(dampingRatio = 0.85f, stiffness = stiffness(380f, s)),
        press = spring(dampingRatio = 0.55f, stiffness = stiffness(700f, s)),
        navigate = spring(dampingRatio = 0.9f, stiffness = stiffness(260f, s)),
        fadeThrough = tween<Float>(durationMillis = dur(260, s), easing = FastOutSlowInEasing),
        quick = tween<Float>(durationMillis = dur(140, s), easing = FastOutSlowInEasing),

        // Tab 切换：旧页先退，新页淡入 + 轻微放大 + 1/90 高度上浮（不做横向滑动）
        tabEnter = if (!tabFade) {
            EnterTransition.None
        } else {
            fadeIn(tween(dur(220, s), delayMillis = dur(40, s), easing = FastOutSlowInEasing)) +
                scaleIn(
                    tween(dur(260, s), delayMillis = dur(40, s), easing = FastOutSlowInEasing),
                    initialScale = 0.97f,
                ) +
                slideInVertically(
                    tween(dur(280, s), delayMillis = dur(40, s), easing = FastOutSlowInEasing),
                ) { height -> height / 90 }
        },
        tabExit = if (!tabFade) {
            ExitTransition.None
        } else {
            fadeOut(tween(dur(110, s), easing = FastOutSlowInEasing))
        },

        // 底栏 / 横幅：弹簧，从屏幕边缘带阻尼进出
        barEnter = slideInVertically(
            spring(
                dampingRatio = 0.86f,
                stiffness = stiffness(420f, s),
                visibilityThreshold = IntOffset.VisibilityThreshold,
            ),
        ) { height -> height } + fadeIn(tween(dur(120, s))),
        barExit = slideOutVertically(
            spring(
                dampingRatio = 1f,
                stiffness = stiffness(560f, s),
                visibilityThreshold = IntOffset.VisibilityThreshold,
            ),
        ) { height -> height } + fadeOut(tween(dur(100, s))),

        // 展开 / 收起（致谢、支持清单、轨迹…）
        revealEnter = expandVertically(
            animationSpec = spring(dampingRatio = 0.92f, stiffness = stiffness(420f, s)),
            expandFrom = Alignment.Top,
        ) + fadeIn(tween(dur(160, s), easing = FastOutSlowInEasing)),
        revealExit = shrinkVertically(
            animationSpec = spring(dampingRatio = 1f, stiffness = stiffness(520f, s)),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(tween(dur(110, s), easing = FastOutSlowInEasing)),

        pressEnabled = pressStrength > 0.01f,
        pressStrength = pressStrength.coerceIn(0f, 2f),
        reduceMotion = false,
        barAutoHide = barAutoHide,
    )
}

/** 默认令牌（无 Provider 时的兜底，等价于「标准」档） */
val DefaultMotionSpec: MotionSpec = buildMotionSpec()

/** 全局动效令牌；由 `rememberMotionSpec()` 在 App 根部提供 */
val LocalMotion = staticCompositionLocalOf { DefaultMotionSpec }

/** 取当前令牌（等价于 LocalMotion.current，语义更顺） */
@Composable
fun motionSpec(): MotionSpec = LocalMotion.current

/** 读用户偏好并构造令牌；偏好一变就重算，整个界面立刻跟着变 */
@Composable
fun rememberMotionSpec(): MotionSpec {
    val speed = MotionPrefs.speed.value.factor
    val press = MotionPrefs.press.value.strength
    val tabFade = MotionPrefs.tabStyle.value == MotionPrefs.TabStyle.FADE
    val reduce = MotionPrefs.reduceMotion.value
    val barAutoHide = MotionPrefs.barAutoHide.value
    return remember(speed, press, tabFade, reduce, barAutoHide) {
        buildMotionSpec(speed, press, tabFade, reduce, barAutoHide)
    }
}

/**
 * 可点区域统一手感：按下立即缩到 [pressedScale]（强度受设置页「按压反馈」调节），
 * 松手用弹簧弹回。
 *
 * 替代裸 `Modifier.clickable`：有按下去"有反应"的确认感，但不会像缩放动画那样拖沓
 * （按下用短 tween 跟手，松手才上弹簧）。按压反馈关闭时自动退化为纯 ripple。
 *
 * ⚠️ [onClick] 必须保持**最后一个参数**：否则 `.pressable(0.93f) { tab = t }` 的尾随
 * lambda 会绑到 [onClickLabel] 上（v5.5 改造时实际踩到过，编译器报
 * "No value passed for parameter 'onClick'"）。
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val strength = motion.pressStrength
    // 强度 1.0 时等于入参；1.8 时缩得更狠；0（关闭）时不动
    val targetScale = if (motion.pressEnabled && pressed && enabled) {
        (1f - (1f - pressedScale) * strength).coerceIn(0.80f, 1f)
    } else {
        1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (targetScale != 1f) {
            tween(durationMillis = dur(90, 1f), easing = FastOutSlowInEasing)
        } else {
            motion.press
        },
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}
