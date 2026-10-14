package com.campusglass

import com.campusglass.pickup.ExpressApi
import com.campusglass.pickup.ExpressHistory
import com.campusglass.pickup.ExpressHistory.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v4.15：快递模块的纯逻辑单测（无 Android 依赖） */
class ExpressLogicTest {

    // ---------------- ExpressHistory.parse ----------------

    @Test
    fun parse_oldFormat_plainNumbers() {
        val list = ExpressHistory.parse("SF1234567890\nYT0000000000")
        assertEquals(2, list.size)
        assertEquals("SF1234567890", list[0].nu)
        assertEquals("", list[0].com)          // 旧格式没有公司/状态
        assertEquals("", list[0].state)
        assertEquals(0L, list[0].time)
    }

    @Test
    fun parse_newFormat_fields() {
        val raw = "SF1234567890\t顺丰速运\t派件中 🚚\t1700000000000"
        val e = ExpressHistory.parse(raw).single()
        assertEquals("SF1234567890", e.nu)
        assertEquals("顺丰速运", e.com)
        assertEquals("派件中 🚚", e.state)
        assertEquals(1700000000000L, e.time)
    }

    @Test
    fun parse_ignoresBlankAndBadLines_andDedupes() {
        val raw = "\n  \nSF123\nsf123\n\t\t\t\nYT999\t圆通速递"
        val list = ExpressHistory.parse(raw)
        assertEquals(2, list.size)             // SF123 / sf123 视为同一条
        assertEquals("SF123", list[0].nu)
        assertEquals("YT999", list[1].nu)
    }

    @Test
    fun parse_nullOrEmpty_isEmpty() {
        assertTrue(ExpressHistory.parse(null).isEmpty())
        assertTrue(ExpressHistory.parse("").isEmpty())
        assertTrue(ExpressHistory.parse("   \n  ").isEmpty())
    }

    @Test
    fun parse_capsAtMax() {
        val raw = (1..30).joinToString("\n") { "NU$it" }
        assertEquals(ExpressHistory.MAX, ExpressHistory.parse(raw).size)
    }

    // ---------------- 编解码往返 ----------------

    @Test
    fun encodeParse_roundTrip() {
        val src = listOf(
            Entry("SF1234567890", "顺丰速运", "已签收 ✔", 1700000000000L),
            Entry("YT999", "圆通速递", "运输中", 1700000001000L),
            Entry("ZT777"),                                   // 全空字段也要能往返
        )
        assertEquals(src, ExpressHistory.parse(ExpressHistory.encode(src)))
    }

    @Test
    fun encode_replacesTabInsideFields() {
        // 公司/状态里混入制表符不能把列串掉
        val e = Entry("SF1", "顺\t丰", "运\t输中", 1L)
        val back = ExpressHistory.parse(ExpressHistory.encode(listOf(e))).single()
        assertEquals("SF1", back.nu)
        assertEquals("顺 丰", back.com)
        assertEquals("运 输中", back.state)
    }

    // ---------------- upsert ----------------

    @Test
    fun upsert_movesToTopAndDedupesCaseInsensitively() {
        val list = listOf(Entry("A1"), Entry("B2"), Entry("C3"))
        val out = ExpressHistory.upsert(list, Entry("b2", time = 9L))
        assertEquals(listOf("B2", "A1", "C3"), out.map { it.nu })
        assertEquals(9L, out[0].time)
    }

    @Test
    fun upsert_blankFieldsKeepOldValues() {
        val list = listOf(Entry("SF1", "顺丰速运", "派件中 🚚", 100L))
        // 开始查询：只传单号与新时间 → 公司与状态必须保留（否则历史会闪空）
        val out = ExpressHistory.upsert(list, Entry("SF1", time = 200L))
        assertEquals("顺丰速运", out[0].com)
        assertEquals("派件中 🚚", out[0].state)
        assertEquals(200L, out[0].time)
    }

    @Test
    fun upsert_nonBlankFieldsOverwrite() {
        val list = listOf(Entry("SF1", "顺丰速运", "派件中 🚚", 100L))
        val out = ExpressHistory.upsert(list, Entry("SF1", "顺丰速运", "已签收 ✔", 300L))
        assertEquals("已签收 ✔", out[0].state)
    }

    @Test
    fun upsert_capsAtMax() {
        var list = emptyList<Entry>()
        for (i in 1..15) list = ExpressHistory.upsert(list, Entry("NU$i", time = i.toLong()))
        assertEquals(ExpressHistory.MAX, list.size)
        assertEquals("NU15", list.first().nu)          // 最新的在最前
    }

    // ---------------- Entry.signed / subtitle ----------------

    @Test
    fun entry_signedFlag() {
        assertTrue(Entry("A", state = "已签收 ✔").signed)
        assertTrue(Entry("A", state = "已妥投").signed)
        assertFalse(Entry("A", state = "派件中 🚚").signed)
        assertFalse(Entry("A", state = "拒签").signed)     // 拒签不是签收
        assertFalse(Entry("A").signed)
    }

    @Test
    fun subtitle_omitsBlankParts() {
        assertEquals("", ExpressHistory.subtitle(Entry("A"), 0L))
        assertEquals("顺丰速运", ExpressHistory.subtitle(Entry("A", com = "顺丰速运"), 0L))
        assertEquals("顺丰速运 · 运输中", ExpressHistory.subtitle(Entry("A", "顺丰速运", "运输中"), 0L))
    }

    // ---------------- relativeTime ----------------

    @Test
    fun relativeTime_branches() {
        val now = 1_700_000_000_000L
        assertEquals("", ExpressHistory.relativeTime(now, 0L))
        assertEquals("刚刚", ExpressHistory.relativeTime(now, now - 5_000L))
        assertEquals("刚刚", ExpressHistory.relativeTime(now, now + 5_000L))    // 时钟回拨也不能炸
        assertEquals("5 分钟前", ExpressHistory.relativeTime(now, now - 5 * 60_000L))
        assertEquals("3 小时前", ExpressHistory.relativeTime(now, now - 3 * 3_600_000L))
        assertEquals("2 天前", ExpressHistory.relativeTime(now, now - 2 * 86_400_000L))
        assertTrue(ExpressHistory.relativeTime(now, now - 30L * 86_400_000L).endsWith("日"))
    }

    // ---------------- ExpressApi.guessCompanies（离线识别） ----------------

    @Test
    fun guessCompanies_byPrefix() {
        assertEquals(listOf("顺丰速运"), ExpressApi.guessCompanies("SF1234567890"))
        assertEquals(listOf("圆通速递"), ExpressApi.guessCompanies("YT0000000000"))
        assertEquals(listOf("京东物流"), ExpressApi.guessCompanies("JD1234567890123"))
        assertEquals(listOf("德邦快递"), ExpressApi.guessCompanies("DBL123456789"))
    }

    @Test
    fun guessCompanies_pureDigits_fallsBackToPost() {
        assertEquals(listOf("邮政包裹"), ExpressApi.guessCompanies("1234567890123"))
    }

    @Test
    fun guessCompanies_fullWidthAndSpacesAreNormalized() {
        assertEquals(listOf("顺丰速运"), ExpressApi.guessCompanies("ＳＦ 123 456 7890"))
    }

    @Test
    fun guessCompanies_tooShortOrUnknown_isEmpty() {
        assertTrue(ExpressApi.guessCompanies("SF").isEmpty())          // 太短必然误报
        assertTrue(ExpressApi.guessCompanies("ZZ1234567890").isEmpty()) // 未知前缀
        assertTrue(ExpressApi.guessCompanies("").isEmpty())
    }

    // ---------------- 支持清单（v4.16，UI 展示用） ----------------

    @Test
    fun supportedCouriers_isNonEmptyDistinctAndCoversMajorOnes() {
        val list = ExpressApi.SUPPORTED_COURIERS
        assertTrue(list.size >= 10)
        assertEquals(list.size, list.distinct().size)          // 不能有重复项
        assertTrue(list.none { it.isBlank() })
        for (must in listOf("顺丰速运", "圆通速递", "中通快递", "申通快递", "韵达速递", "极兔速递", "京东物流")) {
            assertTrue("支持清单缺少 $must", list.contains(must))
        }
    }

    @Test
    fun supportedCouriers_matchPrefixTableNames() {
        // 清单必须覆盖离线前缀表里所有能自动识别的公司，否则 UI 会漏报
        for (nu in listOf("SF1234567890", "YT0000000000", "ZT1234567890", "ST1234567890",
                          "YD1234567890", "JT1234567890", "JD1234567890", "DBL123456789", "EMS123456789")) {
            val g = ExpressApi.guessCompanies(nu)
            assertTrue("清单缺少 $g", g.all { ExpressApi.SUPPORTED_COURIERS.contains(it) })
        }
    }

    // ---------------- 占位轨迹判定（v4.17：不再把"查无结果"当轨迹） ----------------

    @Test
    fun placeholderTrace_detectsEmptyAndNoResult() {
        assertTrue(ExpressApi.isPlaceholderTrace(""))
        assertTrue(ExpressApi.isPlaceholderTrace("   "))
        assertTrue(ExpressApi.isPlaceholderTrace("查无结果"))
        assertTrue(ExpressApi.isPlaceholderTrace("暂无结果"))
        assertTrue(ExpressApi.isPlaceholderTrace("暂时查无结果，请稍后再试"))
    }

    @Test
    fun placeholderTrace_keepsRealTraces() {
        // 真实轨迹里出现"查"或"结果"这类字眼也不能被误判
        assertFalse(ExpressApi.isPlaceholderTrace("您的快件已投递，收件人:3栋大堂"))
        assertFalse(ExpressApi.isPlaceholderTrace("【广东省珠海市香洲区】正在为您派件"))
        assertFalse(ExpressApi.isPlaceholderTrace("快件已到达【佛山转运中心】"))
        assertFalse(ExpressApi.isPlaceholderTrace("查询结果已更新，快件在【郑州转运中心】"))
    }
}
