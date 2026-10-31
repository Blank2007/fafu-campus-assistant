package com.campusglass.ui.motion

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * 动效偏好（设置 →「✨ 动效」卡片可改，持久化到 ui_prefs，与 ThemePrefs 同一个 SharedPreferences）。
 *
 * 五个旋钮，全部实时生效、无需重启：
 * 1. [speed]       动画速度（时长 ÷ 倍率，弹簧按刚度反比缩放）
 * 2. [press]       按压反馈强度（按下缩多少、松手回弹多明显）
 * 3. [tabStyle]    底栏 Tab 切换方式（淡入淡出 / 无动画）
 * 4. [reduceMotion] 减少动效（无障碍：去掉位移与缩放，只留短淡入淡出）
 * 5. [barAutoHide] 底栏是否随下滑自动隐藏
 *
 * 取值全部存枚举下标（Int），新增档位只会往后加，不会让老配置错位。
 */
object MotionPrefs {

    /** 动画速度：factor = 播放速度倍率，时长 ÷ factor、弹簧刚度 × factor² */
    enum class Speed(val label: String, val factor: Float) {
        VERY_SLOW("很慢", 0.55f),
        SLOW("慢", 0.8f),
        NORMAL("标准", 1.0f),
        FAST("快", 1.35f),
    }

    /** 按压反馈：strength = 缩放强度倍率，0 表示关闭 */
    enum class Press(val label: String, val strength: Float) {
        OFF("关闭", 0f),
        NORMAL("标准", 1f),
        STRONG("明显", 1.8f),
    }

    enum class TabStyle(val label: String) {
        FADE("淡入淡出"),
        NONE("无动画"),
    }

    val speed = mutableStateOf(Speed.NORMAL)
    val press = mutableStateOf(Press.NORMAL)
    val tabStyle = mutableStateOf(TabStyle.FADE)
    val reduceMotion = mutableStateOf(false)
    val barAutoHide = mutableStateOf(true)

    private fun prefs(c: Context) = c.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        speed.value = Speed.entries.getOrElse(p.getInt("motionSpeed", 2)) { Speed.NORMAL }
        press.value = Press.entries.getOrElse(p.getInt("motionPress", 1)) { Press.NORMAL }
        tabStyle.value = TabStyle.entries.getOrElse(p.getInt("motionTab", 0)) { TabStyle.FADE }
        reduceMotion.value = p.getBoolean("motionReduce", false)
        barAutoHide.value = p.getBoolean("motionBarHide", true)
    }

    fun setSpeed(c: Context, v: Speed) {
        speed.value = v
        prefs(c).edit().putInt("motionSpeed", v.ordinal).apply()
    }

    fun setPress(c: Context, v: Press) {
        press.value = v
        prefs(c).edit().putInt("motionPress", v.ordinal).apply()
    }

    fun setTabStyle(c: Context, v: TabStyle) {
        tabStyle.value = v
        prefs(c).edit().putInt("motionTab", v.ordinal).apply()
    }

    fun setReduceMotion(c: Context, v: Boolean) {
        reduceMotion.value = v
        prefs(c).edit().putBoolean("motionReduce", v).apply()
    }

    fun setBarAutoHide(c: Context, v: Boolean) {
        barAutoHide.value = v
        prefs(c).edit().putBoolean("motionBarHide", v).apply()
    }

    /** 是否全是默认值（设置页「恢复默认」按钮的置灰判断用） */
    fun isDefault(): Boolean =
        speed.value == Speed.NORMAL && press.value == Press.NORMAL &&
            tabStyle.value == TabStyle.FADE && !reduceMotion.value && barAutoHide.value

    fun reset(c: Context) {
        speed.value = Speed.NORMAL
        press.value = Press.NORMAL
        tabStyle.value = TabStyle.FADE
        reduceMotion.value = false
        barAutoHide.value = true
        prefs(c).edit()
            .putInt("motionSpeed", Speed.NORMAL.ordinal)
            .putInt("motionPress", Press.NORMAL.ordinal)
            .putInt("motionTab", TabStyle.FADE.ordinal)
            .putBoolean("motionReduce", false)
            .putBoolean("motionBarHide", true)
            .apply()
    }
}
