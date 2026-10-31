package com.campusglass

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.TweenSpec
import com.campusglass.ui.motion.buildMotionSpec
import com.campusglass.ui.motion.dur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S26：动效令牌单测（v5.5「动效可自定义」）。
 *
 * 盯的是三件容易在重构里被悄悄改坏的事：
 * 1. 默认档必须是"标准"——不能因为改了常量就把默认速度调得忽快忽慢；
 * 2. 用户选的档位必须真的作用到令牌上（速度→时长/刚度，按压→开关与强度）；
 * 3. 「减少动效」与「无动画」必须真的把位移/缩放拿掉（无障碍要求，不能只改数字）。
 */
class MotionSpecTest {

    /** 默认构造 = 设置页「标准 / 标准 / 淡入淡出 / 不减少 / 自动隐藏」 */
    @Test
    fun defaultSpecIsStandard() {
        val m = buildMotionSpec()
        assertTrue("默认应启用按压反馈", m.pressEnabled)
        assertFalse("默认不应是减少动效", m.reduceMotion)
        assertTrue("默认底栏自动隐藏", m.barAutoHide)
        assertEquals(1f, m.pressStrength, 0.0001f)
    }

    /** 速度档位：快档的淡入时长必须比慢档短，且都比标准档更偏离 */
    @Test
    fun speedScalesDuration() {
        val slow = buildMotionSpec(speed = 0.55f).fadeThrough
        val normal = buildMotionSpec(speed = 1f).fadeThrough
        val fast = buildMotionSpec(speed = 1.35f).fadeThrough
        assertTrue("慢档时长 > 标准档", slow.durationMillis > normal.durationMillis)
        assertTrue("标准档时长 > 快档", normal.durationMillis > fast.durationMillis)
    }

    /** 速度也作用到弹簧刚度：慢档刚度必须低于快档（时长 ÷ 倍率、刚度 × 倍率²） */
    @Test
    fun speedScalesSpringStiffness() {
        val slow = buildMotionSpec(speed = 0.8f).fluid.stiffness
        val fast = buildMotionSpec(speed = 1.35f).fluid.stiffness
        assertTrue("慢档弹簧刚度 < 快档", slow < fast)
    }

    /** 时长换算的边界：夹在 40..1200ms，任何速度都不会算出 0 或长到卡住 */
    @Test
    fun durationIsClamped() {
        assertEquals(1200, dur(260, 0.01f))
        assertEquals(40, dur(260, 100f))
        assertEquals(260, dur(260, 1f))
    }

    /** 按压反馈选「关闭」⇒ 不再启用按压动画，强度归零 */
    @Test
    fun pressOffDisablesPressAnimation() {
        val m = buildMotionSpec(pressStrength = 0f)
        assertFalse("关闭按压反馈后不应再缩放", m.pressEnabled)
        assertEquals(0f, m.pressStrength, 0.0001f)
    }

    /** 按压反馈选「明显」⇒ 强度倍率 > 1（各处按得比标准更狠） */
    @Test
    fun pressStrongExceedsStandard() {
        val strong = buildMotionSpec(pressStrength = 1.8f)
        assertTrue("明显档强度应 > 1", strong.pressStrength > 1f)
        assertTrue(strong.pressEnabled)
    }

    /** Tab 选「无动画」⇒ 进出场必须是 EnterTransition.None（不是 0 时长的淡入） */
    @Test
    fun tabNoneIsTrulyNone() {
        val m = buildMotionSpec(tabFade = false)
        assertSame(EnterTransition.None, m.tabEnter)
        assertSame(ExitTransition.None, m.tabExit)
    }

    /** 「减少动效」⇒ 关掉按压缩放、标记 reduceMotion，供调用方跳过 Crossfade / FAB 缩放 */
    @Test
    fun reduceMotionDisablesPressAndFlagsCallers() {
        val m = buildMotionSpec(reduceMotion = true)
        assertTrue("应标记 reduceMotion", m.reduceMotion)
        assertFalse("减少动效时不应缩放按压", m.pressEnabled)
        assertEquals(0f, m.pressStrength, 0.0001f)
        // 但底栏自动隐藏这类"用户主动选的"开关必须原样保留
        assertTrue(buildMotionSpec(reduceMotion = true, barAutoHide = false).barAutoHide.not())
    }
}
