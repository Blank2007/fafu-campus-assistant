package com.campusglass.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效令牌。
 *
 * 第一阶段：完全使用 Android 原生（Jetpack Compose / MotionSpec）动画。
 * 优先适配 Android 17（小米澎湃 OS 4 beta）的"流体动效"手感：
 * 偏低阻尼 + 中等刚度的弹簧，接近 HyperOS 系统曲线；
 * 同时启用系统预测式返回（Manifest 中 enableOnBackInvokedCallback），
 * 跟随系统动效缩放（开发者选项/无障碍的动画时长缩放同样生效）。
 */
object Motion {

    /** 界面切换：流体弹簧（HyperOS 风格） */
    val fluid = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)

    /** 卡片/按钮按压回弹 */
    val press = spring<Float>(dampingRatio = 0.55f, stiffness = 700f)

    /** 大块内容位移（如跳转教学楼） */
    val navigate = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)

    /** 淡入淡出（fade-through，原生强调） */
    val fadeThrough = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)

    /** 短促微动效 */
    val quick = tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)
}
