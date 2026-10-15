package com.campusglass

import com.campusglass.update.compareVersion
import com.campusglass.update.verParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v4.18：版本比较逻辑单测。
 *
 * 这段逻辑原先内联在 SettingsScreen.checkUpdate() 里，v4.18 抽到 update/UpdateCheck.kt，
 * 供「设置页手动检查」与「启动自动检查」共用 —— 抽出来后必须锁住行为，避免以后改坏导致
 * 自动检查误报（把旧版当新版弹窗，或把新版当旧版漏报）。
 */
class UpdateVersionTest {

    private fun cmp(latest: String, current: String) = compareVersion(verParts(latest), verParts(current))

    @Test
    fun plainVersions() {
        assertTrue(cmp("4.18", "4.17") > 0)
        assertTrue(cmp("4.17", "4.18") < 0)
        assertEquals(0, cmp("4.17", "4.17"))
    }

    @Test
    fun suffixStyles_areIgnored() {
        // 兼容 3.2 / v3.2.0 / 3.2.zilyf / 4.17.sljzy 等写法：非数字后缀不参与比较
        assertEquals(0, cmp("4.17.sljzy", "4.17"))
        assertEquals(0, cmp("4.17", "4.17.0"))
        assertTrue(cmp("4.18.sljzy", "4.17.sljzy") > 0)
        assertTrue(cmp("4.17.1", "4.17.sljzy") > 0)
    }

    @Test
    fun multipleDigitSegments_notLexicographic() {
        // 关键回归点：不能按字符串比大小（"4.9" > "4.10" 会是错的）
        assertTrue(cmp("4.10", "4.9") > 0)
        assertTrue(cmp("4.100", "4.99") > 0)
        assertTrue(cmp("10.0", "9.9") > 0)
    }

    @Test
    fun differentLengths_padWithZero() {
        assertTrue(cmp("5", "4.17") > 0)
        assertTrue(cmp("4.17.1", "4.17") > 0)
        assertEquals(0, cmp("4.17.0.0", "4.17"))
    }

    @Test
    fun malformedInput_doesNotCrash() {
        assertEquals(0, cmp("", ""))
        assertEquals(emptyList<Int>(), verParts("abc"))
        assertTrue(cmp("v4.18", "4.17") > 0)   // 前缀 v 在调用方剥离，这里兜底不影响
    }
}
